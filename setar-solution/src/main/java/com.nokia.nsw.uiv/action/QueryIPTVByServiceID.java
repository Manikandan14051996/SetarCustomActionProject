package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.QueryIPTVByServiceIDRequest;
import com.nokia.nsw.uiv.response.QueryIPTVByServiceIDResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@RestController
@Action
@Slf4j
public class QueryIPTVByServiceID implements HttpAction {

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Autowired
    private ProductCustomRepository productRepo;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepo;

    @Autowired
    private LogicalDeviceCustomRepository logicalDeviceRepo;

    protected static final String ACTION_LABEL = Constants.QUERY_IPTV_BY_SERVICE_ID;
    private static final String ERROR_PREFIX = "UIV action QueryIPTVByServiceID execution failed - ";

    @Override
    public Class<?> getActionClass() {
        return QueryIPTVByServiceIDRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error("Executing QueryIPTVByServiceID action...");
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        QueryIPTVByServiceIDRequest req = (QueryIPTVByServiceIDRequest) actionContext.getObject();

        try {
            // 1. Mandatory validation
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatoryParams(req.getServiceID(), "serviceId");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (Exception ex) {
                QueryIPTVByServiceIDResponse resp = new QueryIPTVByServiceIDResponse();
                resp.setStatus("400");
                resp.setMessage("Missing mandatory parameter(s)");
                resp.setTimestamp(Instant.now().toString());
                return resp;
            }

            String serviceId = req.getServiceID();

            // 2. Identify target CFS names
            Set<String> matchingCfsNames = new TreeSet<>();
            List<Service> allCFS = (List<Service>) serviceCustomRepository.findAll();
            List<Service> cfsServices=new ArrayList<>();
            for(Service s:allCFS)
            {
                if(s.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_CFS))
                {
                    cfsServices.add(s);
                }
            }
            for (Service cfs : cfsServices) {
                String cfsName = cfs.getDiscoveredName();
                if (cfsName == null) continue;
                // Middle portion (text between first '_' and last '_'), if present
                int firstUnd = cfsName.indexOf('_');
                int lastUnd = cfsName.lastIndexOf('_');
                if (firstUnd >= 0 && lastUnd > firstUnd) {
                    String middle = cfsName.substring(firstUnd + 1, lastUnd);
                    if (serviceId.equals(middle)) {
                        matchingCfsNames.add(cfsName);
                        continue;
                    }
                }
                // third segment when split by '_'
                String[] parts = cfsName.split(Constants.UNDER_SCORE );
                if (parts.length >= 3 && serviceId.equals(parts[2])) {
                    matchingCfsNames.add(cfsName);
                }
            }

            if (matchingCfsNames.isEmpty()) {
                QueryIPTVByServiceIDResponse resp = new QueryIPTVByServiceIDResponse();
                resp.setStatus("400");
                resp.setMessage("No entry found for delete");
                resp.setTimestamp(Instant.now().toString());
                return resp;
            }

            // 3. Prepare to collect IPTV details
            boolean successFlag = false;
            int apCounter = 1;
            int stbCounter = 1;
            int serviceComponentCounter = 1;

            Set<String> outputParameterNames = new HashSet<>();
            Map<String, String> iptvInfo = new TreeMap<>();

            // 4. Retrieve related entities for each CFS
            for (String cfsName : matchingCfsNames) {
                // try to load CFS by exact name (global name lookup may be required; using name directly)
                Optional<Service> optCfs;
                try {
                    optCfs = serviceCustomRepository.findByDiscoveredName(cfsName);
                } catch (Exception e) {
                    // fallback to find by iterating (we already have cfsName); try to get from repo.findAll
                    optCfs = findCfsByNameFromAll(cfsName);
                }
                if (!optCfs.isPresent()) continue;
                Service cfs = optCfs.get();

                // Determine corresponding RFS name by replacing CFS -> RFS in name
                String rfsName = cfsName.replaceFirst("^CFS", "RFS"); // only first occurrence
                Optional<Service> optRfs = Optional.empty();
                try {
                    optRfs = serviceCustomRepository.findByDiscoveredName(rfsName);
                } catch (Exception e) {
                    // fallback: find by iterating
                    optRfs = findRfsByNameFromAll(rfsName);
                }

                // From CFS get Product -> Subscription -> Subscriber
                Product product = null;
                try {
                    String productDiscName = cfs.getUsingService().stream().filter(ser->ser.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_PRODUCT)).findFirst().get().getDiscoveredName();
                    product = productRepo.findByDiscoveredName(productDiscName).get();
                } catch (Exception e) {
                    // defensive - attempt property-based retrieval if needed
                    product = null;
                }

                Subscription subscription = null;
                Customer customer = null;
                if (product != null) {
                    try {
                        String productDiscoveredName = product.getDiscoveredName();
                        product = productRepo.findByDiscoveredName(productDiscoveredName).get();
                        subscription = product.getSubscription().stream().findFirst().get();
                    } catch (Exception e) {
                        subscription = null;
                    }
                    if (subscription != null) {
                        try {
                            customer = subscription.getCustomer();
                        } catch (Exception e) {
                            customer = null;
                        }
                    }
                }

                // 5. Capture subscription details
                if (subscription != null) {
                    // Map legacy fields to UIV fields (defensive getters)
                    String customerGroupId = safeGet(subscription.getProperties(), "customerGroupId");
                    String serviceMac = safeGet(subscription.getProperties(), "serviceMAC");
                    String serviceLink = safeGet(subscription.getProperties(), "serviceLink");
                    String gatewayMac = safeGet(subscription.getProperties(), "gatewayMacAddress");
                    String cbmSubscriberId = safeGet(subscription.getProperties(), "subscriberID_CableModem");
                    // Put into iptvInfo
                    if (customerGroupId != null) {
                        iptvInfo.put("CUSTOMER_GROUP_ID", customerGroupId);
                        outputParameterNames.add("CUSTOMER_GROUP_ID");
                        successFlag = true;
                    }
                    if (serviceMac != null) {
                        iptvInfo.put("CPE_MacAddr_1", serviceMac);
                        outputParameterNames.add("CPE_MacAddr_1");
                        successFlag = true;
                    }
                    if (serviceLink != null) {
                        iptvInfo.put("Service_Link", serviceLink);
                        if (!outputParameterNames.contains("Service_Link")) outputParameterNames.add("Service_Link");
                        successFlag = true;
                        if ("Cable_Modem".equalsIgnoreCase(serviceLink) && cbmSubscriberId != null) {
                            iptvInfo.put("CBM_Subscriber_ID_1", cbmSubscriberId);
                            outputParameterNames.add("CBM_Subscriber_ID_1");
                        }
                    }
                    if (gatewayMac != null) {
                        iptvInfo.put("CPE_GW_MacAddr_1", gatewayMac);
                        outputParameterNames.add("CPE_GW_MacAddr_1");
                        successFlag = true;
                    }

                    // 6. Capture service components (products under subscription)
                    try {
                        Set<Service> prodSet = subscription.getService().stream().filter(ser->ser.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_PRODUCT)).collect(Collectors.toSet());
                        if (prodSet != null && !prodSet.isEmpty()) {
                            for (Service prodItem : prodSet) {
                                String prodName = prodItem.getDiscoveredName();
                                if (prodName != null && prodName.startsWith(serviceId)) {
                                    String comp = prodName.replace(serviceId, "");
                                    // remove underscores
                                    comp = comp.replace(Constants.UNDER_SCORE , "");
                                    String label = "Service_Component_" + serviceComponentCounter;
                                    iptvInfo.put(label, comp);
                                    outputParameterNames.add(label);
                                    serviceComponentCounter++;
                                    successFlag = true;
                                }
                            }
                        } else {
                            // fallback: product (containing product) maybe itself is the one; check product variable
                            if (product != null && product.getDiscoveredName() != null && product.getDiscoveredName().startsWith(serviceId)) {
                                String comp = product.getName().replaceFirst("^" + serviceId, "").replace(Constants.UNDER_SCORE , "");
                                String label = "Service_Component_" + serviceComponentCounter;
                                iptvInfo.put(label, comp);
                                outputParameterNames.add(label);
                                serviceComponentCounter++;
                                successFlag = true;
                            }
                        }
                    } catch (Exception e) {
                        // ignore - continue
                    }
                }

