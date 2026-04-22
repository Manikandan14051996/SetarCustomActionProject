package com.nokia.nsw.uiv.action;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.exception.InternalServerErrorException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.service.*;
import com.nokia.nsw.uiv.repository.CustomerCustomRepository;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.repository.CustomerCustomRepository;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.model.resource.logical.LogicalComponent;
import com.nokia.nsw.uiv.model.resource.logical.LogicalComponentRepository;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.request.DeleteCBMRequest;
import com.nokia.nsw.uiv.response.DeleteCBMResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@Action
@Slf4j
public class DeleteCBM implements HttpAction {
    private static final String ERROR_PREFIX = "UIV action DeleteCBM execution failed - ";
    private static final String ACTION_LABEL = Constants.DELETE_CBM;

    @Autowired
    private LogicalDeviceCustomRepository cbmDeviceRepository;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;

    @Autowired
    private ProductCustomRepository productRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private CustomerCustomRepository subscriberRepository;

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Override
    public Class getActionClass() {
        return DeleteCBMRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error("Executing action {}", ACTION_LABEL);

        DeleteCBMRequest request = (DeleteCBMRequest) actionContext.getObject();

        // 1. Validate mandatory params (including CBM_SN)
        try {
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
            Validations.validateMandatoryParams(request.getSubscriberName(), "subscriberName");
            Validations.validateMandatoryParams(request.getProductType(), "productType");
            Validations.validateMandatoryParams(request.getProductSubtype(), "productSubtype");
            Validations.validateMandatoryParams(request.getServiceId(), "serviceId");
            Validations.validateMandatoryParams(request.getCbmSN(), "cbmSN");
            Validations.validateMandatoryParams(request.getServiceFlag(), "serviceFlag");
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);// <-- added
            // serviceFlag was previously validated in your code; it's optional in spec — validate only if required.
        } catch (BadRequestException bre) {
            return ResponseEntity.status(400).body(new DeleteCBMResponse("400", ERROR_PREFIX  + bre.getMessage(),
                    DateTimeUtil.now(), "", ""));
        }

        // 2. Construct names
        String subscriptionName = request.getSubscriberName() + Constants.UNDER_SCORE + request.getServiceId();
        String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
        String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
        String productName = request.getSubscriberName() + Constants.UNDER_SCORE + request.getProductSubtype() + Constants.UNDER_SCORE + request.getServiceId();
        String cbmName = "CBM" + request.getCbmSN();
        String subscriberName = request.getSubscriberName();

        // 6. Validate CBM name length early
        if (cbmName.length() > 100) {
            return ResponseEntity.status(400).body( new DeleteCBMResponse("400", ERROR_PREFIX + "CBM name too long",
                    DateTimeUtil.now(), cbmName, subscriptionName));
        }

