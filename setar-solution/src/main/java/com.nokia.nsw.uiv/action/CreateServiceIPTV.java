package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.AccessForbiddenException;
import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.exception.ModificationNotAllowedException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.response.CreateServiceFibernetResponse;
import com.nokia.nsw.uiv.response.CreateServiceVoIPResponse;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterface;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterfaceRepository;
import com.nokia.nsw.uiv.request.CreateServiceIPTVRequest;
import com.nokia.nsw.uiv.response.CreateServiceIPTVResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RestController
@Action
@Slf4j
public class CreateServiceIPTV implements HttpAction {

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

    @Override
    public Class getActionClass() {
        return CreateServiceIPTVRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);

        CreateServiceIPTVRequest request = (CreateServiceIPTVRequest) actionContext.getObject();

        try {
            // Validate mandatory parameters
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
            try{
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
            }catch (BadRequestException bre) {
                return new CreateServiceIPTVResponse("400", ERROR_PREFIX  + bre.getMessage(),
                        Instant.now().toString(), "","");
            }
            AtomicBoolean isSubscriberExist = new AtomicBoolean(true);
            AtomicBoolean isSubscriptionExist = new AtomicBoolean(true);
            AtomicBoolean isProductExist = new AtomicBoolean(true);


            // Construct entity names
            String subscriberName = request.getSubscriberName();
            String subscriptionName = subscriberName + Constants.UNDER_SCORE  + request.getServiceID();
            String productName = subscriberName + Constants.UNDER_SCORE + request.getProductSubtype() + Constants.UNDER_SCORE+ request.getServiceID();
            String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
            String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
            String ontName ="ONT" + request.getOntSN();
            String mgmtVlanName = request.getMenm() + Constants.UNDER_SCORE  + request.getVlanID();
            try{
                Validations.validateLength(subscriberName,"Subscriber");
                Validations.validateLength(subscriptionName,"Subscription");
                Validations.validateLength(productName, "Product");
                Validations.validateLength(ontName, "ONTDevice");
                Validations.validateLength(mgmtVlanName, "MgmtVlanName");
            }catch (BadRequestException bre){
                return new CreateServiceIPTVResponse("400", ERROR_PREFIX +  bre.getMessage(),
                        Instant.now().toString(), "","");
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
                subscriberProps.put("subscriberStatus", "Active");
                subscriberProps.put("subscriberFirstName", request.getFirstName());
                subscriberProps.put("subscriberLastName", request.getLastName());
                subscriberProps.put("companyName", request.getCompanyName());
                subscriberProps.put("contactPhoneNumber", request.getContactPhone());
                subscriberProps.put("subscriberAddress",request.getSubsAddress());
                subscriber.setProperties(subscriberProps);
                customerRepository.save(subscriber, 2);
                log.error("Created Subscriber: {}", subscriberName);
            }

            // ------------------- Subscription -------------------
            Optional<Subscription> optSubscription = subscriptionRepository.findByDiscoveredName(subscriptionName);
            Subscription subscription;
            if (optSubscription.isPresent()) {
                subscription = optSubscription.get();
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
                subscriptionProps.put("serviceMac", request.getOntMacAddr());
                subscriptionProps.put("iptvQosSessionProfile", request.getQosProfile());
                subscriptionProps.put("customerGroupId", request.getCustomerGroupID());
                subscriptionProps.put("subscriptionStatus", "Active");
                subscriptionProps.put("householdID", request.getHhid());
                subscriptionProps.put("servicePackage", request.getServicePackage());
                subscriptionProps.put("kenanSubscriberId", request.getKenanUidNo());
                subscriptionProps.put("gatewayMacAddress", request.getGatewayMac());
                subscriptionProps.put("serviceLink",((request.getOltName()!=null) && request.getOltName().equalsIgnoreCase("SRX"))?"SRX":"ONT");

                subscription.setProperties(subscriptionProps);
                subscription.setCustomer(subscriber); // association
                subscriptionRepository.save(subscription, 2);
                log.error("Created Subscription: {}", subscriptionName);
            }

            // ------------------- Product -------------------
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

                product.setProperties(productProps);
                product.setCustomer(subscriber);
                productRepository.save(product, 2);
                log.error("Created Product: {}", productName);
            }
            if(isSubscriberExist.get() && isSubscriptionExist.get() && isProductExist.get()){
                log.error("createServiceIPTV service already exist");
                return new CreateServiceIPTVResponse("409","Service already exist/Duplicate entry",Instant.now().toString(),subscriptionName,"ONT" + request.getOntSN());
            }
            if(isSubscriptionExist.get()){
                subscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
                Set<Service> existingServices = subscription.getService();
                existingServices.add(product);
                subscription.setService(existingServices);
            }else{
                subscription.setService(new HashSet<>(List.of(product)));
            }
            subscriptionRepository.save(subscription,2);

