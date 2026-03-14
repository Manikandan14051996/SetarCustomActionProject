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

import static com.nokia.nsw.uiv.utils.Constants.*;

@Component
@RestController
@Action
@Slf4j
public class QueryFlags implements HttpAction {

    private static final String ACTION_LABEL = QUERY_FLAGS;
    private static final String ERROR_PREFIX = "UIV action QueryFlags execution failed - ";

    @Autowired
    private LogicalDeviceCustomRepository deviceRepository;
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
        log.info("Executing action {}", ACTION_LABEL);

        QueryFlagsRequest request = (QueryFlagsRequest) actionContext.getObject();

        Map<String, String> flags = new HashMap<>();
        initializeFlags(flags);

        String subscriber = request.getSubscriberName();
        String ontSN = request.getOntSN();
        String productName = request.getProductType();
        String productSubType = request.getProductSubtype();
        String actionType = request.getActionType();
        String serviceID = request.getServiceId();
        String ontPort = request.getOntPort();

        Validations.validateMandatory(subscriber, "subscriberName");
        Validations.validateMandatory(ontSN, "ontSN");
        Validations.validateMandatory(productName, "productType");
        Validations.validateMandatory(productSubType, "productSubtype");

        String subscriptionName = subscriber + UNDER_SCORE + serviceID;

        String serviceLink = "";
        String serviceSN = "";
        String serviceMAC = "";
        String cbmmac = "";
        String evpnPort = "";
        String voicePotsPort = null;
        String qosProfile = "";
        String qosProfileBridge = "";
        String bridgeService = "NA";
        String subscriberFirstName = "";
        String subscriberLastName = "";
        String accountExistFlag = "";
        String cbmAccountExistFlag = "";
        String simaCustId = "";
        String serviceFlag = "";
        String kenanUidNumber = "";
        String mtaMacAddrOld = "";
        String mtaModelOld = "";
        String ontModel = "";
        String oltPosition = "";
        String ontTemplate = "";
        String serviceOltPosition = "";
        String serviceevpnwififlag = "NO";

        String templateNameOnt = "New";
        String templateNameCard = "New";
        String templateNamePort = "New";
        String templateNamePort2 = "New";
        String templateNamePort3 = "New";
        String templateNamePort4 = "New";
        String templateNamePort5 = "New";
        String templateNameVeip = "New";
        String templateNameVoip = "New";
        String templateNamePots1 = "New";
        String templateNamePots2 = "New";
        String templateNameHSI = "New";
        String templateNameIPTV = "New";

        String tempVLAN = "";
        String tempVPLS = "";
        String tempCreate = "";
        String tempCard = "";
        String tempPortTemp = "";
        String tempTemplateMGMT = "";
        String tempTemplateCreate = "";
        String tempVlanID = "";
        String tempVEIP = "";
        String tempONT = "";
        String tempVOIP = "";
        String tempPOTS1 = "";
        String tempPOTS2 = "";
        String tempHSI = "";
        String tempIPTV = "";

        String number1 = null;
        String number2 = null;
        String oltGdn = "";
        List<Service> rfscounts;
        List<Service> rfslist = new ArrayList<>();
        String[] iptvServiceID = new String[15];
        LogicalDevice ontdevice=new LogicalDevice();
        LogicalDevice oltdevice=new LogicalDevice();

        String serviceidflag = "New";
        String iptvCount = "0";
        String fibernetCount = "0";

        if (!equalsIgnoreCase(productName, "VOIP") && !equalsIgnoreCase(productName, "Voice")) {
            number1 = "Available";
            number2 = "Available";
        }

        if (ontSN != null) {
            if (ontSN.startsWith("ALC")) serviceLink = "ONT";
            else if (ontSN.startsWith("CW")) serviceLink = "SRX";
        }

