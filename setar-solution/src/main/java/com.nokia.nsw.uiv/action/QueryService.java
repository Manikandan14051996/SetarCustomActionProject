package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.Resource;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.QueryServiceRequest;
import com.nokia.nsw.uiv.response.QueryServiceResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Action
@Slf4j
public class QueryService implements HttpAction {

    private static final String ACTION_LABEL = Constants.QUERY_SERVICE;
    private static final String ERROR_PREFIX = "UIV action QueryService execution failed - ";

    @Autowired
    private CustomerCustomRepository customerRepository;
    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;
    @Autowired
    private ProductCustomRepository productRepository;
    @Autowired
    private ServiceCustomRepository serviceCustomRepository;
    @Autowired
    private LogicalDeviceCustomRepository logicalDeviceRepository;

    @Override
    public Class<?> getActionClass() {
        return QueryServiceRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error("Executing action {}", ACTION_LABEL);
        QueryServiceRequest request = (QueryServiceRequest) actionContext.getObject();
        Map<String, Object> iptvinfo = new HashMap<>();
        List<String> returnedParams = new ArrayList<>();

        try {
            // Step 1: Validate Mandatory Inputs
            try {
                Validations.validateMandatory(request.getServiceId(), "serviceId");
            } catch (BadRequestException bre) {
                return new QueryServiceResponse("400", ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage(),
                        Instant.now().toString(), false, "Missing parameter");
            }

            String serviceId = request.getServiceId().trim();
            log.error("Processing QueryService for SERVICE_ID: {}", serviceId);

            // Step 2: Find Candidate CFS Names
            List<Service> cfsList = (List<Service>) serviceCustomRepository.findAll();
            Set<String> cfsNameSet = new LinkedHashSet<>();

            for (Service cfs : cfsList) {
                if (cfs.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_CFS)) {

                    if (cfs.getDiscoveredName().contains(serviceId)) {
                        String cfsName = cfs.getDiscoveredName();
                        if (cfsName == null) continue;
                        if(request.getServiceId().contains("_")){
                            String finalValue=cfsName.substring(cfsName.indexOf("_")+1, cfsName.lastIndexOf("_"));
                            if(finalValue!=null&&finalValue.equalsIgnoreCase(request.getServiceId())){
                                cfsNameSet.add(cfsName);
                            }
                        }
                        String[] parts = cfsName.split(Constants.UNDER_SCORE);
                        if (parts.length > 2 && parts[2].equalsIgnoreCase(serviceId)) {
                            cfsNameSet.add(cfsName);
                        }
                    }
                }
            }

            if (cfsNameSet.isEmpty()) {
                log.error("No matching CFS found for serviceId {}", serviceId);
                return new QueryServiceResponse("404", "No IPTV Service Details Found.",
                        Instant.now().toString(), false, "No CFS match found");
            }

            // Step 3: For each CFS, fetch linked data
            for (String cfsName : cfsNameSet) {
                Optional<Service> optCfs = serviceCustomRepository.findByDiscoveredName(cfsName);
                if (!optCfs.isPresent()) continue;
                Service cfs = optCfs.get();

                String productName = "";
                try{
                    productName = cfs.getUsingService().stream().filter(ser -> ser.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_PRODUCT)).findFirst().get().getDiscoveredName();
                }catch (Exception e){
                    log.error(e.getMessage() + " Product entry is not found: "+productName);
                    productName = "";
                }
                String rfsName = cfsName.replace("CFS", "RFS");
                Optional<Service> optRfs = serviceCustomRepository.findByDiscoveredName(rfsName);
                Optional<Product> optProd = productRepository.findByDiscoveredName(productName);
                Optional<Subscription> optSub = Optional.empty();
                Optional<Customer> optCust = Optional.empty();

                if (optProd.isPresent()) {
                    Product prod = optProd.get();
                    if (prod.getSubscription() != null)
                        optSub = prod.getSubscription().stream().findFirst();
                    if (prod.getSubscription() != null && prod.getCustomer() != null)
                        optCust = Optional.ofNullable(prod.getCustomer());
                }else {
                    return new QueryServiceResponse("404", "No entry found to update.",
                            Instant.now().toString(), true, iptvinfo);
                }

                // Step 4: Populate Subscription details
                if (optSub.isPresent()) {
                    Subscription sub = optSub.get();
                    iptvinfo.put("CUSTOMER_GROUP_ID", sub.getProperties().getOrDefault("customerGroupId", ""));
                    iptvinfo.put("CPE_MacAddr_1", sub.getProperties().getOrDefault("serviceMAC",""));
                    if (sub.getProperties().get("serviceLink").toString().equalsIgnoreCase("Cable_Modem")) {
                        iptvinfo.put("CBM_Subscriber_ID_1", sub.getProperties().getOrDefault("subscriberID_CableModem",""));
                    }
                    iptvinfo.put("Service_Link", sub.getProperties().getOrDefault("serviceLink",""));
                    iptvinfo.put("CPE_GW_MacAddr_1", sub.getProperties().getOrDefault("gatewayMacAddress",""));
                    iptvinfo.put("Service_Package_1", sub.getProperties().getOrDefault("veipQosSessionProfile",""));
                    iptvinfo.put("Service_Subscriber_1", sub.getProperties().getOrDefault("veipQosSessionProfile",""));
                    returnedParams.addAll(Arrays.asList("CUSTOMER_GROUP_ID", "CPE_MacAddr_1", "Service_Link",
                            "CPE_GW_MacAddr_1", "Service_Package_1", "Service_Subscriber_1", "CBM_Subscriber_ID_1"));


                    Set<Service> products = sub.getService();

                    if (products != null && !products.isEmpty()) {

                        int ordinal = 1;

                        for (Service product : products) {

                            String prodName = product.getDiscoveredName();

                            if (prodName != null && prodName.startsWith(request.getServiceId())) {

                                String value = prodName.replace(request.getServiceId(),"");

                                value=value.replace("_","");

                                String key = "Service_Component_" + ordinal;

                                iptvinfo.put(key, value);
                                returnedParams.add(key);

                                ordinal++;
                            }
                        }
                    }

                }


                // Step 5: Populate Subscriber details
                if (optCust.isPresent()) {
                    Customer cust = optCust.get();
                    iptvinfo.put("Service_EmailId_1", cust.getProperties().getOrDefault("email",""));
                    iptvinfo.put("Service_EmailPw_1", cust.getProperties().getOrDefault("emailPassword",""));
                    iptvinfo.put("Service_Company_1", cust.getProperties().getOrDefault("companyName",""));
                    iptvinfo.put("Service_ContactPhone_1", cust.getProperties().getOrDefault("contactPhoneNumber",""));
                    iptvinfo.put("Service_Address_1", cust.getProperties().getOrDefault("subscriberAddress",""));
                    iptvinfo.put("Service_HHID_1", cust.getProperties().getOrDefault("houseHoldId",""));
                    iptvinfo.put("Service_FirstName_1", cust.getProperties().getOrDefault("subscriberFirstName",""));
                    iptvinfo.put("Service_LastName_1", cust.getProperties().getOrDefault("subscriberLastName",""));
                }

                // Step 6: Fetch Devices from RFS
                if (optRfs.isPresent()) {
                    Service rfs = optRfs.get();
                    final Set<Resource>[] ls = new Set[]{rfs.getUsedResource()};
                    AtomicInteger apIndex = new AtomicInteger(1);
                    AtomicInteger stbIndex = new AtomicInteger(1);
                    ls[0].forEach(res -> {
                        // Counters should be defined outside the loop


                        if (res instanceof LogicalDevice) {
                            LogicalDevice device = (LogicalDevice) res;
                            String name = device.getDiscoveredName();

                            if (name == null) {
                                return;
                            }

                            /* ===================== ONT ===================== */
                            if (name.contains("ONT")) {
                                iptvinfo.put("Service_Link", "ONT");
                                iptvinfo.put("CPE_Model_1", device.getProperties().getOrDefault("deviceModel",""));
                                iptvinfo.put("CPE_Serial_Number_1", device.getProperties().getOrDefault("serialNo",""));
                                iptvinfo.put("Menm_1", device.getProperties().getOrDefault("description",""));

                                returnedParams.add("CPE_Model_1");
                                returnedParams.add("CPE_Serial_Number_1");
                                returnedParams.add("Menm_1");
                            }

                            /* ===================== CBM ===================== */
                            else if (name.contains("CBM")) {
                                iptvinfo.put("Service_Link", "Cable_Modem");
                                iptvinfo.put("CBM_Device_MacAddr_1", device.getProperties().getOrDefault("macAddress",""));
                                iptvinfo.put("CBM_Device_Model_1", device.getProperties().getOrDefault("deviceType",""));

                                returnedParams.add("CBM_Device_MacAddr_1");
                                returnedParams.add("CBM_Device_Model_1");
                            }

                            /* ===================== OLT ===================== */
                            else if (name.contains(":")) {
                                iptvinfo.put("ONT_OBJECT_ID", device.getName());
                                iptvinfo.put("TEMPLATE_NAME_ONT", device.getProperties().getOrDefault("ontTemplate",""));
                                iptvinfo.put("TEMPLATE_NAME_IPTV", device.getProperties().getOrDefault("veipIptvTemplate",""));
                                iptvinfo.put("TEMPLATE_NAME_IGMP", device.getProperties().getOrDefault("igmpTemplate",""));
                                iptvinfo.put("TEMPLATE_NAME_VEIP", device.getProperties().getOrDefault("veipServiceTemplate",""));
                                iptvinfo.put("TEMPLATE_NAME_HSI", device.getProperties().getOrDefault("veipHsiTemplate",""));

                                returnedParams.add("ONT_OBJECT_ID");
                                returnedParams.add("TEMPLATE_NAME_ONT");
                                returnedParams.add("TEMPLATE_NAME_IPTV");
                                returnedParams.add("TEMPLATE_NAME_IGMP");
                                returnedParams.add("TEMPLATE_NAME_VEIP");
                                returnedParams.add("TEMPLATE_NAME_HSI");
                            }

                            /* ===================== AP ===================== */
                            else if (name.startsWith("AP")) {
                                String idx = String.valueOf(apIndex.getAndIncrement());

                                iptvinfo.put("AP_SerialNo_" + idx, device.getProperties().getOrDefault("serialNo",""));
                                iptvinfo.put("AP_MacAddr_" + idx, device.getProperties().getOrDefault("macAddress",""));
                                iptvinfo.put("AP_PreShareKey_" + idx, device.getProperties().getOrDefault("presharedKey",""));
                                iptvinfo.put("AP_Status_" + idx, device.getProperties().getOrDefault("AdministrativeState",""));
                                iptvinfo.put("AP_Model_" + idx, device.getProperties().getOrDefault("deviceModel",""));

                                returnedParams.add("AP_SerialNo_" + idx);
                                returnedParams.add("AP_MacAddr_" + idx);
                                returnedParams.add("AP_PreShareKey_" + idx);
                                returnedParams.add("AP_Status_" + idx);
                                returnedParams.add("AP_Model_" + idx);

                            }

                            /* ===================== STB ===================== */
                            else if (name.startsWith("STB")) {
                                String idx = String.valueOf(stbIndex.getAndIncrement());;

                                iptvinfo.put("STB_SerialNo_" + idx, device.getProperties().getOrDefault("serialNo",""));
                                iptvinfo.put("STB_MacAddr_" + idx, device.getProperties().getOrDefault("macAddress",""));
                                iptvinfo.put("STB_PreShareKey_" + idx, device.getProperties().getOrDefault("presharedKey",""));
                                iptvinfo.put("STB_CustomerGroupID_" + idx, device.getProperties().getOrDefault("deviceGroupId",""));
                                iptvinfo.put("STB_Status_" + idx, device.getProperties().getOrDefault("AdministrativeState",""));
                                iptvinfo.put("STB_Model_" + idx, device.getProperties().getOrDefault("deviceModel",""));

                                returnedParams.add("STB_SerialNo_" + idx);
                                returnedParams.add("STB_MacAddr_" + idx);
                                returnedParams.add("STB_PreShareKey_" + idx);
                                returnedParams.add("STB_CustomerGroupID_" + idx);
                                returnedParams.add("STB_Status_" + idx);
                                returnedParams.add("STB_Model_" + idx);

                            }

                            log.debug("Processed device: {}", device.getLocalName());
                        }

                    });
                }

            }

            // Step 7: Build final success response
            return new QueryServiceResponse("200", "IPTV Service Details Found.",
                    Instant.now().toString(), true, iptvinfo);

        } catch (Exception ex) {
            log.error("Error executing QueryService", ex);
            return new QueryServiceResponse("500", ERROR_PREFIX + "Internal server error - " + ex.getMessage(),
                    Instant.now().toString(), false, null);
        }
    }
}
