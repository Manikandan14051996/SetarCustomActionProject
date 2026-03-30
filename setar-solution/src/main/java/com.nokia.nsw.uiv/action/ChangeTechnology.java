package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.AccessForbiddenException;
import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.exception.ModificationNotAllowedException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.AdministrativeState;
import com.nokia.nsw.uiv.model.resource.OperationalState;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterface;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterfaceRepository;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.AssociateResourcesRequest;
import com.nokia.nsw.uiv.request.ChangeTechnologyRequest;
import com.nokia.nsw.uiv.response.ChangeTechnologyResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;

import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.recycler.Recycler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@RestController
@Action
@Slf4j
public class ChangeTechnology implements HttpAction {
    protected static final String ACTION_LABEL = Constants.CHANGE_TECHNOLOGY;
    private static final String ERROR_PREFIX = "UIV action ChangeTechnology execution failed - ";

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

    @Autowired
    private LogicalDeviceCustomRepository cpeRepo;

    @Autowired
    private LogicalInterfaceCustomRepository vlanRepo;

    @Autowired
    private LogicalDeviceCustomRepository cbmRepo;


    @Override
    public Class<?> getActionClass() { return ChangeTechnologyRequest.class; }

    private void ensureProperty(
            Map<String, Object> props,
            String key,
            String value
    ) {
        if (!props.containsKey(key) || props.get(key) == null) {
            props.put(key, value);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("Executing ChangeTechnology action...");
        ChangeTechnologyRequest req = (ChangeTechnologyRequest) actionContext.getObject();

        try {
            // Declare variables from req
            String subscriberName = req.getSubscriberName();
            String productSubtype = req.getProductSubtype();
            String serviceId = req.getServiceId();
            String fxOrderId = req.getFxOrderId();
            String ontSN = req.getOntSN();
            String ontMacAddr = req.getOntMacAddr();
            String cbmSn = req.getCbmSn();
            String qosProfile = req.getQosProfile();
            String oltName = req.getOltName();
            String templateNameOnt = req.getTemplateNameOnt();
            String templateNameVeip = req.getTemplateNameVeip();
            String templateNameHsi = req.getTemplateNameHsi();
            String templateNameIptv = req.getTemplateNameIptv();
            String templateNameIgmp = req.getTemplateNameIgmp();
            String menm = req.getMenm();
            String vlanId = req.getVlanId();
            String ontModel = req.getOntModel();
            String cbmMac = req.getCbmMac();
            String hhid = req.getHhid();

// Validate mandatory parameters
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatoryParams(subscriberName, "subscriberName");
                Validations.validateMandatoryParams(productSubtype, "productSubtype");
                Validations.validateMandatoryParams(serviceId, "serviceId");
                Validations.validateMandatoryParams(ontSN, "ontSN");
                Validations.validateMandatoryParams(ontMacAddr, "ontMacAddr");
                Validations.validateMandatoryParams(cbmSn, "cbmSn");
                Validations.validateMandatoryParams(oltName, "oltName");
                Validations.validateMandatoryParams(menm, "menm");
                Validations.validateMandatoryParams(vlanId, "vlanId");
                Validations.validateMandatoryParams(ontModel, "ontModel");
                Validations.validateMandatoryParams(cbmMac, "cbmMac");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (BadRequestException bre) {
                return new ChangeTechnologyResponse(
                        "400",
                        ERROR_PREFIX + bre.getMessage(),
                        Instant.now().toString(),
                        "",
                        ""
                );
            }


            // 2. Prepare states - in UIV we typically store as properties; if there are dedicated entities, repo lookups would be used.
            // Legacy ensures existence of SubscriberStatus/SubscriberType/Admin/Operational state entities.
            // In UIV mapped model we set properties accordingly (create missing objects handled by repositories when needed).

            // 3. Prepare names
            String subscriptionName = subscriberName + Constants.UNDER_SCORE + serviceId;
            String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
            String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
            String cbmName = "CBM"+ cbmSn;
            String mgmtVlanName = menm + Constants.UNDER_SCORE + vlanId;
            String ontName = "ONT" + ontSN;
            String subscriberNameFibernet = subscriberName + Constants.UNDER_SCORE + ontSN;
            String subscriberNameCbmKey = subscriberName + Constants.UNDER_SCORE + cbmMac.replace(":", "");

            // 4. Update existing subscriber (only when productSubtype == Fibernet)
            if ("Fibernet".equalsIgnoreCase(productSubtype)) {
                if (subscriberNameFibernet.length() > 100) {
                    return new ChangeTechnologyResponse("400", ERROR_PREFIX + "Subscriber name too long", Instant.now().toString(), subscriptionName, ontName);
                }
                // Try find CBM-keyed subscriber
                Optional<Customer> maybeCbmSubscriber = customerRepo.findByDiscoveredName(subscriberNameCbmKey);
                if (maybeCbmSubscriber.isPresent()) {
                    Customer cbmSubscriber = maybeCbmSubscriber.get();
                    cbmSubscriber.setDiscoveredName(subscriberNameFibernet);
                    cbmSubscriber.setContext("Setar");
                    cbmSubscriber.setKind("SetarSubscriber");
                    Map<String, Object> props = cbmSubscriber.getProperties() != null ? cbmSubscriber.getProperties() : new HashMap<>();

                    ensureProperty(props,
                            "subscriberStatus",
                            Constants.SUBSCRIBER_STATUS_ACTIVE);

                    ensureProperty(props,
                            "subscriberType",
                            Constants.SUBSCRIBER_TYPE_REGULAR);

                    props.put("accountNumber", subscriberName);
                    if (hhid != null) props.put("houseHoldId", hhid);
                    cbmSubscriber.setProperties(props);
                    customerRepo.save(cbmSubscriber);
                }
            }
            if (subscriptionName.length() > 100) {
                return new ChangeTechnologyResponse("400", ERROR_PREFIX + "Subscription name too long", Instant.now().toString(), subscriptionName, "");
            }
            // 5. Update existing subscription (if exists)
            Optional<Subscription> maybeSubscription = subscriptionRepo.findByDiscoveredName(subscriptionName);
            Subscription subscription = null;
            if (maybeSubscription.isPresent()) {
                subscription = maybeSubscription.get();
                Map<String, Object> subProps = subscription.getProperties() != null ? subscription.getProperties() : new HashMap<>();
                subProps.put("serviceLink", "ONT");
                subProps.put("serviceMAC", ontMacAddr);
                subProps.put("serviceSN", ontSN);
                subProps.put("serviceSubType", "Broadband");
                if ("Fibernet".equalsIgnoreCase(productSubtype)) {
                    if (qosProfile != null) subProps.put("veipQosSessionProfile", qosProfile);
                    subscription.setDiscoveredName(subscriptionName + Constants.UNDER_SCORE + ontSN);

                    // link to subscriber updated earlier if present
                    Optional<Customer> maybeSub = customerRepo.findByDiscoveredName(subscriberNameFibernet);
                    if (maybeSub.isPresent()) {
                        Customer newCustomer = maybeSub.get();
                        Customer oldCustomer = subscription.getCustomer();
                        if (oldCustomer != null) {
                            oldCustomer.getSubscription().remove(subscription);   // 💥 removes HAS edge
                            customerRepo.save(oldCustomer);
                        }

                        subscription.setCustomer(newCustomer);  // ✅ attach new
                    }

                }
                subscription.setProperties(subProps);
                subscriptionRepo.save(subscription);
            }

            // 6. Update existing CFS (if exists)
            Optional<Service> maybeCfs = serviceCustomRepository.findByDiscoveredName(cfsName);
            if (maybeCfs.isPresent() && "Fibernet".equalsIgnoreCase(productSubtype)) {
                Service cfs = maybeCfs.get();
                cfs.setDiscoveredName(cfs.getDiscoveredName() + Constants.UNDER_SCORE + ontSN);
                if (fxOrderId != null) {
                    Map<String, Object> p = cfs.getProperties() != null ? cfs.getProperties() : new HashMap<>();
                    cfs.getProperties().put("transactionId", fxOrderId);
                }
                serviceCustomRepository.save(cfs, 2);
            }

            // 7. Update existing RFS (if exists)
            Optional<Service> maybeRfs = serviceCustomRepository.findByDiscoveredName(rfsName);
            if (maybeRfs.isPresent() && "Fibernet".equalsIgnoreCase(productSubtype)) {
                Service rfs = maybeRfs.get();
                // Append ONT_SN to existing RFS name as per specification
                rfs.setDiscoveredName(rfs.getDiscoveredName() + Constants.UNDER_SCORE + ontSN);
                serviceCustomRepository.save(rfs, 2);
            }

            // 8. Prepare OLT device (create if missing)
            LogicalDevice olt = logicalDeviceRepo.findByDiscoveredName(oltName)
                    .orElseGet(() -> {
                        LogicalDevice d = new LogicalDevice();
                        try {
                            d.setLocalName(Validations.encryptName(oltName));
                            d.setDiscoveredName(oltName);
                            d.setContext("Setar");
                            d.setKind("OLTDevice");
                        } catch (AccessForbiddenException e) {
                            throw new RuntimeException(e);
                        } catch (BadRequestException e) {
                            throw new RuntimeException(e);
                        } catch (ModificationNotAllowedException e) {
                            throw new RuntimeException(e);
                        }
                        Map<String, Object> p = new HashMap<>();
                        p.put("OperationalState", "Active");
                        p.put("oltPosition", oltName);
                        if (templateNameOnt != null) p.put("ontTemplate", templateNameOnt);
                        if (templateNameVeip != null) p.put("veipServiceTemplate", templateNameVeip);
                        if (templateNameIptv != null) p.put("veipIptvTemplate", templateNameIptv);
                        if (templateNameHsi != null) p.put("veipHsiTemplate", templateNameHsi);
                        if (templateNameIgmp != null) p.put("igmpTemplate", templateNameIgmp);
                        d.setProperties(p);
                        // if RFS exists link to it
                        maybeRfs.ifPresent(rfs -> d.setUsingService(new HashSet<>(List.of(rfs))));
                        return logicalDeviceRepo.save(d);
                    });

            // validate ont name length
            if (ontName.length() > 100) {
                return new ChangeTechnologyResponse("400", ERROR_PREFIX + "ONT name too long", Instant.now().toString(), "", "");
            }

            // 9. Prepare ONT device (create if missing)
            LogicalDevice ont = logicalDeviceRepo.findByDiscoveredName(ontName)
                    .orElseGet(() -> {
                        LogicalDevice d = new LogicalDevice();
                        try {
                            d.setLocalName(Validations.encryptName(ontName));
                            d.setDiscoveredName(ontName);
                            d.setContext("Setar");
                            d.setKind("ONTDevice");
                        } catch (AccessForbiddenException e) {
                            throw new RuntimeException(e);
                        } catch (BadRequestException e) {
                            throw new RuntimeException(e);
                        } catch (ModificationNotAllowedException e) {
                            throw new RuntimeException(e);
                        }
                        Map<String, Object> p = new HashMap<>();
                        p.put("OperationalState", "Active");
                        p.put("serialNo", ontSN);
                        p.put("deviceModel", ontModel);
                        p.put("description", menm);
                        p.put("veipVlan", vlanId);
                        p.put("iptvVlan", vlanId);
                        p.put("oltPosition", oltName);
                        if (templateNameOnt != null) p.put("ontTemplate", templateNameOnt);
                        d.setProperties(p);
                        // link containing logical device (OLT)
                        d.addManagingDevices(olt);
                        // link rfs if present
                        maybeRfs.ifPresent(rfs -> d.setUsingService(new HashSet<>(List.of(rfs))));
                        return logicalDeviceRepo.save(d);
                    });

            if (mgmtVlanName.length() > 100) {
                return new ChangeTechnologyResponse("400", ERROR_PREFIX + "Vlan name too long", Instant.now().toString(), "", "");
            }
            // 10. Create or retrieve management VLAN interface
            vlanRepo.findByDiscoveredName(mgmtVlanName).orElseGet(() -> {
                LogicalInterface v = new LogicalInterface();
                try {
                    v.setLocalName(Validations.encryptName(mgmtVlanName));
                    v.setDiscoveredName(mgmtVlanName);
                    v.setContext("Setar");
                    v.setKind("VLANInterface");
                } catch (AccessForbiddenException e) {
                    throw new RuntimeException(e);
                } catch (BadRequestException e) {
                    throw new RuntimeException(e);
                } catch (ModificationNotAllowedException e) {
                    throw new RuntimeException(e);
                }

                Map<String, Object> p = new HashMap<>();
                p.put("vlanId", vlanId);
                p.put("OperationalState", "Active");
                v.setProperties(p);
                return vlanRepo.save(v);
            });

            // 11. Remove CBM device if exists
            Optional<LogicalDevice> maybeCbm = cbmRepo.findByDiscoveredName(cbmName);
            if (maybeCbm.isPresent()) {
                LogicalDevice cbmDevice = maybeCbm.get();
                // Clear Resource-Facing Service link as per specification
                cbmDevice.setUsingService(null);
                Map<String, Object> cbmProps = cbmDevice.getProperties() != null ? cbmDevice.getProperties() : new HashMap<>();
                ensureProperty(cbmProps,
                        "AdministrativeState",
                        Constants.ADMIN_STATE_AVAILABLE);

                ensureProperty(cbmProps,
                        "OperationalState",
                        Constants.OPER_STATE_AVAILABLE);

                cbmDevice.setProperties(cbmProps);
                // remove from inventory
                cbmRepo.delete(cbmDevice);
            }

            // 12. Reassign CPE devices
            // 12. Reassign CPE devices (ONT <-> CBM)

// Expected names as per specification
            String cpeDeviceName = "ONT_" + ontSN;
            String cpeDeviceOldName = "CBM_" + cbmMac.replace(":", "");

// Fetch ONT CPE
            Optional<LogicalDevice> maybeCpeNew =
                    cpeRepo.findByDiscoveredName(cpeDeviceName);

// Fetch CBM CPE
            Optional<LogicalDevice> maybeCpeOld =
                    cpeRepo.findByDiscoveredName("CBM_" + cbmMac.replace(":", ""));


// Validate ONT CPE existence
            if (maybeCpeNew.isEmpty()) {
                return new ChangeTechnologyResponse(
                        "500",
                        ERROR_PREFIX + "ONT name \"" + ontName + "\" is not found in CPEDevice",
                        Instant.now().toString(),
                        "",
                        ""
                );
            }

// Validate CBM CPE existence
            if (maybeCpeOld.isEmpty()) {
                return new ChangeTechnologyResponse(
                        "500",
                        ERROR_PREFIX + "CBM device \"" + cpeDeviceOldName + "\" is not found in CPEDevice",
                        Instant.now().toString(),
                        "",
                        ""
                );
            }

// Retrieve devices
            LogicalDevice cpeNew = maybeCpeNew.get(); // ONT CPE
            LogicalDevice cpeOld = maybeCpeOld.get(); // CBM CPE

// Prepare properties safely
            Map<String, Object> newProps =
                    cpeNew.getProperties() != null ? cpeNew.getProperties() : new HashMap<>();

            Map<String, Object> oldProps =
                    cpeOld.getProperties() != null ? cpeOld.getProperties() : new HashMap<>();

// -------------------------
// ONT CPE updates
// -------------------------
            newProps.put("description", "Internet");
            newProps.put("AdministrativeState", Constants.ADMIN_STATE_ALLOCATED);
            newProps.put("OperationalState", Constants.OPER_STATE_ACTIVE);

// -------------------------
// CBM CPE updates
// -------------------------
            oldProps.put("description", " "); // safer than setting null
            oldProps.put("AdministrativeState", Constants.ADMIN_STATE_AVAILABLE);
            oldProps.put("OperationalState", Constants.OPER_STATE_AVAILABLE);

// Apply properties
            if (cpeNew.getKind().equalsIgnoreCase(Constants.SETAR_KIND_CPE_DEVICE))
            {
                cpeNew.setProperties(newProps);
                cpeRepo.save(cpeNew);
            }

            if (cpeOld.getKind().equalsIgnoreCase(Constants.SETAR_KIND_CPE_DEVICE))
            {
                cpeOld.setProperties(oldProps);
                cpeRepo.save(cpeOld);

            }



// Persist changes




            // 13. Final success response: include subscriptionName and ontName
            Map<String, String> out = new HashMap<>();
            out.put("subscriptionName", subscriptionName);
            out.put("ontName", ontName);
            log.error(Constants.ACTION_COMPLETED);
            return new ChangeTechnologyResponse("200", "ChangeTechnology executed successfully.", Instant.now().toString(), subscriptionName,ontName);

        } catch (Exception ex) {
            log.error("Exception in ChangeTechnology", ex);
            return new ChangeTechnologyResponse("500", ERROR_PREFIX + ex.getMessage(), Instant.now().toString(),"","");
        }
    }
}