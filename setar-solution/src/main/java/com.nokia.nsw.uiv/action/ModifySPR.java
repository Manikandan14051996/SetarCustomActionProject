package com.nokia.nsw.uiv.action;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

import com.nokia.nsw.uiv.exception.AccessForbiddenException;
import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.exception.ModificationNotAllowedException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;
import com.nokia.nsw.uiv.model.resource.infra.InfraDevice;
import com.nokia.nsw.uiv.model.resource.logical.*;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.ModifySPRRequest;
import com.nokia.nsw.uiv.response.CreateServiceIPTVResponse;
import com.nokia.nsw.uiv.response.ModifyCBMResponse;
import com.nokia.nsw.uiv.response.ModifySPRResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.jcodings.util.Hash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@Action
@Slf4j
public class ModifySPR implements HttpAction {

    protected static final String ACTION_LABEL = Constants.MODIFY_SPR;
    private static final String ERROR_PREFIX = "UIV action ModifySPR execution failed - ";

    @Autowired
    private LogicalDeviceCustomRepository logicalCustomDeviceRepository;

    @Autowired
    private LogicalDeviceRepository logicDeviceRepository;

    @Autowired
    private LogicalComponentCustomRepository logicalComponentRepository;

    @Autowired
    private LogicalInterfaceCustomRepository logicalInterfaceRepository;
    @Autowired
    private CustomerCustomRepository customerRepository;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;
    @Autowired
    private ServiceCustomRepository serviceCustomRepository;


    @Autowired
    private ProductCustomRepository productCustomRepository;

    @Override
    public Class getActionClass() {
        return ModifySPRRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);

        ModifySPRRequest request = (ModifySPRRequest) actionContext.getObject();
        boolean success = false;
        String flag = "";

        try {
            // 1. Mandatory Validations
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
            Validations.validateMandatoryParams(request.getSubscriberName(), "SUBSCRIBER_NAME");
            Validations.validateMandatoryParams(request.getProductType(), "PRODUCT_TYPE");
            Validations.validateMandatoryParams(request.getProductSubtype(), "PRODUCT_SUB_TYPE");
            Validations.validateMandatoryParams(request.getOntSN(), "ONT_SN");
            Validations.validateMandatoryParams(request.getServiceId(), "SERVICE_ID");
            Validations.validateMandatoryParams(request.getModifyType(), "MODIFY_TYPE");
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);

            // 2. Name Construction
            String subscriberName = request.getSubscriberName() + Constants.UNDER_SCORE + request.getOntSN();
            String subscriptionName = request.getSubscriberName() + Constants.UNDER_SCORE + request.getServiceId() + Constants.UNDER_SCORE + request.getOntSN();
            String ontName = "ONT" + request.getOntSN();

            try {
                Validations.validateLength(ontName, "ONT");
                Validations.validateLength(subscriberName, "Subscriber");
                Validations.validateLength(subscriptionName, "Subscription");
            } catch (BadRequestException bre) {
                return new ModifySPRResponse("400", ERROR_PREFIX + bre.getMessage(),
                        Instant.now().toString(), "", "");
            }


            // 3. Fetch Entities
            Optional<Customer> subscriberOpt = customerRepository.findByDiscoveredName(subscriberName);
            Customer subscriber = null;

            LogicalDevice ontDevice = logicalCustomDeviceRepository.findByDiscoveredName(ontName).orElseThrow(() -> new BadRequestException("No entry found to modify ONTDevice: " + ontName));

            if (!subscriberOpt.isPresent()) {
                flag = "partly";
            } else {
                subscriber = subscriberOpt.get();
            }
            Subscription subscription = subscriptionRepository.findByDiscoveredName(subscriptionName)
                    .orElseThrow(() -> new BadRequestException("No entry found to modify Subscription: " + subscriptionName));

