package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.CreateServiceCBMRequest;
import com.nokia.nsw.uiv.response.CreateServiceCBMResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Action
@Slf4j
public class CreateServiceCBM implements HttpAction {
    protected static final String ACTION_LABEL = Constants.CREATE_SERVICE_CBM;
    private static final String ERROR_PREFIX = "UIV action CreateServiceCBM execution failed - ";
    @Autowired
    private CustomerCustomRepository subscriberRepository;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;

    @Autowired
    private ProductCustomRepository productRepository;

    @Autowired
    private ServiceCustomRepository serviceRepository;


    @Autowired
    private LogicalDeviceCustomRepository cbmDeviceRepository;

    @Autowired
    private LogicalDeviceCustomRepository cpeDeviceRepository;


    @Override
    public Class<?> getActionClass() {
        return CreateServiceCBMRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        CreateServiceCBMRequest request = (CreateServiceCBMRequest) actionContext.getObject();
        // 1. Validate mandatory params
        try{
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
            Validations.validateMandatoryParams(request.getSubscriberName(), "subscriberName");
            Validations.validateMandatoryParams(request.getProductType(), "productType");
            Validations.validateMandatoryParams(request.getCbmSN(), "cbmSN");
            Validations.validateMandatoryParams(request.getCbmMac(), "cbmMAC");
            Validations.validateMandatoryParams(request.getCbmManufacturer(), "cbmManufacturer");
            Validations.validateMandatoryParams(request.getCbmType(), "cbmType");
            Validations.validateMandatoryParams(request.getCbmModel(), "cbmModel");
            Validations.validateMandatoryParams(request.getHhid(), "hhid");
            Validations.validateMandatoryParams(request.getServiceId(), "serviceId");
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
        }catch (BadRequestException bre) {
            return ResponseEntity.status(400).body(new CreateServiceCBMResponse("400", ERROR_PREFIX +  bre.getMessage(),
                    DateTimeUtil.now(), "",""));
        }


        AtomicBoolean isSubscriberExist = new AtomicBoolean(true);
        AtomicBoolean isSubscriptionExist = new AtomicBoolean(true);
        AtomicBoolean isProductExist = new AtomicBoolean(true);
        // --- 2. Subscriber Logic ---
        String subscriberName;
        if ("Broadband".equalsIgnoreCase(request.getProductSubtype())
                || "Cloudstarter".equalsIgnoreCase(request.getProductSubtype())
                || "Bridged".equalsIgnoreCase(request.getProductSubtype())) {

            // Update CPE if exists
            String cpeName = "CBM" + Constants.UNDER_SCORE + request.getCbmMac();
            Optional<LogicalDevice> cpeOpt = cpeDeviceRepository.findByDiscoveredName(cpeName);
            if (cpeOpt.isPresent()) {
                LogicalDevice cpe = cpeOpt.get();
                Map<String,Object> cpeProps = cpe.getProperties();
                cpeProps.put("AdministrativeState","Available");
                if ("Broadband".equalsIgnoreCase(request.getProductSubtype())) {
                    cpeProps.put("description","Internet");
                }
                cpe.setProperties(cpeProps);
                cpeDeviceRepository.save(cpe, 2);
            }

            subscriberName = request.getSubscriberName() + Constants.UNDER_SCORE + request.getCbmMac().replace(":", "");
        } else {
            subscriberName = request.getSubscriberName();
        }

        if (subscriberName.length() > 100) {
            return ResponseEntity.status(400).body(createErrorResponse("Subscriber name too long", 400));
        }


        Customer subscriber = subscriberRepository.findByDiscoveredName(subscriberName)
                .orElseGet(() -> {
                    isSubscriberExist.set(false);
                    Customer s = new Customer();
                    try {
                        s.setLocalName(Validations.encryptName(subscriberName));
                        s.setDiscoveredName(subscriberName);
                        s.setKind(Constants.SETAR_KIND_SETAR_SUBSCRIBER);
                        s.setContext(Constants.SETAR);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    Map<String, Object> prop = new HashMap<>();
                    prop.put("accountNumber", request.getSubscriberName());
                    prop.put("subscriberStatus", "Active");
                    prop.put("subscriberUserName", request.getUserName());
                    prop.put("address", request.getSubsAddress());
                    prop.put("houseHoldId", request.getHhid());
                    prop.put("subscriberType", "Regular");
                    prop.put("createdBy",
                            request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                    ? request.getCreatedBy()
                                    : "CA"
                    );
                    prop.put("actionName", ACTION_LABEL);
                    s.setProperties(prop);

                    subscriberRepository.save(s, 2);
                    return s;
                });
        try {
            Map<String, Object> sp = subscriber.getProperties() == null ? new HashMap<>() : subscriber.getProperties();
            if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) sp.put("subscriberFirstName", request.getFirstName());
            if (request.getLastName() != null && !request.getLastName().trim().isEmpty()) sp.put("subscriberLastName", request.getLastName());
            if (request.getCompanyName() != null && !request.getCompanyName().trim().isEmpty()) sp.put("companyName", request.getCompanyName());
            if (request.getContactPhone() != null && !request.getContactPhone().trim().isEmpty()) sp.put("contactPhoneNumber", request.getContactPhone());
            if (request.getSubsAddress() != null && !request.getSubsAddress().trim().isEmpty()) sp.put("subscriberAddress", request.getSubsAddress());
            if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) sp.put("email", request.getEmail());
            if (request.getEmailPassword() != null  && !request.getEmailPassword().trim().isEmpty()) sp.put("emailPassword", request.getEmailPassword());
            subscriber.setProperties(sp);
            subscriberRepository.save(subscriber, 2);
        } catch (Exception e) {
            log.error("Persistence error updating subscriber properties", e);
            return ResponseEntity.status(400).body(createErrorResponse("Persistence error while updating subscriber: " + e.getMessage(),400));
        }