        try {
            Optional<LogicalDevice> optCbmDevice = Optional.empty();
            Optional<Subscription> optSubscription = Optional.empty();
            Optional<Product> optProduct = Optional.empty();
            Optional<Service> optCfs = Optional.empty();
            Optional<Service> optRfs = Optional.empty();
            int subscriptionCount = 0;


            // --- 3. Fetch CBM Device ---
            try {
                optCbmDevice = cbmDeviceRepository.findByDiscoveredName(cbmName);
                if (!optCbmDevice.isPresent()) {
                    log.error("CBM device {} not found", cbmName);
                }
            } catch (Exception e) {
                log.error("Error fetching CBM device {}", cbmName, e);
            }

            // If IPTV: direct subscriber name, else add MAC
            try {
                if ("IPTV".equalsIgnoreCase(request.getProductType())) {
                    Optional<Customer> subOpt = subscriberRepository.findByDiscoveredName(subscriberName);
                    if (subOpt.isPresent()) {
                        Customer setarSubscriber = subOpt.get();
                        if (setarSubscriber.getSubscription() != null) {
                            subscriptionCount = setarSubscriber.getSubscription().size();
                        } else {
                            subscriptionCount = 0;
                        }
                    } else if (!subOpt.isPresent()) {
                        throw new BadRequestException("Subscriber not found: " + subscriberName);
                    } else {
                        log.error("Subscriber {} not found (IPTV)", subscriberName);
                        subscriptionCount = 0;
                    }
                } else {
                    // Non-IPTV: need MAC from CBM device
                    if (!optCbmDevice.isPresent()) {
                        log.error("CBM device required to derive subscriber name for non-IPTV product but CBM not found: {}", cbmName);
                        // If CBM required, return or continue depending on business decision.
                        return ResponseEntity.status(404).body(new DeleteCBMResponse("404", ERROR_PREFIX + "No entry found for delete, CBMDevice not found: "+cbmName,
                                DateTimeUtil.now(), cbmName, subscriptionName));
                    }
                    LogicalDevice cbm = optCbmDevice.get();
                    Object macObj = cbm.getProperties() != null ? cbm.getProperties().get("macAddress") : null;
                    if (macObj == null) {
                        log.error("CBM {} has no macAddress property", cbmName);
                        return ResponseEntity.status(400).body(new DeleteCBMResponse("400", ERROR_PREFIX + "CBM missing macAddress",
                                DateTimeUtil.now(), cbmName, subscriptionName));
                    }
                    String cbmMacAddr = macObj.toString();
                    String macWithoutColons = cbmMacAddr.replaceAll(":", "");
                    String newSubscriberName = subscriberName + Constants.UNDER_SCORE + macWithoutColons;
                    if (newSubscriberName.length() > 100) {
                        return ResponseEntity.status(400).body( new DeleteCBMResponse("400", ERROR_PREFIX + "Subscriber name too long",
                                DateTimeUtil.now(), cbmName, subscriptionName));
                    }
                    Optional<Customer> subscriberOpt = subscriberRepository.findByDiscoveredName(newSubscriberName);
                    if (subscriberOpt.isPresent()) {
                        Customer setarSubscriber = subscriberOpt.get();
                        if (setarSubscriber.getSubscription() != null) {
                            subscriptionCount = setarSubscriber.getSubscription().size();
                        } else {
                            subscriptionCount = 0;
                        }
                        subscriberName = newSubscriberName; // update for later deletion if necessary
                    } else {
                        log.error("Subscriber {} not found (derived using MAC)", newSubscriberName);
                        subscriptionCount = 0;
                    }
                }
            } catch (Exception e) {
                log.error("Error while deriving subscriber or subscriptionCount", e);
            }

            // --- 4. Retrieve Associated Entities ---
            try {
                optSubscription = subscriptionRepository.findByDiscoveredName(subscriptionName);
            } catch (Exception e) {
                log.error("Error fetching subscription {}", subscriptionName, e);
            }

            try {
                optProduct = productRepository.findByDiscoveredName(productName);
            } catch (Exception e) {
                log.error("Error fetching product {}", productName, e);
            }

            try {
                optCfs = serviceCustomRepository.findByDiscoveredName(cfsName);
            } catch (Exception e) {
                log.error("Error fetching CFS {}", cfsName, e);
            }

            try {
                optRfs = serviceCustomRepository.findByDiscoveredName(rfsName);
                if (!optRfs.isPresent()) {
                    log.error("RFS {} not found; continuing but skipping RFS-specific updates", rfsName);
                }
            } catch (Exception e) {
                log.error("Error fetching RFS {}", rfsName, e);
            }
            if (!optSubscription.isPresent()
                    || !optProduct.isPresent()
                    || !optCfs.isPresent()
                    || !optRfs.isPresent()) {

                return ResponseEntity.status(404).body(new DeleteCBMResponse(
                        "404",
                        ERROR_PREFIX + "No entry found for delete",
                        DateTimeUtil.now(),
                        cbmName,
                        subscriptionName
                ));
            }


            // --- 5. CPE Device logic (Voice/Broadband) ---
            if (optSubscription.isPresent() && optCbmDevice.isPresent()) {
                Subscription subscription = optSubscription.get();
                LogicalDevice cbmDevice = optCbmDevice.get();

                // --- 5. Correct CPE Device Logic for Voice ----
                if ("Voice".equalsIgnoreCase(request.getProductSubtype())) {

                    if (!optSubscription.isPresent()) {
                        log.error("Subscription not found - cannot derive CPE device.");
                    } else {
                        Map<String, Object> subProps = subscription.getProperties();

                        if (subProps == null || subProps.get("serviceMAC") == null) {
                            log.error("Subscription missing macAddress - cannot derive CPE.");
                        } else {

                            String serviceMac = subProps.get("serviceMAC").toString();

                            String cpeDeviceName = "CBM_" + serviceMac;

                            log.error("Derived CPE device name from subscription = {}", cpeDeviceName);

                            Optional<LogicalDevice> cpeOpt =
                                    cbmDeviceRepository.findByDiscoveredName(cpeDeviceName);

                            if (!cpeOpt.isPresent()) {
                                log.error("CPE device {} not found", cpeDeviceName);
                            } else {
                                LogicalDevice cpeDevice = cpeOpt.get();
                                log.error("CPE device {} found - applying VOIP reset", cpeDeviceName);

                                resetVoipPorts(subscription, cpeDevice);
                            }
                        }
                    }
                }
                else if ("Broadband".equalsIgnoreCase(request.getProductSubtype())) {
                    // clear description
                    cbmDevice.getProperties().put("description", "");
                    // check voip ports availability - if both available set admin state available
                    try {
                        Object vp1 = cbmDevice.getProperties() != null ? cbmDevice.getProperties().get("voipPort1") : null;
                        Object vp2 = cbmDevice.getProperties() != null ? cbmDevice.getProperties().get("voipPort2") : null;
                        if ("Available".equals(vp1) && "Available".equals(vp2)) {
                            if (cbmDevice.getProperties() == null) {
                                cbmDevice.setProperties(new HashMap<>());
                            }
                            cbmDevice.getProperties().put("AdministrativeState", "Available");
                        }
                    } catch (Exception e) {
                        log.error("Error while evaluating voip ports for broadband CBM {}", cbmName, e);
                    }
                    cbmDeviceRepository.save(cbmDevice, 2);
                }
            } else {
                log.error("Subscription or CBM device not present; skipping CPE device logic.");
            }

            // --- 7. Delete CBM device if exists ---
            try {
                optCbmDevice.ifPresent(device -> {
                    try {
                        cbmDeviceRepository.delete(device);
                        log.error("Deleted CBM device {}", cbmName);
                    } catch (Exception e) {
                        log.error("Error deleting CBM device {}", cbmName, e);
                    }
                });
            } catch (Exception e) {
                log.error("Exception while attempting to delete CBM", e);
            }

            // --- 8. Reset and Update RFS-Linked Resources ---
            if (optRfs.isPresent()) {
                Service setarRFS = optRfs.get();
                if (setarRFS.getUsedResource() != null) {
                    setarRFS.getUsedResource().forEach(resource -> {
                        try {
                            String discoveredName = resource.getDiscoveredName();
                            if (discoveredName == null) {
                                return;
                            }
                            boolean isAP = discoveredName.startsWith("AP");
                            boolean isSTB = discoveredName.startsWith("STB");

                            if (isAP || isSTB) {
                                Map<String, Object> props = resource.getProperties() != null
                                        ? new HashMap<>(resource.getProperties())
                                        : new HashMap<>();
                                props.put("AdministrativeState", "Available");

                                if (isSTB) {
                                    // clear deviceGroupId for STB
                                    props.put("DeviceGroupId", "");
                                }

                                resource.setProperties(props);

                                // Persist resource - resource may be LogicalDevice
                                try {
                                    Optional<LogicalDevice> tempDeviceOpt = cbmDeviceRepository.findByDiscoveredName(resource.getDiscoveredName());
                                    LogicalDevice tempDevice = null;
                                    if(tempDeviceOpt.isPresent()){
                                        tempDevice = tempDeviceOpt.get();
                                    }
                                    if(tempDevice!=null){
                                        tempDevice.setProperties(resource.getProperties());
                                        cbmDeviceRepository.save(tempDevice, 2);
                                        log.error("Updated resource {} administrative state to Available", discoveredName);
                                    }
                                } catch (ClassCastException cce) {
                                    log.error("Resource {} is not a LogicalDevice; skipping save via cbmDeviceRepository", discoveredName);
                                } catch (Exception saveEx) {
                                    log.error("Failed to save resource {}", discoveredName, saveEx);
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error processing resource in RFS", e);
                        }
                    });
                }
                // Delete RFS after resources processed
                try {
                    serviceCustomRepository.delete(setarRFS);
                    log.error("Deleted RFS {}", rfsName);
                } catch (Exception e) {
                    log.error("Error deleting RFS {}", rfsName, e);
                }
            } else {
                log.error("No RFS to process for {}", rfsName);
            }

            // --- 9. Delete Product & Subscription & CFS (SPR objects) ---
            try {
                optProduct.ifPresent(productRepository::delete);
            } catch (Exception e) {
                log.error("Error deleting Product {}", productName, e);
            }

            try {
                optSubscription.ifPresent(subscriptionRepository::delete);
            } catch (Exception e) {
                log.error("Error deleting Subscription {}", subscriptionName, e);
            }

            try {
                optCfs.ifPresent(serviceCustomRepository::delete);
            } catch (Exception e) {
                log.error("Error deleting CFS {}", cfsName, e);
            }

            // --- 10. Delete Subscriber if last subscription ---
            if (subscriptionCount == 1) {
                try {
                    // subscriberName may have been updated for non-IPTV case
                    final String subscriberNameToDelete = subscriberName;
                    Optional<Customer> subscriberToDelete = subscriberRepository.findByDiscoveredName(subscriberNameToDelete);
                    subscriberToDelete.ifPresent(subscriber -> {
                        try {
                            subscriberRepository.delete(subscriber);
                            log.error("Deleted subscriber {}", subscriberNameToDelete);
                        } catch (Exception e) {
                            log.error("Error deleting subscriber {}", subscriberNameToDelete, e);
                        }
                    });
                } catch (Exception e) {
                    log.error("Error while attempting to delete subscriber {}", subscriberName, e);
                }
            } else {
                log.error("Not deleting subscriber {} because subscriptionCount != 1 (count={})", subscriberName, subscriptionCount);
            }
            log.error(Constants.ACTION_COMPLETED);
            // --- 11. Return success response ---
            return ResponseEntity.status(200).body( new DeleteCBMResponse("200", "CBM objects Deleted", DateTimeUtil.now(),
                    cbmName, subscriptionName));

        } catch (Exception e) {
            log.error("DeleteCBM action failed", e);
            throw new InternalServerErrorException("UIV action DeleteCBM execution failed - " + e.getMessage());
        }
    }

    private void resetVoipPorts(Subscription subscription, LogicalDevice cpeDevice) {
        try {
            if (subscription == null || cpeDevice == null) {
                log.error("resetVoipPorts called with null params");
                return;
            }

            Map<String, Object> subProps = subscription.getProperties();
            if (subProps == null) {
                log.error("Subscription has no properties");
                return;
            }

            String voipNumber = subProps.getOrDefault("voipNumber1", "").toString();
            if (voipNumber.isEmpty()) {
                log.error("Subscription has no voipNumber1");
            }
            String voipNumber2 = subProps.getOrDefault("voipNumber2", "").toString();
            if (voipNumber2.isEmpty()) {
                log.error("Subscription has no voipNumber2");
            }

            Map<String, Object> devProps = cpeDevice.getProperties();
            if (devProps == null) {
                devProps = new HashMap<>();
                cpeDevice.setProperties(devProps);
            }

            Object port1 = devProps.get("voipPort1");
            Object port2 = devProps.get("voipPort2");

            boolean updated = false;

            // Compare subscription VOIP number with CPE ports
            if (voipNumber.equals(port1)) {
                devProps.put("voipPort1", "Available");
                updated = true;
                log.error("Reset voipPort1 to Available on CPE {}", cpeDevice.getDiscoveredName());
            }

            if (voipNumber2.equals(port2)) {
                devProps.put("voipPort2", "Available");
                updated = true;
                log.error("Reset voipPort2 to Available on CPE {}", cpeDevice.getDiscoveredName());
            }

            if (updated) {
                cbmDeviceRepository.save(cpeDevice, 2);
                log.error("Saved updated CPE device {}", cpeDevice.getDiscoveredName());
            } else {
                log.error("No matching VOIP ports found on CPE {} - no reset performed",
                        cpeDevice.getDiscoveredName());
            }

        } catch (Exception e) {
            log.error("Error resetting VOIP ports for CPE device {}",
                    cpeDevice != null ? cpeDevice.getDiscoveredName() : "UNKNOWN", e);
        }
    }

}