        if ((equalsAnyIgnoreCase(productSubType, "Broadband", "Voice", "Cloudstarter", "Bridged") ||
                equalsIgnoreCase(productName, "ENTERPRISE")) &&
                !"Configure".equalsIgnoreCase(actionType) &&
                (ontSN == null || ontSN.trim().isEmpty() || "NA".equalsIgnoreCase(ontSN))) {

            String rfsPattern = "RFS_" + subscriber + (serviceID != null ? "_" + serviceID : "");
            List<Service> rfsTempLists = (List<Service>) serviceCustomRepository.findAll();
            List<Service> rfsTempList = rfsTempLists.stream()
                    .filter(s -> SETAR_KIND_SETAR_RFS.equalsIgnoreCase(s.getKind()))
                    .filter(s -> s.getDiscoveredName() != null && s.getDiscoveredName().contains(rfsPattern))
                    .collect(Collectors.toList());

            for (Service rfs : rfsTempList) {
                Service cfs = getFirstUsedService(rfs);
                if (cfs == null) continue;

                Product prod = getProductFromCFS(cfs);
                if (prod == null) continue;

                Subscription sub = getFirstSubscription(prod);
                if (sub == null) continue;

                String subServiceID = safeString(sub.getProperties().get("serviceID"));
                if (serviceID != null && serviceID.equals(subServiceID)) {
                    for (Resource res : rfs.getUsedResource()) {
                        if (res instanceof LogicalDevice ld) {
                            String name = ld.getDiscoveredName();
                            if (name != null) {
                                Map<String, Object> props = safeProps(ld.getProperties());
                                if (name.contains("ONT")) {
                                    ontSN = safeString(props.get("serialNo"));
                                    serviceLink = "ONT";
                                    serviceSN = ontSN;
                                    flags.put("SERVICE_SN", ontSN);
                                    flags.put("SERVICE_LINK", "ONT");
                                } else if (name.contains("CBM")) {
                                    serviceLink = "Cable_Modem";
                                    flags.put("SERVICE_LINK", serviceLink);
                                    String mac = safeString(props.get("macAddress"));
                                    if (mac != null && !mac.isEmpty()) {
                                        serviceSN = mac;
                                        cbmmac = mac;
                                        flags.put("SERVICE_SN", mac);
                                        flags.put("CBM_MAC", mac);
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }

            bridgeService = deriveBridgeService(rfsTempList, ontSN);
            flags.put("BRIDGE_SERVICE", bridgeService);
        }


        List<Service> rfsList = null;
        if (serviceID != null && !serviceID.trim().isEmpty()) {
            List<Service> rfsLists = (List<Service>) serviceCustomRepository.findAll();
            rfsList = rfsLists.stream()
                    .filter(s -> Constants.SETAR_KIND_SETAR_RFS.equalsIgnoreCase(s.getKind()))
                    .filter(s -> {
                        String name = s.getDiscoveredName();
                        if (name == null) return false;
                        String[] parts = name.split(Constants.UNDER_SCORE);
                        return parts.length > 2 && serviceID.equals(parts[2]);
                    })
                    .collect(Collectors.toList());

            serviceidflag = rfsList.isEmpty() ? "New" : "Exist";
        }
        flags.put("SERVICE_ID", serviceidflag);

        if (ontSN != null && serviceID != null && subscriber != null) {
            Map<String, String> accountFlags = computeAccountAndServiceFlags(
                    subscriber, actionType, productSubType, ontSN, serviceID);
            accountExistFlag = accountFlags.getOrDefault("ACCOUNT_EXIST", "New");
            serviceFlag = accountFlags.getOrDefault("SERVICE_FLAG", "New");
            simaCustId = accountFlags.getOrDefault("SIMA_CUST_ID", "");
            cbmAccountExistFlag = accountFlags.getOrDefault("CBM_ACCOUNT_EXIST", "New");

            flags.put("ACCOUNT_EXIST", accountExistFlag);
            flags.put("SERVICE_FLAG", serviceFlag);
            flags.put("CBM_ACCOUNT_EXIST", cbmAccountExistFlag);
            if (!simaCustId.isEmpty()) flags.put("SIMA_CUST_ID", simaCustId);
        }

        if (ontSN != null) {
            if (equalsIgnoreCase(productSubType, "IPTV")) {
                String effectiveOntSN = "";
                if (equalsIgnoreCase(actionType, "Unconfigure")) {
                    String subGdn = subscriber + UNDER_SCORE + serviceID;
                    Optional<Subscription> subOpt = subscriptionRepository.findByDiscoveredName(subGdn);
                    if (subOpt.isPresent()) {
                        Map<String, Object> p = safeProps(subOpt.get().getProperties());
                        String sLink = safeString(p.get("serviceLink"));
                        String sSN = safeString(p.get("serviceSN"));
                        String sMAC = safeString(p.get("serviceMac"));

                        if (sSN != null && !sSN.isEmpty()) {
                            if ("ONT".equalsIgnoreCase(sLink) || "SRX".equalsIgnoreCase(sLink)) {
                                effectiveOntSN = sSN;
                            } else if ("Cable_Modem".equalsIgnoreCase(sLink)) {
                                effectiveOntSN = sMAC;
                            } else {
                                effectiveOntSN = ontSN;
                            }
                        } else {
                            effectiveOntSN = ontSN;
                        }
                    }
                } else {
                    effectiveOntSN = ontSN;
                }
                String ontsn = effectiveOntSN;
                if (!"NA".equalsIgnoreCase(effectiveOntSN) && effectiveOntSN != null && !effectiveOntSN.isEmpty()) {
                    List<Subscription> subscriptions = (List<Subscription>) subscriptionRepository.findAll();
                    long count = subscriptions.stream()
                            .filter(s -> {
                                Map<String, Object> p = safeProps(s.getProperties());
                                return "IPTV".equalsIgnoreCase(safeString(p.get("serviceSubType"))) &&
                                        (ontsn.equals(safeString(p.get("serviceSN"))) ||
                                                ontsn.equals(safeString(p.get("macAddress"))));
                            })
                            .count();
                    iptvCount = String.valueOf(count);
                    flags.put("IPTV_COUNT", iptvCount);
                }
            } else if (equalsAnyIgnoreCase(productSubType, "Fibernet", "Broadband", "Voice", "Bridged") ||
                    (equalsIgnoreCase(productName, "Broadband") && equalsIgnoreCase(productSubType, "Bridged") || equalsIgnoreCase(productSubType, "Cloudstarter"))) {

                rfslist = (List<Service>) serviceCustomRepository.findAll();
                if (ontSN != null && !ontSN.equals("NA") && ontSN.contains("ALCL")) {
                    String containsontsn = ontSN;
                    rfscounts = rfslist.stream()
                            .filter(s -> SETAR_KIND_SETAR_RFS.equalsIgnoreCase(s.getKind()))
                            .filter(s -> s.getDiscoveredName() != null && s.getDiscoveredName().contains(containsontsn))
                            .collect(Collectors.toList());
                } else {
                    rfscounts = rfslist.stream()
                            .filter(s -> SETAR_KIND_SETAR_RFS.equalsIgnoreCase(s.getKind()))
                            .filter(s -> s.getDiscoveredName() != null && s.getDiscoveredName().contains(subscriber))
                            .collect(Collectors.toList());
                }
                String ontsn = ontSN;
                long subCount = rfscounts.stream()
                        .filter(rfs -> {
                            String name = rfs.getDiscoveredName();
                            if (name == null) return false;
                            String lastPart = name.substring(name.lastIndexOf(UNDER_SCORE) + 1);
                            return lastPart.equalsIgnoreCase(ontsn) &&
                                    equalsAnyIgnoreCase(productName, "Fibernet", "Broadband");
                        })
                        .count();

                fibernetCount = String.valueOf(subCount);
                flags.put("FIBERNET_COUNT", fibernetCount);

                // Bridge service & qosProfileBridge for Modify_CPE
                if (ontSN != null && !ontSN.equals("NA") && ontSN.contains("ALCL")) {
                    bridgeService = deriveBridgeService(rfscounts, ontSN);
                    flags.put("BRIDGE_SERVICE", bridgeService);
                }

                if (!"NA".equalsIgnoreCase(bridgeService) && actionType != null && actionType.contains("Modify_CPE")) {
                    for (Service rfs : rfscounts) {
                        Service cfs = getFirstUsedService(rfs);
                        if (cfs == null) continue;
                        Product prod = getProductFromCFS(cfs);
                        if (prod == null) continue;
                        Subscription sub = getFirstSubscription(prod);
                        if (sub == null) continue;

                        Map<String, Object> p = safeProps(sub.getProperties());
                        if ("Bridged".equalsIgnoreCase(safeString(p.get("serviceSubType"))) &&
                                sub.getDiscoveredName().contains(ontSN)) {
                            qosProfileBridge = safeString(p.get("evpnQosSessionProfile"));
                            flags.put("QOS_PROFILE_BRIDGE", qosProfileBridge);
                            break;
                        }
                    }
                }
                if (!"Configure".equalsIgnoreCase(actionType)) {
                    String subToFind;
                    String subNamefoFind;
                    if (ontSN != null && ontSN.startsWith("ALCL")) {
                        subToFind = subscriber + UNDER_SCORE + serviceID + UNDER_SCORE + ontSN;
                        subNamefoFind = subscriber + UNDER_SCORE + ontSN;
                    } else {
                        subToFind = subscriber + UNDER_SCORE + serviceID;
                        subNamefoFind = subscriber + UNDER_SCORE;
                    }

                    Optional<Subscription> subOpt = subscriptionRepository.findByDiscoveredName(subToFind);
                    if (subOpt.isPresent()) {
                        Map<String, Object> p = safeProps(subOpt.get().getProperties());
                        serviceLink = safeString(p.get("serviceLink"));
                        serviceSN = safeString(p.get("serviceSN"));
                        serviceMAC = safeString(p.get("serviceMac"));
                        cbmmac = safeString(p.get("serviceMac"));
                        qosProfile = safeString(p.get("veipQosSessionProfile"));
                        kenanUidNumber = safeString(p.get("kenanSubscriberId"));
                        if (!serviceLink.isEmpty()) {
                            flags.put("SERVICE_LINK", serviceLink);
                        }

                        if (!serviceSN.isEmpty()) {
                            flags.put("SERVICE_SN", serviceSN);
                        }

                        if (!serviceMAC.isEmpty()) {
                            flags.put("SERVICE_MAC", serviceMAC);
                        }

                        if (!cbmmac.isEmpty()) {
                            flags.put("CBM_MAC", cbmmac);
                        }

                        if (!qosProfile.isEmpty()) {
                            flags.put("QOS_PROFILE", qosProfile);
                        }

                        if (!kenanUidNumber.isEmpty()) {
                            flags.put("KENAN_UIDNO", kenanUidNumber);
                        }


                        if (p.containsKey("oltPosition") && !safeString(p.get("oltPosition")).isEmpty()) {
                            serviceOltPosition = safeString(p.get("oltPosition"));
                            flags.put("SERVICE_OLT_POSITION", serviceOltPosition);
                        }

                        if ("Cable_Modem".equalsIgnoreCase(serviceLink)) {
                            flags.put("SERVICE_VOIP_EXIST", "New");
                            String cbmName = "CBM_" + cbmmac;
                            Optional<LogicalDevice> cbmOpt = deviceRepository.findByDiscoveredName(cbmName);
                            if (cbmOpt.isEmpty() && serviceID != null) {
                                // Original fallback
                                cbmName = "CBM" + serviceID;
                                cbmOpt = deviceRepository.findByDiscoveredName(cbmName);
                            }
                            if (cbmOpt.isPresent()) {
                                LogicalDevice cbm = cbmOpt.get();
                                Map<String, Object> cp = safeProps(cbm.getProperties());
                                number1 = safeString(cp.get("voipPort1"));
                                number2 = safeString(cp.get("voipPort2"));
                                ontModel = safeString(cp.get("deviceModel"));
                                flags.put("SERVICE_VOIP_NUMBER1", number1);
                                flags.put("SERVICE_VOIP_NUMBER2", number2);
                                flags.put("ONT_MODEL", ontModel);

                                if (!"Available".equalsIgnoreCase(number1)) templateNameVoip = "Exist";
                                if (!"Available".equalsIgnoreCase(number2)) templateNameVoip = "Exist";
                                flags.put("SERVICE_VOIP_EXIST", templateNameVoip);

                                if (serviceID != null) {
                                    if (number1 != null && serviceID.equals(number1)) {
                                        voicePotsPort = "1";
                                        flags.put("VOICE_POTS_PORT", "1");
                                    }
                                    if (number2 != null && serviceID.equals(number2)) {
                                        voicePotsPort = "2";
                                        flags.put("VOICE_POTS_PORT", "2");
                                    }
                                }
                            }

                        }
                    }

                    // Subscriber names
                    List<Customer> customers = (List<Customer>) customerRepository.findAll();
                    for (Customer cust : customers) {
                        if (cust.getDiscoveredName().equalsIgnoreCase(subToFind) || cust.getDiscoveredName().contains(subNamefoFind)) {
                            Map<String, Object> cp = safeProps(cust.getProperties());
                            subscriberFirstName = safeString(cp.get("subscriberFirstName"));
                            subscriberLastName = safeString(cp.get("subscriberLastName"));
                            flags.put("FIRST_NAME", subscriberFirstName);
                            flags.put("LAST_NAME", subscriberLastName);
                        }
                    }
                }
            }

        }
        if (rfsList.size() == 0) {
            flags.put("SERVICE_ID", "New");
        } else {
            flags.put("SERVICE_ID", "Exist");
        }


        if ("Modify".equalsIgnoreCase(actionType) &&
                equalsAnyIgnoreCase(productName, "MOCA", "BridgeMode", "APMNT", "WIFION") &&
                serviceID != null) {

            Optional<Subscription> subOpt = subscriptionRepository.findByDiscoveredName(subscriptionName);

            if (subOpt.isPresent()) {
                Subscription sub = subOpt.get();

                Map<String, Object> p = safeProps(sub.getProperties());

                cbmmac = safeString(p.get("serviceMac"));

                flags.put("CBM_MAC", cbmmac);
            }
        }

        if (productName.contains("IPTV") && !"Configure".equalsIgnoreCase(actionType)) {
            Optional<Subscription> subOpt = subscriptionRepository.findByDiscoveredName(subscriptionName);

            if (subOpt.isPresent()) {
                Subscription sub = subOpt.get();

                Map<String, Object> p = safeProps(sub.getProperties());

                serviceLink = safeString(p.get("serviceLink"));
                serviceSN = safeString(p.get("serviceSN"));
                serviceMAC = safeString(p.get("serviceMac"));

                flags.put("SERVICE_LINK", serviceLink);
                flags.put("SERVICE_SN", serviceSN);
                flags.put("SERVICE_MAC", serviceMAC);

                if ("NA".equalsIgnoreCase(ontSN) && serviceSN != null && !serviceSN.isEmpty()) {
                    ontSN = serviceSN;
                }
            }
        }

        if (serviceLink != null && equalsAnyIgnoreCase(serviceLink, "ONT", "SRX")) {
            String ontGdn = "ONT" + ontSN;
            if (ontGdn.length() > 100) {
                return new QueryFlagsResponse("400", ERROR_PREFIX + "ONT name too long", getCurrentTimestamp(), flags);
            }

            Optional<LogicalDevice> ontOpt = deviceRepository.findByDiscoveredName(ontGdn);

            if (ontOpt.isPresent()) {
                LogicalDevice ontDev = ontOpt.get();
                ontdevice=ontDev;

                Map<String, Object> ontP = safeProps(ontDev.getProperties());
                ontModel = safeString(ontP.get("deviceModel"));
                serviceSN = safeString(ontP.get("serialNo"));

                ontTemplate = safeString(ontP.get("ontTemplate"));
                if (ontTemplate != null && !ontTemplate.isEmpty()) {
                    flags.put("ONT_TEMPLATE", ontTemplate);
                }

                if (ontModel != null && !ontModel.isEmpty()) {
                    flags.put("ONT_MODEL", ontModel);
                }

                if (serviceSN != null && !serviceSN.isEmpty()) {
                    flags.put("SERVICE_SN", serviceSN);
                }

                // POTS ports from ONT
                number1 = safeString(ontP.get("potsPort1Number"));
                number2 = safeString(ontP.get("potsPort2Number"));

                flags.put("SERVICE_VOIP_NUMBER1", number1);
                flags.put("SERVICE_VOIP_NUMBER2", number2);

                if (serviceID != null) {
                    if (number1 != null && serviceID.equals(number1)) {
                        voicePotsPort = "1";
                    }
                    if (number2 != null && serviceID.equals(number2)) {
                        voicePotsPort = "2";
                    }
                    flags.put("VOICE_POTS_PORT", voicePotsPort);
                }

                Object oltPosObj = ontP.get("oltPosition");

                if (oltPosObj != null) {
                    oltGdn = oltPosObj.toString();
                    oltPosition = oltGdn;

                    flags.put("OLT_POSITION", oltPosition);

                    Optional<LogicalDevice> oltOpt = deviceRepository.findByDiscoveredName(oltGdn);

                    if (oltOpt.isPresent()) {
                        LogicalDevice olt = oltOpt.get();

                        Map<String, Object> oltP = safeProps(olt.getProperties());

                        templateNameOnt = exists(oltP.get("ontTemplate"));
                        templateNameVeip = exists(oltP.get("veipServiceTemplate"));
                        templateNameHSI = exists(oltP.get("veipHsiTemplate"));
                        templateNameVoip = exists(oltP.get("voipServiceTemplate"));
                        templateNamePots1 = exists(oltP.get("voipPots1Template"));
                        templateNamePots2 = exists(oltP.get("voipPots2Template"));
                        templateNameIPTV = exists(oltP.get("veipIptvTemplate"));

                        flags.put("SERVICE_EXIST", templateNameOnt);
                        flags.put("SERVICE_VEIP_EXIST", templateNameVeip);
                        flags.put("SERVICE_HSI_EXIST", templateNameHSI);
                        flags.put("SERVICE_VOIP_EXIST", templateNameVoip);
                        flags.put("SERVICE_POTS1_EXIST", templateNamePots1);
                        flags.put("SERVICE_POTS2_EXIST", templateNamePots2);
                        flags.put("SERVICE_IPTV_EXIST", templateNameIPTV);

                        if (oltP.get("ontTemplate") != null && !oltP.get("ontTemplate").toString().isEmpty()) {
                            flags.put("SERVICE_TEMPLATE_ONT", oltP.get("ontTemplate").toString());
                        }
                        // in SRI servicetemplate value only taken form olt device
//                        if (ontTemplate!=null && !ontTemplate.isEmpty()) {
//                            flags.put("SERVICE_TEMPLATE_ONT", ontTemplate);
//                        }

                        if (oltP.get("veipServiceTemplate") != null && !oltP.get("veipServiceTemplate").toString().isEmpty()) {
                            flags.put("SERVICE_TEMPLATE_VEIP", oltP.get("veipServiceTemplate").toString());
                        }

                        if (oltP.get("veipHsiTemplate") != null && !oltP.get("veipHsiTemplate").toString().isEmpty()) {
                            flags.put("SERVICE_TEMPLATE_HSI", oltP.get("veipHsiTemplate").toString());
                        }

                        if (oltP.get("voipServiceTemplate") != null && !oltP.get("voipServiceTemplate").toString().isEmpty()) {
                            flags.put("SERVICE_TEMPLATE_VOIP", oltP.get("voipServiceTemplate").toString());
                        }

                        if (oltP.get("voipPots1Template") != null && !oltP.get("voipPots1Template").toString().isEmpty()) {
                            flags.put("SERVICE_TEMPLATE_POTS1", oltP.get("voipPots1Template").toString());
                        }

                        if (oltP.get("voipPots2Template") != null && !oltP.get("voipPots2Template").toString().isEmpty()) {
                            flags.put("SERVICE_TEMPLATE_POTS2", oltP.get("voipPots2Template").toString());
                        }

                        if (oltP.get("veipIptvTemplate") != null && !oltP.get("veipIptvTemplate").toString().isEmpty()) {
                            flags.put("SERVICE_TEMPLATE_IPTV", oltP.get("veipIptvTemplate").toString());
                        }
                    }
                }

                if ("ENTERPRISE".equalsIgnoreCase(productName) && ontOpt.isPresent()) {

                    Set<Service> tmpRfss = ontOpt.get().getUsingService();
Service finalRfs;
                    if (tmpRfss != null) {

                        for (Service tmpRfs : tmpRfss) {
                            Optional<Service> rfs=serviceCustomRepository.findByDiscoveredName(tmpRfs.getDiscoveredName());
                            if(rfs.isPresent())
                            {
                              finalRfs=rfs.get();
                                if (finalRfs.getDiscoveredName() != null &&
                                        finalRfs.getDiscoveredName().contains(serviceID)) {

                                    Service cfs = finalRfs.getUsedService()
                                            .stream()
                                            .findFirst()
                                            .orElse(null);

                                    if (cfs == null) continue;

                                    Service productService = cfs.getUsingService()
                                            .stream()
                                            .filter(s -> Constants.SETAR_KIND_SETAR_PRODUCT.equalsIgnoreCase(s.getKind()))
                                            .findFirst()
                                            .orElse(null);

                                    if (productService == null) continue;

                                    Product product = productRepository
                                            .findByDiscoveredName(productService.getDiscoveredName())
                                            .orElse(null);

                                    if (product == null) continue;

                                    Subscription subscription = product.getSubscription()
                                            .stream()
                                            .findFirst()
                                            .orElse(null);

                                    if (subscription != null && subscription.getProperties() != null) {

                                        ontPort = Objects.toString(
                                                subscription.getProperties().get("evpnPort"),
                                                ""
                                        );

                                        break;
                                    }
                                }
                            }

                        }
                    }
                }

                // VLAN MGMT template check
                Set<String> countVlan = new TreeSet<>();
                String vlanont = ontPort;
                logicalInterfaceRepository.findAll().forEach(vif -> {
                    String vname = vif.getDiscoveredName();

                    if (vname != null && vname.contains("_P" + vlanont + "_")) {
                        countVlan.add(vname);
                    }
                });

                Set<String> countMgmt = new TreeSet<>();

                for (String vname : countVlan) {

                    Optional<LogicalInterface> vifOpt =
                            logicalInterfaceRepository.findByDiscoveredName(vname);

                    if (vifOpt.isPresent()) {

                        LogicalInterface vif = vifOpt.get();

                        Map<String, Object> vp = safeProps(vif.getProperties());

                        if ("4.3B EVPN SINGLETAGGED VLAN v2"
                                .equalsIgnoreCase(safeString(vp.get("mgmtTemplate")))) {

                            countMgmt.add(vname);
                        }
                    }
                }

                if (!countMgmt.isEmpty()) {
                    tempTemplateMGMT = "4.3B EVPN SINGLETAGGED VLAN v2";
                    flags.put("SERVICE_TEMPLATE_MGMT", tempTemplateMGMT);
                }
            }
        } else {
            if (equalsIgnoreCase(productSubType, "Voice")) {
                List<Subscription> voiceSubscriptions = (List<Subscription>) subscriptionRepository.findAll();
                List<Subscription> voiceSubs = voiceSubscriptions.stream()
                        .filter(s -> s.getDiscoveredName() != null && s.getDiscoveredName().contains(subscriber))
                        .filter(s -> "Voice".equalsIgnoreCase(safeString(safeProps(s.getProperties()).get("serviceSubType"))))
                        .collect(Collectors.toList());

                if (!voiceSubs.isEmpty()) {
                    String macAddr = safeString(safeProps(voiceSubs.get(0).getProperties()).get("serviceMAC"));
                    if (macAddr != null && !macAddr.isEmpty()) {
                        String cbmName = "CBM_" + macAddr;
                        Optional<LogicalDevice> cbmOpt = deviceRepository.findByDiscoveredName(cbmName);
                        if (cbmOpt.isEmpty() && serviceID != null) {
                            cbmName = "CBM" + serviceID;
                            cbmOpt = deviceRepository.findByDiscoveredName(cbmName);
                        }

                        if (cbmOpt.isPresent()) {

                            LogicalDevice cbm = cbmOpt.get();

                            Map<String, Object> p = safeProps(cbm.getProperties());

                            number1 = safeString(p.get("voipPort1"));
                            number2 = safeString(p.get("voipPort2"));
                            ontModel = safeString(p.get("deviceModel"));

                            flags.put("SERVICE_VOIP_NUMBER1", number1);
                            flags.put("SERVICE_VOIP_NUMBER2", number2);
                            flags.put("ONT_MODEL", ontModel);

                            if (number1 != null && serviceID != null && serviceID.equals(number1)) {
                                voicePotsPort = "1";
                                flags.put("VOICE_POTS_PORT", "1");
                            }

                            if (number2 != null && serviceID != null && serviceID.equals(number2)) {
                                voicePotsPort = "2";
                                flags.put("VOICE_POTS_PORT", "2");
                            }

                            if (number1 != null || number2 != null) {
                                templateNameVoip = "Exist";
                                flags.put("SERVICE_VOIP_EXIST", "Exist");
                            }

                            // VOIP reset rule
                            if (equalsAnyIgnoreCase(productName, "VOIP", "Voice")) {

                                if ("Available".equalsIgnoreCase(number1)) {
                                    number1 = null;
                                }

                                if ("Available".equalsIgnoreCase(number2)) {
                                    number2 = null;
                                }

                                flags.put("SERVICE_VOIP_NUMBER1", number1);
                                flags.put("SERVICE_VOIP_NUMBER2", number2);
                            }
                        }
                    }
                }
            }
            if ("Cable_Modem".equalsIgnoreCase(serviceLink)) {

                boolean iptvExists = false;
                boolean broadbandExists = false;

                List<Subscription> subscriptionList = (List<Subscription>) subscriptionRepository.findAll();

                for (Subscription subscription : subscriptionList) {

                    String subtype = subscription.getProperties().get("serviceSubType").toString();

                    if ("IPTV".equalsIgnoreCase(subtype)) {
                        iptvExists = true;
                    }

                    if ("Broadband".equalsIgnoreCase(subtype)) {
                        broadbandExists = true;
                    }
                }

                templateNameIPTV = iptvExists ? "Exist" : "New";
                templateNameVeip = broadbandExists ? "Exist" : "New";

                flags.put("SERVICE_IPTV_EXIST", templateNameIPTV);
                flags.put("SERVICE_VEIP_EXIST", templateNameVeip);
            }
        }


        if (equalsAnyIgnoreCase(actionType, "AccountTransfer", "Modify_CPE", "ChangeTechnology", "Unconfigure", "MoveOut")) {
            List<String> iptvIds = new ArrayList<>();

            if ("ONT".equalsIgnoreCase(serviceLink)) {
                for (Subscription s : subscriptionRepository.findAll()) {

                    if (s.getDiscoveredName() == null ||
                            !s.getDiscoveredName().contains(subscriber)) {
                        continue;
                    }

                    Map<String, Object> props = safeProps(s.getProperties());

                    String serviceSubType = safeString(props.get("serviceSubType"));
                    if (!"IPTV".equalsIgnoreCase(serviceSubType)) {
                        continue;
                    }

                    String serviceSNProp = safeString(props.get("serviceSN"));
                    if (!flags.getOrDefault("SERVICE_SN", ontSN).equals(serviceSNProp)) {
                        continue;
                    }

                    if (iptvIds.size() < 15) {
                        String sid = safeString(props.get("serviceID"));
                        if (sid != null && !sid.isEmpty()) {
                            iptvIds.add(sid);
                        }
                    }
                }
            } else {
                String macLocal = flags.getOrDefault("CBM_MAC", serviceMAC);
                if (!macLocal.isEmpty()) {

                    for (Subscription s : subscriptionRepository.findAll()) {

                        Map<String, Object> props = safeProps(s.getProperties());

                        String serviceSubType = safeString(props.get("serviceSubType"));
                        if (!"IPTV".equalsIgnoreCase(serviceSubType)) {
                            continue;
                        }

                        String macAddr = safeString(props.get("macAddress"));
                        if (!macLocal.equals(macAddr)) {
                            continue;
                        }

                        if (iptvIds.size() < 15) {
                            String sid = safeString(props.get("serviceID"));
                            if (sid != null && !sid.isEmpty()) {
                                iptvIds.add(sid);
                            }
                        }
                    }

                    String cbmGdn = "CBM_" + macLocal;

                    Optional<LogicalDevice> cpeOpt = deviceRepository.findByDiscoveredName(cbmGdn);

                    if (cpeOpt.isPresent()) {

                        LogicalDevice cpe = cpeOpt.get();

                        Map<String, Object> p = safeProps(cpe.getProperties());

                        mtaMacAddrOld = safeString(p.get("macAddressMta"));
                        mtaModelOld = safeString(p.get("deviceModelMta"));

                        flags.put("RESOURCE_MAC_MTA_OLD", mtaMacAddrOld);
                        flags.put("RESOURCE_MODEL_MTA_OLD", mtaModelOld);
                    }
                }
            }

            for (int i = 0; i < Math.min(5, iptvIds.size()); i++) {
                flags.put("Service_IPTV_Service_ID" + UNDER_SCORE + (i + 1), iptvIds.get(i));
            }
        }


        if (productName.contains("EVPN") || productName.contains("ENTERPRISE") ||
                productSubType.contains("Cloudstarter") || productSubType.contains("Bridged")) {
            log.debug("Entering detailed EVPN/Enterprise logic block");
            // CASE A: Configure or Migrate
            if (actionType.contains("Configure") || actionType.contains("Migrate")) {

                // Port templates from OLT
                String evpnPort2Tpl = getOltProperty(oltGdn, "evpnEthPort2Template"); // helper below
                String evpnPort3Tpl = getOltProperty(oltGdn, "evpnEthPort3Template");
                String evpnPort4Tpl = getOltProperty(oltGdn, "evpnEthPort4Template");
                String evpnPort5Tpl = getOltProperty(oltGdn, "evpnEthPort5Template");

                flags.put("SERVICE_PORT2_EXIST", exists(evpnPort2Tpl));
                flags.put("SERVICE_PORT3_EXIST", exists(evpnPort3Tpl));
                flags.put("SERVICE_PORT4_EXIST", exists(evpnPort4Tpl));
                flags.put("SERVICE_PORT5_EXIST", exists(evpnPort5Tpl));

                templateNamePort = "New";

                if ("4".equals(ontPort)) {
                    String portTpl = getOltProperty(oltGdn, "evpnEthPort4Template");
                    if (!portTpl.isEmpty() && portTpl != null) {
                        String counter = "0";
                        String ontname = "";
                        String iswifiMain = "false";

                        // Get attached RFS
                        List<Service> listRFS = (List<Service>) serviceCustomRepository.findAll();
                        String ontsn = ontSN;
                        List<Service> attachedRfs = listRFS.stream()
                                .filter(s -> SETAR_KIND_SETAR_RFS.equalsIgnoreCase(s.getKind()))
                                .filter(s -> s.getDiscoveredName() != null && s.getDiscoveredName().contains(ontsn))
                                .collect(Collectors.toList());

                        if (!attachedRfs.isEmpty() && attachedRfs.size() == 1) {
                            Service firstRfs = attachedRfs.get(0);
                            String rfsName = firstRfs.getDiscoveredName();

                            // Simulate findRFS → get first matching
                            Service finalRfs = serviceCustomRepository.findByDiscoveredName(rfsName).orElse(null);
                            if (finalRfs != null) {
                                // Simulate resource list
                                for (Resource res : finalRfs.getUsedResource()) {
                                    if (res instanceof LogicalDevice ld && ld.getDiscoveredName().contains("ALCL")) {
                                        ontname = ld.getDiscoveredName();
                                    }
                                }

                                Optional<LogicalDevice> ontDevOpt = deviceRepository.findByDiscoveredName(ontname);

                                if (ontDevOpt.isPresent()) {

                                    LogicalDevice ontDev = ontDevOpt.get();

                                    Map<String, Object> p = safeProps(ontDev.getProperties());

                                    if ("1".equals(safeString(p.get("evpnEthPort4Template")))) {
                                        counter = "1";
                                    }
                                }

                                // Subscription lookup from RFS name split
                                String[] parts = rfsName.split(UNDER_SCORE);
                                if (parts.length >= 4) {
                                    String subsname = parts[1] + UNDER_SCORE + parts[2] + UNDER_SCORE + parts[3];
                                    Optional<Subscription> subOpt = subscriptionRepository.findByDiscoveredName(subsname);

                                    if (subOpt.isPresent()) {

                                        Subscription sub = subOpt.get();

                                        Map<String, Object> sp = safeProps(sub.getProperties());

                                        // Check WIFI Maintenance
                                        if ("WIFI Maintenance".equalsIgnoreCase(safeString(sp.get("serviceSubType")))) {
                                            iswifiMain = "true";
                                        }

                                        // Check and store OLT position
                                        if (sp.containsKey("oltPosition") && !safeString(sp.get("oltPosition")).isEmpty()) {
                                            serviceOltPosition = safeString(sp.get("oltPosition"));
                                            flags.put("SERVICE_OLT_POSITION", serviceOltPosition);
                                        }
                                    }
                                }
                            }
                        }

                        if ("true".equals(iswifiMain) && "1".equals(counter)) {
                            serviceevpnwififlag = "YES";
                        } else {
                            serviceevpnwififlag = "NO";
                        }

                        templateNamePort = "Exist";
                    } else {
                        templateNamePort = "New";
                    }
                    flags.put("SERVICE_PORT_EXIST", templateNamePort);
                } else if ("5".equals(ontPort)) {

                    String portTpl = getOltProperty(oltGdn, "evpnEthPort5Template");

                    if (portTpl != null && !portTpl.isEmpty()) {

                        String counter = "0";
                        String ontname = "";
                        String iswifiMain = "false";

                        List<Service> listRFS = (List<Service>) serviceCustomRepository.findAll();
                        String ontsn = ontSN;

                        List<Service> attachedRfs = listRFS.stream()
                                .filter(s -> SETAR_KIND_SETAR_RFS.equalsIgnoreCase(s.getKind()))
                                .filter(s -> s.getDiscoveredName() != null && s.getDiscoveredName().contains(ontsn))
                                .collect(Collectors.toList());

                        if (!attachedRfs.isEmpty() && attachedRfs.size() == 1) {

                            Service firstRfs = attachedRfs.get(0);
                            String rfsName = firstRfs.getDiscoveredName();

                            if (rfsName != null) {

                                for (Resource res : firstRfs.getUsedResource()) {

                                    if (res instanceof LogicalDevice ld
                                            && ld.getDiscoveredName() != null
                                            && ld.getDiscoveredName().contains("ALCL")) {

                                        ontname = ld.getDiscoveredName();
                                        break;
                                    }
                                }

                                if (!ontname.isEmpty()) {

                                    Optional<LogicalDevice> ontDevOpt =
                                            deviceRepository.findByDiscoveredName(ontname);

                                    if (ontDevOpt.isPresent()) {

                                        LogicalDevice ontDev = ontDevOpt.get();
                                        Map<String, Object> p = safeProps(ontDev.getProperties());

                                        // SRI logic (port 5 still checks port 4 template)
                                        if ("1".equals(safeString(p.get("evpnEthPort4Template")))) {
                                            counter = "1";
                                        }
                                    }
                                }

                                String[] parts = rfsName.split(UNDER_SCORE);

                                if (parts.length >= 4) {

                                    String subsname = parts[1] + UNDER_SCORE + parts[2] + UNDER_SCORE + parts[3];

                                    Optional<Subscription> subOpt =
                                            subscriptionRepository.findByDiscoveredName(subsname);

                                    if (subOpt.isPresent()) {

                                        Subscription sub = subOpt.get();
                                        Map<String, Object> sp = safeProps(sub.getProperties());

                                        if ("WIFI Maintenance".equalsIgnoreCase(
                                                safeString(sp.get("serviceSubType")))) {
                                            iswifiMain = "true";
                                        }

                                        String oltPos = safeString(sp.get("oltPosition"));

                                        if (!oltPos.isEmpty()) {
                                            serviceOltPosition = oltPos;
                                            flags.put("SERVICE_OLT_POSITION", serviceOltPosition);
                                        }
                                    }
                                }
                            }
                        }

                        if ("true".equals(iswifiMain) && "1".equals(counter)) {
                            serviceevpnwififlag = "YES";
                        } else {
                            serviceevpnwififlag = "NO";
                        }

                        templateNamePort = "Exist";

                    } else {
                        templateNamePort = "New";
                    }

                    flags.put("SERVICE_PORT_EXIST", templateNamePort);
                } else if ("3".equals(ontPort)) {

                    String portTpl = getOltProperty(oltGdn, "evpnEthPort3Template");

                    if (portTpl != null && !portTpl.isEmpty()) {

                        String counter = "0";
                        String ontname = "";
                        String iswifiMain = "false";

                        List<Service> listRFS = (List<Service>) serviceCustomRepository.findAll();
                        String tmpontsn = ontSN;
                        List<Service> attachedRfs = listRFS.stream()
                                .filter(s -> SETAR_KIND_SETAR_RFS.equalsIgnoreCase(s.getKind()))
                                .filter(s -> s.getDiscoveredName() != null && s.getDiscoveredName().contains(tmpontsn))
                                .collect(Collectors.toList());

                        if (!attachedRfs.isEmpty() && attachedRfs.size() == 1) {

                            Service firstRfs = attachedRfs.get(0);
                            String rfsName = firstRfs.getDiscoveredName();

                            if (rfsName != null) {

                                for (Resource res : firstRfs.getUsedResource()) {

                                    if (res instanceof LogicalDevice ld
                                            && ld.getDiscoveredName() != null
                                            && ld.getDiscoveredName().contains("ALCL")) {

                                        ontname = ld.getDiscoveredName();
                                        break;
                                    }
                                }

                                if (!ontname.isEmpty()) {

                                    Optional<LogicalDevice> ontDevOpt =
                                            deviceRepository.findByDiscoveredName(ontname);

                                    if (ontDevOpt.isPresent()) {

                                        LogicalDevice ontDev = ontDevOpt.get();
                                        Map<String, Object> p = safeProps(ontDev.getProperties());

                                        if ("1".equals(safeString(p.get("evpnEthPort3Template")))) {
                                            counter = "1";
                                        }
                                    }
                                }

                                String[] parts = rfsName.split(UNDER_SCORE);

                                if (parts.length >= 4) {

                                    String subsname = parts[1] + UNDER_SCORE + parts[2] + UNDER_SCORE + parts[3];

                                    Optional<Subscription> subOpt =
                                            subscriptionRepository.findByDiscoveredName(subsname);

                                    if (subOpt.isPresent()) {

                                        Subscription sub = subOpt.get();
                                        Map<String, Object> sp = safeProps(sub.getProperties());

                                        if ("WIFI Maintenance".equalsIgnoreCase(
                                                safeString(sp.get("serviceSubType")))) {
                                            iswifiMain = "true";
                                        }

                                        String oltPos = safeString(sp.get("oltPosition"));

                                        if (!oltPos.isEmpty()) {
                                            serviceOltPosition = oltPos;
                                            flags.put("SERVICE_OLT_POSITION", serviceOltPosition);
                                        }
                                    }
                                }
                            }
                        }

                        if ("true".equals(iswifiMain) && "1".equals(counter)) {
                            serviceevpnwififlag = "YES";
                        } else {
                            serviceevpnwififlag = "NO";
                        }

                        templateNamePort = "Exist";

                    } else {
                        templateNamePort = "New";
                    }

                    flags.put("SERVICE_PORT_EXIST", templateNamePort);
                } else if ("2".equals(ontPort)) {

                    String portTpl = getOltProperty(oltGdn, "evpnEthPort2Template");

                    if (portTpl != null && !portTpl.isEmpty()) {
                        templateNamePort = "Exist";
                    } else {
                        templateNamePort = "New";
                    }

                    flags.put("SERVICE_PORT_EXIST", templateNamePort);
                }


                // Card template
                String cardTpl = "5".equals(ontPort) ?
                        getOltProperty(oltGdn, "evpnOntCard5Template") :
                        getOltProperty(oltGdn, "evpnOntCardTemplate");

                flags.put("SERVICE_EVPN_EXIST", exists(cardTpl));

            }
            else if (((productName.contains("EVPN")) || (productName.contains("ENTERPRISE")) || (productSubType.contains("Cloudstarter"))) &&!actionType.contains("Configure")) {

                String tempPort = "";
                Set<String> setarsubset = new TreeSet<>();

                // UnconfigureIPBH + IPBH subtype
                if ((productName.contains("EVPN") || productName.contains("ENTERPRISE")) &&
                        actionType.contains("UnconfigureIPBH") &&
                        "IPBH".equalsIgnoreCase(productSubType)) {

                    String rfsPattern = "RFS_" + subscriber + "_" + serviceID;
                    List<Service> rfsTempLists = (List<Service>) serviceCustomRepository.findAll();
                    List<Service> rfsTempList = rfsTempLists.stream()
                            .filter(s -> SETAR_KIND_SETAR_RFS.equalsIgnoreCase(s.getKind()))
                            .filter(s -> s.getDiscoveredName() != null && s.getDiscoveredName().contains(rfsPattern))
                            .collect(Collectors.toList());

                    if (!rfsTempList.isEmpty()) {
                        Service rfsTemp = rfsTempList.get(0);
                        for (Resource res : rfsTemp.getUsedResource()) {
                            if (res instanceof LogicalDevice ld && ld.getDiscoveredName().contains("ALCL")) {
                                ontdevice=ld;
                                Map<String, Object> p = safeProps(ld.getProperties());
                                ontSN = safeString(p.get("serialNo"));
                                serviceSN = ontSN;
                                ontModel = safeString(p.get("deviceModel"));
                                flags.put("SERVICE_SN", ontSN);
                                flags.put("ONT_MODEL", ontModel);
                            }
                        }

                        // Simulate SRIActionUtils.splitAll
                        String rfsName = rfsTemp.getDiscoveredName();
                        String[] parts = rfsName.split(UNDER_SCORE);
                        if (parts.length >= 4) {
                            String subsname = parts[1] + UNDER_SCORE + parts[2] + UNDER_SCORE + parts[3];
                            Optional<Subscription> subOpt = subscriptionRepository.findByDiscoveredName(subsname);

                            if (subOpt.isPresent()) {

                                Subscription sub = subOpt.get();

                                Map<String, Object> sp = safeProps(sub.getProperties());

                                // Set serviceLink
                                serviceLink = safeString(sp.get("serviceLink"));
                                flags.put("SERVICE_LINK", serviceLink);

                                // Subscriber names from subscription (approximated from customer)
                                Optional<Customer> custOpt = customerRepository.findByDiscoveredName(subscriber);

                                if (custOpt.isPresent()) {

                                    Customer cust = custOpt.get();

                                    Map<String, Object> cp = safeProps(cust.getProperties());

                                    subscriberFirstName = safeString(cp.get("firstName"));
                                    subscriberLastName = safeString(cp.get("lastName"));

                                    flags.put("FIRST_NAME", subscriberFirstName);
                                    flags.put("LAST_NAME", subscriberLastName);
                                }
                            }
                        }
                    }
                }

                // Normal Unconfigure (non WIFI Maintenance)
                else if ((productName.contains("EVPN") || productName.contains("ENTERPRISE")) &&
                        actionType.contains("Unconfigure") &&
                        !"WIFI Maintenance".equalsIgnoreCase(productSubType)) {

                    String ontGdn = "ONT" + ontSN;
                    Optional<LogicalDevice> ontDevOpt = deviceRepository.findByDiscoveredName(ontGdn);

                    if (ontDevOpt.isPresent()) {

                        LogicalDevice ontDev = ontDevOpt.get();

                        Map<String, Object> ontP = safeProps(ontDev.getProperties());

                        tempTemplateCreate = exists(ontP.get("createTemplate"));
                        tempTemplateMGMT = exists(ontP.get("mgmtTemplate"));

                        flags.put("SERVICE_TEMPLATE_CREATE", tempTemplateCreate);
                        flags.put("SERVICE_TEMPLATE_MGMT", tempTemplateMGMT);

                        String subNameFull = subscriber + UNDER_SCORE + serviceID + UNDER_SCORE + ontSN;

                        Optional<Subscription> subOpt = subscriptionRepository.findByDiscoveredName(subNameFull);

                        if (subOpt.isPresent()) {

                            Subscription sub = subOpt.get();

                            Map<String, Object> sp = safeProps(sub.getProperties());

                            serviceLink = safeString(sp.get("serviceLink"));
                            evpnPort = safeString(sp.get("evpnPort"));
                            tempPort = evpnPort;
                            tempVLAN = safeString(sp.get("evpnTemplateVLAN"));
                            tempVPLS = safeString(sp.get("evpnTemplateVPLS"));
                            tempCreate = safeString(sp.get("evpnTemplateCreateVLAN"));
                            tempVlanID = safeString(sp.get("evpnVLAN"));

//                            flags.put("SERVICE_LINK", serviceLink);
//                            flags.put("SERVICE_ONT_PORT", evpnPort);
//                            flags.put("SERVICE_TEMPLATE_VLAN", tempVLAN);
//                            flags.put("SERVICE_TEMPLATE_VPLS", tempVPLS);
//                            flags.put("SERVICE_TEMPLATE_CREATE", tempCreate);
//                            flags.put("SERVICE_VLAN_ID", tempVlanID);

                            // OLT Position
                            if (sp.containsKey("oltPosition") && !safeString(sp.get("oltPosition")).isEmpty()) {
                                serviceOltPosition = safeString(sp.get("oltPosition"));
                                flags.put("SERVICE_OLT_POSITION", serviceOltPosition);
                            }

                            // WIFI Maintenance check
                            Set<String> wifiMaintSet = new TreeSet<>();

                            List<Service> allRfs = (List<Service>) serviceCustomRepository.findAll();

                            for (Service rfs : allRfs) {

                                if (!SETAR_KIND_SETAR_RFS.equalsIgnoreCase(rfs.getKind())) {
                                    continue;
                                }

                                if (rfs.getDiscoveredName() == null || !rfs.getDiscoveredName().contains(ontSN)) {
                                    continue;
                                }

                                Service cfs = getFirstUsedService(rfs);
                                if (cfs == null) continue;

                                Product prod = getProductFromCFS(cfs);
                                if (prod == null) continue;

                                Subscription subM = getFirstSubscription(prod);
                                if (subM == null) continue;

                                Map<String, Object> subMProps = safeProps(subM.getProperties());

                                if ("WIFI Maintenance".equalsIgnoreCase(safeString(subMProps.get("serviceSubType")))) {
                                    wifiMaintSet.add(subM.getDiscoveredName());
                                }
                            }

                            if (wifiMaintSet.size() == 1) {
                                serviceevpnwififlag = "YES";
                            } else {
                                serviceevpnwififlag = "NO";
                            }

                            flags.put("SERVICE_EVPN_WIFIM_FIRST", serviceevpnwififlag);
                        }
                    }
                }

                tempTemplateCreate = getOntProperty(ontSN, "createTemplate");

                tempTemplateMGMT = getOntProperty(ontSN, "mgmtTemplate");
                //tempTemplateBase = tempTemplateMGMT + " 2";

                Subscription setarSubscribtion = new Subscription();

                subscriptionName = subscriber + UNDER_SCORE + serviceID + UNDER_SCORE + ontSN;

                Optional<Subscription> subOpt = subscriptionRepository.findByDiscoveredName(subscriptionName);

                if (subOpt.isPresent()) {

                    Subscription sub = subOpt.get();

                    Map<String, Object> p = safeProps(sub.getProperties());

                    serviceLink = safeString(p.get("serviceLink"));

                    evpnPort = safeString(p.get("evpnPort"));

                    tempPort = safeString(p.get("evpnPort"));
                    tempVLAN = safeString(p.get("evpnTemplateVLAN"));
                    tempVPLS = safeString(p.get("evpnTemplateVPLS"));
                    tempCreate = safeString(p.get("evpnTemplateCreateVLAN"));
                    tempVlanID = safeString(p.get("evpnVLAN"));
                    ontPort = safeString(p.get("evpnPort"));
                    String oltposition = safeString(p.get("oltPosition"));


                    if (oltposition != null && oltposition != "") {

                        serviceOltPosition = oltposition;
                    }
                    if (ontPort.equals("5")) {
                        tempCard = getOltProperty(oltGdn, "evpnOntCard5Template");
                    } else {
                        tempCard = getOltProperty(oltGdn, "evpnOntCardTemplate");
                    }

                }


                // Port-specific reset logic for Unconfigure
                if ("4".equals(tempPort)) {

                    serviceevpnwififlag = (!setarsubset.isEmpty() && setarsubset.size() == 1) ? "YES" : "NO";

                    String portTpl = getOntProperty(ontSN, "evpnEthPort4Template");
                    tempPortTemp = getOltProperty(oltGdn, "evpnEthPort4Template");

                    flags.put("SERVICE_PORT_EXIST", exists(portTpl));
                    flags.put("SERVICE_PORT4_EXIST", exists(portTpl));

                    if ("1".equals(portTpl)) {
                        templateNamePort = "New";
                        templateNamePort4 = "New";
                    } else {
                        templateNamePort = "Exist";
                        templateNamePort4 = "Exist";
                    }

                    templateNamePort2 = exists(getOltProperty(oltGdn, "evpnEthPort2Template"));
                    templateNamePort3 = exists(getOltProperty(oltGdn, "evpnEthPort3Template"));
                }
                else if ("5".equals(tempPort)) {

                    serviceevpnwififlag = (!setarsubset.isEmpty() && setarsubset.size() == 1) ? "YES" : "NO";

                    String portTpl = getOntProperty(ontSN, "evpnEthPort5Template");
                    tempPortTemp = getOltProperty(oltGdn, "evpnEthPort5Template");

                    flags.put("SERVICE_PORT_EXIST", exists(portTpl));
                    flags.put("SERVICE_PORT5_EXIST", exists(portTpl));

                    if ("1".equals(portTpl)) {
                        templateNamePort = "New";
                        templateNamePort5 = "New";
                    } else {
                        templateNamePort = "Exist";
                        templateNamePort5 = "Exist";
                    }

                    templateNamePort2 = exists(getOltProperty(oltGdn, "evpnEthPort2Template"));
                    templateNamePort3 = exists(getOltProperty(oltGdn, "evpnEthPort3Template"));
                }
                else if ("3".equals(tempPort)) {

                    serviceevpnwififlag = (!setarsubset.isEmpty() && setarsubset.size() == 1) ? "YES" : "NO";

                    String portTpl = getOntProperty(ontSN, "evpnEthPort3Template");
                    tempPortTemp = getOltProperty(oltGdn, "evpnEthPort3Template");

                    flags.put("SERVICE_PORT_EXIST", exists(portTpl));
                    flags.put("SERVICE_PORT3_EXIST", exists(portTpl));

                    if ("1".equals(portTpl)) {
                        templateNamePort = "New";
                        templateNamePort3 = "New";
                    } else {
                        templateNamePort = "Exist";
                        templateNamePort3 = "Exist";
                    }

                    templateNamePort2 = exists(getOltProperty(oltGdn, "evpnEthPort2Template"));
                    templateNamePort4 = exists(getOltProperty(oltGdn, "evpnEthPort4Template"));
                }
                else if ("2".equals(tempPort)) {

                    String portTpl = getOntProperty(ontSN, "evpnEthPort2Template");
                    tempPortTemp = getOltProperty(oltGdn, "evpnEthPort2Template");

                    flags.put("SERVICE_PORT_EXIST", exists(portTpl));
                    flags.put("SERVICE_PORT2_EXIST", exists(portTpl));

                    if ("1".equals(portTpl)) {
                        templateNamePort = "New";
                        templateNamePort2 = "New";
                    } else {
                        templateNamePort = "Exist";
                        templateNamePort2 = "Exist";
                    }

                    templateNamePort3 = exists(getOltProperty(oltGdn, "evpnEthPort3Template"));
                    templateNamePort4 = exists(getOltProperty(oltGdn, "evpnEthPort4Template"));
                }

                // Card summary
                if (!"New".equals(templateNamePort4) || !"New".equals(templateNamePort3) || !"New".equals(templateNamePort2)) {
                    templateNameCard = "Exist";
                } else {
                    templateNameCard = "New";
                }
                flags.put("SERVICE_EVPN_EXIST", templateNameCard);
            }
            else if (!(productName.contains("EVPN") || productName.contains("ENTERPRISE")) && !actionType.contains("Configure")) {


                //String tempTemplateBase = "";
                String tempPort = "";

                System.out.println("------------Test Trace # 24---------------");
                //tempTemplateCreate = ontDevice.getCreateTemplate();
                //tempTemplateMGMT =	ontDevice.getMgmtTemplate();
                //tempTemplateBase = tempTemplateMGMT + " 2";

                Subscription setarSubscribtion = new Subscription();

                subscriptionName = subscriber + UNDER_SCORE + serviceID + UNDER_SCORE + ontSN;

                Optional<Subscription> subOpt = subscriptionRepository.findByDiscoveredName(subscriptionName);

                if (subOpt.isPresent()) {

                    Subscription sub = subOpt.get();

                    Map<String, Object> p = safeProps(sub.getProperties());
                    tempPort = safeString(p.get("evpnPort"));
                    tempVLAN = safeString(p.get("evpnTemplateVLAN"));
                    //tempVPLS = safeString(p.get("evpnTemplateVPLS"));
                    //tempCreate = safeString(p.get("evpnTemplateCreateVLAN"));
                    tempCard = oltdevice.getProperties().get("EvpnOntCardTemplate").toString();
                    evpnPort = safeString(p.get("evpnPort"));

                }




                if (tempPort.equals("4")) {

                    templateNamePort = getOntProperty(ontSN, "evpnEthPort4Template");
                    tempPortTemp = getOltProperty(oltGdn, "evpnEthPort4Template");


                    templateNamePort = "New";
                    templateNamePort4 = "New";


                    templateNamePort2 = getOltProperty(oltGdn, "evpnEthPort2Template");

                    if (templateNamePort2 != "" && templateNamePort2 != null) {
                        templateNamePort2 = "Exist";
                    } else {
                        templateNamePort2 = "New";
                    }

                    templateNamePort3 = getOltProperty(oltGdn, "evpnEthPort3Template");

                    if (templateNamePort3 != "" && templateNamePort3 != null) {
                        templateNamePort3 = "Exist";
                    } else {
                        templateNamePort3 = "New";
                    }

                }
                else if (tempPort.equals("3")) {

                    templateNamePort = getOntProperty(ontSN, "evpnEthPort3Template");
                    tempPortTemp = getOltProperty(oltGdn, "evpnEthPort3Template");

                    templateNamePort = "New";
                    templateNamePort3 = "New";

                    templateNamePort2 = getOltProperty(oltGdn, "evpnEthPort2Template");

                    if (templateNamePort2 != "" && templateNamePort2 != null) {
                        templateNamePort2 = "Exist";
                    } else {
                        templateNamePort2 = "New";
                    }

                    templateNamePort4 = getOltProperty(oltGdn, "evpnEthPort4Template");

                    if (templateNamePort4 != "" && templateNamePort4 != null) {
                        templateNamePort4 = "Exist";
                    } else {
                        templateNamePort4 = "New";
                    }

                }
                else if (tempPort.equals("2")) {

                    templateNamePort = getOntProperty(ontSN, "evpnEthPort2Template");
                    tempPortTemp = getOltProperty(oltGdn, "evpnEthPort2Template");

                    templateNamePort = "New";
                    templateNamePort2 = "New";


                    templateNamePort3 = getOltProperty(oltGdn, "evpnEthPort3Template");

                    if (templateNamePort3 != "" && templateNamePort3 != null) {
                        templateNamePort3 = "Exist";
                    } else {
                        templateNamePort3 = "New";
                    }

                    templateNamePort4 = getOltProperty(oltGdn, "evpnEthPort4Template");

                    if (templateNamePort4 != "" && templateNamePort4 != null) {
                        templateNamePort4 = "Exist";
                    } else {
                        templateNamePort4 = "New";
                    }
                }

                if (templateNamePort4 != "New" || templateNamePort3 != "New" || templateNamePort2 != "New") {
                    templateNameCard = "Exist";
                } else {
                    templateNameCard = "New";
                }

            }
        } else {

            // Fallback card template (non-EVPN path)
            String cardTpl = getOltProperty(oltGdn, "evpnOntCardTemplate");
            templateNameCard = exists(cardTpl);
            flags.put("SERVICE_EVPN_EXIST", templateNameCard);

            if (actionType.contains("Unconfigure")) {
                tempVEIP = getOltProperty(oltGdn, "veipServiceTemplate");
                tempHSI = getOltProperty(oltGdn, "veipHsiTemplate");
                tempVOIP = getOltProperty(oltGdn, "voipServiceTemplate");
                tempPOTS1 = getOltProperty(oltGdn, "voipPots1Template");
                tempPOTS2 = getOltProperty(oltGdn, "voipPots2Template");
                tempIPTV = getOltProperty(oltGdn, "veipIptvTemplate");

                flags.put("SERVICE_TEMPLATE_VEIP", tempVEIP);
                flags.put("SERVICE_TEMPLATE_HSI", tempHSI);
                flags.put("SERVICE_TEMPLATE_VOIP", tempVOIP);
                flags.put("SERVICE_TEMPLATE_POTS1", tempPOTS1);
                flags.put("SERVICE_TEMPLATE_POTS2", tempPOTS2);
                flags.put("SERVICE_TEMPLATE_IPTV", tempIPTV);
            }

            // OltPosition from subscription
            String subNameFull = subscriber + UNDER_SCORE + serviceID + UNDER_SCORE + ontSN;
            Optional<Subscription> subOpt = subscriptionRepository.findByDiscoveredName(subNameFull);

            if (subOpt.isPresent()) {

                Subscription sub = subOpt.get();

                Map<String, Object> p = safeProps(sub.getProperties());

                if (p.containsKey("oltPosition") && !safeString(p.get("oltPosition")).isEmpty()) {
                    serviceOltPosition = safeString(p.get("oltPosition"));
                    flags.put("SERVICE_OLT_POSITION", serviceOltPosition);
                }
            }
        }
        flags.put("SERVICE_TEMPLATE_VLAN", tempVLAN);
        flags.put("SERVICE_TEMPLATE_VPLS", tempVPLS);
        flags.put("SERVICE_EXIST", templateNameOnt);
        flags.put("SERVICE_EVPN_EXIST", templateNameCard);
        flags.put("SERVICE_PORT_EXIST", templateNamePort);
        flags.put("SERVICE_VEIP_EXIST", templateNameVeip);
        flags.put("SERVICE_VOIP_EXIST", templateNameVoip);
        flags.put("SERVICE_HSI_EXIST", templateNameHSI);
        flags.put("SERVICE_IPTV_EXIST", templateNameIPTV);
        flags.put("SERVICE_POTS1_EXIST", templateNamePots1);
        flags.put("SERVICE_POTS2_EXIST", templateNamePots2);

        flags.put("SERVICE_TEMPLATE_VEIP", tempVEIP);
        flags.put("SERVICE_TEMPLATE_HSI", tempHSI);
        flags.put("SERVICE_TEMPLATE_ONT", tempONT);
        flags.put("SERVICE_TEMPLATE_VOIP", tempVOIP);
        flags.put("SERVICE_TEMPLATE_POTS1", tempPOTS1);
        flags.put("SERVICE_TEMPLATE_POTS2", tempPOTS2);
        flags.put("SERVICE_TEMPLATE_IPTV", tempIPTV);

        if (!qosProfileBridge.isEmpty()) {
            flags.put("QOS_PROFILE", qosProfileBridge);
        } else {
            flags.put("QOS_PROFILE", qosProfile);
        }

        flags.putIfAbsent("SERVICE_LINK", serviceLink);
        flags.putIfAbsent("SERVICE_SN", serviceSN);
        flags.putIfAbsent("SERVICE_MAC", serviceMAC);
        flags.putIfAbsent("CBM_MAC", cbmmac);
        flags.putIfAbsent("ONT_MODEL", ontModel);
        flags.putIfAbsent("OLT_POSITION", oltPosition);
        flags.put("ONT_TEMPLATE", ontTemplate);
        flags.put("SERVICE_OLT_POSITION", serviceOltPosition);
        flags.put("SERVICE_EVPN_WIFIM_FIRST", serviceevpnwififlag);
        flags.put("VOICE_POTS_PORT", voicePotsPort);
        flags.put("KENAN_UIDNO", kenanUidNumber);
        flags.put("SIMA_CUST_ID", simaCustId);
        flags.put("IPTV_COUNT", iptvCount);
        flags.put("FIBERNET_COUNT", fibernetCount);
        flags.put("ACCOUNT_EXIST", accountExistFlag);
        flags.put("SERVICE_FLAG", serviceFlag);
        flags.put("SERVICE_TEMPLATE_VLAN", tempVLAN);
        flags.put("SERVICE_TEMPLATE_VPLS", tempVPLS);
        flags.put("SERVICE_VLAN_ID", tempVlanID);
        flags.put("SERVICE_TEMPLATE_CARD", tempCard);
        flags.put("SERVICE_TEMPLATE_PORT", tempPortTemp);
        flags.put("SERVICE_TEMPLATE_MGMT_CREATE", tempTemplateCreate);
        flags.put("SERVICE_TEMPLATE_CREATE", tempCreate);

        flags.putIfAbsent("CBM_ACCOUNT_EXIST", cbmAccountExistFlag);

        // VOIP reset rule (original logic)
        if (equalsAnyIgnoreCase(productName, "VOIP", "Voice")) {
            if ("Available".equalsIgnoreCase(number1)) number1 = "";
            if ("Available".equalsIgnoreCase(number2)) number2 = "";
            flags.put("SERVICE_VOIP_NUMBER1", number1);
            flags.put("SERVICE_VOIP_NUMBER2", number2);
        }

        log.info("QueryFlags completed - returning {} flags", flags.size());
        return new QueryFlagsResponse("200", "UIV action QueryFlags executed successfully.", getCurrentTimestamp(), flags);
    }

    // ───────────────────────────────────────────────
    // Helper methods (ported from original)
    // ───────────────────────────────────────────────

    private Map<String, String> computeAccountAndServiceFlags(
            String subscriber, String actionType, String productSubType,
            String ontName, String serviceID) {

        Map<String, String> result = new HashMap<>();
        result.put("ACCOUNT_EXIST", "New");
        result.put("SERVICE_FLAG", "New");
        result.put("CBM_ACCOUNT_EXIST", "New");

        List<Subscription> subscriptions = (List<Subscription>) subscriptionRepository.findAll();

        List<Subscription> subs = subscriptions.stream()
                .filter(s -> s.getDiscoveredName() != null && s.getDiscoveredName().contains(subscriber))
                .collect(Collectors.toList());

        if (actionType.contains("Unconfigure") || equalsAnyIgnoreCase(actionType, "MoveOut", "ChangeTechnology", "AccountTransfer")) {
            int count = subs.size();
            if (count == 0) {
                // already New
            } else if (count == 1) {
                Map<String, Object> p = safeProps(subs.get(0).getProperties());
                if ("Cable_Modem".equalsIgnoreCase(safeString(p.get("serviceLink")))) {
                    result.put("CBM_ACCOUNT_EXIST", "New");
                }
            } else {
                result.put("SERVICE_FLAG", "Exist");
                result.put("ACCOUNT_EXIST", "Exist");

                int cbmCount = 0;
                Set<String> macSet = new HashSet<>();

                for (Subscription s : subs) {
                    Map<String, Object> p = safeProps(s.getProperties());
                    if ("Cable_Modem".equalsIgnoreCase(safeString(p.get("serviceLink")))) {
                        cbmCount++;
                        String mac = safeString(p.get("serviceMac"));
                        if (mac != null && !mac.isEmpty()) macSet.add(mac);
                    }
                }

                if (equalsAnyIgnoreCase(actionType, "ChangeTechnology", "AccountTransfer")) {
                    result.put("CBM_ACCOUNT_EXIST", macSet.size() > 1 ? "Exist" : "New");
                } else {
                    result.put("CBM_ACCOUNT_EXIST", cbmCount > 1 ? "Exist" : "New");
                }
            }
        } else if ("Configure".equalsIgnoreCase(actionType) && ontName.contains("ALCL")) {
            String subWithOnt = subscriber + UNDER_SCORE + ontName;
            boolean exists = customerRepository.findByDiscoveredName(subWithOnt).isPresent();
            result.put("SERVICE_FLAG", exists ? "Exist" : "New");
            result.put("ACCOUNT_EXIST", exists ? "Exist" : "New");
        } else if ("Migrate".equalsIgnoreCase(actionType) && ontName.contains("ALCL")) {
            result.put("SERVICE_FLAG", "New");
            result.put("ACCOUNT_EXIST", "New");

            for (Subscription s : subs) {
                if (s.getDiscoveredName() != null && s.getDiscoveredName().contains(ontName)) {
                    result.put("SERVICE_FLAG", "Exist");
                    if ((subscriber + UNDER_SCORE + serviceID).equalsIgnoreCase(s.getDiscoveredName())) {
                        result.put("ACCOUNT_EXIST", "Exist");
                    }
                    String sima = safeString(safeProps(s.getProperties()).get("simaCustId"));
                    if (sima != null && !sima.isEmpty()) {
                        result.put("SIMA_CUST_ID", sima);
                    }else if(s.getProperties().get("simaCustId2")!=null) {
                        result.put("SIMA_CUST_ID", s.getProperties().get("simaCustId2").toString());
                    }
                }
            }
        } else {
            boolean exists = customerRepository.findByDiscoveredName(subscriber).isPresent();
            result.put("SERVICE_FLAG", exists ? "Exist" : "New");
            result.put("ACCOUNT_EXIST", exists ? "Exist" : "New");

            boolean hasCbm = subs.stream().anyMatch(s ->
                    "Cable_Modem".equalsIgnoreCase(safeString(safeProps(s.getProperties()).get("serviceLink"))));
            result.put("CBM_ACCOUNT_EXIST", hasCbm ? "Exist" : "New");
        }

        return result;
    }

    private String deriveBridgeService(List<Service> rfsList, String ontSN) {
        if (rfsList == null || ontSN == null || ontSN.isEmpty()) return "NA";

        for (Service rfs : rfsList) {
            Service cfs = getFirstUsedService(rfs);
            if (cfs == null) continue;
            Product prod = getProductFromCFS(cfs);
            if (prod == null) continue;
            Subscription sub = getFirstSubscription(prod);
            if (sub == null) continue;

            Map<String, Object> p = safeProps(sub.getProperties());
            if ("Bridged".equalsIgnoreCase(safeString(p.get("serviceSubType"))) &&
                    sub.getDiscoveredName().contains(ontSN)) {
                return safeString(p.get("serviceID"));
            }
        }
        return "NA";
    }

    // Safe helpers
    private Service getFirstUsedService(Service rfs) {
        if (rfs == null || rfs.getUsedService() == null) return null;
        return rfs.getUsedService().stream().findFirst().orElse(null);
    }

    private Product getProductFromCFS(Service cfs) {
        if (cfs == null || cfs.getUsingService() == null) return null;
        String prodName = cfs.getUsingService().stream()
                .filter(s -> SETAR_KIND_SETAR_PRODUCT.equalsIgnoreCase(s.getKind()))
                .map(Service::getDiscoveredName)
                .findFirst().orElse(null);
        if (prodName == null) return null;
        return productRepository.findByDiscoveredName(prodName).orElse(null);
    }

    private Subscription getFirstSubscription(Product prod) {
        if (prod == null || prod.getSubscription() == null) return null;
        return prod.getSubscription().stream().findFirst().orElse(null);
    }

    private String safeString(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private Map<String, Object> safeProps(Map<String, Object> props) {
        return props != null ? props : new HashMap<>();
    }

    private String exists(Object o) {
        if (o == null) return "New";
        String s = safeString(o);
        return s.isEmpty() ? "New" : "Exist";
    }

    private void initializeFlags(Map<String, String> flags) {
        String[] keys = {
                "SERVICE_EXIST",
                "SERVICE_EVPN_EXIST",
                "SERVICE_PORT_EXIST",
                "SERVICE_VEIP_EXIST",
                "SERVICE_VOIP_EXIST",
                "SERVICE_VOIP_NUMBER1",
                "SERVICE_VOIP_NUMBER2",
                "SERVICE_PORT2_EXIST",
                "SERVICE_PORT3_EXIST",
                "SERVICE_PORT4_EXIST",
                "SERVICE_POTS1_EXIST",
                "SERVICE_POTS2_EXIST",
                "SERVICE_TEMPLATE_VLAN",
                "SERVICE_VLAN_ID",
                "SERVICE_TEMPLATE_CREATE",
                "SERVICE_TEMPLATE_CARD",
                "SERVICE_TEMPLATE_PORT",
                "SERVICE_TEMPLATE_MGMT",
                "SERVICE_TEMPLATE_MGMT_CREATE",
                "SERVICE_TEMPLATE_VEIP",
                "SERVICE_TEMPLATE_HSI",
                "SERVICE_TEMPLATE_ONT",
                "SERVICE_TEMPLATE_VOIP",
                "SERVICE_TEMPLATE_POTS1",
                "SERVICE_TEMPLATE_POTS2",
                "SERVICE_HSI_EXIST",
                "SERVICE_LINK",
                "SERVICE_SN",
                "SERVICE_MAC",
                "ONT_MODEL",
                "SERVICE_PORT5_EXIST",
                "SERVICE_TEMPLATE_VPLS",
                "SERVICE_TEMPLATE_IPTV",
                "SERVICE_IPTV_EXIST",
                "SERVICE_ID",
                "SERVICE_EVPN_WIFIM_FIRST",
                "OLT_POSITION",
                "ONT_TEMPLATE",
                "SERVICE_OLT_POSITION",
                "CBM_MAC",
                "IPTV_COUNT",
                "FIBERNET_COUNT",
                "QOS_PROFILE",
                "FIRST_NAME",
                "LAST_NAME",
                "ACCOUNT_EXIST",
                "SERVICE_FLAG",
                "SERVICE_ONT_PORT",
                "KENAN_UIDNO",
                "SIMA_CUST_ID",
                "CBM_ACCOUNT_EXIST",
                "VOICE_POTS_PORT",
                "RESOURCE_MAC_MTA_OLD",
                "RESOURCE_MODEL_MTA_OLD",
                "BRIDGE_SERVICE",
                "QOS_PROFILE_BRIDGE"
        };
        for (String k : keys) flags.put(k, "");
    }

    private String getCurrentTimestamp() {
        return Instant.now().toString();
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private boolean equalsAnyIgnoreCase(String val, String... candidates) {
        if (val == null) return false;
        for (String c : candidates) if (equalsIgnoreCase(val, c)) return true;
        return false;
    }

    private boolean containsIgnoreCase(String val, String token) {
        if (val == null || token == null) return false;
        return val.toLowerCase().contains(token.toLowerCase());
    }

    private String getOltProperty(String oltGdn, String propertyKey) {

        if (oltGdn == null || oltGdn.isEmpty()) {
            return "";
        }

        Optional<LogicalDevice> oltOpt = deviceRepository.findByDiscoveredName(oltGdn);

        if (oltOpt.isPresent()) {
            LogicalDevice olt = oltOpt.get();

            if (olt.getProperties() != null) {
                Object value = olt.getProperties().get(propertyKey);
                return value != null ? value.toString() : "";
            }
        }

        return "";
    }

    private String getOntProperty(String ontSN, String propertyKey) {

        String value = "";
        if (ontSN == null || ontSN.isEmpty()) {
            return "";
        }

        String ontGdn = "ONT" + ontSN;

        Optional<LogicalDevice> ont = deviceRepository.findByDiscoveredName(ontGdn);
        LogicalDevice ontDevice = null;

        if (ont.isPresent()) {
            ontDevice = ont.get();

            if (ontDevice.getProperties() != null &&
                    ontDevice.getProperties().get(propertyKey) != null) {

                value = ontDevice.getProperties().get(propertyKey).toString();
            }
        }

        return value;
    }
}
