package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.resource.Resource;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.QueryAllServicesByCPERequest;
import com.nokia.nsw.uiv.response.QueryAllServicesByCPEResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

@Component
@RestController
@Action
@Slf4j
public class QueryAllServicesByCPE implements HttpAction {
    protected static final String ACTION_LABEL = Constants.QUERY_ALL_SERVICES_BY_CPE;
    private static final String ERROR_PREFIX = "UIV action QueryAllServicesByCPE execution failed - ";

    @Autowired
    private LogicalDeviceCustomRepository logicalDeviceRepo;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepo;

    @Autowired
    private ProductCustomRepository productRepo;

    @Autowired
    private CustomerCustomRepository subscriberRepo;

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Override
    public Class<?> getActionClass() {
        return QueryAllServicesByCPERequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.info("Executing action {}", ACTION_LABEL);
        QueryAllServicesByCPERequest req = (QueryAllServicesByCPERequest) actionContext.getObject();

        // Step 1: Mandatory Validation
        try {
            Validations.validateMandatory(req.getOntSn(), "ONT_SN");
        } catch (BadRequestException bre) {
            return errorResponse("400", "Missing mandatory parameter: " + bre.getMessage());
        }

        try {
            String ontName = "ONT" + req.getOntSn();

            if (ontName.length() > 100) {
                return new QueryAllServicesByCPEResponse(
                        "400",
                        ERROR_PREFIX + "ONT name too long",
                        Instant.now().toString(),
                        null);
            }

            // Step 2: Identify the ONT
            Optional<LogicalDevice> ontOpt = logicalDeviceRepo.findByDiscoveredName(ontName);
            if (!ontOpt.isPresent()) {
                return errorResponse("404", "CPE/ONT not found");
            }
            LogicalDevice ont = ontOpt.get();
            log.info("ONT located: {}", ontName);

            // Find parent OLT (ONT -> managingDevices)
            LogicalDevice olt = null;
            Set<Resource> managingDevices = ont.getUsedResource();
            if (managingDevices != null && !managingDevices.isEmpty()) {
                olt = (LogicalDevice) managingDevices.iterator().next();
            }

            // Collect linked RFS entries from ONT
            Set<Service> linkedServiceList = ont.getUsingService();
            List<Service> linkedRfsList = new ArrayList<>();
            if (linkedServiceList != null) {
                for (Service s : linkedServiceList) {
                    if (s.getKind() != null && s.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_RFS)) {
                        linkedRfsList.add(s);
                    }
                }
            }

            if (linkedRfsList.isEmpty()) {
                return errorResponse("404", "No services linked to CPE");
            }

            // Step 3: Initialize counters and output map
            int bbCount = 0, voiceCount = 0, entCount = 0, iptvCount = 0,cloudstarterCount=0,bridgedCount=0;
            Map<String, Object> output = new LinkedHashMap<>();

            // Step 4: Traverse services linked to ONT
            for (Service rfs : linkedRfsList) {
//                String rfsType1 = (String) rfs.getProperties().get("serviceType");
//                if (rfsType1 == null)
//                    continue;

                // Derive CFS name: replace RFS_ with CFS_
                String rfsName = rfs.getDiscoveredName();
                String cfsName = rfsName != null ? rfsName.replaceFirst("^RFS_", "CFS_") : null;

                // Find CFS, Product, Subscription, Customer
                Service cfs = null;
                Product product = null;
                Subscription subscription = null;
                Customer customer = null;
                String serviceSubType="";

                if (cfsName != null) {
                    Optional<Service> cfsOpt = serviceCustomRepository.findByDiscoveredName(cfsName);
                    if (cfsOpt.isPresent()) {
                        cfs = cfsOpt.get();
                        // CFS -> Product (via usingService with kind SetarProduct)
                        if (cfs.getUsingService() != null) {
                            for (Service svc : cfs.getUsingService()) {
                                if (svc.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_PRODUCT)) {
                                    String productName = svc.getDiscoveredName();
                                    Optional<Product> prodOpt = productRepo.findByDiscoveredName(productName);
                                    if (prodOpt.isPresent()) {
                                        product = prodOpt.get();
                                        // Product -> Subscription
                                        if (product.getSubscription() != null && !product.getSubscription().isEmpty()) {
                                            subscription = product.getSubscription().iterator().next();
                                        }
                                        // Product -> Customer
                                        customer = product.getCustomer();
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }

                String rfsType = (String) product.getProperties().get("productType");
                serviceSubType=subscription.getProperties().get("serviceSubType").toString();
                boolean isBroadbandType = rfsType.equals("Broadband") || rfsType.equals("Fibernet");
                boolean isExcludedSubtype = serviceSubType.equalsIgnoreCase("Cloudstarter")
                        || serviceSubType.equalsIgnoreCase("Bridged");

                if (isBroadbandType && !isExcludedSubtype) {
                    bbCount++;
                    String bbPrefix = "Broadband_" + bbCount + "_";
                    populateBroadband(output, bbPrefix, rfs, subscription, customer, olt, ont);
                }else if(serviceSubType.equalsIgnoreCase("Cloudstarter"))
                {
                    cloudstarterCount++;
                    String csprefix = "CLOUD_" + cloudstarterCount + "_";

                    populateCloudStarter(output, csprefix,rfs, subscription, customer, olt, ont);
                }else if(serviceSubType.equalsIgnoreCase("Bridged")) {
                    bridgedCount++;
                    String bgprefix = "BRIDGED_" + bridgedCount + "_";

                    populateBridged(output, bgprefix, rfs, subscription, customer, olt, ont);
                }else if(rfsType.equals("Voice") || rfsType.equals("VOIP"))
                {
                    voiceCount++;
                    String voicePrefix = "Voice_" + voiceCount + "_";
                    populateVoice(output, voicePrefix, rfs, subscription, customer, olt, ont,req.getOntSn());
                }else if(rfsType.equals("ENTERPRISE")){
                    entCount++;
                    String entPrefix = "ENTERPRISE_" + entCount + "_";
                    populateEnterprise(output, entPrefix, rfs, subscription, customer, olt, ont);
                }else if(rfsType.equals("EVPN")){
                    entCount++;
                    String prefix = "EVPN_" + entCount + "_";
                    populateEvpnService(output, prefix,
                            "ENTERPRISE",
                            rfsType,
                            subscription, customer, olt, ont);
                }else if(rfsType.equals("IPTV"))
                {
                    iptvCount++;
                    String iptvPrefix = "IPTV_" + iptvCount + "_";
                    populateIptv(output, iptvPrefix, iptvCount, rfs, subscription, customer, olt, ont);
                }
            }

            // Step 5: Aggregate counts
            output.put("BB_COUNT", String.valueOf(bbCount));
            output.put("VOICE_COUNT", String.valueOf(voiceCount));
            output.put("ENT_COUNT", String.valueOf(entCount));
            output.put("IPTV_COUNT", String.valueOf(iptvCount));
            output.put("CLOUD_COUNT", String.valueOf(cloudstarterCount));
            output.put("BRIDGED_COUNT", String.valueOf(bridgedCount));

            // Step 6: Success response
            log.info("QueryAllServicesByCPE completed successfully.");
            return new QueryAllServicesByCPEResponse(
                    "200",
                    "UIV action QueryAllServicesByCPE executed successfully.",
                    Instant.now().toString(),
                    output);

        } catch (Exception ex) {
            log.error("Exception in QueryAllServicesByCPE", ex);
            return errorResponse("500", "Internal server error occurred");
        }
    }

    // --- Broadband / Fiber ---
    private void populateBroadband(Map<String, Object> out, String prefix, Service rfs,
                                   Subscription sub, Customer cust, LogicalDevice olt, LogicalDevice ont) {
        Map<String, Object> rfsProps = rfs.getProperties();
        putIfNotNull(out, prefix + "SERVICE_ID", sub != null ? sub.getProperties().get("serviceID") : null);
        putIfNotNull(out, prefix + "SERVICE_SUBTYPE", sub != null ? sub.getProperties().get("serviceSubType") : null);
        out.put(prefix + "SERVICE_TYPE", "Broadband");
        putIfNotNull(out, prefix + "QOS_PROFILE", sub.getProperties().get("veipQosSessionProfile"));
        putIfNotNull(out, prefix + "KENAN_SUBS_ID", sub.getProperties().get("kenanSubscriberId"));

        populateSubscriberDetails(out, prefix, cust);

        // Templates from OLT
        if (olt != null && ont != null) {
            Map<String, Object> ontProps = ont.getProperties();
            Map<String, Object> oltProps = olt.getProperties();
            putIfNotNull(out, prefix + "ONT_TEMPLATE", ontProps.get("ontTemplate"));
            putIfNotNull(out, prefix + "SERVICE_TEMPLATE_VEIP", oltProps.get("veipServiceTemplate"));
            putIfNotNull(out, prefix + "SERVICE_TEMPLATE_HSI", oltProps.get("veipHsiTemplate"));
        }
        putIfNotNull(out, "Service_Prefix", "Broadband");
    }

    // --- Voice / VoIP ---
    private void populateVoice(Map<String, Object> out, String prefix, Service rfs,
                               Subscription sub, Customer cust, LogicalDevice olt, LogicalDevice ont,String ontsn) {
        Map<String, Object> subProps = sub != null ? sub.getProperties() : Collections.emptyMap();
        Optional<LogicalDevice> cpe=logicalDeviceRepo.findByDiscoveredName("ONT_"+ontsn);
        String voipNumber1Cpe = "";
        String voipNumber2Cpe = "";
        String voipNumber1 = subProps.get("voipNumber1") != null
                ? subProps.get("voipNumber1").toString()
                : "";

        String voipNumber2 = subProps.get("voipNumber2") != null
                ? subProps.get("voipNumber2").toString()
                : "";
        String voipPotsTemplate1="";
        String voipPotsTemplate2="";

        if (olt != null) {
            Map<String, Object> oltProps = olt.getProperties();
            voipPotsTemplate1 = oltProps.get("voipPots1Template") != null
                    ? oltProps.get("voipPots1Template").toString()
                    : "";

            voipPotsTemplate2 = oltProps.get("voipPots2Template") != null
                    ? oltProps.get("voipPots2Template").toString()
                    : "";
        }

        if(cpe.isPresent())
        {
            LogicalDevice cpeDevice= cpe.get();
            if(cpeDevice.getProperties().get("voipPort1")!=null) {
                voipNumber1Cpe = cpeDevice.getProperties().get("voipPort1").toString();
            }
            if(cpeDevice.getProperties().get("voipPort2")!=null) {
                voipNumber2Cpe = cpeDevice.getProperties().get("voipPort2").toString();
            }

        }
        putIfNotNull(out, prefix + "SERVICE_ID", subProps.get("serviceID"));
        putIfNotNull(out, prefix + "SERVICE_SUBTYPE", subProps.get("serviceSubType"));
        out.put(prefix + "SERVICE_TYPE", "Voice");
        putIfNotNull(out, prefix + "CUSTOMER_ID", subProps.get("simaCustId1"));
        putIfNotNull(out, prefix + "CUSTOMER_ID2", subProps.get("simaCustId2"));
        putIfNotNull(out, prefix + "SIMA_SUBS_ID", subProps.get("simaSubsId1"));
        putIfNotNull(out, prefix + "SIMA_SUBS_ID2", subProps.get("simaSubsId2"));
        putIfNotNull(out, prefix + "SIMA_ENDPOINT_ID", subProps.get("simaEndpointId1"));
        putIfNotNull(out, prefix + "SIMA_ENDPOINT_ID2", subProps.get("simaEndpointId2"));
        putIfNotNull(out, prefix + "VOIP_NUMBER_1", subProps.get("voipNumber1"));
        putIfNotNull(out, prefix + "VOIP_NUMBER_2", subProps.get("voipNumber2"));
        putIfNotNull(out, prefix + "VOIP_CODE_1", subProps.get("voipServiceCode1"));
        putIfNotNull(out, prefix + "VOIP_CODE_2", subProps.get("voipServiceCode2"));
        putIfNotNull(out, prefix + "QOS_PROFILE", subProps.get("voipPackage1"));

        populateSubscriberDetails(out, prefix, cust);

        // Templates from OLT
        if (olt != null) {
            Map<String, Object> oltProps = olt.getProperties();
            putIfNotNull(out, prefix + "ONT_TEMPLATE", oltProps.get("ontTemplate"));
            putIfNotNull(out, prefix + "SERVICE_TEMPLATE_VOIP", oltProps.get("voipServiceTemplate"));
        }

        if(voipNumber1Cpe != null && voipNumber1Cpe.equals(voipNumber1)){

            putIfNotNull(out, prefix + "VOIP_NUMBER_1", subProps.get("voipNumber1"));
            putIfNotNull(out, prefix + "SERVICE_TEMPLATE_POTS1", voipPotsTemplate1);
        }

        if(voipNumber2Cpe != null && voipNumber2Cpe.equals(voipNumber2)){

            putIfNotNull(out, prefix + "VOIP_NUMBER_2", subProps.get("voipNumber2"));
            putIfNotNull(out, prefix + "SERVICE_TEMPLATE_POTS2", voipPotsTemplate2);
        }
        putIfNotNull(out, "Service_Prefix", "Voice");
    }

    // --- Enterprise / EVPN ---
    private void populateEnterprise(Map<String, Object> out, String prefix, Service rfs,
                                    Subscription sub, Customer cust, LogicalDevice olt, LogicalDevice ont) {
        Map<String, Object> rfsProps = rfs.getProperties();
        Map<String, Object> subProps = sub != null ? sub.getProperties() : Collections.emptyMap();

        putIfNotNull(out, prefix + "SERVICE_ID", subProps.get("serviceID"));
        putIfNotNull(out, prefix + "SERVICE_SUBTYPE", subProps.get("serviceSubType"));
        out.put(prefix + "SERVICE_TYPE", "Enterprise");
        putIfNotNull(out, prefix + "QOS_PROFILE", subProps.get("evpnQosSessionProfile"));
        putIfNotNull(out, prefix + "KENAN_SUBS_ID", subProps.get("kenanSubscriberId"));
        putIfNotNull(out, prefix + "PORT", subProps.get("evpnPort"));
        putIfNotNull(out, prefix + "VLAN", subProps.get("evpnVLAN"));

        // EVPN templates from RFS properties
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN", subProps.get("evpnTemplateVLAN"));
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN_CREATE", subProps.get("evpnTemplateCreateVLAN"));
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VPLS", subProps.get("evpnTemplateVPLS"));

        populateSubscriberDetails(out, prefix, cust);
        String evpnPort= subProps.get("evpnPort").toString();
        // OLT templates
        if (olt != null) {
            Map<String, Object> ontProps = ont.getProperties();
            Map<String, Object> oltProps = olt.getProperties();
            putIfNotNull(out, prefix + "ONT_TEMPLATE", oltProps.get("ontTemplate"));
            putIfNotNull(out, prefix + "TEMPLATE_NAME_CARD", oltProps.get("evpnOntCardTemplate"));
            if(ontProps.get("evpnEthPort3Template") != null && !ontProps.get("evpnEthPort3Template").toString().isEmpty() && evpnPort.equals("3")){
                putIfNotNull(out, prefix + "TEMPLATE_NAME_PORT", ontProps.get("evpnEthPort3Template"));
            }
            if(ontProps.get("evpnEthPort4Template") != null && !ontProps.get("evpnEthPort4Template").toString().isEmpty() && evpnPort.equals("4")){
                putIfNotNull(out, prefix + "TEMPLATE_NAME_PORT", ontProps.get("evpnEthPort4Template"));
            }
            putIfNotNull(out, prefix + "TEMPLATE_NAME_PORT_CREATE", ontProps.get("createTemplate"));
            putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN_MGMNT", ontProps.get("mgmtTemplate"));
        }
        putIfNotNull(out, "Service_Prefix", "ENTERPRISE");
    }

    // --- IPTV ---
    private void populateIptv(Map<String, Object> out, String prefix, int iptvCount, Service rfs,
                              Subscription sub, Customer cust, LogicalDevice olt, LogicalDevice ont) {
        Map<String, Object> subProps = sub != null ? sub.getProperties() : Collections.emptyMap();

        putIfNotNull(out, prefix + "SERVICE_ID", subProps.get("serviceID"));
        putIfNotNull(out, prefix + "SERVICE_SUBTYPE", subProps.get("serviceSubType"));
        out.put(prefix + "SERVICE_TYPE", "IPTV");
        putIfNotNull(out, prefix + "QOS_PROFILE", subProps.get("iptvQosSessionProfile"));
        putIfNotNull(out, prefix + "KENAN_SUBS_ID", subProps.get("kenanSubscriberId"));
        putIfNotNull(out, prefix + "CUSTOMER_GROUP_ID", subProps.get("customerGroupId"));
        populateSubscriberDetails(out, prefix, cust);

        // VLAN from ONT
        if (ont != null) {
            putIfNotNull(out, prefix + "VLAN", ont.getProperties().get("iptvVlan"));
        }

        // Templates from OLT
        if (olt != null) {
            Map<String, Object> oltProps = olt.getProperties();
            putIfNotNull(out, prefix + "TEMPLATE_NAME_IPTV", oltProps.get("veipIptvTemplate"));
            putIfNotNull(out, prefix + "TEMPLATE_NAME_IGMP", oltProps.get("igmpTemplate"));
        }

        // Process STB and AP devices linked to RFS
        int stbIndex = 1;
        int apIndex = 1;
        Set<Resource> usedResources = rfs.getUsedResource();
        if (usedResources != null) {
            for (Resource res : usedResources) {
                if (res instanceof LogicalDevice) {
                    LogicalDevice device = (LogicalDevice) res;
                    String kind = device.getKind();
                    Map<String, Object> devProps = device.getProperties() != null ? device.getProperties()
                            : Collections.emptyMap();

                    if ("StbApCmDevice".equalsIgnoreCase(kind)) {
                        String deviceType = (String) devProps.get("deviceType");
                        if ("STB".equalsIgnoreCase(deviceType)) {
                            String stbPre = prefix + "STB_";
                            putIfNotNull(out, stbPre + "SN_" + stbIndex, devProps.get("serialNo"));
                            putIfNotNull(out, stbPre + "MAC_" + stbIndex, devProps.get("macAddress"));
                            putIfNotNull(out, stbPre + "MODEL_" + stbIndex, devProps.get("deviceModel"));
                            putIfNotNull(out, stbPre + "MANUFACTURER_" + stbIndex, devProps.get("manufacturer"));
                            putIfNotNull(out, stbPre + "GID_" + stbIndex, devProps.get("customerGroupId"));
                            putIfNotNull(out, stbPre + "MDLSBTYPE_" + stbIndex, devProps.get("modelSubType"));
                            putIfNotNull(out, stbPre + "PKEY_" + stbIndex, devProps.get("presharedKey"));
                            stbIndex++;
                        } else if ("AP".equalsIgnoreCase(deviceType)) {
                            String apPre = prefix + "AP_";
                            putIfNotNull(out, apPre + "SN_" + apIndex, devProps.get("serialNo"));
                            putIfNotNull(out, apPre + "MAC_" + apIndex, devProps.get("macAddress"));
                            putIfNotNull(out, apPre + "MODEL_" + apIndex, devProps.get("deviceModel"));
                            putIfNotNull(out, apPre + "MANUFACTURER_" + apIndex, devProps.get("manufacturer"));
                            putIfNotNull(out, apPre + "GID_" + apIndex, devProps.get("customerGroupId"));
                            putIfNotNull(out, apPre + "MDLSBTYPE_" + apIndex, devProps.get("modelSubType"));
                            putIfNotNull(out, apPre + "PKEY_" + apIndex, devProps.get("presharedKey"));
                            apIndex++;
                        }
                    }
                }
            }
        }

        // Process IPTV products from Subscription properties
        // Note: IPTV catalog items may be stored in subscription properties as
        // prodName1, prodVariant1, etc.
        int prodIndex = 1;

        if (sub != null) {
            Set<Service> products = sub.getService();
            if (products != null && !products.isEmpty()) {
                for (Service product : products) {

                    Map<String, Object> props = product.getProperties();
                    if (props == null) {
                        continue;
                    }

                    String prodName = props.get("catalogItemName") != null
                            ? props.get("catalogItemName").toString()
                            : null;

                    String prodVariant = props.get("catalogItemVersion") != null
                            ? props.get("catalogItemVersion").toString()
                            : null;

                    putIfNotNull(out, prefix + "PROD_NAME_" + prodIndex, prodName);
                    putIfNotNull(out, prefix + "PROD_VARIANT_" + prodIndex, prodVariant);

                    prodIndex++;
                }
            }
        }

    }

    // --- Subscriber Details ---
    private void populateSubscriberDetails(Map<String, Object> out, String prefix, Customer cust) {
        if (cust == null)
            return;
        Map<String, Object> custProps = cust.getProperties() != null ? cust.getProperties() : Collections.emptyMap();
        putIfNotNull(out, prefix + "HHID", custProps.get("houseHoldId"));
        putIfNotNull(out, prefix + "ACCOUNT_NUMBER", custProps.get("accountNumber"));
        putIfNotNull(out, prefix + "FIRST_NAME", custProps.get("subscriberFirstName"));
        putIfNotNull(out, prefix + "LAST_NAME", custProps.get("subscriberLastName"));
        putIfNotNull(out, prefix + "COMPANY_NAME", custProps.get("companyName"));
        putIfNotNull(out, prefix + "CONTACT_PHONE", custProps.get("contactPhoneNumber"));
        putIfNotNull(out, prefix + "SUBS_ADDRESS", custProps.get("subscriberAddress"));
        putIfNotNull(out, prefix + "EMAIL", custProps.get("email"));
        putIfNotNull(out, prefix + "EMAIL_PASSWORD", custProps.get("emailPassword"));
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        } else {
            map.put(key, "");
        }
    }

    private QueryAllServicesByCPEResponse errorResponse(String status, String msg) {
        return new QueryAllServicesByCPEResponse(
                status,
                ERROR_PREFIX + msg,
                Instant.now().toString(),
                null);
    }

    private void populateCloudStarter(Map<String, Object> out, String prefix,
                                      Service rfs, Subscription sub, Customer cust,
                                      LogicalDevice olt, LogicalDevice ont) {

        Map<String, Object> subProps = sub != null ? sub.getProperties() : null;
        Map<String, Object> custProps = cust != null ? cust.getProperties() : null;

        // Service details
        putIfNotNull(out, prefix + "SERVICE_ID", subProps != null ? subProps.get("serviceID") : null);
        putIfNotNull(out, prefix + "SERVICE_SUBTYPE", subProps != null ? subProps.get("serviceSubType") : null);
        out.put(prefix + "SERVICE_TYPE", "Broadband");

        putIfNotNull(out, prefix + "QOS_PROFILE", subProps != null ? subProps.get("evpnQosSessionProfile") : null);
        putIfNotNull(out, prefix + "KENAN_SUBS_ID", subProps != null ? subProps.get("kenanSubscriberId") : null);

        // EVPN fields
        putIfNotNull(out, prefix + "PORT", subProps != null ? subProps.get("evpnPort") : null);
        putIfNotNull(out, prefix + "VLAN", subProps != null ? subProps.get("evpnVLAN") : null);
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN", subProps != null ? subProps.get("evpnTemplateVLAN") : null);
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN_CREATE", subProps != null ? subProps.get("evpnTemplateCreateVLAN") : null);
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VPLS", subProps != null ? subProps.get("evpnTemplateVPLS") : null);

        // Customer details
        if (custProps != null) {
            populateSubscriberDetails(out, prefix, cust);
        }

        // Templates
        if (olt != null && ont != null) {
            Map<String, Object> oltProps = olt.getProperties();
            Map<String, Object> ontProps = ont.getProperties();

            putIfNotNull(out, prefix + "ONT_TEMPLATE", oltProps.get("ontTemplate"));
            putIfNotNull(out, prefix + "TEMPLATE_NAME_CARD", oltProps.get("evpnOntCardTemplate"));

            String evpnPort = subProps != null ? (String) subProps.get("evpnPort") : null;

            if ("3".equals(evpnPort)) {
                putIfNotNull(out, prefix + "TEMPLATE_NAME_PORT", oltProps.get("evpnEthPort3Template"));
            }

            if ("4".equals(evpnPort)) {
                putIfNotNull(out, prefix + "TEMPLATE_NAME_PORT", oltProps.get("evpnEthPort4Template"));
            }

            putIfNotNull(out, prefix + "TEMPLATE_NAME_CREATE", ontProps.get("createTemplate"));
            putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN_MGMNT", ontProps.get("mgmtTemplate"));
        }

        putIfNotNull(out, "Service_Prefix", "CLOUD");
    }
    private void populateBridged(Map<String, Object> out, String prefix,
                                 Service rfs, Subscription sub, Customer cust,
                                 LogicalDevice olt, LogicalDevice ont) {

        Map<String, Object> subProps = sub != null ? sub.getProperties() : null;
        Map<String, Object> custProps = cust != null ? cust.getProperties() : null;

        // Service details
        putIfNotNull(out, prefix + "SERVICE_ID", subProps != null ? subProps.get("serviceID") : null);
        putIfNotNull(out, prefix + "SERVICE_SUBTYPE", subProps != null ? subProps.get("serviceSubType") : null);
        out.put(prefix + "SERVICE_TYPE", "Broadband");

        putIfNotNull(out, prefix + "QOS_PROFILE", subProps != null ? subProps.get("evpnQosSessionProfile") : null);
        putIfNotNull(out, prefix + "KENAN_SUBS_ID", subProps != null ? subProps.get("kenanSubscriberId") : null);

        // EVPN fields
        putIfNotNull(out, prefix + "PORT", subProps != null ? subProps.get("evpnPort") : null);
        putIfNotNull(out, prefix + "VLAN", subProps != null ? subProps.get("evpnVLAN") : null);
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN", subProps != null ? subProps.get("evpnTemplateVLAN") : null);
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN_CREATE", subProps != null ? subProps.get("evpnTemplateCreateVLAN") : null);
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VPLS", subProps != null ? subProps.get("evpnTemplateVPLS") : null);

        // Customer details
        if (custProps != null) {
            populateSubscriberDetails(out, prefix, cust);
        }

        // Templates
        if (olt != null && ont != null) {
            Map<String, Object> oltProps = olt.getProperties();
            Map<String, Object> ontProps = ont.getProperties();

            putIfNotNull(out, prefix + "ONT_TEMPLATE", oltProps.get("ontTemplate"));
            putIfNotNull(out, prefix + "TEMPLATE_NAME_CARD", oltProps.get("evpnOntCardTemplate"));

            String evpnPort = subProps != null ? (String) subProps.get("evpnPort") : null;

            if ("3".equals(evpnPort)) {
                putIfNotNull(out, prefix + "TEMPLATE_NAME_PORT", oltProps.get("evpnEthPort3Template"));
            }

            if ("4".equals(evpnPort)) {
                putIfNotNull(out, prefix + "TEMPLATE_NAME_PORT", oltProps.get("evpnEthPort4Template"));
            }

            putIfNotNull(out, prefix + "TEMPLATE_NAME_CREATE", ontProps.get("createTemplate"));
            putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN_MGMNT", ontProps.get("mgmtTemplate"));
        }

        // Common
        putIfNotNull(out, "Service_Prefix", "BRIDGED");
    }

    private void populateEvpnService(Map<String, Object> out, String prefix,
                                     String serviceType, String servicePrefixValue,
                                     Subscription sub, Customer cust,
                                     LogicalDevice olt, LogicalDevice ont) {

        Map<String, Object> subProps = sub != null ? sub.getProperties() : null;
        Map<String, Object> custProps = cust != null ? cust.getProperties() : null;

        // Service details
        putIfNotNull(out, prefix + "SERVICE_ID", subProps != null ? subProps.get("serviceID") : null);
        putIfNotNull(out, prefix + "SERVICE_SUBTYPE", subProps != null ? subProps.get("serviceSubType") : null);
        out.put(prefix + "SERVICE_TYPE", serviceType);

        putIfNotNull(out, prefix + "QOS_PROFILE", subProps != null ? subProps.get("evpnQosSessionProfile") : null);
        putIfNotNull(out, prefix + "KENAN_SUBS_ID", subProps != null ? subProps.get("kenanSubscriberId") : null);

        // EVPN fields
        putIfNotNull(out, prefix + "PORT", subProps != null ? subProps.get("evpnPort") : null);
        putIfNotNull(out, prefix + "VLAN", subProps != null ? subProps.get("evpnVLAN") : null);
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN", subProps != null ? subProps.get("evpnTemplateVLAN") : null);
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN_CREATE", subProps != null ? subProps.get("evpnTemplateCreateVLAN") : null);
        putIfNotNull(out, prefix + "TEMPLATE_NAME_VPLS", subProps != null ? subProps.get("evpnTemplateVPLS") : null);

        // Customer details
        if (custProps != null) {
            populateSubscriberDetails(out, prefix, cust);
        }

        // Templates
        if (olt != null && ont != null) {
            Map<String, Object> oltProps = olt.getProperties();
            Map<String, Object> ontProps = ont.getProperties();

            putIfNotNull(out, prefix + "ONT_TEMPLATE", oltProps.get("ontTemplate"));
            putIfNotNull(out, prefix + "TEMPLATE_NAME_CARD", oltProps.get("evpnOntCardTemplate"));

            String evpnPort = subProps != null ? (String) subProps.get("evpnPort") : null;

            if ("3".equals(evpnPort)) {
                putIfNotNull(out, prefix + "TEMPLATE_NAME_PORT", oltProps.get("evpnEthPort3Template"));
            }

            if ("4".equals(evpnPort)) {
                putIfNotNull(out, prefix + "TEMPLATE_NAME_PORT", oltProps.get("evpnEthPort4Template"));
            }

            putIfNotNull(out, prefix + "TEMPLATE_NAME_CREATE", ontProps.get("createTemplate"));
            putIfNotNull(out, prefix + "TEMPLATE_NAME_VLAN_MGMNT", ontProps.get("mgmtTemplate"));
        }

        // Common
        putIfNotNull(out, "Service_Prefix", servicePrefixValue);
    }
}