                // 7. Capture resource details from RFS, if RFS exists
                if (optRfs.isPresent()) {
                    Service rfs = optRfs.get();
                    try {
                        // Expecting rfs.getResource() or rfs.getResources() -> collection
                        Collection<?> resources = null;
                        try {
                            // try several method names defensively
                            Object resObj = rfs.getUsedResource();
                            if (resObj instanceof Collection) resources = (Collection<?>) resObj;
                        } catch (Throwable ignored) {}
                        if (resources == null) {
                            try {
                                Object resObj = rfs.getUsedResource();
                                if (resObj instanceof Collection) resources = (Collection<?>) resObj;
                            } catch (Throwable ignored) {}
                        }
                        if (resources == null) {
                            // fallback: try find logical devices whose properties/relations link to this RFS
                            resources = Collections.emptyList();
                        }

                        for (Object res : resources) {
                            // Expect resource to be LogicalDevice or something with getName/getProperties
                            String resName = null;
                            Map<String, Object> resProps = null;

                            if (res instanceof LogicalDevice) {
                                LogicalDevice ld = (LogicalDevice) res;
                                resName = ld.getDiscoveredName();
                                resProps = ld.getProperties();
                            } else {
                                // reflectively attempt to read getName/getProperties (best-effort)
                                try {
                                    resName = (String) res.getClass().getMethod("getDiscoveredName").invoke(res);
                                    Object propsObj = res.getClass().getMethod("getProperties").invoke(res);
                                    if (propsObj instanceof Map) resProps = (Map<String, Object>) propsObj;
                                } catch (Throwable ignored) {}
                            }
                            if (resName == null) continue;

                            // ONT
                            if (resName.contains("ONT")) {
                                successFlag = true;
                                iptvInfo.put("Service_Link", "ONT");
                                outputParameterNames.add("Service_Link");
                                String devModel = safeGet(resProps, "deviceModel");
                                String serialNo = safeGet(resProps, "serialNo");
                                if (devModel != null) {
                                    iptvInfo.put("CPE_Model_1", devModel);
                                    outputParameterNames.add("CPE_Model_1");
                                }
                                if (serialNo != null) {
                                    iptvInfo.put("CPE_Serial_Number_1", serialNo);
                                    outputParameterNames.add("CPE_Serial_Number_1");
                                }
                            }
                            // Cable Modem (CBM)
                            else if (resName.contains("CBM") || resName.startsWith("CBM")) {
                                successFlag = true;
                                iptvInfo.put("Service_Link", "Cable_Modem");
                                outputParameterNames.add("Service_Link");
                                String mac = safeGet(resProps, "macAddress");
                                String model = safeGet(resProps, "deviceModel"); // or deviceType
                                if (mac == null) mac = safeGet(resProps, "deviceMac");
                                if (model == null) model = safeGet(resProps, "deviceType");
                                if (mac != null) {
                                    iptvInfo.put("CBM_Device_MacAddr_1", mac);
                                    outputParameterNames.add("CBM_Device_MacAddr_1");
                                }
                                if (model != null) {
                                    iptvInfo.put("CBM_Device_Model_1", model);
                                    outputParameterNames.add("CBM_Device_Model_1");
                                }
                            }
                            // OLT (name contains ":" in legacy, in migrated may appear as a string with ":")
                            else if (resName.contains(":") || resName.startsWith("OLT") || resName.contains("OLT")) {
                                successFlag = true;
                                String ontObjId = resName;
                                outputParameterNames.add("ONT_OBJECT_ID");
                                iptvInfo.put("ONT_OBJECT_ID", ontObjId);

                                // read templates from properties
                                String ontTemplate = safeGet(resProps, "ontTemplate");
                                String veipIptvTemplate = safeGet(resProps, "veipIptvTemplate");
                                String igmpTemplate = safeGet(resProps, "igmpTemplate");
                                String veipServiceTemplate = safeGet(resProps, "veipServiceTemplate");
                                if (ontTemplate != null) {
                                    iptvInfo.put("TEMPLATE_NAME_ONT", ontTemplate);
                                    outputParameterNames.add("TEMPLATE_NAME_ONT");
                                }
                                if (veipIptvTemplate != null) {
                                    iptvInfo.put("TEMPLATE_NAME_IPTV", veipIptvTemplate);
                                    outputParameterNames.add("TEMPLATE_NAME_IPTV");
                                }
                                if (igmpTemplate != null) {
                                    iptvInfo.put("TEMPLATE_NAME_IGMP", igmpTemplate);
                                    outputParameterNames.add("TEMPLATE_NAME_IGMP");
                                }
                                if (veipServiceTemplate != null) {
                                    iptvInfo.put("TEMPLATE_NAME_VEIP", veipServiceTemplate);
                                    outputParameterNames.add("TEMPLATE_NAME_VEIP");
                                }
                            }
                            // AP (starts with "AP")
                            else if (resName.startsWith("AP")) {
                                successFlag = true;
                                String sNoKey = "AP_SerialNo_" + apCounter;
                                String macKey = "AP_MacAddr_" + apCounter;
                                String preKey = "AP_PreShareKey_" + apCounter;
                                String statusKey = "AP_Status_" + apCounter;
                                String modelKey = "AP_Model_" + apCounter;

                                String serial = safeGet(resProps, "serialNo");
                                String mac = safeGet(resProps, "macAddress");
                                String preshared = safeGet(resProps, "presharedKey");
                                String status = safeGet(resProps, "AdministrativeState");
                                String model = safeGet(resProps, "deviceModel");

                                if (serial != null) { iptvInfo.put(sNoKey, serial); outputParameterNames.add(sNoKey); }
                                if (mac != null) { iptvInfo.put(macKey, mac); outputParameterNames.add(macKey); }
                                if (preshared != null) { iptvInfo.put(preKey, preshared); outputParameterNames.add(preKey); }
                                if (status != null) { iptvInfo.put(statusKey, status); outputParameterNames.add(statusKey); }
                                if (model != null) { iptvInfo.put(modelKey, model); outputParameterNames.add(modelKey); }

                                apCounter++;
                            }
                            // STB (starts with "STB")
                            else if (resName.startsWith("STB")) {
                                successFlag = true;
                                String sNoKey = "STB_SerialNo_" + stbCounter;
                                String macKey = "STB_MacAddr_" + stbCounter;
                                String preKey = "STB_PreShareKey_" + stbCounter;
                                String grpKey = "STB_CustomerGroupID_" + stbCounter;
                                String statusKey = "STB_Status_" + stbCounter;
                                String modelKey = "STB_Model_" + stbCounter;

                                String serial = safeGet(resProps, "serialNo");
                                String mac = safeGet(resProps, "macAddress");
                                String preshared = safeGet(resProps, "presharedKey");
                                String groupId = safeGet(resProps, "deviceGroupId");
                                String status = safeGet(resProps, "AdministrativeState");
                                String model = safeGet(resProps, "deviceModel");

                                if (serial != null) { iptvInfo.put(sNoKey, serial); outputParameterNames.add(sNoKey); }
                                if (mac != null) { iptvInfo.put(macKey, mac); outputParameterNames.add(macKey); }
                                if (preshared != null) { iptvInfo.put(preKey, preshared); outputParameterNames.add(preKey); }
                                if (groupId != null) { iptvInfo.put(grpKey, groupId); outputParameterNames.add(grpKey); }
                                if (status != null) { iptvInfo.put(statusKey, status); outputParameterNames.add(statusKey); }
                                if (model != null) { iptvInfo.put(modelKey, model); outputParameterNames.add(modelKey); }

                                stbCounter++;
                            }
                        }

                    } catch (Exception e) {
                        log.error("Error while processing RFS resources for {} : {}", rfsName, e.getMessage());
                    }
                } // end rfs present

            } // end for each matching CFS

            // 8. Final response
            if (successFlag) {
                log.error(Constants.ACTION_COMPLETED);
                QueryIPTVByServiceIDResponse resp = new QueryIPTVByServiceIDResponse();
                resp.setStatus("200");
                resp.setMessage("IPTV Service Details Found");
                resp.setTimestamp(Instant.now().toString());
                resp.setIptvInfo(iptvInfo);
                return resp;

            } else {
                QueryIPTVByServiceIDResponse resp = new QueryIPTVByServiceIDResponse();
                resp.setStatus("200");
                resp.setMessage("No IPTV Service Details Found");
                resp.setTimestamp(Instant.now().toString());
                return resp;
            }

        } catch (Exception ex) {
            QueryIPTVByServiceIDResponse resp = new QueryIPTVByServiceIDResponse();
            resp.setStatus("1");
            resp.setMessage("Exception in QueryIPTVByServiceID");
            resp.setTimestamp(Instant.now().toString());
            return resp;
        }
    }

    // -------------------------
    // Helper / utility methods
    // -------------------------

    private Optional<Service> findCfsByNameFromAll(String name) {
        for (Service cfs : serviceCustomRepository.findAll()) {
            if (name.equals(cfs.getName())) return Optional.of(cfs);
        }
        return Optional.empty();
    }

    private Optional<Service> findRfsByNameFromAll(String name) {
        for (Service rfs : serviceCustomRepository.findAll()) {
            if (name.equals(rfs.getName())) return Optional.of(rfs);
        }
        return Optional.empty();
    }

    /**
     * Safely fetch a string property from properties map
     */
    private static String safeGet(Map<String, Object> props, String key) {
        if (props == null || key == null) return null;
        Object v = props.get(key);
        return v == null ? null : String.valueOf(v);
    }

}
