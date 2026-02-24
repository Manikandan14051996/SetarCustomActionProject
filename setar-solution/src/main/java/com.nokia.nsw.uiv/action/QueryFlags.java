package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.resource.Resource;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterface;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.QueryFlagsRequest;
import com.nokia.nsw.uiv.response.QueryFlagsResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@RestController
@Action
@Slf4j
public class QueryFlags implements HttpAction {

    protected static final String ACTION_LABEL = Constants.QUERY_FLAGS;
    private static final String ERROR_PREFIX = "UIV action QueryFlags execution failed - ";

    @Autowired
    private LogicalDeviceCustomRepository deviceRepository;

    @Autowired
    private LogicalComponentCustomRepository componentRepository;

    @Autowired
    private LogicalInterfaceCustomRepository logicalInterfaceRepository;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;

    @Autowired
    private CustomerCustomRepository customerRepository;

    @Autowired
    private ProductCustomRepository productRepository;

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Override
    public Class getActionClass() {
        return QueryFlagsRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error("------------Test Trace # 1---------------");
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);

        QueryFlagsRequest request = (QueryFlagsRequest) actionContext.getObject();

        Map<String, String> flags = new HashMap<>();
        initializeFlags(flags);

        String subscriber = request.getSubscriberName();
        String productType = request.getProductType();
        String productSubtype = request.getProductSubtype();
        String actionType = request.getActionType();
        String ontSN = request.getOntSN();
        String ontPort = request.getOntPort();
        String serviceID = request.getServiceId();
        String subName =
                subscriber + Constants.UNDER_SCORE + serviceID;

        try {
            log.error("------------Test Trace # 2---------------");
            log.error("Validating mandatory parameters...");
            try {
                Validations.validateMandatory(subscriber, "subscriberName");
                Validations.validateMandatory(productType, "productType");
                Validations.validateMandatory(productSubtype, "productSubtype");
                Validations.validateMandatory(ontSN, "ontSN");
                Validations.validateMandatory(ontPort, "ontPort");
            } catch (BadRequestException bre) {
                String msg = ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage();
                return new QueryFlagsResponse("400", msg, getCurrentTimestamp(), Collections.emptyMap());
            }
            log.error("Mandatory validation completed.");

            log.error("------------Test Trace # 3---------------");
            if (!equalsIgnoreCase(productType, "VOIP") && !equalsIgnoreCase(productType, "Voice")) {
                log.error("Trace: Non-voice product -> default VOIP ports to Available");
                flags.put("SERVICE_VOIP_NUMBER1", "Available");
                flags.put("SERVICE_VOIP_NUMBER2", "Available");
            }
            // ------------ ServiceID-scoped RFS check ----------------
            String serviceIdFlag = "New";

            if (serviceID != null && !serviceID.trim().isEmpty()) {

                List<Service> rfsList = new ArrayList<>();
                List<Service> rfsServices =
                        StreamSupport.stream(serviceCustomRepository.findAll().spliterator(), false)
                                .filter(sc -> sc.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_RFS))
                                .collect(Collectors.toList());
                for (Service rfs : rfsServices) {
                    String rfsName = rfs.getDiscoveredName();

                    if (rfsName != null && rfsName.contains(serviceID)) {

                        String[] tokens = rfsName.split(Constants.UNDER_SCORE);

                        if (tokens.length > 2 && serviceID.equals(tokens[2])) {
                            rfsList.add(rfs);
                        }
                    }
                }

                serviceIdFlag = rfsList.isEmpty() ? "New" : "Exist";
            }

            flags.put("SERVICE_ID_FLAG", serviceIdFlag);
// --------------------------------------------------------

            log.error("------------Test Trace # 4---------------");
            String serviceLink = "NA";
            if (ontSN != null) {
                if (ontSN.startsWith("ALC")) {
                    serviceLink = "ONT";
                    log.error("Trace: ontSN startsWith ALCL -> serviceLink=ONT");
                } else if (ontSN.startsWith("CW")) {
                    serviceLink = "SRX";
                    log.error("Trace: ontSN startsWith CW -> serviceLink=SRX");
                } else {
                    log.error("Trace: ontSN pattern not recognized -> serviceLink=NA");
                }
            }
            flags.put("SERVICE_LINK", serviceLink);