            // ------------------- Customer Facing Service (CFS) -------------------
            Optional<Service> optCFS = serviceCustomRepository.findByDiscoveredName(cfsName);
            Service cfs;
            if (optCFS.isPresent()) {
                cfs = optCFS.get();
                log.error("CFS already exists: {}", cfsName);
                return new CreateServiceIPTVResponse("409","CFS already exists/Duplicate entry",Instant.now().toString(),subscriptionName,"ONT" + request.getOntSN());
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

                cfs.setProperties(cfsProps);
                cfs.setUsingService(new HashSet<>(List.of(product)));
                serviceCustomRepository.save(cfs, 2);
                log.error("Created CFS: {}", cfsName);
            }

            // ------------------- Resource Facing Service (RFS) -------------------
            Optional<Service> optRFS = serviceCustomRepository.findByDiscoveredName(rfsName);
            Service rfs;
            if (optRFS.isPresent()) {
                rfs = optRFS.get();
                log.error("RFS already exists: {}", rfsName);
                return new CreateServiceIPTVResponse("409","RFS already exists/Duplicate entry",Instant.now().toString(),subscriptionName,"ONT" + request.getOntSN());
            } else {
                rfs = new Service();
                rfs.setLocalName(Validations.encryptName(rfsName));
                rfs.setDiscoveredName(rfsName);
                rfs.setKind("SetarRFS");
                rfs.setContext(Constants.SETAR);

                Map<String, Object> rfsProps = new HashMap<>();
                rfsProps.put("serviceStatus", "ACTIVE");
                rfsProps.put("serviceType", request.getProductType());

                rfs.setProperties(rfsProps);
                rfs.setUsedService(new HashSet<>(List.of(cfs)));
                serviceCustomRepository.save(rfs, 2);
                log.error("Created RFS: {}", rfsName);
            }

            String oltName=request.getOltName()==null?"":request.getOltName();

            // ------------------- Logical Devices -------------------
            // OLT Device
            Optional<LogicalDevice> optOlt = logicalDeviceRepository.findByDiscoveredName(oltName);
            LogicalDevice oltDevice;
            if (optOlt.isPresent()) {
                oltDevice = optOlt.get();
                oltDevice.setUsingService(new HashSet<>(List.of(rfs)));
               logicalDeviceRepository.save(oltDevice);

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
                oltProps.put("veipServiceTemplate", request.getTemplateNameVEIP());
                oltProps.put("veipIptvTemplate", request.getTemplateNameIPTV());
                oltProps.put("igmpTemplate", request.getTemplateNameIGMP());

                oltDevice.setProperties(oltProps);
                oltDevice.setUsingService(new HashSet<>(List.of(rfs)));
                logicalDeviceRepository.save(oltDevice, 2);
                log.error("Created OLT Device: {}", request.getOltName());
            }

            // ONT Device
            Optional<LogicalDevice> optOnt = logicalDeviceRepository.findByDiscoveredName(ontName);
            LogicalDevice ontDevice;
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
                if (request.getMenm() != "" && request.getMenm() !=null && request.getMenm() != "NA"){
                    ontProps.put("description",request.getMenm());
                }
                ontProps.put("serialNo", request.getOntSN());
                ontProps.put("deviceModel", request.getOntModel());
                ontProps.put("oltPosition",request.getOltName());
                ontProps.put("OperationalState", "Active");
                ontProps.put("iptvVlan", request.getVlanID());
                ontDevice.setProperties(ontProps);
                ontDevice.setUsingService(new HashSet<>(List.of(rfs)));
                ontDevice.setUsedResource(new HashSet<>(List.of(oltDevice)));
                logicalDeviceRepository.save(ontDevice, 2);
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
                vlanInterface.setProperties(vlanProps);
                vlanRepository.save(vlanInterface, 2);
                log.error("Created VLAN Interface: {}", mgmtVlanName);
            }

            log.error(Constants.ACTION_COMPLETED);

            return new CreateServiceIPTVResponse(
                    "201",
                    "IPTV service created",
                    Instant.now().toString(),
                    subscriptionName,
                    ontName
            );

        } catch (BadRequestException bre) {
            log.error("Validation error: {}", bre.getMessage(), bre);
            return new CreateServiceIPTVResponse(
                    "400",
                    ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage(),
                    String.valueOf(System.currentTimeMillis()),
                    "",
                    ""
            );
        } catch (AccessForbiddenException | ModificationNotAllowedException ex) {
            log.error("Access or modification error: {}", ex.getMessage(), ex);
            return new CreateServiceIPTVResponse(
                    "403",
                    ERROR_PREFIX + ex.getMessage(),
                    String.valueOf(System.currentTimeMillis()),
                    "",
                    ""
            );
        } catch (Exception ex) {
            log.error("Unhandled exception during CreateServiceIPTV", ex);
            return new CreateServiceIPTVResponse(
                    "500",
                    ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage(),
                    String.valueOf(System.currentTimeMillis()),
                    "",
                    ""
            );
        }
    }
}