            // 4. Route to correct handler
            try {
                if (isBroadband(request)) {
                    success = handleFibernetOrBroadband(request, subscriber, subscription);
                } else if (isEnterprise(request)) {
                    success = handleEVPN(request, subscription);
                } else if (isVoip(request)) {
                    success = handleVOIP(request, subscription, ontName);
                }

                if (isOntModification(request)) {
                    success = handleModifyONT(request, ontName, flag);
                }
            } catch (BadRequestException bre) {
                log.error("Validation or not found error: {}", bre.getMessage(), bre);
                String msg = ERROR_PREFIX + bre.getMessage();
                return new ModifySPRResponse("400", msg, getCurrentTimestamp(), "", "");
            }
            Subscription updatedSubscription = subscriptionRepository.findById(subscription.getUuid())
                    .orElse(subscription); // fallback to original if somehow missing
            String updatedSubscriptionName = updatedSubscription.getDiscoveredName();

            LogicalDevice updatedOnt = logicalCustomDeviceRepository.findById(ontDevice.getUuid())
                    .orElse(ontDevice); // fallback to original if somehow missing
            String updatedOntName = updatedOnt.getDiscoveredName();

            // 5. Response
            if (success) {
                log.error(Constants.ACTION_COMPLETED);
                return new ModifySPRResponse("200", "UIV action ModifySPR executed successfully.", getCurrentTimestamp(),
                        updatedOntName, updatedSubscriptionName);
            } else {
                return new ModifySPRResponse("200", "UIV action ModifySPR executed successfully.Modification not done", getCurrentTimestamp(),
                        updatedOntName, updatedSubscriptionName);
            }

        } catch (BadRequestException bre) {
            log.error("Validation or not found error: {}", bre.getMessage(), bre);
            String msg = ERROR_PREFIX + bre.getMessage();
            return new ModifySPRResponse("400", msg, getCurrentTimestamp(), "", "");
        } catch (ModificationNotAllowedException ex) {
            log.error("Persistence error: {}", ex.getMessage(), ex);
            String msg = ERROR_PREFIX + ex.getMessage();
            return new ModifySPRResponse("500", msg, getCurrentTimestamp(), "", "");
        } catch (Exception ex) {
            log.error("Unhandled exception during ModifySPR", ex);
            String msg = ERROR_PREFIX + "Internal server error occurred";
            return new ModifySPRResponse("500", msg, getCurrentTimestamp(), "", "");
        }
    }

    // ------------------ HANDLERS ------------------

    private boolean handleFibernetOrBroadband(ModifySPRRequest request,
                                              Customer subscriber,
                                              Subscription subscription) throws ModificationNotAllowedException, BadRequestException, AccessForbiddenException {
        if ("Username".equalsIgnoreCase(request.getModifyType())) {
            Map<String, Object> subProps = subscription.getProperties();
            subProps.put("subscriptionDetails", request.getModifyParam1());
            subProps.put("serviceID", request.getModifyParam3());
            subscription.setProperties(subProps);

            Map<String, Object> subrProps = subscriber.getProperties();
            subrProps.put("email", request.getModifyParam2());

            if (!request.getServiceId().equals(request.getModifyParam3())) {
                updateSubscriptionAndChildren(request, subscription, request.getModifyParam3());
            } else {
                subscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
                subscription.setProperties(subProps);
                subscriptionRepository.save(subscription);
            }
            subscriber = customerRepository.findByDiscoveredName(subscriber.getDiscoveredName()).get();
            subscriber.setProperties(subrProps);
            customerRepository.save(subscriber, 2);
            return true;

        } else if ("Password".equalsIgnoreCase(request.getModifyType())) {
            try {
                Map<String, Object> subrProps = subscriber.getProperties();
                subrProps.put("emailPassword", request.getModifyParam1());
                subscriber.setProperties(subrProps);
                customerRepository.save(subscriber, 2);
                return true;
            } catch (Exception e) {
                throw new ModificationNotAllowedException("Failed to persist password update " + e.getMessage());
            }
        } else if (List.of("Package", "Component", "Product", "Contract").contains(request.getModifyType())) {
            try {
                Map<String, Object> subProps = subscription.getProperties();
                if ("Cloudstarter".equalsIgnoreCase(request.getProductSubtype())
                        || "Bridged".equalsIgnoreCase(request.getProductSubtype())) {
                    subProps.put("evpnQosSessionProfile", request.getModifyParam1());
                } else {
                    subProps.put("veipQosSessionProfile", request.getModifyParam1());
                }
                subscription.setProperties(subProps);
                subscriptionRepository.save(subscription, 2);
                return true;
            } catch (Exception e) {
                throw new ModificationNotAllowedException("Failed to update QoS profile " + e.getMessage());
            }
        }
        return false;
    }

    private boolean handleEVPN(ModifySPRRequest request, Subscription subscription) throws ModificationNotAllowedException, BadRequestException, AccessForbiddenException {
        if ("Username".equalsIgnoreCase(request.getModifyType())) {
            Map<String, Object> subProps = subscription.getProperties();
            subProps.put("subscriptionDetails", "FTTB-" + request.getModifyParam1());
            subProps.put("serviceID", request.getModifyParam1());
            subscription.setProperties(subProps);

            if (!request.getServiceId().equals(request.getModifyParam1())) {
                updateSubscriptionAndChildren(request, subscription, request.getModifyParam1());
            } else {
                subscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
                subscription.setProperties(subProps);
                subscriptionRepository.save(subscription);
            }
            return true;

        } else if ("Component".equalsIgnoreCase(request.getModifyType())) {
            try {
                subscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
                Map<String, Object> subProps = subscription.getProperties();
                subProps.put("evpnQosSessionProfile", request.getModifyParam1());
                subscription.setProperties(subProps);
                subscriptionRepository.save(subscription, 2);
                return true;
            } catch (Exception e) {
                throw new ModificationNotAllowedException("Failed to update EVPN component " + e.getMessage());
            }
        }
        return false;
    }

    private boolean handleVOIP(ModifySPRRequest request, Subscription subscription, String ontName) throws ModificationNotAllowedException, BadRequestException {
        if (List.of("Package", "Product").contains(request.getModifyType())) {
            try {
                Map<String, Object> subProps = subscription.getProperties();
                subProps.put("voipPackage1", request.getModifyParam1());
                subProps.put("voipServiceCode1", request.getModifyParam2());
                subscription.setProperties(subProps);
                subscriptionRepository.save(subscription, 2);
                return true;
            } catch (Exception e) {
                throw new ModificationNotAllowedException("Failed to update VoIP package " + e.getMessage());
            }
        } else if ("Modify_Number".equalsIgnoreCase(request.getModifyType())) {
            try {
                Map<String, Object> subProps = subscription.getProperties();
                subProps.put("serviceID", request.getModifyParam1());
                LogicalDevice ont = logicalCustomDeviceRepository.findByDiscoveredName(ontName)
                        .orElseThrow(() -> new BadRequestException("No entry found to modify ONT"));
                logicalCustomDeviceRepository.save(ont, 2);
                String tempNumberOnt2 = ont.getProperties().get("potsPort2Number") != null ? ont.getProperties().get("potsPort2Number").toString() : "";
                if (tempNumberOnt2 == null || tempNumberOnt2 == "") {
                    tempNumberOnt2 = "empty";
                }
                if (tempNumberOnt2.equals(request.getServiceId())) {
                    subProps.put("voipNumber1", request.getModifyParam1());
                    ont.getProperties().put("potsPort2Number", request.getModifyParam1());
                } else {
                    subProps.put("voipNumber1", request.getModifyParam1());
                    ont.getProperties().put("potsPort1Number", request.getModifyParam1());
                }
                logicalCustomDeviceRepository.save(ont);
                subscription.setProperties(subProps);
                if (!request.getServiceId().equals(request.getModifyParam1())) {
                    updateSubscriptionAndChildren(request, subscription, request.getModifyParam1());
                } else {
                    subscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
                    subscription.setProperties(subProps);
                    subscriptionRepository.save(subscription);
                }
                return true;
            } catch (BadRequestException e) {
                throw new BadRequestException(e.getMessage());
            } catch (Exception e) {
                throw new ModificationNotAllowedException("Failed to modify VOIP number: " + e.getMessage());
            }
        }
        return false;
    }

    private boolean handleModifyONT(ModifySPRRequest request, String ontName, String flag) throws BadRequestException, AccessForbiddenException {
        LogicalDevice ont = logicalCustomDeviceRepository.findByDiscoveredName(ontName)
                .orElseThrow(() -> new BadRequestException("No entry found to modify ONT: " + ontName));
        if (ont != null) {
            Set<Subscription> iptvSubscriptions = getiptvSubscriptions(request.getSubscriberName());
            if (!iptvSubscriptions.isEmpty()) {
                for (Subscription subscription : iptvSubscriptions) {
                    subscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
                    subscription.getProperties().put("serviceSN", request.getModifyParam1());
                    subscriptionRepository.save(subscription, 2);
                }
            }
        }
        Set<Service> services = ont.getUsingService();
        for (Service s : services) {
            Optional<Customer> setarSubscriber1 = Optional.empty();
            String subscriberNewName = "";
            try {
                Service rfs = s;
                String[] rfsNameArray = rfs.getDiscoveredName().split("_");
                String subscriber = rfsNameArray[1];
                String subscriberNameForOnt = subscriber + Constants.UNDER_SCORE + request.getOntSN();
                setarSubscriber1 = customerRepository.findByDiscoveredName(subscriberNameForOnt);
                subscriberNewName = subscriber + Constants.UNDER_SCORE + request.getModifyParam1();
                String ontExist = "";
                String ontNameNew = "ONT" + request.getModifyParam1();
                try {
                    Validations.validateLength(ontNameNew, "New ONT");
                } catch (BadRequestException bre) {
                    throw new BadRequestException(bre.getMessage());
                }
                Optional<LogicalDevice> ontDevice = logicalCustomDeviceRepository.findByDiscoveredName(ontName);
                if (!ontDevice.isPresent()) {
                    ontExist = "fail";
                }
                Set<Subscription> subscriptions = setarSubscriber1.get().getSubscription();
                for (Subscription subs : subscriptions) {
                    subs = subscriptionRepository.findByDiscoveredName(subs.getDiscoveredName()).get();
                    String serviceSubType = subs.getProperties().get("serviceSubType") != null ? subs.getProperties().get("serviceSubType").toString() : "";
                    String ontModel = request.getOntModel() != null ? request.getOntModel() : "";
                    if ((serviceSubType.equalsIgnoreCase("Bridged") && ontModel.contains("XS-2426G-B"))) {

                        LogicalDevice oltDevice = (LogicalDevice) ontDevice.get().getUsedResource().stream().findFirst().get();
                        oltDevice = logicalCustomDeviceRepository.findByDiscoveredName(oltDevice.getDiscoveredName()).get();
                        oltDevice.getProperties().put("veipServiceTemplate", request.getTemplateNameVEIP());
                        oltDevice.getProperties().put("veipHsiTemplate", request.getTemplateNameVLAN());
                        logicalCustomDeviceRepository.save(oltDevice, 2);
                        subs = subscriptionRepository.findByDiscoveredName(subs.getDiscoveredName()).get();
                        subs.getProperties().put("evpnTemplateVLAN", request.getTemplateNameVLAN());
                    }
                    String subscriptionName = subs.getDiscoveredName();
                    String serviceID = subs.getProperties().get("serviceID") != null ? subs.getProperties().get("serviceID").toString() : "";
                    String subscriptionNameNew = subscriber + Constants.UNDER_SCORE + serviceID + Constants.UNDER_SCORE + request.getModifyParam1();
                    String productName = subscriber + Constants.UNDER_SCORE + request.getProductSubtype() + Constants.UNDER_SCORE + request.getServiceId();
                    String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
                    String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
                    String cfsNameNew = "CFS" + Constants.UNDER_SCORE + subscriptionNameNew;
                    String rfsNameNew = "RFS" + Constants.UNDER_SCORE + subscriptionNameNew;
                    try {
                        Validations.validateLength(subscriptionNameNew, "New Subscription");
                        Validations.validateLength(productName, "New Product");
                    } catch (BadRequestException bre) {
                        throw new BadRequestException(bre.getMessage());
                    }
                    subs.setDiscoveredName(subscriptionNameNew);
                    subs.getProperties().put("serviceSN", request.getModifyParam1());
                    subs.getProperties().put("gatewayMacAddress", request.getModifyParam2());
                    subscriptionRepository.save(subs, 2);

                    Service cfsOld = serviceCustomRepository.findByDiscoveredName(cfsName).get();
                    cfsOld.setDiscoveredName(cfsNameNew);
                    cfsOld.getProperties().put("serviceEndDate", getCurrentTimestamp());
                    serviceCustomRepository.save(cfsOld, 2);
                    Service rfsOld = serviceCustomRepository.findByDiscoveredName(rfsName).get();
                    rfsOld.setDiscoveredName(rfsNameNew);
                    if (request.getFxOrderId() != null) {
                        rfsOld.getProperties().put("transactionId", request.getFxOrderId());
                    }
                    rfsOld.getProperties().put("transactionType", request.getModifyType());
                    serviceCustomRepository.save(rfsOld, 2);
                    if (ontExist != "fail") {
                        ontDevice = logicalCustomDeviceRepository.findByDiscoveredName(ontDevice.get().getDiscoveredName());
                        if (!ontDevice.isPresent()) {
                            ontDevice = logicalCustomDeviceRepository.findByDiscoveredName(ontNameNew);
                        }
                        Set<LogicalResource> tempInterfaces = ontDevice.get().getContained();
                        if (!tempInterfaces.isEmpty()) {
                            for (LogicalResource d : tempInterfaces) {
                                LogicalInterface temp = (LogicalInterface) d;
                                temp = logicalInterfaceRepository.findByDiscoveredName(temp.getDiscoveredName()).get();
                                String vlanName = temp.getDiscoveredName();
                                String vlanPort = temp.getProperties().get("configuredPort") != null ? temp.getProperties().get("configuredPort").toString() : "";
                                if (vlanName.contains(request.getOntSN())) {
                                    for (int i = 2; i <= 8; i++) {
                                        Integer tempID = i;
                                        String freeTemp = tempID.toString();

                                        if (vlanName.endsWith(freeTemp) == true) {

                                            String tempName = request.getModifyParam1() + Constants.UNDER_SCORE + "P" + vlanPort + Constants.UNDER_SCORE + "SINGLETAGGED" + Constants.UNDER_SCORE + freeTemp;
                                            System.out.println("++++++  {630}  ++++++");
                                            temp.setDiscoveredName(tempName);
                                            temp.getProperties().put("configuredOnt", request.getModifyParam1());
                                            logicalInterfaceRepository.save(temp, 2);
                                            break;
                                        }
                                    }
                                }

                            }
                        }
                        if (ontDevice.get().getDiscoveredName().contains(request.getOntSN())) {
                            LogicalDevice tempOnt = logicalCustomDeviceRepository.findByDiscoveredName(ontDevice.get().getDiscoveredName()).get();
                            log.error("++++++  ontModel  ++++++" + request.getOntModel());
                            tempOnt.setDiscoveredName(ontNameNew);
                            tempOnt.getProperties().put("serialNo", request.getModifyParam1());
                            if (request.getOntModel() != null) {
                                tempOnt.getProperties().put("deviceModel", request.getOntModel());
                            }
                            logicalCustomDeviceRepository.save(tempOnt, 2);
                        }

                    }
                }
                try {
                    Optional<LogicalDevice> cpeDeviceOldOpt = logicalCustomDeviceRepository.findByDiscoveredName("ONT_" + request.getOntSN());
                    Optional<LogicalDevice> cpeDeviceNewOpt = logicalCustomDeviceRepository.findByDiscoveredName("ONT_" + request.getModifyParam1());

                    LogicalDevice cpeDeviceOld = null;
                    LogicalDevice cpeDeviceNew = null;

                    if (cpeDeviceOldOpt.isPresent() && cpeDeviceNewOpt.isPresent()) {
                        cpeDeviceNew = cpeDeviceNewOpt.get();
                        cpeDeviceOld = cpeDeviceOldOpt.get();
                        String voipPort1 = (cpeDeviceOld.getProperties().get("voipPort1") != null) ? cpeDeviceOld.getProperties().get("voipPort1").toString() : "";
                        String voipPort2 = (cpeDeviceOld.getProperties().get("voipPort2") != null) ? cpeDeviceOld.getProperties().get("voipPort1").toString() : "";
                        Map<String, Object> cpeNewProps = cpeDeviceNew.getProperties();
                        cpeNewProps.put("AdministrativeState", "Allocated");
                        cpeNewProps.put("description", "Internet");
                        cpeNewProps.put("modelSubType", "HFC");
                        cpeNewProps.put("voipPort1", voipPort1);
                        cpeNewProps.put("voipPort2", voipPort2);
                        cpeDeviceNew.setProperties(cpeNewProps);
                        logicalCustomDeviceRepository.save(cpeDeviceNew, 2);

                        cpeDeviceOld = logicalCustomDeviceRepository.findByDiscoveredName(cpeDeviceOldOpt.get().getDiscoveredName()).get();
                        Map<String, Object> cpeOldProps = cpeDeviceOld.getProperties();
                        cpeOldProps.put("AdministrativeState", "Available");
                        cpeOldProps.put("description", "");
                        cpeOldProps.put("modelSubType", "");
                        cpeOldProps.put("voipPort1", "Available");
                        cpeOldProps.put("voipPort2", "Available");
                        cpeDeviceOld.setProperties(cpeOldProps);
                        logicalCustomDeviceRepository.save(cpeDeviceOld, 2);
                    }
                } catch (Exception e) {
                    log.error("Exception while retrieving cpeDevice: " + e.getMessage());
                }
            } catch (BadRequestException e) {
                throw new BadRequestException(e.getMessage());
            } catch (Exception e) {
                log.error("Exception occure while processing RFS: " + e.getMessage());
                return false;
            }
            if (flag != "partly") {
                setarSubscriber1 = customerRepository.findByDiscoveredName(setarSubscriber1.get().getDiscoveredName());
                setarSubscriber1.get().setDiscoveredName(subscriberNewName);
                customerRepository.save(setarSubscriber1.get());
            }
            return true;
        }
        return false;
    }

    private Set<Subscription> getiptvSubscriptions(String subscriberName) {
        List<Subscription> subsList = (List<Subscription>) subscriptionRepository.findAll();
        Set<Subscription> iptvSubscriptions = new HashSet<>();
        for (Subscription sub : subsList) {
            if (sub.getDiscoveredName().contains(subscriberName) && !sub.getDiscoveredName().contains("ALCL")) {
                iptvSubscriptions.add(sub);
            }
        }
        return iptvSubscriptions;
    }

    // ------------------ HELPERS ------------------

    private void updateSubscriptionAndChildren(ModifySPRRequest request,
                                               Subscription subscription,
                                               String newServiceId) throws BadRequestException, AccessForbiddenException {
        String oldSubscriptionName = request.getSubscriberName() + Constants.UNDER_SCORE + request.getServiceId() + Constants.UNDER_SCORE + request.getOntSN();
        String productName = request.getSubscriberName() + Constants.UNDER_SCORE + request.getProductSubtype() + Constants.UNDER_SCORE + request.getServiceId();
        String cfsName = "CFS" + Constants.UNDER_SCORE + oldSubscriptionName;
        String rfsName = "RFS" + Constants.UNDER_SCORE + oldSubscriptionName;

        String subscriptionNameNew = request.getSubscriberName() + Constants.UNDER_SCORE + newServiceId + Constants.UNDER_SCORE + request.getOntSN();
        String productNameNew = request.getSubscriberName() + Constants.UNDER_SCORE + request.getProductSubtype() + Constants.UNDER_SCORE + newServiceId;
        try {
            Validations.validateLength(subscriptionNameNew, "New Subscription");
            Validations.validateLength(productNameNew, "New Product");
        } catch (BadRequestException bre) {
            throw new BadRequestException(bre.getMessage());
        }
        String cfsNameNew = "CFS" + Constants.UNDER_SCORE + subscriptionNameNew;
        String rfsNameNew = "RFS" + Constants.UNDER_SCORE + subscriptionNameNew;
        try {
            Validations.validateLength(productName, "Product");
        } catch (BadRequestException bre) {
            throw new BadRequestException("400 " + ERROR_PREFIX + bre.getMessage());
        }
        productCustomRepository.findByDiscoveredName(productName).ifPresent(product -> {
            product.setDiscoveredName(productNameNew);
            productCustomRepository.save(product, 2);
        });

        serviceCustomRepository.findByDiscoveredName(cfsName).ifPresent(cfs -> {
            cfs.setDiscoveredName(cfsNameNew);
            cfs.getProperties().put("serviceEndDate", getCurrentTimestamp());
            serviceCustomRepository.save(cfs, 2);
        });

        serviceCustomRepository.findByDiscoveredName(rfsName).ifPresent(rfs -> {
            rfs.setDiscoveredName(rfsNameNew);
            rfs.getProperties().put("transactionType", request.getModifyType());
            if (request.getFxOrderId() != null) {
                rfs.getProperties().put("transactionId", request.getFxOrderId());
            }
            serviceCustomRepository.save(rfs, 2);
        });
        Subscription tempSubscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
        tempSubscription.setProperties(subscription.getProperties());
        tempSubscription.setDiscoveredName(subscriptionNameNew);
        subscriptionRepository.save(tempSubscription);
        if ("Modify_Number".equalsIgnoreCase(request.getModifyType())) {
            LogicalDevice cpeDevice = logicalCustomDeviceRepository.findByDiscoveredName("ONT_" + request.getOntSN()).get();

            String voipNumber1 = cpeDevice.getProperties().get("voipPort1") != null ? cpeDevice.getProperties().get("voipPort1").toString() : "";
            String voipNumber2 = cpeDevice.getProperties().get("voipPort2") != null ? cpeDevice.getProperties().get("voipPort2").toString() : "";

            if (request.getServiceId().equalsIgnoreCase(voipNumber1)) {
                cpeDevice.getProperties().put("voipPort1", request.getModifyParam1());

            } else if (request.getServiceId().equalsIgnoreCase(voipNumber2)) {
                cpeDevice.getProperties().put("voipPort2", request.getModifyParam1());
            }
            logicalCustomDeviceRepository.save(cpeDevice, 2);
        }

    }

    private boolean isBroadband(ModifySPRRequest request) {
        return "Fibernet".equalsIgnoreCase(request.getProductType())
                || "Broadband".equalsIgnoreCase(request.getProductType());
    }

    private boolean isEnterprise(ModifySPRRequest request) {
        return "EVPN".equalsIgnoreCase(request.getProductType())
                || "ENTERPRISE".equalsIgnoreCase(request.getProductType());
    }

    private boolean isVoip(ModifySPRRequest request) {
        return "VOIP".equalsIgnoreCase(request.getProductType())
                || "Voice".equalsIgnoreCase(request.getProductType());
    }

    private boolean isOntModification(ModifySPRRequest request) {
        return "Modify_ONT".equalsIgnoreCase(request.getModifyType())
                || "ONT".equalsIgnoreCase(request.getModifyType());
    }

    private String getCurrentTimestamp() {
        return java.time.Instant.now().toString();
    }

}
