package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterface;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.CreateServiceFibernetRequest;
import com.nokia.nsw.uiv.response.CreateServiceFibernetResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Action
@Slf4j
public class CreateServiceFibernet implements HttpAction {

    private static final String ACTION_LABEL = "CreateServiceFibernet";
    private static final String ERROR_PREFIX = "UIV action CreateServiceFibernet execution failed - ";

    @Autowired
    private CustomerCustomRepository customerRepository;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;

    @Autowired
    private ProductCustomRepository productRepository;

    @Autowired
    private ServiceCustomRepository serviceRepository;


    @Autowired
    private LogicalDeviceCustomRepository logicalDeviceRepository;

    @Autowired
    private LogicalInterfaceCustomRepository logicalInterfaceRepository;

    @Override
    public Class<?> getActionClass() {
        return CreateServiceFibernetRequest.class;
    }



    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        CreateServiceFibernetRequest request = (CreateServiceFibernetRequest) actionContext.getObject();

        try {
            // 1. Validate mandatory params
            try{
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatory(request.getSubscriberName(), "subscriberName");
                Validations.validateMandatory(request.getProductType(), "productType");
                Validations.validateMandatory(request.getProductSubtype(), "productSubtype");
                Validations.validateMandatory(request.getOntSN(), "ontSN");
                Validations.validateMandatory(request.getOltName(), "oltName");
                Validations.validateMandatory(request.getQosProfile(), "qosProfile");
                Validations.validateMandatory(request.getVlanID(), "vlanID");
                Validations.validateMandatory(request.getMenm(), "menm");
                Validations.validateMandatory(request.getHhid(), "hhid");
                Validations.validateMandatory(request.getServiceID(), "serviceID");
                Validations.validateMandatory(request.getOntModel(), "ontModel");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            }catch (BadRequestException bre) {
                return new CreateServiceFibernetResponse("400", ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage(),
                        Instant.now().toString(), "","");
            }
            // optional: template names etc.

            // Build canonical names
            String subscriberName = request.getSubscriberName() + Constants.UNDER_SCORE  + request.getOntSN();
            String subscriptionName = request.getSubscriberName() + Constants.UNDER_SCORE  + request.getServiceID() + Constants.UNDER_SCORE  + request.getOntSN();
            String productName = request.getSubscriberName() + Constants.UNDER_SCORE  + request.getProductSubtype() + Constants.UNDER_SCORE  + request.getServiceID();
            String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
            String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
            String ontName ="ONT" + request.getOntSN();

            AtomicBoolean isSubscriberExist = new AtomicBoolean(true);
            AtomicBoolean isSubscriptionExist = new AtomicBoolean(true);
            AtomicBoolean isProductExist = new AtomicBoolean(true);
            // Length checks
            if (ontName.length() > 100) {
                return createErrorResponse("ONT name too long", "400", "", "");
            }
            if (subscriberName.length() > 100) {
                return createErrorResponse("Subscriber name too long", "400", "", "");
            }
            if (subscriptionName.length() > 100) {
                return createErrorResponse("Subscription name too long", "400", "", "");
            }
            if (productName.length() > 100) {
                return createErrorResponse("Product name too long", "400", "", "");
            }

            // 2. Subscriber: create or fetch
            Optional<Customer> optCustomer = customerRepository.findByDiscoveredName(subscriberName);
            Customer subscriber;
            if (optCustomer.isPresent()) {
                subscriber = optCustomer.get();
                log.error("Found existing subscriber: {}", subscriberName);
            } else {
                isSubscriberExist.set(false);
                subscriber = new Customer();
                subscriber.setDiscoveredName(subscriberName);
                subscriber.setLocalName(Validations.encryptName(subscriberName));
                subscriber.setKind(Constants.SETAR_KIND_SETAR_SUBSCRIBER); // if you have a constant
                subscriber.setContext(Constants.SETAR);
                Map<String, Object> custProps = new HashMap<>();
                custProps.put("accountNumber", request.getSubscriberName());
                custProps.put("subscriberStatus", "Active");
                custProps.put("subscriberType", "Regular");
                if (request.getFirstName() != null) custProps.put("subscriberFirstName", request.getFirstName());
                if (request.getLastName() != null) custProps.put("subscriberLastName", request.getLastName());
                if (request.getSubsAddress() != null) custProps.put("subscriberAddress", request.getSubsAddress());
                if (request.getCompanyName() != null) custProps.put("companyName", request.getCompanyName());
                if (request.getContactPhone() != null) custProps.put("contactPhoneNumber", request.getContactPhone());
                if (request.getHhid() != null) custProps.put("houseHoldId", request.getHhid());
                if (request.getEmail() != null) custProps.put("email", request.getEmail());
                if (request.getEmailPassword() != null) custProps.put("emailPassword", request.getEmailPassword());
                custProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                custProps.put("actionName", ACTION_LABEL);
                subscriber.setProperties(custProps);
                customerRepository.save(subscriber, 2);
                log.error("Created subscriber: {}", subscriberName);
            }

            // 3. Subscription: create or fetch
            Optional<Subscription> optSubscription = subscriptionRepository.findByDiscoveredName(subscriptionName);
            Subscription subscription;
            if (optSubscription.isPresent()) {
                subscription = optSubscription.get();
                log.error("Found existing subscription: {}", subscriptionName);
            } else {
                isSubscriptionExist.set(false);
                subscription = new Subscription();
                subscription.setLocalName(Validations.encryptName(subscriptionName));
                subscription.setDiscoveredName(subscriptionName);
                subscription.setKind(Constants.SETAR_KIND_SETAR_SUBSCRIPTION);
                subscription.setContext(Constants.SETAR);
                Map<String, Object> subProps = new HashMap<>();
                subProps.put("subscriptionStatus", "Active");
                subProps.put("serviceSubType", request.getProductSubtype());
                subProps.put("serviceLink", "ONT");
                subProps.put("subscriptionDetails", request.getSubscriberID());
                subProps.put("serviceID", request.getServiceID());
                subProps.put("serviceSN", request.getOntSN());
                subProps.put("oltPosition", request.getOltName());
                subProps.put("householdID", request.getHhid());
                subProps.put("subscriberID_CableModem", request.getSubscriberID());
                subProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                subProps.put("actionName", ACTION_LABEL);
                if (request.getQosProfile() != null) subProps.put("veipQosSessionProfile", request.getQosProfile());
                if (request.getKenanUidNo() != null) subProps.put("kenanSubscriberId", request.getKenanUidNo());
                subscription.setProperties(subProps);
                subscription.setCustomer(subscriber);
                subscriptionRepository.save(subscription, 2);
                log.error("Created subscription: {}", subscriptionName);
            }

            // 4. Product: create or fetch
            Optional<Product> optProduct = productRepository.findByDiscoveredName(productName);
            Product product;
            if (optProduct.isPresent()) {
                product = optProduct.get();
                log.error("Found existing product: {}", productName);
            } else {
                isProductExist.set(false);
                product = new Product();
                product.setLocalName(Validations.encryptName(productName));
                product.setDiscoveredName(productName);
                product.setKind(Constants.SETAR_KIND_SETAR_PRODUCT);
                product.setContext(Constants.SETAR);
                Map<String, Object> prodProps = new HashMap<>();
                prodProps.put("productType", request.getProductType());
                prodProps.put("productSubtype", request.getProductSubtype());
                prodProps.put("productStatus", "Active");
                prodProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                prodProps.put("actionName", ACTION_LABEL);
                product.setProperties(prodProps);
                product.setCustomer(subscriber);
                productRepository.save(product, 2);
                log.error("Created product: {}", productName);
            }
            if(isSubscriberExist.get() && isSubscriptionExist.get() && isProductExist.get()){
                log.error("createServiceFibernate service already exist");
                return new CreateServiceFibernetResponse("409","Service already exist/Duplicate entry",Instant.now().toString(),subscriptionName,ontName);
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

            // 5. CFS: create or fetch
            Optional<Service> optCfs = serviceRepository.findByDiscoveredName(cfsName);
            Service cfs;
            if (optCfs.isPresent()) {
                cfs = optCfs.get();
                log.error("Found existing CFS: {}", cfsName);
            } else {
                cfs = new Service();
                cfs.setLocalName(Validations.encryptName(cfsName));
                cfs.setDiscoveredName(cfsName);
                cfs.setKind(Constants.SETAR_KIND_SETAR_CFS);
                cfs.setContext(Constants.SETAR);
                Map<String, Object> cfsProps = new HashMap<>();
                cfsProps.put("serviceStartDate", Instant.now().toString());
                cfsProps.put("serviceType", request.getProductType());
                cfsProps.put("serviceStatus", "Active");
                cfsProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                cfsProps.put("actionName", ACTION_LABEL);
                cfs.setProperties(cfsProps);
                if (request.getFxOrderID() != null) cfsProps.put("transactionId", request.getFxOrderID());
                cfs.setUsingService(new HashSet<>(List.of(product)));
                serviceRepository.save(cfs, 2);
                log.error("Created CFS: {}", cfsName);
            }

            // 6. RFS: create or fetch
            Optional<Service> optRfs = serviceRepository.findByDiscoveredName(rfsName);
            Service rfs;
            if (optRfs.isPresent()) {
                rfs = optRfs.get();
                log.error("Found existing RFS: {}", rfsName);
            } else {
                rfs = new Service();
                rfs.setLocalName(Validations.encryptName(rfsName));
                rfs.setDiscoveredName(rfsName);
                rfs.setKind(Constants.SETAR_KIND_SETAR_RFS);
                rfs.setContext(Constants.SETAR);
                Map<String, Object> rfsProps = new HashMap<>();
                rfsProps.put("serviceType", request.getProductType());
                rfsProps.put("serviceStatus", "Active");
                rfsProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                rfsProps.put("actionName", ACTION_LABEL);
//                if (request.getFxOrderID() != null) rfsProps.put("transactionId", request.getFxOrderID());
                rfs.setProperties(rfsProps);
                rfs.setUsedService(new HashSet<>(List.of(cfs)));
                serviceRepository.save(rfs, 2);
                log.error("Created RFS: {}", rfsName);
            }
            // 7. OLT device: find or create as LogicalDevice with kind=OLT
            String oltName = request.getOltName() == null ? "" : request.getOltName();
            LogicalDevice oltDevice = null;
            if (!oltName.isEmpty()) {
                Optional<LogicalDevice> optOlt = logicalDeviceRepository.findByDiscoveredName(oltName);
                if (optOlt.isPresent()) {
                    oltDevice = optOlt.get();
                } else {
                    oltDevice = new LogicalDevice();
                    oltDevice.setLocalName(Validations.encryptName(oltName));
                    oltDevice.setDiscoveredName(oltName);
                    oltDevice.setKind(Constants.SETAR_KIND_OLT_DEVICE);
                    oltDevice.setContext(Constants.SETAR);
                    Map<String, Object> props = new HashMap<>();
                    props.put("localName", oltName);
                    if (request.getTemplateNameVEIP() != null) props.put("veipServiceTemplate", request.getTemplateNameVEIP());
                    if (request.getTemplateNameHSI() != null) props.put("veipHsiTemplate", request.getTemplateNameHSI());
                    props.put("oltPosition", request.getOltName());
                    props.put("OperationalState", "Active");
                    props.put("createdBy",
                            request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                    ? request.getCreatedBy()
                                    : "CA"
                    );
                    props.put("actionName", ACTION_LABEL);
                    oltDevice.setProperties(props);
                    oltDevice.setUsingService(new HashSet<>(List.of(rfs)));
                    logicalDeviceRepository.save(oltDevice, 2);
                    log.error("Created OLT device: {}", oltName);
                }
            }


            // 8. ONT device: find or create as LogicalDevice with kind=ONT
            String ontContext = Constants.SETAR;
            Optional<LogicalDevice> optOnt = logicalDeviceRepository.findByDiscoveredName(ontName);
            LogicalDevice ontDevice;
            if (optOnt.isPresent()) {
                ontDevice = optOnt.get();
                Map<String, Object> ontProps = ontDevice.getProperties();
                ontProps.put("serialNo", request.getOntSN());
                ontProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                ontProps.put("actionName", ACTION_LABEL);
                if (request.getOntModel() != null) ontProps.put("deviceModel", request.getOntModel());
                if (request.getTemplateNameONT() != null) ontProps.put("ontTemplate", request.getTemplateNameONT());
                if (request.getMenm() != null) ontProps.put("description", request.getMenm());
                if (request.getVlanID() != null) ontProps.put("mgmtVlan", request.getVlanID());
//                Set<Service> used = new HashSet<>();
//                used.add(rfs);
                ontDevice.setUsingService(new HashSet<>(List.of(rfs)));
                ontDevice.setProperties(ontProps);
                logicalDeviceRepository.save(ontDevice, 2);
                log.error("Found existing ONT: {}", ontName);
            } else {
                ontDevice = new LogicalDevice();
                ontDevice.setLocalName(Validations.encryptName(ontName));
                ontDevice.setDiscoveredName(ontName);
                ontDevice.setKind(Constants.SETAR_KIND_ONT_DEVICE);
                ontDevice.setContext(ontContext);
                Map<String, Object> ontProps = new HashMap<>();
                ontProps.put("serialNo", request.getOntSN());
                ontProps.put("oltPosition", request.getOltName());
                ontProps.put("OperationalState", "Active");
                ontProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                ontProps.put("actionName", ACTION_LABEL);
                if (request.getOntModel() != null) ontProps.put("deviceModel", request.getOntModel());
                if (request.getTemplateNameONT() != null) ontProps.put("ontTemplate", request.getTemplateNameONT());
                if (request.getMenm() != null) ontProps.put("description", request.getMenm());
                if (request.getVlanID() != null) ontProps.put("mgmtVlan", request.getVlanID());

                ontDevice.setUsingService(new HashSet<>(List.of(rfs)));
                ontDevice.setProperties(ontProps);
                ontDevice.setUsedResource(new HashSet<>(List.of(oltDevice)));
                logicalDeviceRepository.save(ontDevice, 2);
                log.error("Created ONT device: {}", ontName);
            }

            // 9. VLAN interface (LogicalInterface) creation if needed
            if (request.getMenm() != null && request.getVlanID() != null) {
                String vlanName = request.getMenm() + Constants.UNDER_SCORE  + request.getVlanID();
                String vlanContext=Constants.SETAR;
                Optional<LogicalInterface> optVlan = logicalInterfaceRepository.findByDiscoveredName(vlanName);
                if (!optVlan.isPresent()) {
                    LogicalInterface vlan = new LogicalInterface();
                    vlan.setLocalName(Validations.encryptName(vlanName));
                    vlan.setDiscoveredName(vlanName);
                    vlan.setKind(Constants.SETAR_KIND_VLAN_INTERFACE);
                    vlan.setContext(vlanContext);
                    Map<String, Object> vlanProps = new HashMap<>();
                    vlanProps.put("vlanId", request.getVlanID());
                    vlanProps.put("OperationalState", "Active");
                    vlanProps.put("serviceId", request.getServiceID());
                    vlanProps.put("createdBy",
                            request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                    ? request.getCreatedBy()
                                    : "CA"
                    );
                    vlanProps.put("actionName", ACTION_LABEL);
                    vlan.setProperties(vlanProps);
                    //uncommented for checking devicetointerface association
                    logicalInterfaceRepository.save(vlan, 2);
                    log.error("Created VLAN interface: {}", vlanName);
                    if (oltDevice != null) {
                        oltDevice = logicalDeviceRepository.findByDiscoveredName(oltDevice.getDiscoveredName()).get();
                        oltDevice.setContained(new HashSet<>(List.of(vlan)));
                        logicalDeviceRepository.save(oltDevice);
                    }

                }
            }


            // 10. Link RFS -> ONT or OLT (if model supports linking via properties)
//            Map<String, Object> rfsProps = rfs.getProperties() == null ? new HashMap<>() : rfs.getProperties();
//            rfsProps.put("serviceSN", request.getOntSN());
//            if (oltDevice != null) rfsProps.put("oltPosition", oltDevice.getDiscoveredName());
//            rfs.setProperties(rfsProps);
//            rfsRepository.save(rfs, 2);
            log.error(Constants.ACTION_COMPLETED);
            // 11. Final response
            String ontNameResp = ontDevice != null ? ontDevice.getDiscoveredName() : "";
            CreateServiceFibernetResponse response = new CreateServiceFibernetResponse();
            response.setStatus("201");
            response.setMessage("Fibernet service created");
            response.setTimestamp(Instant.now().toString());
            response.setSubscriptionName(subscriptionName);
            response.setOntName(ontNameResp);
            return response;

        } catch (BadRequestException bre) {
            log.error("Validation error creating Fibernet", bre);
            return createErrorResponse(bre.getMessage(), "400", "", "");
        } catch (Exception ex) {
            log.error("Unhandled error in CreateServiceFibernet", ex);
            return createErrorResponse("Internal server error occurred - " + ex.getMessage(), "500", "", "");
        }
    }

    private CreateServiceFibernetResponse createErrorResponse(String message, String status, String subscriptionName, String ontName) {
        CreateServiceFibernetResponse resp = new CreateServiceFibernetResponse();
        resp.setStatus(status);
        resp.setMessage(ERROR_PREFIX + message);
        resp.setTimestamp(Instant.now().toString());
        resp.setSubscriptionName(subscriptionName == null ? "" : subscriptionName);
        resp.setOntName(ontName == null ? "" : ontName);
        return resp;
    }
}