            log.error("------------Test Trace # 5---------------");
            boolean subtypeMatches = equalsAnyIgnoreCase(productSubtype, "Broadband", "Voice", "Cloudstarter", "Bridged");
            if ((subtypeMatches || equalsIgnoreCase(productType, "ENTERPRISE"))
                    && !"Configure".equalsIgnoreCase(actionType)) {
                if (ontSN == null || ontSN.trim().isEmpty() || "NA".equalsIgnoreCase(ontSN)) {
                    try {
                        String rfsName = "RFS" + Constants.UNDER_SCORE + subscriber + Constants.UNDER_SCORE  + (serviceID == null ? "" : serviceID);
                        List<Service> rfsServices =
                                StreamSupport.stream(serviceCustomRepository.findAll().spliterator(), false)
                                        .filter(sc -> sc.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_RFS))
                                        .collect(Collectors.toList());

                        for (Service rfs : rfsServices) {
                            if (rfs.getDiscoveredName() == null
                                    || serviceID == null
                                    || !rfs.getDiscoveredName().contains(
                                    Constants.UNDER_SCORE + serviceID)) {
                                continue;
                            }
                            if (rfs.getDiscoveredName() != null) {
                                Service rfs1=serviceCustomRepository.findByDiscoveredName(rfs.getDiscoveredName()).get();
                                Service cfs=rfs1.getUsedService().stream().findFirst().get();
                                cfs = serviceCustomRepository.findByDiscoveredName(cfs.getDiscoveredName()).get();
                                String productName = cfs.getUsingService().stream().filter(ser->ser.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_PRODUCT)).findFirst().get().getDiscoveredName();
                                Product product = productRepository.findByDiscoveredName(productName).get();
                                product=productRepository.findByDiscoveredName(product.getDiscoveredName()).get();
                                Subscription subscription=product.getSubscription().stream().findFirst().get();

                                if (subscription!= null) {


                                    String subServiceId = (String) safeProps(subscription.getProperties())
                                            .getOrDefault("serviceID", "");

                                    if (serviceID != null && serviceID.equals(subServiceId)) {
                                        Set<LogicalDevice> used = rfs1.getUsedResource().stream().map(r->(LogicalDevice)r).collect(Collectors.toSet());
                                        if (used != null) {
                                            for (Resource res : used) {
                                                if (res.getDiscoveredName() != null &&
                                                        res.getDiscoveredName().contains("ONT")) {
                                                    Object serial = safeProps(res.getProperties())
                                                            .get("serialNo");
                                                    if (serial != null) {
                                                        String derivedOntSN = serial.toString();
                                                        ontSN = derivedOntSN;

                                                        flags.put("ONTSN", derivedOntSN);
                                                        flags.put("SERVICE_SN", derivedOntSN);
                                                        flags.put("SERVICE_LINK", "ONT");
                                                        serviceLink = "ONT";

                                                    }
                                                } else if (res.getDiscoveredName() != null &&
                                                        res.getDiscoveredName().contains("CBM")) {

                                                    flags.put("SERVICE_LINK", "Cable_Modem");

                                                    Object mac = safeProps(res.getProperties()).get("macAddress");
                                                    if (mac != null) {
                                                        flags.put("SERVICE_SN", mac.toString());   // ✅ REQUIRED
                                                        flags.put("CBM_MAC", mac.toString());      // ✅ REQUIRED
                                                    }
                                                }
                                            }
                                        }
                                        // 🔧 CBM-only fallback (no ONT resource found)



                                        String effectiveOntSN =
                                                flags.getOrDefault("ONT", ontSN);

                                        String bridgeService =
                                                deriveBridgeServiceForSubscriberRfs(
                                                        rfsServices,
                                                        effectiveOntSN,
                                                        subscriber
                                                );

                                        flags.put("BRIDGE_SERVICE",
                                                bridgeService == null ? "NA" : bridgeService);
                                        break;

                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("RFS discovery best-effort failed: {}", e.getMessage());
                    }
                }
            }

            log.error("------------Test Trace # 6---------------");
            if (equalsIgnoreCase(productType, "VOIP") && equalsIgnoreCase(actionType, "Configure") && serviceID != null) {
                log.error("Trace: VOIP Configure flow - checking voip device mapping");
                String voipDeviceName = subscriber + Constants.UNDER_SCORE  + serviceID;
                Optional<LogicalDevice> optVoip = deviceRepository.findByDiscoveredName(voipDeviceName);

                if (optVoip.isPresent()) {
                    Map<String, Object> p = safeProps(optVoip.get().getProperties());
                    String pots1 = (String) p.getOrDefault("potsPort1Number", "");
                    String pots2 = (String) p.getOrDefault("potsPort2Number", "");
                    if (serviceID.equals(pots1)) {
                        flags.put("VOICE_POTS_PORT", "1");
                        log.error("Trace: VOIP pots mapped on port1");
                    } else if (serviceID.equals(pots2)) {
                        flags.put("VOICE_POTS_PORT", "2");
                        log.error("Trace: VOIP pots mapped on port2");
                    }
                } else {
                    log.error("Trace: VOIP device not found by GDN - scanning all devices (best-effort)");
                    for (LogicalDevice d : deviceRepository.findAll()) {
                        Map<String, Object> p = safeProps(d.getProperties());
                        if (serviceID.equals(p.getOrDefault("potsPort1Number", ""))) {
                            flags.put("VOICE_POTS_PORT", "1");
                            break;
                        } else if (serviceID.equals(p.getOrDefault("potsPort2Number", ""))) {
                            flags.put("VOICE_POTS_PORT", "2");
                            break;
                        }
                    }
                }
            }
            // --------------------Logic 12--------------
            // ------------ Additional MAC Assignment (Modify) ----------------
            if ("Modify".equalsIgnoreCase(actionType)
                    && equalsAnyIgnoreCase(productType,
                    "MOCA", "BridgeMode", "APMNT", "WIFION")
                    && serviceID != null
                    && subscriber != null) {



                log.error("Trace: Modify MAC assignment, searching subscription {}", subName);

                subscriptionRepository.findByDiscoveredName(subName)
                        .ifPresent(sub -> {
                            Map<String, Object> p = safeProps(sub.getProperties());
                            Object mac = p.get("serviceMac");

                            if (mac != null && !mac.toString().isEmpty()) {
                                flags.put("CBM_MAC", mac.toString());
                                log.error("Trace: CBM_MAC set from subscription: {}", mac);
                            }
                        });
            }
// ----------------------------------------------------------------

// ================= Step 6 integration =================
            if (ontSN != null && serviceID != null && subscriber != null) {

                log.error("Trace: Step-6 flags routine invoked");

                Map<String, String> step6Result =
                        executeStep6Flags(
                                ontSN,
                                serviceID,
                                subscriber,
                                actionType,
                                productSubtype
                        );

                if (!step6Result.isEmpty()) {
                    flags.put("ACCOUNT_EXIST",
                            step6Result.getOrDefault("ACCOUNT_EXIST", flags.get("ACCOUNT_EXIST")));

                    flags.put("SERVICE_FLAG",
                            step6Result.getOrDefault("SERVICE_FLAG", flags.get("SERVICE_FLAG")));

                    flags.put("CBM_ACCOUNT_EXIST",
                            step6Result.getOrDefault("CBM_ACCOUNT_EXIST", flags.get("CBM_ACCOUNT_EXIST")));

                    if (step6Result.containsKey("SIMA_CUST_ID")) {
                        flags.put("SIMA_CUST_ID", step6Result.get("SIMA_CUST_ID"));
                    }
                }
            } else {
                log.error("Trace: Step-6 skipped (ontSN/serviceID/subscriber missing)");
            }
// =====================================================

            log.error("------------Test Trace # 7---------------");

            List<Subscription> subsForCustomer = new ArrayList<>();
            for (Subscription s : subscriptionRepository.findAll()) {
                if (s.getDiscoveredName() != null &&
                        s.getDiscoveredName().contains(subscriber)) {
                    subsForCustomer.add(s);
                }
            }
            // ================= MULTIPLE MATCHING SUBSCRIBERS LOGIC =================
            List<Customer> matchingSubscribers = new ArrayList<>();

            for (Customer cust : customerRepository.findAll()) {
                if (cust.getDiscoveredName() != null
                        && cust.getDiscoveredName().contains(subscriber)) {
                    matchingSubscribers.add(cust);
                }
            }

            if (matchingSubscribers.size() > 1) {

                log.error("Trace: Multiple matching subscribers found for {}", subscriber);

                // Initial values as per spec
                flags.put("SERVICE_FLAG", "Exist");
                flags.put("ACCOUNT_EXIST", "New");
                flags.put("CBM_ACCOUNT_EXIST", "New");

                boolean cbmFound = false;

                // List subscriptions whose name contains subscriber
                for (Subscription s : subscriptionRepository.findAll()) {

                    if (s.getDiscoveredName() == null
                            || !s.getDiscoveredName().contains(subscriber)) {
                        continue;
                    }

                    Map<String, Object> sp = safeProps(s.getProperties());
                    Object link = sp.get("serviceLink");

                    if ("Cable_Modem".equalsIgnoreCase(String.valueOf(link))) {
                        cbmFound = true;
                        break;
                    }
                }

                if (cbmFound) {
                    flags.put("CBM_ACCOUNT_EXIST", "Exist");
                    log.error("Trace: Cable_Modem subscription found → CBM_ACCOUNT_EXIST=Exist");
                } else {
                    flags.put("ACCOUNT_EXIST", "Exist");
                    log.error("Trace: No Cable_Modem subscription → ACCOUNT_EXIST=Exist");
                }
            }


            if (Arrays.asList("Unconfigure", "MoveOut",
                            "ChangeTechnology", "AccountTransfer")
                    .contains(actionType)) {

                if (subsForCustomer.isEmpty()) {
                    flags.put("SERVICE_FLAG", "New");
                    flags.put("ACCOUNT_EXIST", "New");
                    flags.put("CBM_ACCOUNT_EXIST", "New");

                } else if (subsForCustomer.size() == 1) {
                    flags.put("SERVICE_FLAG", "New");
                    flags.put("ACCOUNT_EXIST", "New");

                    Object link =
                            safeProps(subsForCustomer.get(0).getProperties())
                                    .get("serviceLink");

                    if ("Cable_Modem".equalsIgnoreCase(String.valueOf(link))) {
                        flags.put("CBM_ACCOUNT_EXIST", "New");
                    }

                } else {
                    flags.put("SERVICE_FLAG", "Exist");
                    flags.put("ACCOUNT_EXIST", "Exist");

                    int cbmCount = 0;
                    Set<String> macSet = new HashSet<>();

                    for (Subscription s : subsForCustomer) {
                        Map<String, Object> p = safeProps(s.getProperties());
                        if ("Cable_Modem".equalsIgnoreCase(
                                (String) p.get("serviceLink"))) {

                            cbmCount++;
                            Object mac = p.get("serviceMac");
                            if (mac != null) macSet.add(mac.toString());
                        }
                    }

                    if (Arrays.asList("ChangeTechnology", "AccountTransfer")
                            .contains(actionType)) {
                        flags.put("CBM_ACCOUNT_EXIST",
                                macSet.size() > 1 ? "Exist" : "New");
                    } else {
                        flags.put("CBM_ACCOUNT_EXIST",
                                cbmCount > 1 ? "Exist" : "New");
                    }
                }

            } else if ("Configure".equalsIgnoreCase(actionType)
                    && ontSN != null && ontSN.contains("ALCL")) {

                String subscriberWithOnt =
                        subscriber + Constants.UNDER_SCORE + ontSN;

                boolean exists =
                        customerRepository
                                .findByDiscoveredName(subscriberWithOnt)
                                .isPresent();

                flags.put("SERVICE_FLAG", exists ? "Exist" : "New");
                flags.put("ACCOUNT_EXIST", exists ? "Exist" : "New");

            } else if ("Migrate".equalsIgnoreCase(actionType)
                    && ontSN != null && ontSN.contains("ALCL")) {

                flags.put("SERVICE_FLAG", "New");
                flags.put("ACCOUNT_EXIST", "New");

                for (Subscription s : subsForCustomer) {
                    if (s.getDiscoveredName() != null &&
                            s.getDiscoveredName().contains(ontSN)) {

                        flags.put("SERVICE_FLAG", "Exist");

                        if (subName.equalsIgnoreCase(
                                s.getDiscoveredName())) {
                            flags.put("ACCOUNT_EXIST", "Exist");
                        }

                        Object sima =
                                safeProps(s.getProperties())
                                        .get("subscriberIDForCableModem");
                        if (sima != null && !sima.toString().isEmpty()) {
                            flags.put("SIMA_CUST_ID", sima.toString());
                        }
                    }
                }

            } else {
                boolean subscriberExists =
                        customerRepository
                                .findByDiscoveredName(subscriber)
                                .isPresent();

                flags.put("SERVICE_FLAG",
                        subscriberExists ? "Exist" : "New");
                flags.put("ACCOUNT_EXIST",
                        subscriberExists ? "Exist" : "New");

                boolean anyCbm = subsForCustomer.stream().anyMatch(s -> {
                    Object l =
                            safeProps(s.getProperties()).get("serviceLink");
                    return l != null &&
                            l.toString().contains("Cable_Modem");
                });

                flags.put("CBM_ACCOUNT_EXIST",
                        anyCbm ? "Exist" : "New");
            }


            log.error("------------Test Trace # 8---------------");
            if (ontSN != null && !"".equals(ontSN) && equalsIgnoreCase(productSubtype, "IPTV") && equalsIgnoreCase(actionType, "Unconfigure")) {
                log.error("Trace: IPTV Unconfigure path - searching subscription");
                String subGdn = subscriber + Constants.UNDER_SCORE  + (serviceID == null ? "" : serviceID);
                Optional<Subscription> optSub = subscriptionRepository.findByDiscoveredName(subGdn);
                String ontSNO = "NA";
                if (optSub.isPresent()) {
                    Subscription s = optSub.get();
                    Map<String, Object> p = safeProps(s.getProperties());
                    Object sSN = p.get("serviceSN");
                    if (sSN != null) {
                        if ("ONT".equalsIgnoreCase(serviceLink) || "SRX".equalsIgnoreCase(serviceLink)) {
                            ontSNO = sSN.toString();
                        } else if ("Cable_Modem".equalsIgnoreCase(serviceLink)) {
                            ontSNO = p.getOrDefault("macAddress", "").toString();
                        }
                    } else {
                        ontSNO = ontSN;
                    }
                } else {
                    ontSNO = ontSN;
                }
                if (!"NA".equalsIgnoreCase(ontSNO) && ontSNO != null && !"".equals(ontSNO)) {
                    int iptvCount = 0;
                    for (Subscription s : subscriptionRepository.findAll()) {
                        Map<String, Object> p = safeProps(s.getProperties());
                        if ("IPTV".equalsIgnoreCase((String) p.getOrDefault("serviceSubType", ""))) {
                            if (ontSNO.equals(p.getOrDefault("serviceSN", "")) || ontSNO.equals(p.getOrDefault("macAddress", ""))) {
                                iptvCount++;
                            }
                        }
                    }
                    flags.put("IPTV_COUNT", String.valueOf(iptvCount));
                    log.error("Trace: IPTV count for ontSNO=" + ontSNO + " is " + iptvCount);
                }
            }

            log.error("------------Test Trace # 9---------------");

            List<String> subscount = new ArrayList<>();

            boolean eligible =
                    equalsAnyIgnoreCase(productSubtype,
                            "Fibernet", "Broadband", "Voice", "Bridged")
                            || (equalsIgnoreCase(productType, "Broadband")
                            && equalsIgnoreCase(productSubtype, "Bridged"));

            if (eligible) {

                boolean ontBasedSearch =
                        ontSN != null && ontSN.contains("ALCL");

                // 🔁 Re-derive Bridge Service ID on these records
                String bridgeService = "NA";
                if (ontBasedSearch) {
                    List<Service> rfsServices =
                            StreamSupport.stream(serviceCustomRepository.findAll().spliterator(), false)
                                    .filter(sc -> sc.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_RFS))
                                    .collect(Collectors.toList());
                    bridgeService =
                            deriveBridgeServiceForSubscriberRfs(
                                    rfsServices,
                                    ontSN,
                                    subscriber
                            );
                }

                for (Subscription s : subscriptionRepository.findAll()) {

                    String name = s.getDiscoveredName();
                    if (name == null) continue;

                    boolean match;

                    if (ontBasedSearch) {
                        match = name.contains(ontSN);
                    } else {
                        match = name.contains(subscriber);
                    }

                    if (!match) continue;

                    Map<String, Object> sp = safeProps(s.getProperties());
                    if (name.endsWith(ontSN)
                            && equalsAnyIgnoreCase(productType,
                            "Fibernet", "Broadband")) {
                        subscount.add(name);
                    }

                    if (ontBasedSearch
                            && bridgeService != null
                            && !"NA".equalsIgnoreCase(bridgeService)
                            && actionType != null
                            && actionType.contains("Modify_CPE")) {

                        if ("Bridged".equalsIgnoreCase(
                                (String) sp.getOrDefault("serviceSubType", ""))
                                && name.contains(ontSN)) {

                            String qos =
                                    (String) sp.getOrDefault(
                                            "evpnQosSessionProfile", "");

                            if (qos != null && !qos.isEmpty()) {
                                flags.put("QOS_PROFILE_BRIDGE", qos);
                                log.error("Trace: QOS_PROFILE_BRIDGE set to {}", qos);
                            }
                        }
                    }
                }
            }

            flags.put("FIBERNET_COUNT",
                    subscount.isEmpty()
                            ? "0"
                            : String.valueOf(subscount.size()));

            log.error("Trace: Fibernet count = {}",
                    flags.get("FIBERNET_COUNT"));

            log.error("Trace: Fibernet count = " + flags.get("FIBERNET_COUNT"));

            log.error("------------Test Trace # 10---------------");

            if (equalsIgnoreCase(productSubtype, "IPTV")
                    && !equalsIgnoreCase(actionType, "Configure")
                    && subscriber != null
                    && serviceID != null) {

                String subscriptionToSearch =
                        subscriber + Constants.UNDER_SCORE + serviceID;

                log.error("Trace: IPTV subscription lookup: {}", subscriptionToSearch);

                Optional<Subscription> optFound =
                        subscriptionRepository.findByDiscoveredName(subscriptionToSearch);

                if (optFound.isPresent()) {

                    Subscription found = optFound.get();
                    Map<String, Object> p = safeProps(found.getProperties());

                    Object link  = p.get("serviceLink");
                    Object sSN   = p.get("serviceSN");
                    Object sMAC  = p.get("serviceMac");
                    Object qos   = p.get("veipQosSessionProfile");
                    Object kenan = p.get("kenanSubscriberId");

                    if (link != null) {
                        flags.put("SERVICE_LINK", link.toString());
                    }

                    if (sSN != null) {
                        flags.put("SERVICE_SN", sSN.toString());
                    }

                    if (sMAC != null) {
                        flags.put("SERVICE_MAC", sMAC.toString());
                        flags.put("CBM_MAC", sMAC.toString()); // ✅ REQUIRED
                    }

                    if (qos != null) {
                        flags.put("QOS_PROFILE", qos.toString());
                    }

                    if (kenan != null) {
                        flags.put("KENAN_UIDNO", kenan.toString());
                    }

                    // ✅ ontSN fallback logic (CRITICAL)
                    if ("NA".equalsIgnoreCase(ontSN)
                            && sSN != null
                            && !sSN.toString().isEmpty()) {

                        ontSN = sSN.toString();
                        log.error("Trace: ontSN derived from serviceSN = {}", ontSN);
                    }
                }
            }

            else if (!equalsIgnoreCase(actionType, "Configure")) {
                String subscriptionToSearch;
                if (ontSN != null && ontSN.contains("ALCL")) {
                    subscriptionToSearch = subscriber + Constants.UNDER_SCORE  + (serviceID == null ? "" : serviceID) + Constants.UNDER_SCORE  + ontSN;
                } else {
                    subscriptionToSearch = subscriber + Constants.UNDER_SCORE  + (serviceID == null ? "" : serviceID);
                }
                log.error("Trace: Searching subscription by DN: " + subscriptionToSearch);
                Optional<Subscription> optFound = subscriptionRepository.findByDiscoveredName(subscriptionToSearch);
                if (optFound.isPresent()) {
                    Subscription found = optFound.get();
                    Map<String, Object> p = safeProps(found.getProperties());
                    Object link = p.get("serviceLink");
                    Object sSN = p.get("serviceSN");
                    Object sMAC = p.get("serviceMac");
                    Object qos = p.get("qosProfile");
                    Object kenan = p.get("billingId");

                    if (link != null) flags.put("SERVICE_LINK", link.toString());
                    if (sSN != null) flags.put("SERVICE_SN", sSN.toString());
                    if (sMAC != null) flags.put("SERVICE_MAC", sMAC.toString());
                    if (qos != null) flags.put("QOS_PROFILE", qos.toString());
                    if (kenan != null) flags.put("KENAN_UIDNO", kenan.toString());

                    if ("Cable_Modem".equalsIgnoreCase(String.valueOf(link))) {
                        String cbmName = "CBM" + Constants.UNDER_SCORE +(sMAC == null ? "" : sMAC.toString());
                        Optional<LogicalDevice> optCbm = deviceRepository.findByDiscoveredName(cbmName);
                        if (optCbm.isPresent()) {
                            LogicalDevice cbm = optCbm.get();
                            Map<String, Object> cbmProps = safeProps(cbm.getProperties());
                            flags.put("CBM_MAC", (String) cbmProps.getOrDefault("macAddress", ""));
                            String n1 = (String) cbmProps.getOrDefault("voipPort1", "Available");
                            String n2 = (String) cbmProps.getOrDefault("voipPort2", "Available");
                            flags.put("SERVICE_VOIP_NUMBER1", n1 == null ? "" : n1);
                            flags.put("SERVICE_VOIP_NUMBER2", n2 == null ? "" : n2);
                            flags.put("ONT_MODEL", (String) cbmProps.getOrDefault("deviceModel", ""));
                            if (!"Available".equalsIgnoreCase(n1) || !"Available".equalsIgnoreCase(n2)) {
                                flags.put("SERVICE_TEMPLATE_VOIP", "Exist");
                            } else {
                                flags.put("SERVICE_TEMPLATE_VOIP", "New");
                            }
                            log.error("Trace: CBM inspected: mac=" + flags.get("CBM_MAC") + " voip1=" + flags.get("SERVICE_VOIP_NUMBER1"));
                        } else {
                            String alt = "CBM" +(serviceID == null ? "" : serviceID);
                            deviceRepository.findByDiscoveredName(alt).ifPresent(dev -> {
                                Map<String, Object> dp = safeProps(dev.getProperties());
                                flags.put("ONT_MODEL", (String) dp.getOrDefault("deviceModel", ""));
                            });
                        }
                    }
                }
            }

            log.error("------------Test Trace # 11---------------");
            customerRepository.findByDiscoveredName(subscriber).ifPresent(cust -> {
                Map<String, Object> cp = safeProps(cust.getProperties());
                flags.put("FIRST_NAME", (String) cp.getOrDefault("firstName", ""));
                flags.put("LAST_NAME", (String) cp.getOrDefault("lastName", ""));
                log.error("Trace: Subscriber info: " + flags.get("FIRST_NAME") + " " + flags.get("LAST_NAME"));
            });

            log.error("------------Test Trace # 12---------------");
            if ("ONT".equalsIgnoreCase(serviceLink) || "SRX".equalsIgnoreCase(serviceLink) || (ontSN != null && ontSN.contains("ALCL"))) {
                String ontGdn = ontSN == null ? "" :"ONT" + ontSN;
                if (ontGdn.length() > 100) {
                    return new QueryFlagsResponse("400", ERROR_PREFIX + "ONT name too long", getCurrentTimestamp(), Collections.emptyMap());
                }
                Optional<LogicalDevice> optOntDev = deviceRepository.findByDiscoveredName(ontGdn);
                if (optOntDev.isPresent()) {
                    LogicalDevice ontDev = optOntDev.get();
                    Map<String, Object> ontProps = safeProps(ontDev.getProperties());
                    // ================= POTS PORT DERIVATION FROM ONT =================
                    if (equalsAnyIgnoreCase(productType, "VOIP", "Voice")
                            && serviceID != null) {

                        Object pots1 = ontProps.get("potsPort1Number");
                        Object pots2 = ontProps.get("potsPort2Number");

                        if (pots1 != null && serviceID.equals(pots1.toString())) {
                            flags.put("VOICE_POTS_PORT", "1");
                            log.error("Trace: VOICE_POTS_PORT set to 1 from ONT");
                        } else if (pots2 != null && serviceID.equals(pots2.toString())) {
                            flags.put("VOICE_POTS_PORT", "2");
                            log.error("Trace: VOICE_POTS_PORT set to 2 from ONT");
                        }
                    }

                    // ================= ENTERPRISE: derive ontPort from RFS =================
                    if (equalsIgnoreCase(productType, "ENTERPRISE")
                            && serviceID != null
                            && ontSN != null) {

                        log.error("Trace: ENTERPRISE flow - deriving ontPort from RFS");

                        for (Service rfs : StreamSupport.stream(serviceCustomRepository.findAll().spliterator(),false).filter(service -> service.getDiscoveredName().contains(Constants.RFS)).toList()) {

                            if (rfs.getDiscoveredName() == null
                                    || !rfs.getDiscoveredName().contains(serviceID)) {
                                continue;
                            }

                            try {
                                Service rfs1 =
                                        serviceCustomRepository.findByDiscoveredName(
                                                rfs.getDiscoveredName()).orElse(null);

                                if (rfs1 == null || rfs1.getUsedService() == null) continue;

                                Service cfs =
                                        serviceCustomRepository.findByDiscoveredName(
                                                rfs1.getUsedService().stream().findFirst().get().getDiscoveredName()).orElse(null);
                                String productName = cfs.getUsingService().stream().filter(ser->ser.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_PRODUCT)).findFirst().get().getDiscoveredName();
                                if (cfs == null || productName.isBlank()) continue;

                                Product product =
                                        productRepository.findByDiscoveredName(productName).orElse(null);

                                if (product == null || product.getSubscription() == null) continue;

                                Subscription sub = product.getSubscription().stream().findFirst().get();
                                Map<String, Object> sp = safeProps(sub.getProperties());

                                Object port = sp.get("evpnPort");
                                if (port != null) {
                                    ontPort = port.toString();
                                    flags.put("SERVICE_ONT_PORT", ontPort);
                                    log.error("Trace: ontPort derived from ENTERPRISE RFS = {}", ontPort);
                                    break;
                                }

                            } catch (Exception e) {
                                log.error("Trace: ENTERPRISE ontPort derivation failed", e);
                            }
                        }
                    }

                    flags.put("ONT_MODEL", (String) ontProps.getOrDefault("deviceModel", ""));
                    flags.put("SERVICE_SN", (String) ontProps.getOrDefault("serialNo", ""));
                    flags.put("SERVICE_MAC", (String) ontProps.getOrDefault("macAddress", ""));
                    log.error("Trace: ONT found: model=" + flags.get("ONT_MODEL") + " sn=" + flags.get("SERVICE_SN"));

                    Object parentOltObj = ontProps.get("oltPosition");
                    if (parentOltObj != null) {
                        String oltDiscoveredName = parentOltObj.toString();
                        deviceRepository.findByDiscoveredName(oltDiscoveredName).ifPresent(olt -> {
                            Map<String, Object> oltProps = safeProps(olt.getProperties());
                            flags.put("SERVICE_OLT_POSITION", oltProps.get("oltPosition")!=null?oltProps.get("oltPosition").toString():"");
                            flags.put("SERVICE_TEMPLATE_ONT", existsString(oltProps.get("ontTemplate")));
                            flags.put("SERVICE_TEMPLATE_IPTV", existsString(oltProps.get("iptvServiceTemplate")));

                            flags.put("SERVICE_TEMPLATE_VEIP", existsString(oltProps.get("veipServiceTemplate")));
                            flags.put("SERVICE_TEMPLATE_HSI", existsString(oltProps.get("veipHsiTemplate")));
                            flags.put("SERVICE_TEMPLATE_VOIP", existsString(oltProps.get("voipServiceTemplate")));
                            flags.put("SERVICE_TEMPLATE_POTS1", existsString(oltProps.get("voipPots1Template")));
                            flags.put("SERVICE_TEMPLATE_POTS2", existsString(oltProps.get("voipPots2Template")));
                            log.error("Trace: OLT templates checked for OLT=" + oltDiscoveredName);
                        });
                    }

                    Set<String> vlanSet = new HashSet<>();
                    for (LogicalInterface vif : logicalInterfaceRepository.findAll()) {
                        String lname = vif.getDiscoveredName();
                        if (lname != null && lname.contains(ontSN) && ontPort != null && lname.contains("P" + ontPort)) {
                            vlanSet.add(lname);
                        }
                    }
                    for (String vl : vlanSet) {
                        logicalInterfaceRepository.findByDiscoveredName(vl).ifPresent(vif -> {
                            Map<String, Object> vp = safeProps(vif.getProperties());
                            Object tmpl = vp.get("vlanTemplate");
                            if ("4.3B EVPN SINGLETAGGED VLAN v2".equalsIgnoreCase(String.valueOf(tmpl))) {
                                flags.put("SERVICE_TEMPLATE_MGMT", "4.3B EVPN SINGLETAGGED VLAN v2");
                                log.error("Trace: VLAN " + vl + " uses management template " + vp.get("template"));
                            }
                        });
                    }
                } else {
                    log.error("Trace: ONT device not found by GDN: " + ontGdn);
                }
            } else if ("Cable_Modem".equalsIgnoreCase(serviceLink) || equalsIgnoreCase(productType, "CBM")) {
                boolean iptvExists = false, veipExists = false;
                for (Subscription s : subscriptionRepository.findAll()) {
                    Map<String, Object> p = safeProps(s.getProperties());
                    if ("IPTV".equalsIgnoreCase((String) p.getOrDefault("serviceSubType", ""))) iptvExists = true;
                    if ("Broadband".equalsIgnoreCase((String) p.getOrDefault("serviceSubType", ""))) veipExists = true;
                }
                flags.put("SERVICE_TEMPLATE_IPTV", iptvExists ? "Exist" : "New");
                flags.put("SERVICE_TEMPLATE_VEIP", veipExists ? "Exist" : "New");
                log.error("Trace: CBM path: IPTV exist=" + flags.get("SERVICE_TEMPLATE_IPTV") + " VEIP exist=" + flags.get("SERVICE_TEMPLATE_VEIP"));
            }
            // ================= VOIP PORT RESET RULE =================
// Spec: If productName in ["VOIP","Voice"] and any ports are "Available" → reset to null
            if (equalsAnyIgnoreCase(productType, "VOIP", "Voice")) {

                String p1 = flags.get("SERVICE_VOIP_NUMBER1");
                String p2 = flags.get("SERVICE_VOIP_NUMBER2");

                if ("Available".equalsIgnoreCase(p1) || "Available".equalsIgnoreCase(p2)) {

                    flags.put("SERVICE_VOIP_NUMBER1", "");
                    flags.put("SERVICE_VOIP_NUMBER2", "");

                    log.error("Trace: VOIP/Voice ports reset to null as per spec (Available detected)");
                }
            }
// ========================================================


