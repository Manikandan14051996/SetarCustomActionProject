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
import com.nokia.nsw.uiv.request.CreateServiceIPTVBulkRequest;
import com.nokia.nsw.uiv.request.CreateServiceIPTVRequest;
import com.nokia.nsw.uiv.request.CreateServiceVoIPBulkRequest;
import com.nokia.nsw.uiv.request.CreateServiceVoIPRequest;
import com.nokia.nsw.uiv.response.CreateServiceIPTVResponse;
import com.nokia.nsw.uiv.response.CreateServiceVoIPResponse;
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
public class CreateServiceVoIP_Migration implements HttpAction {
    protected static final String ACTION_LABEL = Constants.CREATE_SERVICE_VOIP;
    private static final String ERROR_PREFIX = "UIV action CreateServiceVoIP execution failed - ";

    @Autowired private CustomerCustomRepository customerRepo;
    @Autowired private SubscriptionCustomRepository subscriptionRepo;
    @Autowired private ProductCustomRepository productRepo;
    @Autowired private LogicalDeviceCustomRepository logicalDeviceRepo;
    @Autowired private ServiceCustomRepository serviceCustomRepository;

    @Override
    public Class<?> getActionClass() {
        return CreateServiceVoIPBulkRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error("Executing CreateServiceVoIP action...");
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        Object obj = actionContext.getObject();

        if (!(obj instanceof CreateServiceVoIPBulkRequest)) {
            return new CreateServiceIPTVResponse(
                    "400",
                    "Invalid request type",
                    Instant.now().toString(),
                    null,
                    null
            );
        }

        CreateServiceVoIPBulkRequest bulk = (CreateServiceVoIPBulkRequest) obj;

        List<CreateServiceVoIPResponse> responses = new ArrayList<>();

        for (CreateServiceVoIPRequest req : bulk.getServices()) {

            try {
                // Step 1: Validate mandatory params
                try {
                    log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                    Validations.validateMandatoryParams(req.getSubscriberName(), "subscriberName");
                    Validations.validateMandatoryParams(req.getProductType(), "productType");
                    Validations.validateMandatoryParams(req.getProductSubtype(), "productSubtype");
                    Validations.validateMandatoryParams(req.getOntSN(), "ontSN");
                    Validations.validateMandatoryParams(req.getOntPort(), "ontPort");
                    Validations.validateMandatoryParams(req.getOltName(), "oltName");
                    Validations.validateMandatoryParams(req.getVoipServiceTemplate(), "voipServiceTemplate");
                    Validations.validateMandatoryParams(req.getSimaCustID(), "simaCustID");
                    Validations.validateMandatoryParams(req.getSimaSubsID(), "simaSubsID");
                    Validations.validateMandatoryParams(req.getSimaEndpointID(), "simaEndpointID");
                    Validations.validateMandatoryParams(req.getVoipNumber1(), "voipNumber1");
                    Validations.validateMandatoryParams(req.getTemplateNameOnt(), "templateNameOnt");
                    Validations.validateMandatoryParams(req.getTemplateNamePots1(), "templateNamePots1");
                    Validations.validateMandatoryParams(req.getTemplateNamePots2(), "templateNamePots2");
                    Validations.validateMandatoryParams(req.getHhid(), "hhid");
                    Validations.validateMandatoryParams(req.getOntModel(), "ontModel");
                    Validations.validateMandatoryParams(req.getServiceId(), "serviceId");
                    Validations.validateMandatoryParams(req.getVoipServiceCode(), "voipServiceCode");
                    log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
                } catch (BadRequestException bre) {
                    CreateServiceVoIPResponse response=new CreateServiceVoIPResponse(
                            "400",
                            ERROR_PREFIX + bre.getMessage(),
                            Instant.now().toString(),
                            null,
                            null
                    );
                    responses.add(response);
                    continue;
                }
                String ontName = "ONT" + req.getOntSN();
                if (ontName.length() > 100) {
                    CreateServiceVoIPResponse response=new CreateServiceVoIPResponse(
                            "400",
                            ERROR_PREFIX + "ONT name too long",
                            Instant.now().toString(),
                            null,
                            null
                    );
                    responses.add(response);
                    continue;
                }
                LogicalDevice cpeDevice = null;
                Optional<LogicalDevice> optCpeDevice = logicalDeviceRepo.findByDiscoveredName("ONT" + Constants.UNDER_SCORE + req.getOntSN());
                if (optCpeDevice.isPresent()) {
                    cpeDevice = optCpeDevice.get();
                } else {
                    throw new RuntimeException("Could not found CPE device: " + "ONT" + Constants.UNDER_SCORE + req.getOntSN());
                }
                AtomicBoolean isSubscriberExist = new AtomicBoolean(true);
                AtomicBoolean isSubscriptionExist = new AtomicBoolean(true);
                AtomicBoolean isProductExist = new AtomicBoolean(true);
                // Step 2 & 3: Subscriber
                String subscriberNameStr = req.getSubscriberName() + Constants.UNDER_SCORE + req.getOntSN();
                if (subscriberNameStr.length() > 100) {
                    CreateServiceVoIPResponse response=new CreateServiceVoIPResponse(
                            "400",
                            ERROR_PREFIX + "Subscriber name too long",
                            Instant.now().toString(),
                            null,
                            null
                    );
                    responses.add(response);
                    continue;
                }
                Customer subscriber = null;
                Optional<Customer> subscriberOpt = customerRepo.findByDiscoveredName(subscriberNameStr);
                if (subscriberOpt.isPresent()) {
                    subscriber = subscriberOpt.get();
                    log.error("Subscriber already exist with subscriberName: " + subscriberNameStr);
                } else {
                    Customer newSub = new Customer();
                    isSubscriberExist.set(false);
                    try {
                        newSub.setLocalName(Validations.encryptName(subscriberNameStr));
                        newSub.setDiscoveredName(subscriberNameStr);
                        newSub.setContext("Setar");
                        newSub.setKind("SetarSubscriber");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Map<String, Object> subProps = new HashMap<>();
                    subProps.put("subscriberStatus", "Active");
                    subProps.put("subscriberType", "Regular");
                    subProps.put("accountNumber", req.getSubscriberName());
                    subProps.put("houseHoldId", req.getHhid());
                    subProps.put("createdBy",
                            req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                    ? req.getCreatedBy()
                                    : "CA"
                    );
                    subProps.put("actionName", ACTION_LABEL);
                    newSub.setProperties(subProps);
                    subscriber = newSub;
                    customerRepo.save(newSub);
                }
                // Step 4: Subscription
                String subscriptionName = req.getSubscriberName() + Constants.UNDER_SCORE + req.getServiceId() + Constants.UNDER_SCORE + req.getOntSN();
                if (subscriptionName.length() > 100) {
                    CreateServiceVoIPResponse response=new CreateServiceVoIPResponse(
                            "400",
                            ERROR_PREFIX + "Subscription name too long",
                            Instant.now().toString(),
                            null,
                            null
                    );
                    responses.add(response);
                    continue;
                }
                Subscription subscription = null;
                Optional<Subscription> subscriptionOpt = subscriptionRepo.findByDiscoveredName(subscriptionName);
                if (subscriptionOpt.isPresent()) {
                    subscription = subscriptionOpt.get();
                    log.error("Subscription already exist with subscriptionName: " + subscriptionName);
                } else {
                    isSubscriptionExist.set(false);
                    Subscription subs = new Subscription();
                    try {
                        subs.setLocalName(Validations.encryptName(subscriptionName));
                        subs.setDiscoveredName(subscriptionName);
                        subs.setContext("Setar");
                        subs.setKind("SetarSubscription");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Map<String, Object> subProps = new HashMap<>();
                    subProps.put("subscriptionStatus", "Active");
                    subProps.put("serviceSubType", req.getProductSubtype());
                    subProps.put("serviceID", req.getServiceId());
                    subProps.put("oltPosition", req.getOltName());
                    subProps.put("householdId", req.getHhid());
                    subProps.put("createdBy",
                            req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                    ? req.getCreatedBy()
                                    : "CA"
                    );
                    subProps.put("actionName", ACTION_LABEL);
                    subs.setProperties(subProps);
                    subs.setCustomer(subscriber);
                    subscription = subs;
                    subscriptionRepo.save(subs);
                }
                // Step 5: Update attributes
                Map<String, Object> subProps = subscriber.getProperties();
                if (req.getFirstName() != null) subProps.put("subscriberFirstName", req.getFirstName());
                if (req.getLastName() != null) subProps.put("subscriberLastName", req.getLastName());
                if (req.getCompanyName() != null) subProps.put("companyName", req.getCompanyName());
                if (req.getContactPhone() != null) subProps.put("contactPhoneNumber", req.getContactPhone());
                if (req.getSubsAddress() != null) subProps.put("subscriberAddress", req.getSubsAddress());
                Object existingSimaCustId = subProps.get("simaCustId1");

                if ((existingSimaCustId == null || existingSimaCustId.toString().isEmpty())
                        && req.getSimaCustID() != null && !req.getSimaCustID().isEmpty()) {
                    subProps.put("simaCustId1", req.getSimaCustID());
                }
                subscriber.setProperties(subProps);

                Map<String, Object> subsProps = subscription.getProperties();
                if (req.getOntPort().equals("2")) {
                    subsProps.put("simaCustId2", req.getSimaCustID());
                    subsProps.put("voipNumber2", req.getVoipNumber1());
                    if (req.getSimaSubsID() != null && req.getSimaEndpointID() != null) {
                        subsProps.put("simaSubsId2", req.getSimaSubsID());
                        subsProps.put("simaEndpointId2", req.getSimaEndpointID());
                    }

                    if (req.getVoipPackage() != null && req.getVoipServiceCode() != null) {
                        subsProps.put("voipPackage2", req.getVoipPackage());
                        subsProps.put("voipServiceCode2", req.getVoipServiceCode());
                    }

                } else {
                    subsProps.put("voipNumber1", req.getVoipNumber1());
                    subsProps.put("simaCustId1", req.getSimaCustID());
                    subsProps.put("simaSubsId1", req.getSimaSubsID());
                    subsProps.put("simaEndpointId1", req.getSimaEndpointID());
                    if (req.getVoipPackage() != null && !req.getVoipPackage().isEmpty()) {
                        subsProps.put("voipPackage1", req.getVoipPackage());
                    }
                    subsProps.put("voipServiceCode1", req.getVoipServiceCode());
                }

                subsProps.put("serviceLink", (req.getOntSN() != null && req.getOntSN().startsWith("ALCL")) ? "ONT" : "Cable_Modem");
                if (!isSubscriberExist.get() && !isSubscriptionExist.get()) {
                    subscription.setProperties(subsProps);
                    subscriber.setProperties(subProps);
                    customerRepo.save(subscriber);
                    subscriptionRepo.save(subscription);
                }
                // Step 7: Product
                String productNameStr = req.getSubscriberName() + Constants.UNDER_SCORE + req.getProductSubtype() + Constants.UNDER_SCORE + req.getServiceId();
                if (productNameStr.length() > 100) {
                    CreateServiceVoIPResponse response=new CreateServiceVoIPResponse(
                            "400",
                            ERROR_PREFIX + "Product name too long",
                            Instant.now().toString(),
                            null,
                            null
                    );
                    responses.add(response);
                    continue;
                }
                Product product = null;
                Optional<Product> productOpt = productRepo.findByDiscoveredName(productNameStr);
                if (productOpt.isPresent()) {
                    product = productOpt.get();
                    log.error("Product already exist with productName: " + productNameStr);
                } else {
                    Product prod = new Product();
                    isProductExist.set(false);
                    try {
                        prod.setLocalName(Validations.encryptName(productNameStr));
                        prod.setDiscoveredName(productNameStr);
                        prod.setContext("Setar");
                        prod.setKind("SetarProduct");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Map<String, Object> prodProps = new HashMap<>();
                    prodProps.put("productStatus", "Active");
                    prodProps.put("productType", req.getProductType());
                    prodProps.put("createdBy",
                            req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                    ? req.getCreatedBy()
                                    : "CA"
                    );
                    prodProps.put("actionName", ACTION_LABEL);
                    prod.setProperties(prodProps);
                    prod.setCustomer(subscriber);
                    product = prod;
                    productRepo.save(prod);
                }
                if (isSubscriberExist.get() && isSubscriptionExist.get() && isProductExist.get()) {
                    log.error("createServiceVOIP service already exist");
                    CreateServiceVoIPResponse response=new CreateServiceVoIPResponse("409", "Service already exist/Duplicate entry", Instant.now().toString(), subscriptionName, "ONT" + req.getOntSN());
                    responses.add(response);
                    continue;
                }
                if (isSubscriptionExist.get()) {
                    subscription = subscriptionRepo.findByDiscoveredName(subscription.getDiscoveredName()).get();
                    Set<Service> existingServices = subscription.getService();
                    existingServices.add(product);
                    subscription.setService(existingServices);
                } else {
                    subscription.setService(new HashSet<>(List.of(product)));
                }
                subscriptionRepo.save(subscription, 2);
                // Step 8: CFS
                String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
                Service cfs = null;
                Optional<Service> cfsOpt = serviceCustomRepository.findByDiscoveredName(cfsName);
                if (cfsOpt.isPresent()) {
                    cfs = cfsOpt.get();
                    log.error("CFS is already exist with cfsName: " + cfsName);
                } else {
                    Service newCfs = new Service();
                    try {
                        newCfs.setLocalName(Validations.encryptName(cfsName));
                        newCfs.setDiscoveredName(cfsName);
                        newCfs.setContext("Setar");
                        newCfs.setKind("SetarCFS");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Map<String, Object> cfsProps = new HashMap<>();
                    cfsProps.put("serviceStatus", "Active");
                    cfsProps.put("serviceType", req.getProductType());
                    cfsProps.put("serviceStartDate", Instant.now().toString());
                    cfsProps.put("createdBy",
                            req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                    ? req.getCreatedBy()
                                    : "CA"
                    );
                    cfsProps.put("actionName", ACTION_LABEL);
                    if (req.getFxOrderID() != null) cfsProps.put("transactionId", req.getFxOrderID());
                    newCfs.setProperties(cfsProps);
                    newCfs.setUsingService(new HashSet<>(List.of(product)));
                    cfs = newCfs;
                    serviceCustomRepository.save(newCfs);
                }
                // Step 9: RFS
                String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
                Service rfs = null;
                Optional<Service> rfsOpt = serviceCustomRepository.findByDiscoveredName(rfsName);
                if (rfsOpt.isPresent()) {
                    rfs = rfsOpt.get();
                    log.error("RFS is already exist with name RFSName: " + rfsName);
                } else {
                    Service newRfs = new Service();
                    try {
                        newRfs.setLocalName(Validations.encryptName(rfsName));
                        newRfs.setDiscoveredName(rfsName);
                        newRfs.setContext("Setar");
                        newRfs.setKind("SetarRFS");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Map<String, Object> rfsProps = new HashMap<>();
                    rfsProps.put("serviceStatus", "Active");
                    rfsProps.put("serviceType", req.getProductType());
                    rfsProps.put("createdBy",
                            req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                    ? req.getCreatedBy()
                                    : "CA"
                    );
                    rfsProps.put("actionName", ACTION_LABEL);
                    newRfs.setProperties(rfsProps);
                    newRfs.setUsedService(new HashSet<>(List.of(cfs)));
                    rfs = newRfs;
                    serviceCustomRepository.save(newRfs);
                }

                String oltName = req.getOltName();
                LogicalDevice olt = null;
                Optional<LogicalDevice> oltOpt = logicalDeviceRepo.findByDiscoveredName(oltName);
                if (oltOpt.isPresent()) {
                    olt = oltOpt.get();
                    log.error("OLTDevice is already present with oltName: " + oltName);
                } else {
                    olt = new LogicalDevice();
                    try {
                        olt.setLocalName(Validations.encryptName(req.getOltName()));
                        olt.setDiscoveredName(req.getOltName());
                        olt.setContext("Setar");
                        olt.setKind("OLTDevice");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Map<String, Object> oltProps = new HashMap<>();
                    oltProps.put("OperationalState", "Active");
                    oltProps.put("oltPosition", req.getOltName());
                    oltProps.put("ontTemplate", req.getTemplateNameOnt());
                    oltProps.put("createdBy",
                            req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                    ? req.getCreatedBy()
                                    : "CA"
                    );
                    oltProps.put("actionName", ACTION_LABEL);
                    olt.setProperties(oltProps);
                    logicalDeviceRepo.save(olt);
                }
                LogicalDevice ont = null;
                Optional<LogicalDevice> ontOpt = logicalDeviceRepo.findByDiscoveredName(ontName);
                if (ontOpt.isPresent()) {
                    ont = ontOpt.get();
                    log.error("ONTDevice is already exist with ontName: " + ontName);
                } else {
                    ont = new LogicalDevice();
                    try {
                        ont.setLocalName(Validations.encryptName(ontName));
                        ont.setDiscoveredName(ontName);
                        ont.setContext("Setar");
                        ont.setKind("ONTDevice");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Map<String, Object> ontProps = new HashMap<>();
                    ontProps.put("OperationalState", "Active");
                    ontProps.put("serialNo", req.getOntSN());
                    ontProps.put("deviceModel", req.getOntModel());
                    ontProps.put("oltPosition", req.getOltName());
                    ontProps.put("ontTemplate", req.getTemplateNameOnt());
                    ontProps.put("createdBy",
                            req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                    ? req.getCreatedBy()
                                    : "CA"
                    );
                    ontProps.put("actionName", ACTION_LABEL);
                    ont.setProperties(ontProps);
                    logicalDeviceRepo.save(ont);
                }

                // Step 12: Configure VoIP ports

                olt = logicalDeviceRepo
                        .findByDiscoveredName(olt.getDiscoveredName()).get();

                ont = logicalDeviceRepo
                        .findByDiscoveredName(ont.getDiscoveredName()).get();
                Map<String, Object> ontProp = new HashMap<>();
                Map<String, Object> existing = ont.getProperties();
                if (existing != null) {
                    ontProp.putAll(existing);
                }
                if ("1".equals(req.getOntPort())) {
                    ontProp.put("potsPort1Number", req.getVoipNumber1());
                    olt.getProperties().put("voipPots1Template", req.getTemplateNamePots1());
                    cpeDevice.getProperties().put("voipPort1", req.getVoipNumber1());
                } else {
                    ontProp.put("potsPort2Number", req.getVoipNumber1());
                    olt.getProperties().put("voipPots2Template", req.getTemplateNamePots2());
                    cpeDevice.getProperties().put("voipPort2", req.getVoipNumber1());
                }
                olt.getProperties().put("ontTemplate", req.getTemplateNameOnt());
                olt.getProperties().put("voipServiceTemplate", req.getVoipServiceTemplate());

                ont.setProperties(ontProp);
                ont.setUsingService(new HashSet<>(List.of(rfs)));
                ont.setUsedResource(new HashSet<>(List.of(olt)));
                olt.setUsingService(new HashSet<>(List.of(rfs)));

                logicalDeviceRepo.save(olt, 3);
                logicalDeviceRepo.save(ont, 3);
                logicalDeviceRepo.save(cpeDevice);

                log.error(Constants.ACTION_COMPLETED);
                CreateServiceVoIPResponse response=new CreateServiceVoIPResponse(
                        "201",
                        "UIV action CreateServiceVoIP executed successfully.",
                        Instant.now().toString(),
                        subscriptionName,
                        ontName
                );
                responses.add(response);
                continue;

            } catch (Exception ex) {
                log.error("Exception in CreateServiceVoIP", ex);
                CreateServiceVoIPResponse response=new CreateServiceVoIPResponse(
                        "500",
                        ERROR_PREFIX + "Error occurred while creating service VOIP - " + ex.getMessage(),
                        Instant.now().toString(),
                        null,
                        null
                );
                responses.add(response);
                continue;
            }
        }
        return responses;
    }
}
