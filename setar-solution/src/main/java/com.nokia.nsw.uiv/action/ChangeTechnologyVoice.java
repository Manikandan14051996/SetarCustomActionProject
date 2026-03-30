package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.ChangeTechnologyVoiceRequest;
import com.nokia.nsw.uiv.response.ChangeTechnologyVoiceResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;

import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RestController
@Action
@Slf4j
public class ChangeTechnologyVoice implements HttpAction {
    protected static final String ACTION_LABEL = Constants.CHANGE_TECHNOLOGY_VOICE;
    private static final String ERROR_PREFIX = "UIV action ChangeTechnologyVoice execution failed - ";

    @Autowired
    private CustomerCustomRepository customerRepo;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepo;

    @Autowired
    private ProductCustomRepository productRepo;

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Autowired
    private LogicalDeviceCustomRepository logicalDeviceRepo;

    @Override
    public Class<?> getActionClass() {
        return ChangeTechnologyVoiceRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("------------Test Trace # 1---------------");
        ChangeTechnologyVoiceRequest req = (ChangeTechnologyVoiceRequest) actionContext.getObject();

        try {
            // 1. Validate mandatory params (runtime validation)
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatory(req.getSubscriberName(), "subscriberName");
                Validations.validateMandatory(req.getProductSubtype(), "productSubtype");
                Validations.validateMandatory(req.getServiceId(), "serviceId");
                Validations.validateMandatory(req.getOntSN(), "ontSN");
                Validations.validateMandatory(req.getOntMacAddr(), "ontMacAddr");
                Validations.validateMandatory(req.getCbmSn(), "cbmSn");
                Validations.validateMandatory(req.getOltName(), "oltName");
                Validations.validateMandatory(req.getTemplateNamePots1(), "templateNamePots1");
                Validations.validateMandatory(req.getTemplateNamePots2(), "templateNamePots2");
                Validations.validateMandatory(req.getVoipPackage(), "voipPackage");
                Validations.validateMandatory(req.getVoipServiceCode(), "voipServiceCode");
                Validations.validateMandatory(req.getOntModel(), "ontModel");
                Validations.validateMandatory(req.getCbmMac(), "cbmMac");
                Validations.validateMandatory(req.getOntPort(), "ontPort");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (Exception bre) {
                // Missing mandatory param
                log.error("------------Test Trace # 2--------------- Missing mandatory param: " + bre.getMessage());
                return new ChangeTechnologyVoiceResponse("400", ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage(),
                        Instant.now().toString(), "", "");
            }

            log.error("------------Test Trace # 3--------------- Validations OK");

            // 2. Prepare names
            String subscriptionName = req.getSubscriberName() + Constants.UNDER_SCORE  + req.getServiceId();
            String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
            String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
            String cbmName = "CBM" +req.getCbmSn();
            String ontName ="ONT" + req.getOntSN(); // per spec: ONT + SN (no underscore)
            String cpeDeviceName ="ONT" + Constants.UNDER_SCORE + req.getOntSN(); // CPE device convention used elsewhere
            String cpeDeviceOldName = "CBM" + Constants.UNDER_SCORE +req.getCbmMac(); // CBM CPE

            log.error("------------Test Trace # 4--------------- Names prepared:"
                    + " subscriptionName=" + subscriptionName
                    + " cfsName=" + cfsName
                    + " rfsName=" + rfsName
                    + " cbmName=" + cbmName
                    + " ontName=" + ontName);

            // 3. Name length checks
            if (ontName.length() > 100) {
                log.error("------------Test Trace # 5--------------- ONT name too long");
                return new ChangeTechnologyVoiceResponse("400", ERROR_PREFIX + "ONT name too long",
                        Instant.now().toString(), subscriptionName, ontName);
            }

            // 4. Update Subscription (must exist)
            log.error("------------Test Trace # 6--------------- Fetching Subscription by " + subscriptionName);
            Optional<Subscription> subsOpt = subscriptionRepo.findByDiscoveredName(subscriptionName);
            if (subsOpt.isPresent()) {
                Subscription subs = subsOpt.get();
                log.error("------------Test Trace # 7--------------- Subscription found: " + subs.getLocalName());

                // Update core subscription fields
                Map<String, Object> props = subs.getProperties();
                props.put("serviceLink", "ONT");
                props.put("serviceMAC", req.getOntMacAddr());
                props.put("serviceSN", req.getOntSN());
                props.put("serviceSubType", "Voice");

                // SIMA fields if provided
                if (req.getSimaSubsId() != null && !req.getSimaSubsId().trim().isEmpty()) {
                    props.put("simaSubsId1", req.getSimaSubsId());
                    log.error("------------Test Trace # 8--------------- Set SIMA subs id: " + req.getSimaSubsId());
                }
                if (req.getServiceEndpointNumber1() != null && !req.getServiceEndpointNumber1().trim().isEmpty()) {
                    // map to subscription endpoint slot 1
                    props.put("simaEndpointId1", req.getServiceEndpointNumber1());
                    log.error("------------Test Trace # 9--------------- Set SIMA endpoint id 1: " + req.getServiceEndpointNumber1());
                }

                if (req.getServiceEndpointNumber2() != null && !req.getServiceEndpointNumber2().trim().isEmpty()) {
                    props.put("simaEndpointId1", req.getServiceEndpointNumber2());
                    log.error("------------Test Trace # 9--------------- Set SIMA endpoint id 1: " + req.getServiceEndpointNumber1());
                }

                // VoIP package/code
                if (req.getVoipPackage() != null && !req.getVoipPackage().trim().isEmpty()) {
                    props.put("voipPackage1", req.getVoipPackage());
                    log.error("------------Test Trace # 10--------------- Set VoIP package: " + req.getVoipPackage());
                }
                if (req.getVoipServiceCode() != null && !req.getVoipServiceCode().trim().isEmpty()) {
                    props.put("voipServiceCode1", req.getVoipServiceCode());
                    log.error("------------Test Trace # 11--------------- Set VoIP service code: " + req.getVoipServiceCode());
                }

                // Rename subscription to include ONT SN per spec
                String newSubscriptionName = subscriptionName + Constants.UNDER_SCORE  + req.getOntSN();
                log.error("------------Test Trace # 12--------------- Renaming subscription to: " + newSubscriptionName);
                subs.setDiscoveredName(newSubscriptionName);
                subs.setProperties(props);

                // Persist subscription updates
                subscriptionRepo.save(subs);
                log.error("------------Test Trace # 13--------------- Subscription saved: " + newSubscriptionName);

                // 5. Update Subscriber if linked via subscription
                // Try to find linked subscriber — attempt several common patterns
                String subscriberCandidate1 = req.getSubscriberName() + Constants.UNDER_SCORE  + req.getOntSN();
                String subscriberCandidate2 = req.getSubscriberName();
                Optional<Customer> custOpt = customerRepo.findByDiscoveredName(subscriberCandidate1);
                if (!custOpt.isPresent()) {
                    custOpt = customerRepo.findByDiscoveredName(subscriberCandidate2);
                }
                if (custOpt.isPresent()) {
                    Customer cust = custOpt.get();
                    log.error("------------Test Trace # 14--------------- Subscriber found: " + cust.getLocalName());
                    // accountNumber mapping stored in properties map - preserve or set
                    Map<String, Object> custProps = cust.getProperties() == null ? new HashMap<>() : new HashMap<>(cust.getProperties());
                    custProps.put("accountNumber", req.getSubscriberName());
                    custProps.put("subscriberStatus", "Active");
                    custProps.put("subscriberType","Regular");
                    if (req.getHhid() != null) custProps.put("houseHoldId", req.getHhid());
                    if (req.getSimaCustId() != null) custProps.put("simaCustId1", req.getSimaCustId());
                    cust.setProperties(custProps);
                    customerRepo.save(cust);
                    log.error("------------Test Trace # 15--------------- Subscriber updated and saved");
                } else {
                    log.error("------------Test Trace # 16--------------- Subscriber not found using common patterns - continuing");
                }

            } else {
                // Per spec, subscription must exist; but we proceed (action does not create new subscription)
                log.error("------------Test Trace # 17--------------- Subscription not found: " + subscriptionName + " - continuing without subscription updates");
            }

            // 6. Update CFS (if exists)
            log.error("------------Test Trace # 18--------------- Checking CFS: " + cfsName);
            String cfsGdn = Validations.getGlobalName(cfsName);
            Optional<Service> cfsOpt = serviceCustomRepository.findByDiscoveredName(cfsName);
            if (cfsOpt.isPresent()) {
                Service cfs = cfsOpt.get();
                String newCfsName = cfs.getDiscoveredName() + Constants.UNDER_SCORE  + req.getOntSN();
                cfs.setDiscoveredName(newCfsName);
                if (req.getFxOrderId() != null && !req.getFxOrderId().trim().isEmpty()) {
                    Map<String, Object> cfsProps = cfs.getProperties() == null ? new HashMap<>() : new HashMap<>(cfs.getProperties());
                    cfsProps.put("transactionId", req.getFxOrderId());
                    cfs.setProperties(cfsProps);
                }
                serviceCustomRepository.save(cfs);
                log.error("------------Test Trace # 19--------------- CFS renamed/saved: " + newCfsName);
            } else {
                log.error("------------Test Trace # 20--------------- CFS not found: " + cfsName);
            }

            // 7. Update RFS (if exists)
            log.error("------------Test Trace # 21--------------- Checking RFS: " + rfsName);
            Optional<Service> rfsOpt = serviceCustomRepository.findByDiscoveredName(rfsName);
            if (rfsOpt.isPresent()) {
                Service rfs = rfsOpt.get();
                String newRfsName = rfs.getDiscoveredName() + Constants.UNDER_SCORE  + req.getOntSN();
                rfs.setDiscoveredName(newRfsName);
                serviceCustomRepository.save(rfs);
                log.error("------------Test Trace # 22--------------- RFS renamed/saved: " + newRfsName);
            } else {
                log.error("------------Test Trace # 23--------------- RFS not found: " + rfsName);
            }

            // 8. Update OLT (if exists)
            log.error("------------Test Trace # 24--------------- Checking OLT: " + req.getOltName());
            String oltName = req.getOltName();
            Optional<LogicalDevice> oltOpt = logicalDeviceRepo.findByDiscoveredName(oltName);
            if (oltOpt.isPresent()) {
                LogicalDevice olt = oltOpt.get();
                Map<String, Object> oltProps = olt.getProperties() == null ? new HashMap<>() : new HashMap<>(olt.getProperties());
                if (req.getVoipServiceTemplate() != null && !req.getVoipServiceTemplate().trim().isEmpty()) {
                    oltProps.put("voipServiceTemplate", req.getVoipServiceTemplate());
                }
                // Set pots template based on requested port
                if ("1".equals(req.getOntPort())) {
                    oltProps.put("potsTemplate1", req.getTemplateNamePots1());
                } else {
                    oltProps.put("potsTemplate2", req.getTemplateNamePots2());
                }
                oltProps.put("OperationalState", "Available");
                olt.setProperties(oltProps);
                logicalDeviceRepo.save(olt);
                log.error("------------Test Trace # 25--------------- OLT updated and saved: " + olt.getLocalName());
            } else {
                log.error("------------Test Trace # 26--------------- OLT not found: " + req.getOltName());
            }

            // 9. Retrieve CPE devices (ONT_ and CBM_ entries) - these are CPE representations
            log.error("------------Test Trace # 27--------------- Looking for CPE devices: " + cpeDeviceName + ", " + cpeDeviceOldName);
            Optional<LogicalDevice> ontCpeOpt = logicalDeviceRepo.findByDiscoveredName(cpeDeviceName);
            Optional<LogicalDevice> cbmCpeOpt = logicalDeviceRepo.findByDiscoveredName(cpeDeviceOldName);

            if (!ontCpeOpt.isPresent() || !cbmCpeOpt.isPresent()) {
                String missing = !ontCpeOpt.isPresent() ? cpeDeviceName : (!cbmCpeOpt.isPresent() ? cpeDeviceOldName : "");
                log.error("------------Test Trace # 28--------------- CPE missing: " + missing);
                // Per spec: If either device is missing → return error about ONT name in CPEDevice
                return new ChangeTechnologyVoiceResponse("404", ERROR_PREFIX + "ONT name \"" + cpeDeviceName + "\" is not found in CPEDevice",
                        Instant.now().toString(), subscriptionName, ontName);
            }

            LogicalDevice ontCpe = ontCpeOpt.get();
            LogicalDevice cbmCpe = cbmCpeOpt.get();
            log.error("------------Test Trace # 29--------------- Both CPE devices found");

            // 10. Move voice port on CPEs
            if ("1".equals(req.getOntPort())) {
                ontCpe.getProperties().put("voipPort1", req.getServiceId());
                cbmCpe.getProperties().put("voipPort1", "Available");
                log.error("------------Test Trace # 30--------------- POTS1 assigned to ONT and freed on CBM");
            } else {
                ontCpe.getProperties().put("voipPort2", req.getServiceId());
                cbmCpe.getProperties().put("voipPort2", "Available");
                log.error("------------Test Trace # 31--------------- POTS2 assigned to ONT and freed on CBM");
            }
            logicalDeviceRepo.save(ontCpe);
            logicalDeviceRepo.save(cbmCpe);
            log.error("------------Test Trace # 32--------------- CPE devices saved");

            // 11. Update ONT device (logical device, ONT object)
            log.error("------------Test Trace # 33--------------- Looking for ONT device: " + ontName);
            Optional<LogicalDevice> ontDeviceOpt = logicalDeviceRepo.findByDiscoveredName(ontName);
            if (ontDeviceOpt.isPresent()) {
                LogicalDevice ontDevice = ontDeviceOpt.get();
                Map<String, Object> ontProps = ontDevice.getProperties() == null ? new HashMap<>() : new HashMap<>(ontDevice.getProperties());
                if ("1".equals(req.getOntPort())) {
                    ontProps.put("potsPort1Number", req.getServiceId());
                } else {
                    ontProps.put("potsPort2Number", req.getServiceId());
                }
                ontProps.put("OperationalState", "Available");
                // set other ONT attributes if needed
                ontDevice.setProperties(ontProps);

                // attach RFS (if present)
                if (rfsOpt.isPresent()) {
                    // store reference name in properties for downstream consumers
                    ontDevice.getProperties().put("linkedRFS", rfsOpt.get().getDiscoveredName());
                }
                logicalDeviceRepo.save(ontDevice);
                log.error("------------Test Trace # 34--------------- ONT device updated and saved: " + ontDevice.getLocalName());
            } else {
                log.error("------------Test Trace # 35--------------- ONT device not found: " + ontName + " (no creation per spec)");
            }

            // 12. update CBM device (if exists)
            log.error("------------Test Trace # 36--------------- Looking up CBM device: " + cbmName);
            String cbmGdn = Validations.getGlobalName(cbmName);
            Optional<LogicalDevice> cbmDeviceOpt = logicalDeviceRepo.findByDiscoveredName(cbmName);
            if (cbmDeviceOpt.isPresent()) {
                LogicalDevice cbmDevice = cbmDeviceOpt.get();
                // clear RFS association (store null or remove prop)
                Map<String, Object> cbmProps = cbmDevice.getProperties() == null ? new HashMap<>() : new HashMap<>(cbmDevice.getProperties());
                cbmProps.put("AdministrativeState", "Available");
                cbmProps.put("OperationalState", "Available");
                cbmDevice.setProperties(cbmProps);
                // Per spec: delete CBM device from inventory
                try {
                    logicalDeviceRepo.save(cbmDevice);
                    logicalDeviceRepo.delete(cbmDevice);
                    log.error("------------Test Trace # 37--------------- CBM device deleted: " + cbmDevice.getLocalName());
                } catch (Exception e) {
                    log.error("------------Test Trace # 38--------------- CBM updation failed, saved as Available");
                }
            } else {
                log.error("------------Test Trace # 39--------------- CBM device not found: " + cbmName);
            }

            // 13. Persist any pending RFS (already saved earlier if renamed) and final flush - repository implementations typically auto-flush on save

            log.error("------------Test Trace # 40--------------- ChangeTechnologyVoice finished successfully");
            log.error(Constants.ACTION_COMPLETED);
            // Return success
            return new ChangeTechnologyVoiceResponse("200",
                    "UIV action ChangeTechnologyVoice executed successfully.",
                    Instant.now().toString(),
                    subscriptionName,
                    ontName);

        } catch (Exception ex) {
            log.error("Unhandled exception in ChangeTechnologyVoice", ex);
            log.error("------------Test Trace # 99--------------- Exception: " + ex.getMessage());
            return new ChangeTechnologyVoiceResponse("500",
                    ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage(),
                    Instant.now().toString(),
                    "",
                    "");
        }
    }
}