            log.error("------------Test Trace # 13---------------");
            log.error("------------Test Trace # 13---------------");
            if (Arrays.asList("AccountTransfer", "MoveOut", "ChangeTechnology", "Unconfigure")
                    .contains(actionType)
                    || (actionType != null && actionType.contains("Modify_CPE"))) {

                log.error("Trace: IPTV service ID list discovery path");

                List<String> iptvIds = new ArrayList<>();

                if ("ONT".equalsIgnoreCase(serviceLink)) {

                    log.error("Trace: IPTV discovery using subscriber + serviceSN (ONT path)");

                    for (Subscription s : subscriptionRepository.findAll()) {
                        if (iptvIds.size() >= 5) break;

                        if (s.getDiscoveredName() == null ||
                                !s.getDiscoveredName().contains(subscriber)) {
                            flags.getOrDefault("Subscriber", subscriber);
                            continue;   // must belong to same subscriber
                        }

                        Map<String, Object> p = safeProps(s.getProperties());

                        // must be IPTV
                        if (!"IPTV".equalsIgnoreCase(
                                (String) p.getOrDefault("serviceSubType", ""))) {
                            continue;
                        }

                        // match by serviceSN
                        Object sn = p.get("serviceSN");
                        if (sn == null || sn.toString().isEmpty()) continue;

                        if (!sn.toString().equalsIgnoreCase(
                                flags.getOrDefault("SERVICE_SN", ontSN))) {
                            continue;
                        }

                        Object sid = p.get("serviceID");
                        if (sid != null && !sid.toString().isEmpty()) {
                            iptvIds.add(sid.toString());
                            log.error("Trace: IPTV ID added (ONT path) = {}", sid);
                        }
                    }
                }
                else {
                    String cbmMac = flags.getOrDefault("CBM_MAC", "");

                    if (cbmMac != null && !cbmMac.isEmpty()) {

                        for (Subscription s : subscriptionRepository.findAll()) {
                            if (iptvIds.size() >= 5) break;   // limit to 5

                            Map<String, Object> p = safeProps(s.getProperties());

                            if ("IPTV".equalsIgnoreCase(
                                    (String) p.getOrDefault("serviceSubType", "")) &&
                                    cbmMac.equals(p.getOrDefault("macAddress", ""))) {

                                Object sid = p.get("serviceID");
                                if (sid != null && !sid.toString().isEmpty()) {
                                    iptvIds.add(sid.toString());
                                }
                            }
                        }

                        String cbmGdn = "CBM" + Constants.UNDER_SCORE + cbmMac;

                        deviceRepository.findByDiscoveredName(cbmGdn).ifPresent(cpe -> {
                            Map<String, Object> cp = safeProps(cpe.getProperties());
                            flags.put("RESOURCE_MAC_MTA_OLD",
                                    (String) cp.getOrDefault("macAddressMta", ""));
                            flags.put("RESOURCE_MODEL_MTA_OLD",
                                    (String) cp.getOrDefault("deviceModelMta", ""));
                        });
                    }
                }

                // ===== STORE INDEXED IPTV IDS =====
                for (int i = 0; i < 5; i++) {
                    String key = "IPTV_SERVICE_ID" + (i + 1);

                    if (i < iptvIds.size()) {
                        flags.put(key, iptvIds.get(i));
                    } else {
                        flags.put(key, "");   // ensure always present
                    }
                }

                flags.put("IPTV_COUNT", String.valueOf(iptvIds.size()));

                log.error("Trace: IPTV IDs discovered count = " + iptvIds.size());
            }


