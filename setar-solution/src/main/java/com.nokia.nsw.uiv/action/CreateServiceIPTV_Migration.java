package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.AccessForbiddenException;
import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.exception.ModificationNotAllowedException;
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
import com.nokia.nsw.uiv.request.CreateServiceFibernetBulkRequest;
import com.nokia.nsw.uiv.request.CreateServiceFibernetRequest;
import com.nokia.nsw.uiv.request.CreateServiceIPTVBulkRequest;
import com.nokia.nsw.uiv.request.CreateServiceIPTVRequest;
import com.nokia.nsw.uiv.response.CreateServiceFibernetResponse;
import com.nokia.nsw.uiv.response.CreateServiceIPTVResponse;
import com.nokia.nsw.uiv.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RestController
@Action
@Slf4j
public class CreateServiceIPTV_Migration implements HttpAction {

    protected static final String ACTION_LABEL = Constants.CREATE_SERVICE_IPTV;
    private static final String ERROR_PREFIX = "UIV action CreateServiceIPTV execution failed - ";

    @Autowired
    private CustomerCustomRepository customerRepository;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;

    @Autowired
    private ProductCustomRepository productRepository;

    @Autowired
    private LogicalDeviceCustomRepository logicalDeviceRepository;

    @Autowired
    private LogicalInterfaceCustomRepository vlanRepository;

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Autowired
    private LockManager lockManager;

    @Autowired
    private CreateServiceIPTV_Migration self;

    @Override
    public Class getActionClass() {
        return CreateServiceIPTVBulkRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);

        Object obj = actionContext.getObject();

        if (!(obj instanceof CreateServiceIPTVBulkRequest)) {
            return new CreateServiceIPTVResponse(
                    "400",
                    "Invalid request type",
                    Instant.now().toString(),
                    null,
                    null
            );
        }

        CreateServiceIPTVBulkRequest bulk = (CreateServiceIPTVBulkRequest) obj;

        List<CreateServiceIPTVResponse> responses = new ArrayList<>();
        CreateServiceIPTVResponse response;