        // --- 3. Subscription Logic ---
        String subscriptionName = request.getSubscriberName() + Constants.UNDER_SCORE + request.getServiceId();
        if (subscriptionName.length() > 100) {
            return ResponseEntity.status(400).body(createErrorResponse("Subscription name too long", 400));
        }
        Subscription subscription = subscriptionRepository.findByDiscoveredName(subscriptionName)
                .orElseGet(() -> {
                    isSubscriptionExist.set(false);
                    Subscription sub = new Subscription();
                    try {
                        sub.setLocalName(Validations.encryptName(subscriptionName));
                        sub.setDiscoveredName(subscriptionName);
                        sub.setKind(Constants.SETAR_KIND_SETAR_SUBSCRIPTION);
                        sub.setContext(Constants.SETAR);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Map<String, Object> prop = new HashMap<>();

                    prop.put("subscriptionStatus", "Active");
                    prop.put("serviceSubType", request.getProductSubtype());
                    prop.put("serviceLink", "Cable_Modem");
                    prop.put("serviceSN", request.getCbmSN());
                    prop.put("serviceMAC", request.getCbmMac());
                    prop.put("serviceID", request.getServiceId());
                    prop.put("veipQosSessionProfile", request.getQosProfile());
                    prop.put("houseHoldId", request.getHhid());
                    prop.put("customerGroupId", request.getCustomerGroupId());
                    prop.put("subscriberID_CableModem", request.getSubscriberId());
                    prop.put("servicePackage", request.getServicePackage());
                    prop.put("kenanSubscriberId", request.getKenanUidNo());
                    prop.put("createdBy",
                            request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                    ? request.getCreatedBy()
                                    : "CA"
                    );
                    prop.put("actionName", ACTION_LABEL);
                    sub.setCustomer(subscriber);
                    sub.setProperties(prop);
                    subscriptionRepository.save(sub, 2);
                    return sub;
                });

        // --- 4. Product Logic ---
        String productName = request.getSubscriberName() +Constants.UNDER_SCORE+ request.getProductSubtype() +Constants.UNDER_SCORE+ request.getServiceId();
        if (productName.length() > 100) {
            return ResponseEntity.status(400).body(createErrorResponse("Product name too long", 400));
        }

        Product product = productRepository.findByDiscoveredName(productName)
                .orElseGet(() -> {
                    isProductExist.set(false);
                    Product p = new Product();

                    p.setDiscoveredName(productName);
                    try {
                        p.setLocalName(Validations.encryptName(productName));
                        p.setKind(Constants.SETAR_KIND_SETAR_PRODUCT);
                        p.setContext(Constants.SETAR);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Map<String, Object> prop = new HashMap<>();
                    prop.put("productStatus", "Active");
                    prop.put("productType",request.getProductType());
                    prop.put("createdBy",
                            request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                    ? request.getCreatedBy()
                                    : "CA"
                    );
                    prop.put("actionName", ACTION_LABEL);
                    p.setProperties(prop);
                    p.setCustomer(subscriber);
                    productRepository.save(p, 2);
                    return p;
                });

        product.setCustomer(subscriber);
        productRepository.save(product, 2);

//        subscription.addService(product);
//        subscriptionRepository.save(subscription, 2);
        if(isSubscriberExist.get() && isSubscriptionExist.get() && isProductExist.get()){
            log.error("creatServiceCBM service already exist");
            return ResponseEntity.status(409).body(new CreateServiceCBMResponse("409","Service already exist/Duplicate entry",DateTimeUtil.now(),subscriberName,"CBM"+ request.getCbmSN()));
        }
        if(isSubscriptionExist.get()){
            subscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
            Set<Service> existingServices = subscription.getService();
            existingServices.add(product);
            subscription.setService(existingServices);
        }else{
            subscription.setService(new HashSet<>(List.of(product)));
        }
        subscriptionRepository.save(subscription, 2);

        // --- 5. CFS Logic ---
        String cfsName = "CFS" +Constants.UNDER_SCORE + subscriptionName;
        Service cfs = serviceRepository.findByDiscoveredName(cfsName)
                .orElseGet(() -> {
                    Service c = new Service();
                    try {
                        c.setLocalName(Validations.encryptName(cfsName));
                        c.setKind(Constants.SETAR_KIND_SETAR_CFS);
                        c.setContext(Constants.SETAR);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    c.setDiscoveredName(cfsName);
                    Map<String, Object> prop = new HashMap<>();
                    prop.put("serviceStatus", "Active");
                    prop.put("serviceType",request.getProductType());
                    if(request.getFxOrderID()!=null && !request.getFxOrderID().isBlank())
                    {
                        prop.put("TransactionID",request.getProductType());
                    }
                    prop.put("serviceStartDate",new Date());
                    prop.put("createdBy",
                            request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                    ? request.getCreatedBy()
                                    : "CA"
                    );
                    prop.put("actionName", ACTION_LABEL);
                    c.setProperties(prop);
                    c.setUsingService(new HashSet<>(List.of(product)));
                    serviceRepository.save(c, 2);
                    return c;
                });
        // --- 7. CBM Device Logic ---
        String cbmName = "CBM" +request.getCbmSN();
        if (cbmName.length() > 100) {
            return ResponseEntity.status(400).body(createErrorResponse("CBM name too long", 400));
        }
        // --- 6. RFS Logic ---
        String rfsName = "RFS" +Constants.UNDER_SCORE + subscriptionName;
        Service rfs = serviceRepository.findByDiscoveredName(rfsName)
                .orElseGet(() -> {
                    Service r = new Service();
                    try {
                        r.setLocalName(Validations.encryptName(rfsName));
                        r.setKind(Constants.SETAR_KIND_SETAR_RFS);
                        r.setContext(Constants.SETAR);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    r.setDiscoveredName(rfsName);
                    Map<String, Object> prop = new HashMap<>();
                    prop.put("serviceStatus", "Active");
                    prop.put("serviceType",request.getProductType());
                    prop.put("CFSReference",cfs.getDiscoveredName());
                    prop.put("createdBy",
                            request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                    ? request.getCreatedBy()
                                    : "CA"
                    );
                    prop.put("actionName", ACTION_LABEL);
                    r.setProperties(prop);
                    r.setUsedService(new HashSet<>(List.of(cfs)));
                    serviceRepository.save(r, 2);
                    return r;
                });

        LogicalDevice cbmDevice = cbmDeviceRepository.findByDiscoveredName(cbmName)
                .orElseGet(() -> {
                    LogicalDevice d = new LogicalDevice();
                    try {
                        d.setLocalName(Validations.encryptName(cbmName));
                        d.setKind(Constants.SETAR_KIND_STB_AP_CM_DEVICE);
                        d.setContext(Constants.SETAR);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    d.setDiscoveredName(cbmName);
                    Map<String, Object> deviceProps = new HashMap<>();
                    deviceProps.put("serialNo", request.getCbmSN());
                    deviceProps.put("macAddress", request.getCbmMac());
                    deviceProps.put("gatewayMacAddress", request.getCbmGatewayMac());
                    deviceProps.put("deviceType", request.getCbmType());
                    deviceProps.put("manufacturer", request.getCbmManufacturer());
                    deviceProps.put("deviceModel", request.getCbmModel());
                    deviceProps.put("OperationalState", "Active");
                    deviceProps.put("createdBy",
                            request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                    ? request.getCreatedBy()
                                    : "CA"
                    );
                    deviceProps.put("actionName", ACTION_LABEL);
                    d.setProperties(deviceProps);
                    d.setUsingService(new HashSet<>(List.of(rfs)));
                    cbmDeviceRepository.save(d, 2);
                    return d;
                });

        log.error(Constants.ACTION_COMPLETED);
        // --- 8. Final Response ---
        CreateServiceCBMResponse response = new CreateServiceCBMResponse();
        response.setStatus("201");
        response.setMessage("UIV action CreateServiceCBM executed successfully");
        response.setTimestamp(DateTimeUtil.now());
        response.setSubscriptionName(subscriptionName);
        response.setCbmName(cbmName);

        return response;
    }

    private CreateServiceCBMResponse createErrorResponse(String message, int status) {
        CreateServiceCBMResponse response = new CreateServiceCBMResponse();
        response.setStatus(String.valueOf(status));
        response.setMessage("UIV action CreateServiceCBM execution failed - " + message);
        response.setTimestamp(DateTimeUtil.now());
        return response;
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