            log.error("------------Test Trace # 14---------------");
            if ((equalsAnyIgnoreCase(productType, "EVPN", "ENTERPRISE") || equalsAnyIgnoreCase(productSubtype, "Cloudstarter", "Bridged"))
                    && (equalsAnyIgnoreCase(actionType, "Configure", "Migrate"))) {
                log.error("Trace: Evaluating template requirements for EVPN/Enterprise/Cloudstarter/Bridged");
                String oltPos = flags.getOrDefault("SERVICE_OLT_POSITION", "");
                if (!oltPos.isEmpty()) {
                    String oltPosGdn = Validations.getGlobalName(oltPos);
                    deviceRepository.findByDiscoveredName(oltPos).ifPresent(olt -> {
                        Map<String, Object> oltProps = safeProps(olt.getProperties());
                        flags.put("SERVICE_PORT2_EXIST", existsString(oltProps.get("port2Template")));
                        flags.put("SERVICE_PORT3_EXIST", existsString(oltProps.get("port3Template")));
                        flags.put("SERVICE_PORT4_EXIST", existsString(oltProps.get("port4Template")));
                        flags.put("SERVICE_PORT5_EXIST", existsString(oltProps.get("port5Template")));
                        log.error("Trace: OLT port templates checked for OLT=" + oltPos);
                    });
                }
                if (ontPort != null) {
                    if ("3".equals(ontPort) || "4".equals(ontPort) || "5".equals(ontPort)) {
                        flags.put("SERVICE_TEMPLATE_PORT", "Exist");
                    } else if ("2".equals(ontPort)) {
                        flags.put("SERVICE_TEMPLATE_PORT", "New");
                    }
                }
                // ---------- CASE A : ADD RFS SINGLE CHECK ----------
                int rfsCountForOnt = 0;
                List<Service> rfsServices =
                        StreamSupport.stream(serviceCustomRepository.findAll().spliterator(), false)
                                .filter(sc -> sc.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_RFS))
                                .collect(Collectors.toList());
                for (Service rfs : rfsServices) {
                    if (rfs.getDiscoveredName() != null &&
                            rfs.getDiscoveredName().contains(ontSN)) {
                        rfsCountForOnt++;
                    }
                }
                flags.put("SERVICE_RFS_SINGLE", rfsCountForOnt == 1 ? "YES" : "NO");

// ---------- CASE A : WIFI Maintenance check for port 3/4/5 ----------
                if (equalsAnyIgnoreCase(ontPort,"3","4","5")) {
                    String wifiFlag = "NO";

                    for (Subscription s : subscriptionRepository.findAll()) {
                        Map<String,Object> sp = safeProps(s.getProperties());
                        if ("WIFI Maintenance".equalsIgnoreCase(
                                (String) sp.getOrDefault("serviceSubType","")) &&
                                ontSN.equals(sp.getOrDefault("serviceSN","").toString())) {

                            wifiFlag = "YES";
                            break;
                        }
                    }

                    flags.put("SERVICE_EVPN_WIFIM_FIRST", wifiFlag);
                }

// ---------- CASE A : CARD TEMPLATE SELECTION ----------
                if (ontSN != null) {

                    String ontGdn = "ONT" + ontSN;
                    Optional<LogicalDevice> optOnt = deviceRepository.findByDiscoveredName(ontGdn);

                    if (optOnt.isPresent()) {

                        Map<String,Object> ontProps = safeProps(optOnt.get().getProperties());
                        Object parentOlt = ontProps.get("oltPosition");

                        if (parentOlt != null) {

                            Optional<LogicalDevice> optOlt = deviceRepository.findByDiscoveredName(parentOlt.toString());

                            if (optOlt.isPresent()) {

                                Map<String,Object> oltProps = safeProps(optOlt.get().getProperties());

                                String evpnOntPort = (ontPort == null ? "" : ontPort.trim());

                                Object cardTemplate =
                                        "5".equals(evpnOntPort)
                                                ? oltProps.get("port5Template")
                                                : oltProps.get("port3Counter");

                                flags.put("SERVICE_TEMPLATE_CARD", existsString(cardTemplate));
                            }
                        }
                    }
                }


            }
            // ======================= EVPN / ENTERPRISE Extended Logic =======================