        for (CreateServiceIPTVRequest request : bulk.getServices()) {

            try {
                responses.add(self.singleReqprocess(request));
            } catch (BadRequestException bre) {
                log.error("Validation error: {}", bre.getMessage(), bre);
                responses.add(new  CreateServiceIPTVResponse(
                        "400",
                        ERROR_PREFIX  + bre.getMessage(),
                        DateTimeUtil.now(),
                        "",
                        ""
                ));
            } catch (AccessForbiddenException | ModificationNotAllowedException ex) {
                log.error("Access or modification error: {}", ex.getMessage(), ex);
                responses.add(new CreateServiceIPTVResponse(
                        "403",
                        ERROR_PREFIX + ex.getMessage(),
                        DateTimeUtil.now(),
                        "",
                        ""
                ));
            } catch (Exception ex) {
                log.error("Unhandled exception during CreateServiceIPTV", ex);
                responses.add(new CreateServiceIPTVResponse(
                        "500",
                        ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage(),
                        DateTimeUtil.now(),
                        "",
                        ""
                ));
            }
        }
        return responses;
    }
    @Transactional(rollbackFor = Exception.class)
    public CreateServiceIPTVResponse singleReqprocess(CreateServiceIPTVRequest request) throws BadRequestException, AccessForbiddenException, ModificationNotAllowedException {
        CreateServiceIPTVResponse response=null;
        log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);

            Validations.validateMandatoryParams(request.getSubscriberName(), "subscriberName");
            Validations.validateMandatoryParams(request.getProductType(), "productType");
            Validations.validateMandatoryParams(request.getProductSubtype(), "productSubtype");
            Validations.validateMandatoryParams(request.getOntSN(), "ontSN");
            Validations.validateMandatoryParams(request.getOltName(), "oltName");
            Validations.validateMandatoryParams(request.getQosProfile(), "qosProfile");
            Validations.validateMandatoryParams(request.getVlanID(), "vlanID");
            Validations.validateMandatoryParams(request.getHhid(), "hhid");
            Validations.validateMandatoryParams(request.getMenm(), "menm");
            Validations.validateMandatoryParams(request.getServiceID(), "serviceID");
            Validations.validateMandatoryParams(request.getCustomerGroupID(), "customerGroupId");
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);

        AtomicBoolean isSubscriberExist = new AtomicBoolean(true);
        AtomicBoolean isSubscriptionExist = new AtomicBoolean(true);
        AtomicBoolean isProductExist = new AtomicBoolean(true);


        // Construct entity names
        String subscriberName = request.getSubscriberName();
        String subscriptionName = subscriberName + Constants.UNDER_SCORE + request.getServiceID();
        String productName = subscriberName + Constants.UNDER_SCORE + request.getProductSubtype() + Constants.UNDER_SCORE + request.getServiceID();
        String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
        String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
        String ontName = "ONT" + request.getOntSN();
        String mgmtVlanName = request.getMenm() + Constants.UNDER_SCORE + request.getVlanID();
        try {
            Validations.validateLength(subscriberName, "Subscriber");
            Validations.validateLength(subscriptionName, "Subscription");
            Validations.validateLength(productName, "Product");
            Validations.validateLength(ontName, "ONTDevice");
            Validations.validateLength(mgmtVlanName, "MgmtVlanName");
        } catch (BadRequestException bre) {
            response=new CreateServiceIPTVResponse("400", ERROR_PREFIX + bre.getMessage(),
                    Instant.now().toString(), "", "");
        }

            // ------------------- Subscriber -------------------
            Optional<Customer> optSubscriber = customerRepository.findByDiscoveredName(subscriberName);
            Customer subscriber;
            if (optSubscriber.isPresent()) {
                subscriber = optSubscriber.get();
                log.error("Subscriber already exists: {}", subscriberName);
            } else {
                isSubscriberExist.set(false);
                subscriber = new Customer();
                subscriber.setLocalName(Validations.encryptName(subscriberName));
                subscriber.setDiscoveredName(subscriberName);
                subscriber.setKind("SetarSubscriber");
                subscriber.setContext(Constants.SETAR);

                Map<String, Object> subscriberProps = new HashMap<>();
                subscriberProps.put("accountNumber", subscriberName);
                subscriberProps.put("houseHoldId", request.getHhid());
                subscriberProps.put("subscriberType", "Regular");
                if(request.getSubscriberStatus()!=null){
                    subscriberProps.put("subscriberStatus", request.getSubscriberStatus());
                }else{
                    subscriberProps.put("subscriberStatus", "Active");
                }
                subscriberProps.put("subscriberFirstName", request.getFirstName());
                subscriberProps.put("subscriberLastName", request.getLastName());
                subscriberProps.put("companyName", request.getCompanyName());
                subscriberProps.put("contactPhoneNumber", request.getContactPhone());
                subscriberProps.put("subscriberAddress", request.getSubsAddress());
                subscriberProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                subscriberProps.put("actionName", ACTION_LABEL);
                subscriber.setProperties(subscriberProps);
                log.error("before Subscriber Save " + subscriberName + ":" + subscriber.get_version());
                customerRepository.save(subscriber,2);
                log.error("after Subscriber Save " + subscriberName + ":" + subscriber.get_version());
                log.error("Created Subscriber: {}", subscriberName);
            }
            Optional<Product> optProduct = productRepository.findByDiscoveredName(productName);
            Product product;
            if (optProduct.isPresent()) {
                product = optProduct.get();
                log.error("Product already exists: {}", productName);
            } else {
                isProductExist.set(false);
                product = new Product();
                product.setLocalName(Validations.encryptName(productName));
                product.setDiscoveredName(productName);
                product.setKind("SetarProduct");
                product.setContext(Constants.SETAR);

                Map<String, Object> productProps = new HashMap<>();
                productProps.put("productType", request.getProductType());
                productProps.put("productStatus", "ACTIVE");
                productProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                productProps.put("actionName", ACTION_LABEL);
                product.setProperties(productProps);
                product.setCustomer(subscriber);
                log.error("before product Save " + productName + ":" + product.get_version());
                productRepository.save(product,2);
                log.error("after product Save " + productName + ":" + product.get_version());
                log.error("Created Product: {}", productName);
            }
            // ------------------- Subscription -------------------
            Optional<Subscription> optSubscription = subscriptionRepository.findByDiscoveredName(subscriptionName);
            Subscription subscription;
            if (optSubscription.isPresent()) {
                subscription = optSubscription.get();
                subscription = subscriptionRepository.findByDiscoveredName(subscriptionName).get();
                Set<Service> existingServices = subscription.getService();
                existingServices.add(product);
                subscription.setService(existingServices);
                log.error("Subscription already exists: {}", subscriptionName);
            } else {
                isSubscriptionExist.set(false);
                subscription = new Subscription();
                subscription.setLocalName(Validations.encryptName(subscriptionName));
                subscription.setDiscoveredName(subscriptionName);
                subscription.setKind("SetarSubscription");
                subscription.setContext(Constants.SETAR);

                Map<String, Object> subscriptionProps = new HashMap<>();
                subscriptionProps.put("serviceID", request.getServiceID());
                subscriptionProps.put("serviceSubType", request.getProductSubtype());
                subscriptionProps.put("serviceSN", request.getOntSN());
                subscriptionProps.put("serviceMAC", request.getOntMacAddr());
                subscriptionProps.put("iptvQosSessionProfile", request.getQosProfile());
                subscriptionProps.put("customerGroupId", request.getCustomerGroupID());
                if(request.getSubscriberStatus()!=null)
                {
                    subscriptionProps.put("subscriptionStatus", request.getSubscriptionStatus());
                }else{
                    subscriptionProps.put("subscriptionStatus", "Active");
                }
                subscriptionProps.put("householdID", request.getHhid());
                subscriptionProps.put("servicePackage", request.getServicePackage());
                subscriptionProps.put("kenanSubscriberId", request.getKenanUidNo());
                subscriptionProps.put("gatewayMacAddress", request.getGatewayMac());
                subscriptionProps.put("serviceLink", ((request.getOltName() != null) && request.getOltName().equalsIgnoreCase("SRX")) ? "SRX" : "ONT");
                subscriptionProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                subscriptionProps.put("actionName", ACTION_LABEL);
                subscription.setProperties(subscriptionProps);
                subscription.setCustomer(subscriber);
                subscription.setService(new HashSet<>(List.of(product)));
                subscriptionRepository.save(subscription,2);
                log.error("Created Subscription: {}", subscriptionName);
            }

            // ------------------- Product -------------------

            if (isSubscriberExist.get() && isSubscriptionExist.get() && isProductExist.get()) {
                log.error("createServiceIPTV service already exist");
                throw new DuplicateServiceException("Service already exist/Duplicate entry");
            }
            log.error("before Subscription Save " + subscriptionName + ":" + subscription.get_version());
            log.error("after Subscription Save " + subscriptionName + ":" + subscription.get_version());
            // ------------------- Customer Facing Service (CFS) -------------------
            Optional<Service> optCFS = serviceCustomRepository.findByDiscoveredName(cfsName);
            Service cfs;
            if (optCFS.isPresent()) {
                cfs = optCFS.get();
                log.error("CFS already exists: {}", cfsName);
                response = new CreateServiceIPTVResponse("409", "CFS already exists/Duplicate entry", Instant.now().toString(), subscriptionName, "ONT" + request.getOntSN());
            } else {
                cfs = new Service();
                cfs.setLocalName(Validations.encryptName(cfsName));
                cfs.setDiscoveredName(cfsName);
                cfs.setKind("SetarCFS");
                cfs.setContext(Constants.SETAR);

                Map<String, Object> cfsProps = new HashMap<>();
                cfsProps.put("serviceStartDate", Instant.now().toString());
                cfsProps.put("transactionId", request.getFxOrderID());
                cfsProps.put("serviceStatus", "ACTIVE");
                cfsProps.put("serviceType", request.getProductType());
                cfsProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                cfsProps.put("actionName", ACTION_LABEL);
                cfs.setProperties(cfsProps);
                cfs.setUsingService(new HashSet<>(List.of(product)));
                log.error("before cfs Save " + cfsName + ":" + cfs.get_version());
                serviceCustomRepository.save(cfs,2);
                log.error("after cfs Save " + cfsName + ":" + cfs.get_version());
                log.error("Created CFS: {}", cfsName);
            }

            // ------------------- Resource Facing Service (RFS) -------------------
            Optional<Service> optRFS = serviceCustomRepository.findByDiscoveredName(rfsName);
            Service rfs;
            if (optRFS.isPresent()) {
                rfs = optRFS.get();
                log.error("RFS already exists: {}", rfsName);
                throw new DuplicateServiceException("RFS already exists/Duplicate entry");
            } else {
                rfs = new Service();
                rfs.setLocalName(Validations.encryptName(rfsName));
                rfs.setDiscoveredName(rfsName);
                rfs.setKind("SetarRFS");
                rfs.setContext(Constants.SETAR);

                Map<String, Object> rfsProps = new HashMap<>();
                rfsProps.put("serviceStatus", "ACTIVE");
                rfsProps.put("serviceType", request.getProductType());
                rfsProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                rfsProps.put("actionName", ACTION_LABEL);
                rfs.setProperties(rfsProps);
                rfs.setUsedService(new HashSet<>(List.of(cfs)));
                serviceCustomRepository.save(rfs,2);
                log.error("Created RFS: {}", rfsName);
            }


        String oltName = request.getOltName() == null ? "" : request.getOltName();
        LogicalDevice oltDevice;
            // ------------------- Logical Devices -------------------
            // OLT Device
            Optional<LogicalDevice> optOlt = logicalDeviceRepository.findByDiscoveredName(oltName);
            if (optOlt.isPresent()) {
                oltDevice = optOlt.get();
                oltDevice.setUsingService(new HashSet<>(List.of(rfs)));
                log.error("OLT already exists: {}", oltName);
            } else {
                oltDevice = new LogicalDevice();
                oltDevice.setLocalName(Validations.encryptName(oltName));
                oltDevice.setDiscoveredName(oltName);
                oltDevice.setKind("OLTDevice");
                oltDevice.setContext(Constants.SETAR);

                Map<String, Object> oltProps = new HashMap<>();
                oltProps.put("oltPosition", request.getOltName());
                oltProps.put("OperationalState", "Active");
                oltProps.put("ontTemplate", request.getTemplateNameONT());
                oltProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                oltProps.put("actionName", ACTION_LABEL);
                oltDevice.setProperties(oltProps);
                logicalDeviceRepository.save(oltDevice,2);
                log.error("Created OLT Device: {}", request.getOltName());
            }
        LogicalDevice ontDevice;
            // ONT Device
            Optional<LogicalDevice> optOnt = logicalDeviceRepository.findByDiscoveredName(ontName);
            if (optOnt.isPresent()) {
                ontDevice = optOnt.get();
                log.error("ONT already exists: {}", ontName);
            } else {
                ontDevice = new LogicalDevice();
                ontDevice.setLocalName(Validations.encryptName(ontName));
                ontDevice.setDiscoveredName(ontName);
                ontDevice.setKind("ONTDevice");
                ontDevice.setContext(Constants.SETAR);
                Map<String, Object> ontProps = new HashMap<>();
                ontProps.put("serialNo", request.getOntSN());
                ontProps.put("deviceModel", request.getOntModel());
                ontProps.put("oltPosition", request.getOltName());
                ontProps.put("OperationalState", "Active");
                ontProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                ontProps.put("actionName", ACTION_LABEL);
                ontDevice.setProperties(ontProps);
                logicalDeviceRepository.save(ontDevice,2);
                log.error("Created ONT Device: {}", ontName);
            }

            // VLAN Interface
            Optional<LogicalInterface> optVlan = vlanRepository.findByDiscoveredName(mgmtVlanName);
            LogicalInterface vlanInterface;
            if (optVlan.isPresent()) {
                vlanInterface = optVlan.get();
                log.error("VLAN Interface already exists: {}", mgmtVlanName);
            } else {
                vlanInterface = new LogicalInterface();
                vlanInterface.setLocalName(Validations.encryptName(mgmtVlanName));
                vlanInterface.setDiscoveredName(mgmtVlanName);
                vlanInterface.setKind("VLANInterface");
                vlanInterface.setContext(Constants.SETAR);

                Map<String, Object> vlanProps = new HashMap<>();
                vlanProps.put("vlanId", request.getVlanID());
                vlanProps.put("OperationalState", "Active");
                vlanProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                vlanProps.put("actionName", ACTION_LABEL);
                vlanInterface.setProperties(vlanProps);
                log.error("before cfs Save " + mgmtVlanName + ":" + vlanInterface.get_version());
                vlanRepository.save(vlanInterface,2);
                log.error("after cfs Save " + mgmtVlanName + ":" + vlanInterface.get_version());
                log.error("Created VLAN Interface: {}", mgmtVlanName);
            }

            Set<Service> services = ontDevice.getUsingService();
            if (services == null) services = new HashSet<>();
            services.add(rfs);
            ontDevice.getProperties().put("iptvVlan", request.getVlanID());
            ontDevice.setUsingService(services);
            ontDevice.setUsedResource(new HashSet<>(List.of(oltDevice)));
            if (request.getMenm() != "" && request.getMenm() != null && request.getMenm() != "NA") {
                ontDevice.getProperties().put("description", request.getMenm());
            }
            oltDevice.getProperties().put("veipServiceTemplate", request.getTemplateNameVEIP());
            oltDevice.getProperties().put("veipIptvTemplate", request.getTemplateNameIPTV());
            oltDevice.getProperties().put("igmpTemplate", request.getTemplateNameIGMP());
            oltDevice.setUsingService(services);
            log.error("before OLT Save " + oltName + ":" + oltDevice.get_version());
            log.error("before OLT Save " + oltName + ":" + oltDevice.get_version());
            log.error("After ONT Save " + ontName + ":" + ontDevice.get_version());
            log.error("After ONT Save " + ontName + ":" + ontDevice.get_version());

        log.error(Constants.ACTION_COMPLETED);

        response = new CreateServiceIPTVResponse(
                "201",
                "IPTV service created",
                Instant.now().toString(),
                subscriptionName,
                ontName
        );
        return response;
    }
}
