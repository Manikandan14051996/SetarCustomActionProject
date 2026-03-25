package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.ModificationNotAllowedException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterface;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterfaceRepository;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.CreateServiceEVPNRequest;
import com.nokia.nsw.uiv.response.CreateServiceCBMResponse;
import com.nokia.nsw.uiv.response.CreateServiceCbmVoiceResponse;
import com.nokia.nsw.uiv.response.CreateServiceEVPNResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;

import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;

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
public class CreateServiceEVPN implements HttpAction {

    private static final String ERROR_PREFIX = "UIV action CreateServiceEVPN execution failed - ";
    protected static final String ACTION_LABEL = Constants.CREATE_SERVICE_EVPN;

    @Autowired
    private CustomerCustomRepository customerRepo;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepo;

    @Autowired
    private ProductCustomRepository productRepo;

    @Autowired
    private LogicalDeviceCustomRepository logicalDeviceRepo;

    @Autowired
    private LogicalInterfaceCustomRepository vlanRepo;

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;


    @Override
    public Class<?> getActionClass() {
        return CreateServiceEVPNRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("------------Trace # 1--------------- CreateServiceEVPN started");
        CreateServiceEVPNRequest req = (CreateServiceEVPNRequest) actionContext.getObject();

        try {
            // 1) Validate mandatory parameters (runtime)
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatoryParams(req.getSubscriberName(), "subscriberName");
                Validations.validateMandatoryParams(req.getProductType(), "productType");
                Validations.validateMandatoryParams(req.getProductSubtype(), "productSubtype");
                Validations.validateMandatoryParams(req.getOntSN(), "ontSN");
                Validations.validateMandatoryParams(req.getOltName(), "oltName");
                if (req.getProductSubtype() != null && !req.getProductSubtype().equalsIgnoreCase("SRX")) {
                    Validations.validateMandatoryParams(req.getMgmntVlanId(), "mgmntVlanId");
                    Validations.validateMandatoryParams(req.getMenm(), "menm");
                }
                Validations.validateMandatoryParams(req.getServiceId(), "serviceId");
                Validations.validateMandatoryParams(req.getHhid(), "hhid");
                Validations.validateMandatoryParams(req.getOntModel(), "ontModel");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (Exception bre) {
                log.error("------------Trace # 2--------------- Missing mandatory param: " + bre.getMessage());
                return new CreateServiceEVPNResponse(
                        "400",
                        ERROR_PREFIX + bre.getMessage(),
                        Instant.now().toString(),
                        null,
                        null
                );
            }
            AtomicBoolean isSubscriberExist = new AtomicBoolean(true);
            AtomicBoolean isSubscriptionExist = new AtomicBoolean(true);
            AtomicBoolean isProductExist = new AtomicBoolean(true);
            // 2) Prepare names

            String ontName = "ONT" + req.getOntSN();
            if (ontName.length() > 100) {
                log.error("------------Trace # 6--------------- ONT name too long");
                return new CreateServiceEVPNResponse(
                        "400",
                        ERROR_PREFIX + "ONT name too long",
                        Instant.now().toString(),
                        null,
                        null
                );
            }
            String subscriberNameStr = req.getSubscriberName() + Constants.UNDER_SCORE + req.getOntSN();
            if (subscriberNameStr.length() > 100) {
                log.error("------------Trace # 3--------------- Subscriber name too long");
                return new CreateServiceEVPNResponse(
                        "400",
                        ERROR_PREFIX + "Subscriber name too long",
                        Instant.now().toString(),
                        null,
                        null
                );
            }

            String subscriptionName = req.getSubscriberName() + Constants.UNDER_SCORE + req.getServiceId() + Constants.UNDER_SCORE + req.getOntSN();
            if (subscriptionName.length() > 100) {
                log.error("------------Trace # 4-------V-------- Subscription name too long");
                return new CreateServiceEVPNResponse(
                        "400",
                        ERROR_PREFIX + "Subscription name too long",
                        Instant.now().toString(),
                        null,
                        null
                );
            }

            String productNameStr = req.getSubscriberName() + Constants.UNDER_SCORE + req.getProductSubtype() + Constants.UNDER_SCORE + req.getServiceId();
            if (productNameStr.length() > 100) {
                log.error("------------Trace # 5--------------- Product name too long");
                return new CreateServiceEVPNResponse(
                        "400",
                        ERROR_PREFIX + "Product name too long",
                        Instant.now().toString(),
                        null,
                        null
                );
            }

            String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
            String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;

            // 3) Subscriber: find or create (properties map)
            Customer subscriber = customerRepo.findByDiscoveredName(subscriberNameStr)
                    .orElseGet(() -> {
                        isSubscriberExist.set(false);
                        log.error("------------Trace # 8--------------- Creating subscriber: " + subscriberNameStr);
                        Customer newSub = new Customer();
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
                        if (req.getFirstName() != null) subProps.put("subscriberFirstName", req.getFirstName());
                        if (req.getLastName() != null) subProps.put("subscriberLastName", req.getLastName());
                        if (req.getCompanyName() != null) subProps.put("companyName", req.getCompanyName());
                        if (req.getContactPhone() != null)
                            subProps.put("contactPhoneNumber", req.getContactPhone());
                        subProps.put("createdBy",
                                req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                        ? req.getCreatedBy()
                                        : "CA"
                        );
                        subProps.put("actionName", ACTION_LABEL);
                        if (req.getSubsAddress() != null) subProps.put("subscriberAddress", req.getSubsAddress());
                        newSub.setProperties(subProps);
                        return customerRepo.save(newSub);
                    });
            // 4) Subscription: find or create (properties map)
            Optional<Subscription> subscriptionOpt = subscriptionRepo.findByDiscoveredName(subscriptionName);
            Subscription subscription = null;
            if (subscriptionOpt.isPresent()) {
                subscription = subscriptionOpt.get();
            } else {
                isSubscriptionExist.set(false);
                log.error("------------Trace # 9--------------- Creating subscription: " + subscriptionName);
                Subscription subs = new Subscription();
                try {
                    subs.setLocalName(Validations.encryptName(subscriptionName));
                    subs.setDiscoveredName(subscriptionName);
                    subs.setContext("Setar");
                    subs.setKind("SetarSubscription");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                Map<String, Object> subsProps = new HashMap<>();
                subsProps.put("subscriptionStatus", "Active");
                subsProps.put("serviceSubType", req.getProductSubtype());
                if (req.getQosProfile() != null) subsProps.put("evpnQosSessionProfile", req.getQosProfile());
                if (req.getOntPort() != null) subsProps.put("evpnPort", req.getOntPort());
                if (req.getTemplateNameVlanCreate() != null)
                    subsProps.put("evpnTemplateCreateVLAN", req.getTemplateNameVlanCreate());
                if (req.getTemplateNameVlan() != null) subsProps.put("evpnTemplateVLAN", req.getTemplateNameVlan());
                if (req.getTemplateNameVpls() != null) subsProps.put("evpnTemplateVPLS", req.getTemplateNameVpls());
                if (req.getVlanId() != null) subsProps.put("evpnVLAN", req.getVlanId());
                subsProps.put("serviceID", req.getServiceId());
                subsProps.put("householdId", req.getHhid());
                subsProps.put("createdBy",
                        req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                ? req.getCreatedBy()
                                : "CA"
                );
                subsProps.put("actionName", ACTION_LABEL);
                subsProps.put("serviceLink", ("IPBH".equalsIgnoreCase(req.getProductSubtype()) ? "SRX" : "ONT"));
                // compute OLT position string per spec
                String oltPos;
                String p = req.getOntPort();
                if ("5".equals(p)) {
                    oltPos = req.getOltName() + "-10-5";
                } else {
                    // default fallback
                    String portVal = (p == null ? "1" : p);
                    oltPos = req.getOltName() + "-1-" + portVal;
                }
                subsProps.put("oltPosition", oltPos);
                subsProps.put("kenanSubscriberId", req.getKenanUidNo());
                subsProps.put("subscriberID_CableModem", req.getSubscriberId());
                subsProps.put("linkedSubscriber", subscriber.getDiscoveredName());
                subsProps.put("serviceSN", req.getOntSN());
                if (req.getProductSubtype() != null && (req.getProductSubtype().contains("Bridged") || req.getProductSubtype().contains("Cloudstarter"))) {
                    subsProps.put("subscriptionDetails", req.getServiceId());
                } else {
                    subsProps.put("subscriptionDetails", "FTTB-" + req.getServiceId());
                }
                subs.setProperties(subsProps);
                subs.setCustomer(subscriber);
                // association to subscriber (store link name so external process can link)

                subscription = subs;
                subscriptionRepo.save(subs, 2);
            }
            // ensure we keep subscriber-subscription link when we didn't create subs
            if (!subscription.getProperties().containsKey("linkedSubscriber")) {
                subscription.getProperties().put("linkedSubscriber", subscriber.getDiscoveredName());
                subscriptionRepo.save(subscription);
            }

            // 5) Product: find or create (properties map)
            Optional<Product> productOpt = productRepo.findByDiscoveredName(productNameStr);
            Product product = null;
            if (productOpt.isPresent()) {
                product = productOpt.get();
            } else {
                isProductExist.set(false);
                log.error("------------Trace # 10--------------- Creating product: " + productNameStr);
                Product prod = new Product();
                try {
                    prod.setLocalName(Validations.encryptName(productNameStr));
                    prod.setDiscoveredName(productNameStr);
                    prod.setContext("Setar");
                    prod.setKind("SetarProduct");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                Map<String, Object> prodProps = new HashMap<>();
                prodProps.put("productType", req.getProductType());
                prodProps.put("productStatus", "Active");
                prodProps.put("linkedSubscriber", subscriber.getDiscoveredName());
                prodProps.put("linkedSubscription", subscription.getDiscoveredName());
                prodProps.put("createdBy",
                        req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                ? req.getCreatedBy()
                                : "CA"
                );
                prodProps.put("actionName", ACTION_LABEL);
                prod.setProperties(prodProps);
                prod.setCustomer(subscriber);
                product = prod;
                productRepo.save(prod, 2);
            }
            if (isSubscriberExist.get() && isSubscriptionExist.get() && isProductExist.get()) {
                log.error("createServiceEVPN service already exist");
                return new CreateServiceEVPNResponse("409", "Service already exist/Duplicate entry", Instant.now().toString(), subscriberNameStr, ontName);
            }
            if (isSubscriptionExist.get()) {
                subscription = subscriptionRepo.findByDiscoveredName(subscription.getDiscoveredName()).get();
                Set<Service> existingServices = subscription.getService();
                existingServices.add(product);
                subscription.setService(existingServices);
            } else {
                subscription.setService(new HashSet<>(List.of(product)));
            }
            subscriptionRepo.save(subscription);

            // 6) CFS: find or create (properties map)
            Optional<Service> cfsOpt = serviceCustomRepository.findByDiscoveredName(cfsName);
            Service cfs = null;
            if (cfsOpt.isPresent()) {
                cfs = cfsOpt.get();
            } else {
                log.error("------------Trace # 11--------------- Creating CFS: " + cfsName);
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
                cfsProps.put("linkedProduct", product.getDiscoveredName());
                newCfs.setProperties(cfsProps);
                newCfs.addUsingService(product);
                cfs = newCfs;
                serviceCustomRepository.save(newCfs, 2);
            }

            // 7) RFS: find or create (properties map)
            Optional<Service> rfsOpt = serviceCustomRepository.findByDiscoveredName(rfsName);
            Service rfs = null;
            if (rfsOpt.isPresent()) {
                rfs = rfsOpt.get();
            } else {
                log.error("------------Trace # 12--------------- Creating RFS: " + rfsName);
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
                rfsProps.put("linkedCFS", cfs.getDiscoveredName());
                rfsProps.put("createdBy",
                        req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                ? req.getCreatedBy()
                                : "CA"
                );
                rfsProps.put("actionName", ACTION_LABEL);
                newRfs.addUsedService(cfs);
                rfs = newRfs;
                newRfs.setProperties(rfsProps);
                serviceCustomRepository.save(newRfs);
            }

            // 8) OLT: find or create
            Optional<LogicalDevice> oltOpt = logicalDeviceRepo.findByDiscoveredName(req.getOltName());
            LogicalDevice olt = null;
            if (oltOpt.isPresent()) {
                olt = oltOpt.get();
            } else {
                log.error("------------Trace # 13--------------- Creating OLT: " + req.getOltName());
                LogicalDevice dev = new LogicalDevice();
                try {
                    dev.setLocalName(Validations.encryptName(req.getOltName()));
                    dev.setDiscoveredName(req.getOltName());
                    dev.setContext("Setar");
                    dev.setKind("OLTDevice");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                Map<String, Object> oltProps = new HashMap<>();
                oltProps.put("OperationalState", "Active");
                oltProps.put("oltPosition", req.getOltName());
                oltProps.put("createdBy",
                        req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                ? req.getCreatedBy()
                                : "CA"
                );
                oltProps.put("actionName", ACTION_LABEL);
                if (req.getTemplateNameOnt() != null) oltProps.put("ontTemplate", req.getTemplateNameOnt());
                dev.setProperties(oltProps);
                // link RFS reference if exists
                dev.getProperties().put("linkedRFS", rfs.getDiscoveredName());
                olt = dev;
                logicalDeviceRepo.save(dev, 2);
            }

            // 9) ONT: find or create, manage EVPN counters
            LogicalDevice ont = null;
            Optional<LogicalDevice> ontOpt = logicalDeviceRepo.findByDiscoveredName(ontName);
            if (ontOpt.isPresent()) {
                ont = ontOpt.get();
            } else {
                log.error("------------Trace # 14--------------- Creating ONT: " + ontName);
                LogicalDevice dev = new LogicalDevice();
                try {
                    dev.setLocalName(ontName);
                    dev.setDiscoveredName(ontName);
                    dev.setContext("Setar");
                    dev.setKind("ONTDevice");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                Map<String, Object> ontProps = new HashMap<>();
                ontProps.put("serialNo", req.getOntSN());
                ontProps.put("deviceModel", req.getOntModel());
                ontProps.put("OperationalState", "Active");
                if (req.getTemplateNameOnt() != null) ontProps.put("ontTemplate", req.getTemplateNameOnt());
                ontProps.put("oltPosition", req.getOltName());
                // initialize counters
                ontProps.put("evpnEthPort2Template", "0");
                ontProps.put("evpnEthPort3Template", "0");
                ontProps.put("evpnEthPort4Template", "0");
                ontProps.put("evpnEthPort5Template", "0");
                ontProps.put("createdBy",
                        req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                ? req.getCreatedBy()
                                : "CA"
                );
                ontProps.put("actionName", ACTION_LABEL);
                ontProps.put("oltPosition", req.getOltName());
                // management fields
                if (req.getTemplateNameVlanMgmnt() != null)
                    ontProps.put("mgmtTemplate", req.getTemplateNameVlanMgmnt());
                if (req.getMgmntVlanId() != null) ontProps.put("mgmtVlan", req.getMgmntVlanId());
                // link RFS
                ontProps.put("linkedRFS", rfs.getDiscoveredName());
                dev.setProperties(ontProps);
                dev.getProperties().put("containingDevice", olt.getDiscoveredName());
                logicalDeviceRepo.save(dev);
                ont = dev;
            }

            // If ONT existed, ensure it is linked and counters initialized properly
            if (!ont.getProperties().containsKey("evpnEthPort3Template")) {
                ont.getProperties().put("evpnEthPort3Template", "0");
            }
            if (!ont.getProperties().containsKey("evpnEthPort4Template")) {
                ont.getProperties().put("evpnEthPort4Template", "0");
            }
            if (!ont.getProperties().containsKey("evpnEthPort5Template")) {
                ont.getProperties().put("evpnEthPort5Template", "0");
            }
            if (req.getMenm() != null && !req.getMenm().isEmpty() && req.getMgmntVlanId() != null && !req.getMgmntVlanId().isEmpty()) {
                String mgmtVlanName = req.getMenm() + Constants.UNDER_SCORE + req.getMgmntVlanId();

                log.error("------------Trace # 7--------------- Names prepared: subscriber=" + subscriberNameStr
                        + ", subscription=" + subscriptionName + ", product=" + productNameStr
                        + ", ont=" + ontName + ", mgmtVlan=" + mgmtVlanName);
                // 10) Management VLAN (for non-IPBH)
                if (!"IPBH".equalsIgnoreCase(req.getProductSubtype())) {
                    LogicalInterface mgmtVlan = null;
                    Optional<LogicalInterface> mgmtVlanOpt = vlanRepo.findByDiscoveredName(mgmtVlanName);
                    if (mgmtVlanOpt.isPresent()) {
                        mgmtVlan = mgmtVlanOpt.get();
                    } else {
                        log.error("------------Trace # 15--------------- Creating mgmt VLAN: " + mgmtVlanName);
                        LogicalInterface v = new LogicalInterface();
                        try {
                            v.setLocalName(Validations.encryptName(mgmtVlanName));
                            v.setDiscoveredName(mgmtVlanName);
                            v.setContext(Constants.SETAR);
                            v.setKind(Constants.SETAR_KIND_VLAN_INTERFACE);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        Map<String, Object> vProps = new HashMap<>();
                        if (req.getMgmntVlanId() != null) vProps.put("vlanId", req.getMgmntVlanId());
                        if (req.getTemplateNameVlanMgmnt() != null)
                            vProps.put("mgmtTemplate", req.getTemplateNameVlanMgmnt());
                        vProps.put("OperationalState", "Active");
                        vProps.put("createdBy",
                                req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                        ? req.getCreatedBy()
                                        : "CA"
                        );
                        vProps.put("actionName", ACTION_LABEL);
                        v.setProperties(vProps);
                        vlanRepo.save(v);
                    }
                    // no direct association required here beyond existence
                }
            }

            // 11) Service VLAN (per subtype rules)
            boolean usedStandardEvpn;
            if (req.getProductSubtype() != null) {
                String sub = req.getProductSubtype();
                // condition to treat as "generic branch" per spec
                if (sub.contains("BAAS") || sub.contains("PBX") || sub.contains("SIP")
                        || sub.contains("Cloudstarter") || sub.contains("Bridged")) {
                    usedStandardEvpn = false;
                } else {
                    usedStandardEvpn = true;
                }
            } else {
                usedStandardEvpn = true;
            }
            LogicalInterface serviceVlan = null;
            if (req.getMenm() != null && !req.getMenm().isEmpty() && req.getMgmntVlanId() != null && !req.getMgmntVlanId().isEmpty()) {
                String vlanName = req.getMenm() + Constants.UNDER_SCORE + (req.getVlanId() == null ? "" : req.getVlanId());
                log.error("------------Trace # 7--------------- Names prepared: subscriber=" + subscriberNameStr
                        + ", subscription=" + subscriptionName + ", product=" + productNameStr
                        + ", ont=" + ontName + ", vlan=" + vlanName);
                if (req.getVlanId() != null) {
                    Optional<LogicalInterface> serviceVlanOpt = vlanRepo.findByDiscoveredName(vlanName);
                    if (serviceVlanOpt.isPresent()) {
                        serviceVlan = serviceVlanOpt.get();
                    } else {
                        log.error("------------Trace # 16--------------- Creating service VLAN: " + vlanName);
                        LogicalInterface v = new LogicalInterface();
                        try {
                            v.setLocalName(Validations.encryptName(vlanName));
                            v.setDiscoveredName(vlanName);
                            v.setContext("Setar");
                            v.setKind("VLANInterface");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        Map<String, Object> vProps = new HashMap<>();
                        putIfNotNull(vProps, "vlanId", req.getVlanId());
                        vProps.put("OperationalState", "Active");
                        if (usedStandardEvpn) {
                            putIfNotNull(vProps, "mgmtTemplate", req.getTemplateNameVlanMgmnt());
                            putIfNotNull(vProps, "configuredOntSN", req.getOntSN());
                            putIfNotNull(vProps, "configuredPort", req.getOntPort());
                            putIfNotNull(vProps, "vlanTemplate", req.getTemplateNameVlan());
                            putIfNotNull(vProps, "serviceId", req.getServiceId());
                            putIfNotNull(vProps, "vlanCreateTemplate", req.getTemplateNameVlanCreate());
                            putIfNotNull(vProps, "configuredVplsTemplate", req.getTemplateNameVpls());
                            vProps.put("createdBy",
                                    req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                            ? req.getCreatedBy()
                                            : "CA"
                            );
                            vProps.put("actionName", ACTION_LABEL);
                            // associate with ONT
                            vProps.put("linkedOnt", ont.getDiscoveredName());
                        } else {
                            // generic (no ONT association)
                            if (req.getTemplateNameVlan() != null) {
                                vProps.put("vlanTemplate", req.getTemplateNameVlan());
                            }
                        }
                        v.setProperties(vProps);
                        vlanRepo.save(v, 2);
                        if (usedStandardEvpn) {
                            ont = logicalDeviceRepo.findByDiscoveredName(ont.getDiscoveredName()).get();
                            ont.setContained(new HashSet<>(List.of(v)));
                            logicalDeviceRepo.save(ont);
                        }
                        serviceVlan = v;
                    }
                    // if created and usedStandardEvpn -> ensure association entry on ONT exists
                    if (usedStandardEvpn) {
                        ont.getProperties().put("lastServiceVlan", serviceVlan.getDiscoveredName());
                    }
                }
            }

            // 12) EVPN port & counters update
            String selectedPort = req.getOntPort() == null ? "1" : req.getOntPort();
            // determine current counter
            int currentCounter = 0;
            try {
                if ("4".equals(selectedPort)) {
                    currentCounter = Integer.parseInt((String) ont.getProperties().getOrDefault("evpnEthPort4Template", "0"));
                } else if ("5".equals(selectedPort)) {
                    currentCounter = Integer.parseInt((String) ont.getProperties().getOrDefault("evpnEthPort5Template", "0"));
                } else {
                    // ports 1..3 default to port3Counter usage
                    currentCounter = Integer.parseInt((String) ont.getProperties().getOrDefault("evpnEthPort3Template", "0"));
                }
            } catch (Exception e) {
                currentCounter = 0;
            }
            int newCounter = currentCounter + 1;
            String servCounter = String.valueOf(newCounter);

            // apply per-port updates to OLT and ONT properties
            Map<String, Object> oltProps = olt.getProperties();
            Map<String, Object> ontProps = ont.getProperties();

            if ("4".equals(selectedPort)) {
                putIfNotNull(oltProps, "evpnEthPort4Template", req.getTemplateNamePort());
                ontProps.put("evpnEthPort4Template", servCounter);
            } else if ("5".equals(selectedPort)) {
                putIfNotNull(oltProps, "evpnEthPort5Template", req.getTemplateNamePort());
                ontProps.put("evpnEthPort5Template", servCounter);
            } else if ("3".equals(selectedPort)) {
                putIfNotNull(oltProps, "evpnEthPort3Template", req.getTemplateNamePort());
                ontProps.put("evpnEthPort3Template", servCounter);
            } else if ("2".equals(selectedPort)) {
                // spec asked: port2 uses original pre-increment value
                ontProps.put("evpnEthPort2Template", String.valueOf(currentCounter));
                putIfNotNull(oltProps, "evpnEthPort2Template", req.getTemplateNamePort());
            } else { // port 1
                // port 1: set ONT port-1 EVPN VLAN template
                putIfNotNull(ontProps, "evpnEthPort1Template", req.getTemplateNameVlan());
                // no olt update required
            }

            // 13) ONT description / mgmt template population
            if (req.getMenm() != null && !req.getMenm().isEmpty()) {
                ontProps.put("description", req.getMenm());
            }

            // ONT create template logic
            boolean isStandardEvpn = usedStandardEvpn;
            if (isStandardEvpn) {
                if (!ontProps.containsKey("createTemplate") || "NA".equals(ontProps.get("createTemplate"))) {
                    if (req.getTemplateNameCreate() != null && !"NA".equals(req.getTemplateNameCreate())) {
                        ontProps.put("createTemplate", req.getTemplateNameCreate());
                    }
                }
            }

            // Management template update
            if (req.getTemplateNameVlanMgmnt() != null && !"NA".equals(req.getTemplateNameVlanMgmnt())) {
                log.error("------------Trace # 17--------------- enter for tempMGMNTVlan " + ontProps.get("mgmtTemplate"));
                ontProps.put("mgmtTemplate", req.getTemplateNameVlanMgmnt());
                log.error("------------Trace # 18--------------- enter for tempMGMNTVlan 1 " + ontProps.get("mgmtTemplate"));
            }
            // Management VLAN set if blank
            String currentMgmtVlan = (String) ontProps.getOrDefault("mgmtVlan", "");
            if ((currentMgmtVlan == null || currentMgmtVlan.isEmpty() || "NA".equals(currentMgmtVlan))
                    && req.getMgmntVlanId() != null && !"NA".equals(req.getMgmntVlanId())) {
                ontProps.put("mgmtVlan", req.getMgmntVlanId());
            }

            // 14) OLT card template decision per port (if port 5 -> card-5)
            if ("5".equals(selectedPort)) {
                String currentCard5 = (String) oltProps.getOrDefault("evpnOntCard5Template", "");
                if ((currentCard5 == null || currentCard5.isEmpty()) && req.getTemplateNameCard() != null) {
                    oltProps.put("evpnOntCard5Template", req.getTemplateNameCard());
                }
            } else {
                String currentCard = (String) oltProps.getOrDefault("evpnOntCardTemplate", "");
                if ((currentCard == null || currentCard.isEmpty()) && req.getTemplateNameCard() != null) {
                    oltProps.put("evpnOntCardTemplate", req.getTemplateNameCard());
                }
            }

            // persist updates to ONT and OLT
            ont.setProperties(ontProps);
            logicalDeviceRepo.save(ont);
            olt = logicalDeviceRepo.findByDiscoveredName(olt.getDiscoveredName()).get();
            olt.setProperties(oltProps);
            logicalDeviceRepo.save(olt);

            // 15) Single-tagged VLAN interface creation logic (spec) - simplified: create one matching
            if (((req.getProductType() != null && req.getProductType().contains("EVPN")) || req.getProductType().contains("ENTERPRISE"))
                    || (req.getProductSubtype() != null && req.getProductSubtype().contains("Cloudstarter"))) {
                // pick index 2..8 -> naive approach: choose 2
                for (int idx = 2; idx <= 8; idx++) {
                    String freeTemp = String.valueOf(idx);
                    if (req.getTemplateNameVlan() != null) {
                        if (req.getTemplateNameVlan().endsWith(freeTemp)) {
                            String singleName = req.getOntSN() + "_P" + selectedPort + "_SINGLETAGGED_" + idx;
                            if (!vlanRepo.findByDiscoveredName(singleName).isPresent()) {
                                LogicalInterface singleVlan = new LogicalInterface();
                                singleVlan.setLocalName(singleName);
                                singleVlan.setDiscoveredName(singleName);
                                singleVlan.setContext("Setar");
                                singleVlan.setKind("VLANInterface");
                                Map<String, Object> svProps = new HashMap<>();
                                putIfNotNull(svProps, "vlanId", req.getVlanId());
                                putIfNotNull(svProps, "mgmtTemplate", req.getTemplateNameVlanMgmnt());
                                putIfNotNull(svProps, "configuredOntSN", req.getOntSN());
                                putIfNotNull(svProps, "configuredPort", selectedPort);
                                putIfNotNull(svProps, "vlanTemplate", req.getTemplateNameVlan());
                                putIfNotNull(svProps, "serviceId", req.getServiceId());
                                putIfNotNull(svProps, "vlanCreateTemplate", req.getTemplateNameVlanCreate());

                                svProps.put("OperationalState", "Active");
                                svProps.put("createdBy",
                                        req.getCreatedBy() != null && !req.getCreatedBy().isEmpty()
                                                ? req.getCreatedBy()
                                                : "CA"
                                );
                                svProps.put("actionName", ACTION_LABEL);
                                svProps.put("linkedOnt", ont.getDiscoveredName());
                                singleVlan.setProperties(svProps);
                                vlanRepo.save(singleVlan);
                                ont = logicalDeviceRepo.findByDiscoveredName(ont.getDiscoveredName()).get();
                                ont.setContained(new HashSet<>(List.of(singleVlan)));
                                logicalDeviceRepo.save(ont);
                                break;
                            }
                        }
                    }
                }
            }
            // 16) Ensure associations & final persist for RFS/ONT/OLT
            // - add linked RFS on ONT and OLT properties
            ont = logicalDeviceRepo.findByDiscoveredName(ont.getDiscoveredName()).get();
            ont.getProperties().put("linkedRFS", rfs.getDiscoveredName());
            // persist again
            if (req.getTemplateNameOnt() != null) ont.getProperties().put("ontTemplate", req.getTemplateNameOnt());
            ont.getProperties().put("oltPosition", req.getOltName());
            ont.addUsedResource(olt);
            ont.setUsingService(new HashSet<>(List.of(rfs)));
            logicalDeviceRepo.save(ont);
            olt = logicalDeviceRepo.findByDiscoveredName(olt.getDiscoveredName()).get();
            olt.getProperties().put("linkedRFS", rfs.getDiscoveredName());
            olt.setUsingService(new HashSet<>(List.of(rfs)));
            logicalDeviceRepo.save(olt);

            // final response
            log.error(Constants.ACTION_COMPLETED);
            log.error("------------Trace # 19--------------- CreateServiceEVPN completed successfully");
            return new CreateServiceEVPNResponse(
                    "201",
                    "UIV action CreateServiceEVPN executed successfully.",
                    Instant.now().toString(),
                    subscription.getDiscoveredName(),
                    ont.getDiscoveredName()
            );

        } catch (Exception ex) {
            log.error("Exception in CreateServiceEVPN", ex);
            return new CreateServiceEVPNResponse(
                    "500",
                    ERROR_PREFIX + "Error occurred while creating service EVPN - " + ex.getMessage(),
                    Instant.now().toString(),
                    null,
                    null
            );
        }
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

}
