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
import com.nokia.nsw.uiv.request.ModifyCBMRequest;
import com.nokia.nsw.uiv.response.ModifyCBMResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

@Component
@RestController
@Action
@Slf4j
public class ModifyCBM implements HttpAction {

    protected static final String ACTION_LABEL = Constants.MODIFY_CBM;
    private static final String ERROR_PREFIX = "UIV action ModifyCBM execution failed - ";

    @Autowired
    private CustomerCustomRepository customerCustomRepository;
    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;
    @Autowired
    private ProductCustomRepository productRepository;
    @Autowired
    private LogicalDeviceCustomRepository logicalDeviceRepository;
    @Autowired
    private LogicalComponentCustomRepository logicalComponentRepository;
    @Autowired
    private LogicalInterfaceCustomRepository logicalInterfaceRepository;
    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Override
    public Class<?> getActionClass() {
        return ModifyCBMRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        ModifyCBMRequest input = (ModifyCBMRequest) actionContext.getObject();

        try {
            // 1 Mandatory validations
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatoryParams(input.getSubscriberName(), "subscriberName");
                Validations.validateMandatoryParams(input.getResourceSN(), "resourceSN");
                Validations.validateMandatoryParams(input.getProductType(), "productType");
                Validations.validateMandatoryParams(input.getProductSubtype(), "productSubtype");
                Validations.validateMandatoryParams(input.getServiceId(), "serviceId");
                Validations.validateMandatoryParams(input.getModifyType(), "modifyType");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (BadRequestException bre) {
                return new ModifyCBMResponse("400",
                        ERROR_PREFIX + bre.getMessage(),
                        getCurrentTimestamp(), "", "");
            }

            //Extract optional parameters
            String modifyParam1 = trimOrNull(input.getModifyParam1());
            String modifyParam2 = trimOrNull(input.getModifyParam2());
            String fxOrderId = trimOrNull(input.getFxOrderId());
            String cbmModelInput = trimOrNull(input.getCbmModel());

            //Derive names
            String subscriberNameDerived = deriveSubscriberName(
                    input.getProductType(),
                    input.getSubscriberName(),
                    input.getResourceSN(),
                    input.getModifyType(),
                    modifyParam1
            );

            String subscriptionName = input.getSubscriberName() + Constants.UNDER_SCORE + input.getServiceId();
            String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
            String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
            String productName = input.getSubscriberName() + Constants.UNDER_SCORE
                    + input.getProductSubtype() + Constants.UNDER_SCORE + input.getServiceId();
            String cbmDeviceName = "CBM" + input.getServiceId();

            log.error("ModifyCBM start: subscriberDerived='{}', subscriptionName='{}', cfsName='{}', rfsName='{}', productName='{}', cbmDeviceName='{}'",
                    subscriberNameDerived, subscriptionName, cfsName, rfsName, productName, cbmDeviceName);

            if (subscriptionName.length() > 100) {
                log.warn("Subscription name too long: {}", subscriptionName.length());
                return createErrorResponse("Subscription name too long", 400);
            }

            if (subscriberNameDerived.length() > 100) {
                log.warn("Subscription name too long: {}", subscriberNameDerived.length());
                return createErrorResponse("Subscriber  name too long", 400);
            }

            if (productName.length() > 100) {
                log.warn("Product name too long: {}", productName.length());
                return createErrorResponse("Product name too long", 400);
            }

            if (cbmDeviceName.length() > 100) {
                log.warn("CBM device name too long: {}", cbmDeviceName.length());
                return createErrorResponse("CBM device name too long", 400);
            }

            // Retrieve entities
            boolean skipEntities = containsAny(input.getModifyType(), "Package", "Components", "Products", "Contracts");
            Optional<Customer> subscriber = Optional.empty();

            if (!skipEntities) {
                Optional<Customer> optSub = customerCustomRepository.findByDiscoveredName(subscriberNameDerived);
                if (optSub.isPresent()) {
                    Customer customer = optSub.get();  // unwrap the Optional

                    // Check "Status" property
                    Object statusValue = customer.getProperties().get("subscriberStatus");
                    if (statusValue != null && "Active".equalsIgnoreCase(statusValue.toString())) {
                        customerCustomRepository.save(customer);
                        subscriber = Optional.of(customer);
                    } else {
                        return new ModifyCBMResponse("409", ERROR_PREFIX + "Customer not active", String.valueOf(System.currentTimeMillis()), "", "");
                    }
                } else {
                    return new ModifyCBMResponse("409", ERROR_PREFIX + "No entry found to modify Customer", String.valueOf(System.currentTimeMillis()), "", "");
                }
            }

            Optional<Subscription> optSubsc = subscriptionRepository.findByDiscoveredName(subscriptionName);
            if (optSubsc.isEmpty()) {
                return new ModifyCBMResponse("409", ERROR_PREFIX + "No entry found to modify Subscription: " + subscriptionName, String.valueOf(System.currentTimeMillis()), "", "");
            }
            Subscription subscription = optSubsc.get();

            Optional<Service> optCfs = serviceCustomRepository.findByDiscoveredName(cfsName);
            if (optCfs.isEmpty()) {
                return new ModifyCBMResponse("409", ERROR_PREFIX + "No entry found to modify CFS: " + cfsName, String.valueOf(System.currentTimeMillis()), "", "");
            }
            Service cfs = optCfs.get();

            Optional<Service> optRfs = serviceCustomRepository.findByDiscoveredName(rfsName);
            if (optRfs.isEmpty()) {
                return new ModifyCBMResponse("409", ERROR_PREFIX + "No entry found to modify RFS: " + rfsName, String.valueOf(System.currentTimeMillis()), "", "");
            }
            Service rfs = optRfs.get();

            Optional<LogicalDevice> optCbm = logicalDeviceRepository.findByDiscoveredName(cbmDeviceName);
            if (optCbm.isEmpty()) {
                return new ModifyCBMResponse("409", ERROR_PREFIX + "No entry found to modify CBM device: " + cbmDeviceName, String.valueOf(System.currentTimeMillis()), "", "");
            }
            LogicalDevice cbmDevice = optCbm.get();

            //Update RFS metadata if fxOrderId present
            if (fxOrderId != null && !fxOrderId.isEmpty()) {
                Map<String, Object> rfsProps = Optional.ofNullable(rfs.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                rfsProps.put("transactionId", fxOrderId);
                rfsProps.put("transactionType", input.getModifyType());
                rfsProps.put("endDate", getCurrentTimestamp());
                rfs.setProperties(rfsProps);
                serviceCustomRepository.save(rfs);
            }
            if (!"IPTV".equalsIgnoreCase(input.getProductType())) {
                // Update Service MAC / Gateway MAC flows (ModifyCableModem / Cable_Modem)
                if (containsAny(input.getModifyType(), "ModfiyCableModem", "Cable_Modem", "ModifyCableModem")) {

                    // subscriberWithMAC variant (subscriberName + resourceSN sanitized)
                    String subscriberWithMAC = input.getSubscriberName() + Constants.UNDER_SCORE + sanitizeForName(input.getResourceSN());
                    Optional<Customer> optSubscriberWithMac = customerCustomRepository.findByDiscoveredName(subscriberWithMAC);
                    Optional<Customer> subscriberWithMac = optSubscriberWithMac;

                    // CBM for modifyParam1 if present
                    LogicalDevice cbmForParam1 = null;
                    if (modifyParam1 != null && !modifyParam1.isEmpty()) {
                        String cbmForParam1Name = "CBM_" + modifyParam1;
                        Optional<LogicalDevice> opt = logicalDeviceRepository.findByDiscoveredName(cbmForParam1Name);
                        if (opt.isPresent()) {
                            cbmForParam1 = opt.get();
                            cbmModelInput = cbmForParam1.getProperties().get("deviceModel").toString();
                        }
                    }

                    try {
                        Map<String, Object> subProps = subscription.getProperties() == null ? new HashMap<>() : subscription.getProperties();
                        subscription=subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
                        String svcMac = subProps.get("serviceMAC") != null ? subProps.get("serviceMAC").toString() : "";

                        // when serviceMAC equals input.resourceSN -> update the "current" CBM
                        if (svcMac != null && svcMac.equalsIgnoreCase(input.getResourceSN())) {
                            String subType = subProps.get("serviceSubType") != null ? subProps.get("serviceSubType").toString() : "";
                            if ("IPTV".equalsIgnoreCase(subType)) {
                                // IPTV special-case
                                if (modifyParam1 != null && !modifyParam1.isEmpty()) {
                                    subProps.put("serviceMAC", modifyParam1);
                                    cbmDevice.getProperties().put("macAddress", modifyParam1);
                                    if (cbmModelInput != null)
                                        cbmDevice.getProperties().put("deviceModel", cbmModelInput);
                                }
                                if (modifyParam2 != null && !modifyParam2.isEmpty()) {
                                    subProps.put("gatewayMAC", modifyParam2);
                                    cbmDevice.getProperties().put("gatewayMAC", modifyParam2);
                                }
                                subscription.setProperties(subProps);
                                subscriptionRepository.save(subscription);
                            } else {
                                // serviceMAC mismatch: try to find CBM by serviceID (from subscription) and update that device
                                String serviceID = (String) subscription.getProperties().getOrDefault("serviceID", "");
                                String newCBMName = "CBM" + serviceID;
                                Optional<LogicalDevice> optNewCbm = logicalDeviceRepository.findByDiscoveredName(newCBMName);
                                Map<String, Object> dProps = Optional.ofNullable(subscription.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                                if (optNewCbm.isPresent()) {
                                    LogicalDevice newCBM = optNewCbm.get();
                                    Map<String, Object> sProps = Optional.ofNullable(subscription.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                                    if (modifyParam1 != null && !modifyParam1.isEmpty()) {
                                        sProps.put("serviceMAC", modifyParam1);
                                        dProps.put("macAddress", modifyParam1);
                                        if (cbmModelInput != null) dProps.put("deviceModel", cbmModelInput);
                                    }
                                    if (modifyParam2 != null && !modifyParam2.isEmpty()) {
                                        sProps.put("gatewayMAC", modifyParam2);
                                        dProps.put("gatewayMAC", modifyParam2);
                                    }
                                    // Voice subtype handling
                                    String sType = (String) sProps.getOrDefault("serviceSubType", "");
                                    if ("Voice".equalsIgnoreCase(sType) && modifyParam1 != null && !modifyParam1.isEmpty()) {
                                        sProps.put("serviceSN", modifyParam1);
                                    }
                                    subscription.setProperties(sProps);
                                    subscriptionRepository.save(subscription);
                                    newCBM = logicalDeviceRepository.findByDiscoveredName(cbmDeviceName).get();
                                    newCBM.setProperties(dProps);
                                    logicalDeviceRepository.save(newCBM);

                                    // update subscriber name replace resourceSN with modifyParam1
                                    if (modifyParam1 != null && !modifyParam1.isEmpty() && subscriber.isPresent()) {
                                        Customer customer = subscriber.get();
                                        Customer subscriberObj = customerCustomRepository.findByDiscoveredName(customer.getDiscoveredName()).get();
                                        String newSubscriberName = customer.getDiscoveredName().replace(input.getResourceSN().replaceAll(":", ""), input.getModifyParam1().replaceAll(":", ""));
                                        subscriberObj.setDiscoveredName(newSubscriberName);
                                        Map<String, Object> custProps = Optional.ofNullable(subscriberObj.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                                        custProps.put("name", newSubscriberName);
                                        subscriberObj.setProperties(custProps);
                                        customerCustomRepository.save(subscriberObj);
                                    }
                                } else {
                                    // no matching CBM found by serviceID - nothing to change
                                    log.error("no matching CBM found by serviceID - nothing to change");
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.error("Error updating MAC/Gateway", ex);
                        String msg = ERROR_PREFIX + "Error, ModifyCableModem request " + input.getModifyType() + " not executed";
                        return new ModifyCBMResponse("500", msg + " - " + ex.getMessage(), String.valueOf(Instant.now().toEpochMilli()), "", "");
                    }
                }

                //Migrate Broadband Port Assignments
                if ("Broadband".equalsIgnoreCase(input.getProductType()) && modifyParam1 != null && !modifyParam1.isEmpty()) {
                    try {
                        String oldCbmName = "CBM_" + sanitizeForName(input.getResourceSN());
                        String newCbmName = "CBM_" + sanitizeForName(modifyParam1);

                        Optional<LogicalDevice> optOldCbm = logicalDeviceRepository.findByDiscoveredName(oldCbmName);
                        Optional<LogicalDevice> optNewCbm = logicalDeviceRepository.findByDiscoveredName(newCbmName);

                        if (optOldCbm.isPresent() && optNewCbm.isPresent()) {
                            LogicalDevice oldCbm = optOldCbm.get();
                            LogicalDevice newCbm = optNewCbm.get();

                            Map<String, Object> oldProps = Optional.ofNullable(oldCbm.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                            Map<String, Object> newProps = Optional.ofNullable(newCbm.getProperties()).map(HashMap::new).orElse(new HashMap<>());

                            // Copy VOIP_PORT1, VOIP_PORT2 if present
                            if (oldProps.containsKey("voipPort1")) newProps.put("voipPort1", oldProps.get("voipPort1"));
                            if (oldProps.containsKey("voipPort2")) newProps.put("voipPort2", oldProps.get("voipPort2"));

                            // Set new CBM fields
                            newProps.put("AdministrativeState", "Allocated");
                            newProps.put("description", "Internet");
                            newProps.put("modelSubtype", "HFC");
                            String voip1 = (String) newProps.getOrDefault("voipPort1", "Available");
                            String voip2 = (String) newProps.getOrDefault("voipPort2", "Available");
                            newProps.put("voipPorts", voip1 + "," + voip2);

                            newCbm.setProperties(newProps);
                            logicalDeviceRepository.save(newCbm);

                            // Reset old CBM
                            oldProps.put("AdministrativeState", "Available");
                            oldProps.put("description", "");
                            oldProps.put("modelSubtype", "");
                            oldProps.put("voipPorts", "Available");
                            oldCbm.setProperties(oldProps);
                            logicalDeviceRepository.save(oldCbm);
                        }
                    } catch (Exception ex) {
                        log.error("Error migrating broadband ports", ex);
                        String msg = ERROR_PREFIX + "Error while migrating broadband ports";
                        return new ModifyCBMResponse("500", msg + " - " + ex.getMessage(), String.valueOf(Instant.now().toEpochMilli()), "", "");
                    }
                }

                //Modify Profile, Package, or Components
                if (containsAny(input.getModifyType(), "Package", "Components", "Products", "Contracts")) {
                    try {
                        subscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
                        Map<String, Object> sProps = Optional.ofNullable(subscription.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                        if (modifyParam1 != null && !modifyParam1.isEmpty()) {
                            sProps.put("veipQosSessionProfile", modifyParam1);
                            sProps.put("servicePackage", modifyParam1);
                            sProps.put("voipPackage1", modifyParam1);
                        }
                        subscription.setProperties(sProps);
                        subscriptionRepository.save(subscription);
                    } catch (Exception ex) {
                        log.error("Error updating package/components", ex);
                        String msg = ERROR_PREFIX + "Error updating package/components";
                        return new ModifyCBMResponse("500", msg + " - " + ex.getMessage(), String.valueOf(Instant.now().toEpochMilli()), "", "");
                    }
                }

                //Update Email Password
                if (input.getModifyType() != null && input.getModifyType().contains("Password")) {
                    try {
                        // fallback subscriber retrieval if empty
                        if (!subscriber.isPresent()) {
                            Optional<Customer> alt = customerCustomRepository.findByDiscoveredName(input.getSubscriberName());
                            subscriber = alt;
                        }
                        if (subscriber.isPresent()) {
                            Customer customer = subscriber.get();
                            Map<String, Object> custProps = Optional.ofNullable(customer.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                            custProps.put("email_pwd", modifyParam1 != null ? modifyParam1 : "");
                            customer.setProperties(custProps);
                            customerCustomRepository.save(customer);
                        } else {
                            String msg = ERROR_PREFIX + "Subscriber not found for password update";
                            return new ModifyCBMResponse("409", msg, String.valueOf(Instant.now().toEpochMilli()), "", "");
                        }
                    } catch (Exception ex) {
                        log.error("Error updating email password", ex);
                        String msg = ERROR_PREFIX + "Error updating email password";
                        return new ModifyCBMResponse("500", msg + " - " + ex.getMessage(), String.valueOf(Instant.now().toEpochMilli()), "", "");
                    }
                }

                // Modify Service ID and VOIP Number and Rename entities if serviceID changed
                if (input.getModifyType() != null && input.getModifyType().contains("Modify_Number")) {
                    try {
                        subscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
                        String newServiceId = modifyParam1;
                        if (newServiceId != null && !newServiceId.trim().isEmpty()) {
                            // Update subscription fields: serviceID, voipNumber1
                            Map<String, Object> sProps = Optional.ofNullable(subscription.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                            sProps.put("serviceID", newServiceId);
                            sProps.put("voipNumber1", newServiceId);
                            subscription.setProperties(sProps);

                            // If service ID changed, reconstruct names
                            if (!input.getServiceId().equals(newServiceId)) {
                                String subscriptionNameNew = input.getSubscriberName() + Constants.UNDER_SCORE + newServiceId;
                                String cfsNameNew = "CFS" + Constants.UNDER_SCORE + subscriptionNameNew;
                                String rfsNameNew = "RFS" + Constants.UNDER_SCORE + subscriptionNameNew;
                                String productNameNew = input.getSubscriberName() + Constants.UNDER_SCORE + input.getProductSubtype() + Constants.UNDER_SCORE + newServiceId;
                                String cbmDeviceNameNew = "CBM" + newServiceId;

                                // rename subscription
                                subscription.setDiscoveredName(subscriptionNameNew);
                                subscriptionRepository.save(subscription);

                                // rename product if exists
                                Optional<Product> optProduct = productRepository.findByDiscoveredName(productName);
                                if (optProduct.isPresent()) {
                                    Product prod = optProduct.get();
                                    prod.setDiscoveredName(productNameNew);
                                    Map<String, Object> pProps = Optional.ofNullable(prod.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                                    pProps.put("name", productNameNew);
                                    prod.setProperties(pProps);
                                    productRepository.save(prod);
                                }

                                // rename CFS
                                if (cfs != null) {
                                    cfs = serviceCustomRepository.findByDiscoveredName(cfs.getDiscoveredName()).get();
                                    cfs.setDiscoveredName(cfsNameNew);
                                    Map<String, Object> cfsProps = Optional.ofNullable(cfs.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                                    cfsProps.put("name", cfsNameNew);
                                    cfsProps.put("endDate", Instant.now().toString());
                                    cfs.setProperties(cfsProps);
                                    serviceCustomRepository.save(cfs);
                                }

                                // rename RFS
                                if (rfs != null) {
                                    rfs = serviceCustomRepository.findByDiscoveredName(rfs.getDiscoveredName()).get();
                                    rfs.setDiscoveredName(rfsNameNew);
                                    Map<String, Object> rfsProps = Optional.ofNullable(rfs.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                                    rfsProps.put("name", rfsNameNew);
                                    if (fxOrderId != null) rfsProps.put("transactionId", fxOrderId);
                                    rfs.setProperties(rfsProps);
                                    serviceCustomRepository.save(rfs);
                                }

                                // rename CBM device if found by old name
                                Optional<LogicalDevice> optOldCbmDevice = logicalDeviceRepository.findByDiscoveredName(cbmDeviceName);
                                if (optOldCbmDevice.isPresent()) {
                                    LogicalDevice oldCbmDevice = optOldCbmDevice.get();
                                    oldCbmDevice.setDiscoveredName(cbmDeviceNameNew);
                                    Map<String, Object> cbmProps = Optional.ofNullable(oldCbmDevice.getProperties()).map(HashMap::new).orElse(new HashMap<>());
                                    cbmProps.put("name", cbmDeviceNameNew);
                                    oldCbmDevice.setProperties(cbmProps);
                                    logicalDeviceRepository.save(oldCbmDevice);

                                    // Update VOIP_PORT1 if it contains old service id
                                    if (cbmProps.containsKey("voipPort1")) {
                                        String port = String.valueOf(cbmProps.get("voipPort1"));
                                        if (port != null && port.contains(input.getServiceId())) {
                                            cbmProps.put("voipPort1", newServiceId);
                                            oldCbmDevice.setProperties(cbmProps);
                                            logicalDeviceRepository.save(oldCbmDevice);
                                        }
                                    }
                                    // Add missing VOIP_PORT2 update
                                    if (cbmProps.containsKey("voipPort2")) {
                                        String port2 = String.valueOf(cbmProps.get("voipPort2"));
                                        if (port2 != null && port2.contains(input.getServiceId())) {
                                            cbmProps.put("voipPort2", newServiceId);
                                            oldCbmDevice.setProperties(cbmProps);
                                            logicalDeviceRepository.save(oldCbmDevice);
                                        }
                                    }

                                }
                            } else {
                                subscriptionRepository.save(subscription);
                            }
                        }
                    } catch (Exception ex) {
                        log.error("Error modifying service ID", ex);
                        String msg = ERROR_PREFIX + "Error modifying service ID";
                        return new ModifyCBMResponse("500", msg + " - " + ex.getMessage(), String.valueOf(Instant.now().toEpochMilli()), "", "");
                    }
                }
            }

            // 11. Final Response (success)
            String outSubscriberId = subscriber
                    .flatMap(c -> customerCustomRepository.findById(c.getUuid()))
                    .map(c -> c.getDiscoveredName().toString())
                    .orElse(input.getSubscriberName());

            Subscription updatedSubscription = subscriptionRepository.findById(subscription.getUuid())
                    .orElse(subscription);
            String outSubscriptionId = updatedSubscription.getDiscoveredName();


            return new ModifyCBMResponse("200",
                    "UIV action ModifyCBM executed successfully.",
                    Instant.now().toString(),
                    outSubscriberId,
                    outSubscriptionId
            );

        } catch (Exception ex) {
            log.error("Unhandled exception during ModifyCBM", ex);
            String msg = ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage();
            return new ModifyCBMResponse("500", msg, Instant.now().toString(), "", "");
        }
    }

    private ModifyCBMResponse createErrorResponse(String message, int status) {
        return new ModifyCBMResponse(
                String.valueOf(status),
                ERROR_PREFIX + message,
                Instant.now().toString(),
                "",
                ""
        );
    }


    // --- helper utilities ---
    private boolean containsAny(String source, String... toks) {
        if (source == null) return false;
        for (String t : toks) if (source.contains(t)) return true;
        return false;
    }

    private String removeColons(String s) {
        return (s == null) ? "" : s.replace(":", "").replace("-", "").trim();
    }

    // sanitize for names: remove colons and hyphens
    private String sanitizeForName(String s) {
        return removeColons(s);
    }

    private String deriveSubscriberName(String productType, String origSubscriber, String resourceSN,
                                        String modifyType, String modifyParam1) {
        if ("IPTV".equalsIgnoreCase(productType)) {
            return origSubscriber;
        } else if (resourceSN == null || resourceSN.trim().isEmpty() || "NA".equalsIgnoreCase(resourceSN)) {
            if (containsAny(modifyType, "Package", "Components", "Products", "Contracts")) {
                return origSubscriber;
            } else {
                return (modifyParam1 != null && !modifyParam1.isEmpty())
                        ? origSubscriber + Constants.UNDER_SCORE + removeColons(modifyParam1)
                        : origSubscriber;
            }
        } else {
            return origSubscriber + Constants.UNDER_SCORE + removeColons(resourceSN);
        }
    }

    private String trimOrNull(String s) {
        return (s == null) ? null : (s.trim().isEmpty() ? null : s.trim());
    }

    private String getCurrentTimestamp() {
        return Instant.now().toString();
    }
}
