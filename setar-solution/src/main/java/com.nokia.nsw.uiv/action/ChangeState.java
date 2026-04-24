package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;

import com.nokia.nsw.uiv.model.common.party.CustomerRepository;

import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;

import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;

import com.nokia.nsw.uiv.repository.CustomerCustomRepository;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.repository.ServiceCustomRepository;
import com.nokia.nsw.uiv.repository.SubscriptionCustomRepository;
import com.nokia.nsw.uiv.request.ChangeStateRequest;
import com.nokia.nsw.uiv.response.ChangeStateResponse;

import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Optional;

@Component
@RestController
@Action
@Slf4j
public class ChangeState implements HttpAction {
    private static final String ACTION_LABEL = "ChangeState";
    private static final String ERROR_PREFIX = "UIV action ChangeState execution failed - ";

    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Autowired
    private LogicalDeviceCustomRepository logicalDeviceCustomRepository;

    @Autowired
    private LogicalDeviceRepository logicalDeviceRepository;

    @Autowired
    private CustomerCustomRepository customerRepository;

    @Override
    public Class<?> getActionClass() {
        return ChangeStateRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("Executing action {}", ACTION_LABEL);
        ChangeStateRequest req = (ChangeStateRequest) actionContext.getObject();

        // 1. Mandatory validation
        try {
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
            Validations.validateMandatory(req.getSubscriberName(), "subscriberName");
            Validations.validateMandatory(req.getServiceId(), "serviceId");
            Validations.validateMandatory(req.getActionType(), "actionType");
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
        } catch (BadRequestException bre) {
            return ResponseEntity.status(400).body(new ChangeStateResponse("400", ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage(),
                    DateTimeUtil.now(), "","",""));
        }

        // 2. Prepare names based on product type and service link
        String subscriptionName = "";
        String rfsName = "";
        String ontName = null;
        String cbmName = null;
        String productType = nullSafe(req.getProductType());
        String productSubtype = nullSafe(req.getProductSubtype());
        String serviceLink = nullSafe(req.getServiceLink());

        // IPTV case
        if (!isEmpty(productType) && productType.toUpperCase().contains("IPTV")) {
            subscriptionName = req.getSubscriberName() + Constants.UNDER_SCORE  + req.getServiceId();
            rfsName = "RFS" + Constants.UNDER_SCORE + req.getSubscriberName() + Constants.UNDER_SCORE  + req.getServiceId();
        }
        // Broadband/Voice with serviceLink provided and not Cloudstarter/Bridged
        else if ((equalsAny(productType, "Broadband", "Voice") && !equalsAny(productSubtype, "Cloudstarter", "Bridged"))
                && !isEmpty(serviceLink)) {

            if (serviceLink.equalsIgnoreCase("ONT")) {
                subscriptionName = req.getSubscriberName() + Constants.UNDER_SCORE  + req.getServiceId()+ Constants.UNDER_SCORE  + nullSafe(req.getOntSN());
                rfsName = "RFS" + Constants.UNDER_SCORE + req.getSubscriberName()+ Constants.UNDER_SCORE  + req.getServiceId() + Constants.UNDER_SCORE  + nullSafe(req.getOntSN());
            } else { // Cable_Modem
                subscriptionName = req.getSubscriberName() +Constants.UNDER_SCORE  + req.getServiceId();
                rfsName = "RFS" + Constants.UNDER_SCORE + req.getSubscriberName() + Constants.UNDER_SCORE  + req.getServiceId();
            }
        }else if (productType.equalsIgnoreCase("Broadband")&& serviceLink.contains("Cable_Modem")&&productSubtype.equalsIgnoreCase("Cloudstarter")) {
            subscriptionName = req.getSubscriberName()+ Constants.UNDER_SCORE  + req.getServiceId();
            rfsName = "RFS" + Constants.UNDER_SCORE + req.getSubscriberName()+ Constants.UNDER_SCORE  + req.getServiceId();
        }
        // fallback when ontSN present
        else if (req.getOntSN()!=null && !productType.toUpperCase().contains("IPTV")) {
            subscriptionName = req.getSubscriberName()+ Constants.UNDER_SCORE  + req.getServiceId()+ Constants.UNDER_SCORE  + req.getOntSN();
            rfsName = "RFS" + Constants.UNDER_SCORE + req.getSubscriberName()+ Constants.UNDER_SCORE  + req.getServiceId()+ Constants.UNDER_SCORE  + req.getOntSN();
        }
//        else {
//            // default fallback: subscriber + serviceId
//            subscriptionName = req.getSubscriberName()+ Constants.UNDER_SCORE  + req.getServiceId();
//            rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
//        }

        // 3. Check ONT name length if present
        if (!isEmpty(req.getOntSN())) {
            ontName ="ONT" + req.getOntSN();
            if (ontName.length() > 100) {
                return ResponseEntity.status(400).body(new ChangeStateResponse("400", ERROR_PREFIX + "ONT name too long",
                        DateTimeUtil.now(), "", ontName, ""));
            }
        }

        // 4. Search for subscription, rfs, ontd & cbm device
        try {

            Optional<Subscription> optSubscription = subscriptionRepository.findByDiscoveredName(subscriptionName);
            Optional<Service> optRfs = serviceCustomRepository.findByDiscoveredName(rfsName);
            Optional<LogicalDevice> optOnt = Optional.empty();
            Optional<LogicalDevice> optCbm = Optional.empty();

            if (!isEmpty(ontName)) {
                optOnt = logicalDeviceCustomRepository.findByDiscoveredName(ontName);
            }

            if (!isEmpty(req.getCbmMac())) {
                // attempt find by GDN "CBM" + Constants.UNDER_SCORE +mac (as per naming in your system)
                Iterable<LogicalDevice> devices = logicalDeviceRepository.findAll();
                Iterator<LogicalDevice> deviceIterator = devices.iterator();

                while(deviceIterator.hasNext()){
                    LogicalDevice d = deviceIterator.next();
                    if(d.getProperties().get("macAddress")!=null && d.getProperties().get("macAddress").toString().contains(req.getCbmMac())){
                        optCbm = Optional.of(d);
                        cbmName = optCbm.get().getDiscoveredName();
                    }
                }
            }
            if (!optSubscription.isPresent() || !optRfs.isPresent()) {
                return ResponseEntity.status(500).body(new ChangeStateResponse("500", ERROR_PREFIX + "No entry found for Suspend/Resume",
                        DateTimeUtil.now(), (cbmName == null ? "" : cbmName),
                        (ontName == null ? "" : ontName), subscriptionName));
            }

            Subscription subscription = optSubscription.get();
            Service rfs = optRfs.get();

            // 5. Perform state change
            String actionType = req.getActionType().trim();
            String newStatus;
            if ("Suspend".equalsIgnoreCase(actionType)) {
                newStatus = "Suspended";
            } else if ("Resume".equalsIgnoreCase(actionType)) {
                newStatus = "Active";
            } else {
                return ResponseEntity.status(400).body(new ChangeStateResponse("400", ERROR_PREFIX + "Unsupported actionType: " + actionType,
                        DateTimeUtil.now(), (cbmName == null ? "" : cbmName),
                        (ontName == null ? "" : ontName), subscriptionName));
            }

            // Update subscription property - store as subscriptionStatus
            subscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
            if (subscription.getProperties() == null) subscription.setProperties(new HashMap<>());
            subscription.getProperties().put("subscriptionStatus", newStatus);
            // Persist changes
            subscriptionRepository.save(subscription, 2);

            // 6. Update RFS transaction info if fxOrderId present
            rfs = serviceCustomRepository.findByDiscoveredName(rfs.getDiscoveredName()).get();
            if (!isEmpty(req.getFxOrderId())) {
                if (rfs.getProperties() == null) rfs.setProperties(new HashMap<>());
                rfs.getProperties().put("transactionId", req.getFxOrderId());
                rfs.getProperties().put("transactionType", actionType);
            }
            serviceCustomRepository.save(rfs, 2);

            // Also persist ONT/CBM if we located and want to reflect state (optional)
//            if (optOnt.isPresent()) {
//                LogicalDevice ont = optOnt.get();
//                logicalDeviceRepository.save(ont, 2);
//            }
//            if (optCbm.isPresent()) {
//                LogicalDevice cbm = optCbm.get();
//                logicalDeviceRepository.save(cbm, 2);
//            }
            log.error(Constants.ACTION_COMPLETED);
            // 7. Final response
            return new ChangeStateResponse("200",
                    "UIV action ChangeState executed successfully.",
                    DateTimeUtil.now(),
                    (cbmName == null ? "" : cbmName),
                    (ontName == null ? "" : ontName),
                    subscriptionName
            );

        } catch (Exception ex) {
            log.error("ChangeState failed", ex);
            return ResponseEntity.status(500).body(new ChangeStateResponse("500", ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage(),
                    DateTimeUtil.now(),
                    (cbmName == null ? "" : cbmName),
                    (ontName == null ? "" : ontName),
                    subscriptionName));
        }
    }


    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private boolean equalsAny(String s, String... opts) {
        if (s == null) return false;
        for (String o : opts) if (o != null && s.equalsIgnoreCase(o)) return true;
        return false;
    }

    private String nullSafe(String s) { return s == null ? "" : s; }
}