// ---------------------- Case B : UnconfigureIPBH + IPBH -------------------------
            if ("UnconfigureIPBH".equalsIgnoreCase(actionType)
                    && equalsIgnoreCase(productSubtype, "IPBH")
                    && subscriber != null
                    && serviceID != null) {

                log.error("Trace: Case-B UnconfigureIPBH + IPBH triggered");

                try {
                    String rfsName = "RFS" + Constants.UNDER_SCORE + subscriber + Constants.UNDER_SCORE + serviceID;

                    Optional<Service> optRfs = serviceCustomRepository.findByDiscoveredName(rfsName);
                    if (optRfs.isPresent()) {

                        Service rfs = optRfs.get();
                        Set<LogicalDevice> usedRes = rfs.getUsedResource().stream().map(r->(LogicalDevice)r).collect(Collectors.toSet());

                        if (usedRes != null) {
                            for (Resource res : usedRes) {
                                if (res.getDiscoveredName() != null
                                        && res.getDiscoveredName().contains("ONT")) {

                                    Map<String, Object> rp = safeProps(res.getProperties());

                                    ontSN = (String) rp.getOrDefault("serialNo", ontSN);

                                    flags.put("SERVICE_SN", (String) rp.getOrDefault("serialNo", ""));
                                    flags.put("ONT_MODEL", (String) rp.getOrDefault("deviceModel", ""));
                                    flags.put("SERVICE_LINK", "ONT");

                                    break;
                                }
                            }
                        }

                        // derive subscription
                        String subName1 = subscriber + Constants.UNDER_SCORE + serviceID;
                        subscriptionRepository.findByDiscoveredName(subName1).ifPresent(sub -> {
                            Map<String, Object> sp = safeProps(sub.getProperties());
                            flags.put("SERVICE_LINK", (String) sp.getOrDefault("serviceLink", ""));
                            Customer cust = sub.getCustomer();
                            if (cust != null) {
                                Map<String,Object> cp = safeProps(cust.getProperties());
                                flags.put("FIRST_NAME", (String) cp.getOrDefault("subscriberFirstName",""));
                                flags.put("LAST_NAME", (String) cp.getOrDefault("subscriberLastName",""));
                            }
                            });
                    }
                } catch (Exception e) {
                    log.error("Trace: Case-B failed {}", e.getMessage());
                }
            }
            // ---------------------- Case C : Unconfigure EVPN / Enterprise ------------------
            if ((containsIgnoreCase(productType, "EVPN") || containsIgnoreCase(productType, "ENTERPRISE"))
                    && equalsIgnoreCase(actionType, "Unconfigure")
                    && !equalsIgnoreCase(productSubtype, "WIFI Maintenance")
                    && ontSN != null) {

                log.error("Trace: Case-C EVPN Unconfigure Non-WIFI triggered");

                try {

                    final String finalOntSn = ontSN;

                    List<Service> rfsMatched = StreamSupport
                            .stream(serviceCustomRepository.findAll().spliterator(), false)
                            .filter(sc -> sc.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_RFS))
                            .filter(sc -> sc.getDiscoveredName() != null)
                            .filter(sc -> sc.getDiscoveredName().contains(finalOntSn))
                            .collect(Collectors.toList());

                    int rfsCount = rfsMatched.size();

                    if (rfsCount == 2) {

                        log.error("Trace: Case-C ONT has exactly 2 RFS");

                        List<Subscription> evpnSubs = new ArrayList<>();
                        List<String> setarSubset = new ArrayList<>();

                        // ---- Loop RFS → Resolve → Subscription ----
                        for (Service rfs : rfsMatched) {

                            Service resolved = serviceCustomRepository
                                    .findByDiscoveredName(rfs.getDiscoveredName())
                                    .orElse(null);
                            if (resolved == null) continue;

                            Service cfs = resolved.getUsedService().stream().findFirst().orElse(null);
                            if (cfs == null) continue;

                            String prodName = cfs.getUsingService().stream()
                                    .filter(ser -> ser.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_PRODUCT))
                                    .findFirst()
                                    .get()
                                    .getDiscoveredName();

                            Product product = productRepository.findByDiscoveredName(prodName).orElse(null);
                            if (product == null) continue;

                            Subscription sub = product.getSubscription().stream().findFirst().orElse(null);
                            if (sub == null) continue;

                            evpnSubs.add(sub);

                            Map<String, Object> sp = safeProps(sub.getProperties());
                            String subType = (String) sp.getOrDefault("serviceSubType", "");

                            if ("WIFI Maintenance".equalsIgnoreCase(subType)) {
                                setarSubset.add(sub.getDiscoveredName());
                            }
                        }

                        // ---- If NO EVPN subscription, just skip remaining ----
                        if (evpnSubs.isEmpty()) {
                            log.error("Trace: Case-C no EVPN subscription found, skipping Case-C internals");
                        } else {

                            Subscription currentEvpn = evpnSubs.get(0);
                            Subscription currentEvpn1=evpnSubs.get(1);
                            Map<String, Object> evpnProps = safeProps(currentEvpn.getProperties());
                            Map<String, Object> evpnProp = safeProps(currentEvpn.getProperties());

                            String evpnPort = (String) evpnProps.getOrDefault("evpnPort", "");
                            String tempVlan = (String) evpnProps.getOrDefault("evpnQosSessionProfile", "");
                            String tempVpls = (String) evpnProps.getOrDefault("evpnTemplateVPLS", "");
                            String tempCreate = existsString(evpnProps.get("evpnTemplateCreateVLAN"));
                            String tempVlanId = (String) evpnProps.getOrDefault("evpnVLAN", "");

                            flags.put("SERVICE_ONT_PORT", evpnPort);
                            flags.put("QOS_PROFILE", tempVlan);
                            flags.put("SERVICE_TEMPLATE_VPLS", tempVpls);
                            flags.put("SERVICE_TEMPLATE_CREATE", tempCreate);
                            flags.put("SERVICE_VLAN_ID", tempVlanId);
                            flags.put("SERVICE_LINK", (String) evpnProps.getOrDefault("serviceLink", ""));

                            // ---- Resolve ONT ----
                            String ontGdn = "ONT" + ontSN;

                            deviceRepository.findByDiscoveredName(ontGdn).ifPresent(ont -> {

                                Map<String, Object> ontProps = safeProps(ont.getProperties());

                                flags.put("SERVICE_SN", (String) ontProps.getOrDefault("serialNumber", ""));
                                flags.put("ONT_MODEL", (String) ontProps.getOrDefault("deviceModel", ""));

                                Object parentOltObj = ontProps.get("oltPosition");
                                if (parentOltObj == null) {
                                    log.error("Trace: Case-C OLT missing");
                                    return;
                                }

                                deviceRepository.findByDiscoveredName(parentOltObj.toString())
                                        .ifPresent(olt -> {

                                            Map<String, Object> oltProps = safeProps(olt.getProperties());

                                            flags.put("SERVICE_OLT_POSITION",
                                                    (String) oltProps.getOrDefault("oltPosition", ""));

                                            // ---- ONT Card Template ----
                                            Object cardTemplate =
                                                    "5".equals(evpnPort)
                                                            ? oltProps.get("port5Template")
                                                            : oltProps.get("port3Counter");

                                            flags.put("SERVICE_TEMPLATE_CARD", existsString(cardTemplate));

                                            // ---- EVPN WiFi Flag ----
                                            if ("4".equals(evpnPort) && setarSubset.size() == 1) {
                                                flags.put("SERVICE_EVPN_WIFIM_FIRST", "YES");
                                            } else {
                                                flags.put("SERVICE_EVPN_WIFIM_FIRST", "NO");
                                            }

                                            // ---- ONT Port Template Rule ----
                                            if ("1".equals(evpnPort)) {
                                                flags.put("SERVICE_TEMPLATE_PORT1", "New");
                                            } else {
                                                flags.put("SERVICE_TEMPLATE_PORT1", "Exist");
                                            }

                                            // ---- Evaluate OLT Port Templates ----
                                            flags.put("SERVICE_PORT2_EXIST", existsString(oltProps.get("port2Template")));
                                            flags.put("SERVICE_PORT3_EXIST", existsString(oltProps.get("port3Template")));
                                            flags.put("SERVICE_PORT4_EXIST", existsString(oltProps.get("port4Template")));

                                            // ---- Card Template Rule ----
                                            String p2 = flags.get("SERVICE_PORT2_EXIST");
                                            String p3 = flags.get("SERVICE_PORT3_EXIST");
                                            String p4 = flags.get("SERVICE_PORT4_EXIST");

                                            if ("Exist".equals(p2) || "Exist".equals(p3) || "Exist".equals(p4)) {
                                                flags.put("TEMPLATE_NAME_CARD", "Exist");
                                            } else {
                                                flags.put("TEMPLATE_NAME_CARD", "New");
                                            }
                                        });
                            });
                        }
                    }

                } catch (Exception e) {
                    log.error("Trace: Case-C failed {}", e.getMessage());
                }
            }


            // ---------------------- Case D : Non EVPN + Not Configure -----------------------
            // ---------------------- Case D : Non-EVPN / Non-Enterprise + Not Configure ------------------
            else if (!(equalsIgnoreCase(productType, "EVPN") ||
                    equalsIgnoreCase(productType, "ENTERPRISE"))
                    && (actionType == null || !actionType.contains("Configure"))
                    && subscriber != null && serviceID != null && ontSN != null) {

                log.error("Trace: Case-D Non-EVPN Not-Configure triggered");

                try {

                    String subName1 = subscriber + "_" + serviceID + "_" + ontSN;

                    String finalOntSN = ontSN;
                    subscriptionRepository.findByDiscoveredName(subName1).ifPresent(sub -> {

                        Map<String, Object> sp = safeProps(sub.getProperties());

                        // ---------------- BASIC ASSIGNMENTS ----------------
                        String evpnPort = (String) sp.getOrDefault("evpnPort", "");
                        flags.put("SERVICE_ONT_PORT", evpnPort);

                        String vlan = (String) sp.getOrDefault("evpnQosSessionProfile", "");
                        flags.put("QOS_PROFILE", vlan);

                        log.error("Trace Case-D: SERVICE_ONT_PORT=" + evpnPort + "  QOS_PROFILE=" + vlan);

                        // ---------------- RESOLVE OLT ----------------
                        String ontGdn = "ONT" + finalOntSN;

                        deviceRepository.findByDiscoveredName(ontGdn).ifPresent(ont -> {

                            Map<String, Object> ontProps = safeProps(ont.getProperties());
                            Object parentOltObj = ontProps.get("oltPosition");

                            if (parentOltObj == null) {
                                log.error("Trace Case-D: OLT missing for ONT {}", ontGdn);
                                return;
                            }

                            deviceRepository.findByDiscoveredName(parentOltObj.toString()).ifPresent(olt -> {

                                Map<String, Object> oltProps = safeProps(olt.getProperties());

                                // ---------------- READ OLT PORT TEMPLATES ----------------
                                String port2 = existsString(oltProps.get("port2Template"));
                                String port3 = existsString(oltProps.get("port3Template"));
                                String port4 = existsString(oltProps.get("port4Template"));

                                flags.put("SERVICE_PORT2_EXIST", port2);
                                flags.put("SERVICE_PORT3_EXIST", port3);
                                flags.put("SERVICE_PORT4_EXIST", port4);

                                log.error("Trace Case-D: OLT Templates  P2=" + port2 + " P3=" + port3 + " P4=" + port4);

                                // ---------------- CARD TEMPLATE ----------------
                                String cardTemplate = existsString(oltProps.get("generalCardServiceTemplate"));
                                flags.put("SERVICE_TEMPLATE_CARD", cardTemplate);

                                // ---------------- PORT LOGIC ----------------
                                flags.put("SERVICE_TEMPLATE_PORT", "New");   // Always new per rule

                                switch (evpnPort) {

                                    case "4":
                                        flags.put("SERVICE_PORT_EXIST4", "New");
                                        // already set → check others for card decision
                                        break;

                                    case "3":
                                        flags.put("SERVICE_PORT_EXIST3", "New");
                                        break;

                                    case "2":
                                        flags.put("SERVICE_PORT_EXIST2", "New");
                                        break;

                                    default:
                                        log.error("Trace Case-D: Unknown evpnPort {}", evpnPort);
                                }

                                // ---------------- CARD EVALUATION RULE ----------------
                                String p2 = flags.get("SERVICE_PORT2_EXIST");
                                String p3 = flags.get("SERVICE_PORT3_EXIST");
                                String p4 = flags.get("SERVICE_PORT4_EXIST");

                                if ("Exist".equalsIgnoreCase(p2)
                                        || "Exist".equalsIgnoreCase(p3)
                                        || "Exist".equalsIgnoreCase(p4)) {

                                    flags.put("SERVICE_TEMPLATE_CARD", "Exist");

                                } else {
                                    flags.put("SERVICE_TEMPLATE_CARD", "New");
                                }

                                log.error("Trace Case-D FINAL -> PORT="
                                        + evpnPort
                                        + "  TEMPLATE_PORT=" + flags.get("SERVICE_TEMPLATE_PORT")
                                        + "  CARD=" + flags.get("SERVICE_TEMPLATE_CARD"));
                            });
                        });

                        log.error("Trace: Case-D processing completed");
                    });

                } catch (Exception e) {
                    log.error("Trace: Case-D failed {}", e.getMessage());
                }
            }

            // ---------------------- Case E : Fallback Logic ---------------------------------
            // ---------------------- Case E : Fallback Logic ---------------------------------
            else {
                log.error("Trace: Case-E Fallback executed");

                try {
                    if (ontSN != null) {

                        String ontGdn = "ONT" + ontSN;

                        deviceRepository.findByDiscoveredName(ontGdn).ifPresent(ont -> {

                            Map<String, Object> ontProps = safeProps(ont.getProperties());
                            Object parentOltObj = ontProps.get("oltPosition");

                            if (parentOltObj != null) {

                                String oltGdn = parentOltObj.toString();

                                deviceRepository.findByDiscoveredName(oltGdn).ifPresent(olt -> {

                                    Map<String, Object> oltProps = safeProps(olt.getProperties());

                                    // Card template
                                    flags.put("SERVICE_TEMPLATE_CARD",
                                            existsString(oltProps.get("voipServiceTemplate")));

                                    // Only for Unconfigure case
                                    if ("Unconfigure".equalsIgnoreCase(actionType)) {

                                        flags.put("SERVICE_TEMPLATE_VEIP",
                                                existsString(oltProps.get("veipServiceTemplate")));

                                        flags.put("SERVICE_TEMPLATE_HSI",
                                                existsString(oltProps.get("veipHsiTemplate")));

                                        flags.put("SERVICE_TEMPLATE_VOIP",
                                                existsString(oltProps.get("voipServiceTemplate")));

                                        flags.put("SERVICE_TEMPLATE_POTS1",
                                                existsString(oltProps.get("potsTemplate1")));

                                        flags.put("SERVICE_TEMPLATE_POTS2",
                                                existsString(oltProps.get("potsTemplate2")));

                                        flags.put("SERVICE_TEMPLATE_IPTV",
                                                existsString(oltProps.get("iptvServiceTemplate")));
                                    }

                                    log.error("Trace: Case-E fallback OLT template evaluation completed");
                                });
                            }
                        });
                    }

                } catch (Exception e) {
                    log.error("Trace: Case-E fallback failed {}", e.getMessage());
                }
            }

            log.error("------------Test Trace # 15---------------");
            flags.putIfAbsent("SERVICE_EXIST", "Exist");
            flags.putIfAbsent("SERVICE_IPTV_EXIST", flags.getOrDefault("SERVICE_TEMPLATE_IPTV", "New"));
            flags.putIfAbsent("QOS_PROFILE", flags.getOrDefault("QOS_PROFILE", ""));

            log.error("------------Test Trace # 16---------------");
            log.error(Constants.ACTION_COMPLETED);
            log.error("Trace: QueryFlags completed - returning flags map with " + flags.size() + " entries");
            return new QueryFlagsResponse("200", "UIV action QueryFlags executed successfully.", getCurrentTimestamp(), flags);

        } catch (Exception ex) {
            log.error("Unhandled exception during QueryFlags", ex);
            String msg = ERROR_PREFIX + "Internal server error occurred";
            return new QueryFlagsResponse("500", msg + " - " + ex.getMessage(), String.valueOf(System.currentTimeMillis()), Collections.emptyMap());
        }
    }


    private void initializeFlags(Map<String, String> flags) {
        String[] keys = {
                "SERVICE_EXIST","SERVICE_EVPN_EXIST","SERVICE_PORT_EXIST","SERVICE_VEIP_EXIST","SERVICE_VOIP_EXIST",
                "SERVICE_VOIP_NUMBER1","SERVICE_VOIP_NUMBER2","SERVICE_PORT2_EXIST","SERVICE_PORT3_EXIST","SERVICE_PORT4_EXIST",
                "SERVICE_POTS1_EXIST","SERVICE_POTS2_EXIST","SERVICE_TEMPLATE_VLAN","SERVICE_VLAN_ID","SERVICE_TEMPLATE_CREATE",
                "SERVICE_TEMPLATE_CARD","SERVICE_TEMPLATE_PORT","SERVICE_TEMPLATE_MGMT","SERVICE_TEMPLATE_MGMT_CREATE",
                "SERVICE_TEMPLATE_VEIP","SERVICE_TEMPLATE_HSI","SERVICE_TEMPLATE_ONT","SERVICE_TEMPLATE_VOIP","SERVICE_TEMPLATE_POTS1",
                "SERVICE_TEMPLATE_POTS2","SERVICE_HSI_EXIST","SERVICE_LINK","SERVICE_SN","SERVICE_MAC","ONT_MODEL","SERVICE_PORT5_EXIST",
                "SERVICE_TEMPLATE_VPLS","SERVICE_TEMPLATE_IPTV","SERVICE_IPTV_EXIST","SERVICE_ID","SERVICE_EVPN_WIFIM_FIRST",
                "SERVICE_OLT_POSITION","ONT_TEMPLATE","SERVICE_OLT_POSITION","CBM_MAC","IPTV_COUNT","FIBERNET_COUNT","QOS_PROFILE",
                "FIRST_NAME","LAST_NAME","ACCOUNT_EXIST","SERVICE_FLAG","SERVICE_ONT_PORT","KENAN_UIDNO","SIMA_CUST_ID",
                "CBM_ACCOUNT_EXIST","VOICE_POTS_PORT","RESOURCE_MAC_MTA_OLD","RESOURCE_MODEL_MTA_OLD","BRIDGE_SERVICE",
                "QOS_PROFILE_BRIDGE"
        };
        for (String k : keys) flags.put(k, "");
    }

    private Map<String, Object> safeProps(Map<String, Object> p) {
        return p == null ? new HashMap<>() : p;
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private boolean equalsAnyIgnoreCase(String val, String... tokens) {
        if (val == null) return false;
        for (String t : tokens) if (t != null && val.equalsIgnoreCase(t)) return true;
        return false;
    }

    private boolean containsIgnoreCase(String val, String token) {
        if (val == null || token == null) return false;
        return val.toLowerCase().contains(token.toLowerCase());
    }

    private String existsString(Object o) {
        if (o == null) return "New";
        String s = String.valueOf(o);
        if (s.trim().isEmpty()) return "New";
        return "Exist";
    }

    private String deriveBridgeServiceForSubscriberRfs(
            List<Service> allRfs,
            String ontSN,
            String subscriber) {

        String bridgeService = "NA";

        if (allRfs == null || ontSN == null) {
            return bridgeService;
        }

        for (Service rfs : allRfs) {
            try {
                Service rfs1=serviceCustomRepository.findByDiscoveredName(rfs.getDiscoveredName()).get();
                Service cfs=rfs1.getUsedService().stream().findFirst().get();
                cfs = serviceCustomRepository.findByDiscoveredName(cfs.getDiscoveredName()).get();
                String productName = cfs.getUsingService().stream().filter(ser->ser.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_PRODUCT)).findFirst().get().getDiscoveredName();
                Product product=productRepository.findByDiscoveredName(productName).get();
                Subscription sub=product.getSubscription().stream().findFirst().get();

                if (sub == null || sub.getDiscoveredName() == null) {
                    continue;
                }

                Map<String, Object> sp = safeProps(sub.getProperties());

                if ("Bridged".equalsIgnoreCase(
                        (String) sp.getOrDefault("serviceSubType", "")) &&
                        sub.getDiscoveredName().contains(ontSN)) {

                    // 🔥 DO NOT RETURN – last match wins
                    bridgeService =
                            (String) sp.getOrDefault("serviceID", "NA");
                }
            } catch (Exception ignore) {
            }
        }
        return bridgeService;
    }

    private Map<String, String> executeStep6Flags(
                                                          String ontSN,
                                                  String serviceID,
                                                  String subscriber,
                                                  String actionType,
                                                  String productSubtype) {

        Map<String, String> result = new HashMap<>();

        result.put("ACCOUNT_EXIST", "New");
        result.put("SERVICE_FLAG", "New");
        result.put("CBM_ACCOUNT_EXIST", "New");

        List<Subscription> subsForCustomer = new ArrayList<>();

        for (Subscription s : subscriptionRepository.findAll()) {
            if (s.getDiscoveredName() != null &&
                    s.getDiscoveredName().contains(subscriber)) {
                subsForCustomer.add(s);
            }
        }

        if (Arrays.asList("Unconfigure", "MoveOut",
                        "ChangeTechnology", "AccountTransfer")
                .contains(actionType)) {

            int subCount = subsForCustomer.size();

            // ================= CASE 1: NO SUBSCRIPTIONS =================
            if (subCount == 0) {
                result.put("ACCOUNT_EXIST", "New");
                result.put("SERVICE_FLAG", "New");
                result.put("CBM_ACCOUNT_EXIST", "New");
                return result;
            }

            // ================= CASE 2: ONE SUBSCRIPTION =================
            if (subCount == 1) {

                result.put("ACCOUNT_EXIST", "New");
                result.put("SERVICE_FLAG", "New");

                Map<String, Object> p =
                        safeProps(subsForCustomer.get(0).getProperties());

                if ("Cable_Modem".equalsIgnoreCase(
                        (String) p.get("serviceLink"))) {
                    result.put("CBM_ACCOUNT_EXIST", "New");
                }

                Object sima = p.get("simaCustId");
                if (sima != null && !sima.toString().isEmpty()) {
                    result.put("SIMA_CUST_ID", sima.toString());
                }

                return result;
            }

            // ================= CASE 3: MULTIPLE SUBSCRIPTIONS =================
            result.put("ACCOUNT_EXIST", "Exist");
            result.put("SERVICE_FLAG", "Exist");

            int cbmCount = 0;
            Set<String> macSet = new HashSet<>();

            for (Subscription s : subsForCustomer) {
                Map<String, Object> p = safeProps(s.getProperties());

                if ("Cable_Modem".equalsIgnoreCase(
                        (String) p.get("serviceLink"))) {

                    cbmCount++;

                    Object mac = p.get("serviceMac");
                    if (mac != null && !mac.toString().isEmpty()) {
                        macSet.add(mac.toString());
                    }
                }

                Object sima = p.get("simaCustId");
                if (sima != null && !sima.toString().isEmpty()) {
                    result.put("SIMA_CUST_ID", sima.toString());
                }
            }

            // ================= EXACT SPEC LOGIC =================
            if (Arrays.asList("ChangeTechnology", "AccountTransfer")
                    .contains(actionType)) {

                result.put("CBM_ACCOUNT_EXIST",
                        macSet.size() > 1 ? "Exist" : "New");

            } else {
                // Unconfigure OR MoveOut
                result.put("CBM_ACCOUNT_EXIST",
                        cbmCount > 1 ? "Exist" : "New");
            }
        }

        return result;
    }


    private String getCurrentTimestamp() {
        return Instant.now().toString();
    }
}