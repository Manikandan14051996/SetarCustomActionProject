// ============================================================================
// Action: DeleteSPR
// Package: com.nokia.nsw.uiv.action
// Contract: Spring @Component, implements HttpAction, returns DeleteSPRResponse
// Repositories used: CustomerRepository, SubscriptionRepository, ProductRepository,
//                    CustomerFacingServiceRepository, ResourceFacingServiceRepository,
//                    LogicalDeviceRepository, LogicalInterfaceRepository
// Notes:
// - No Setar* types. Only base model classes are used.
// - Name building strictly follows the requirement.
// - Defensive null checks throughout.
// - Business logic paths for Broadband/Fibernet, Bridged, EVPN/Enterprise/Cloudstarter, Voice/VOIP.
// - Error mapping as per requirement (Code5/Code6/Code1).
// ============================================================================

package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.exception.InternalServerErrorException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;

import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;


import com.nokia.nsw.uiv.model.resource.logical.*;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;

import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.DeleteSPRRequest;
import com.nokia.nsw.uiv.response.DeleteSPRResponse;

import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.bsd.RLoginClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RestController
@Action
@Slf4j
public class DeleteSPR implements HttpAction {

    private static final String ACTION_LABEL = "DeleteSPR";
    private static final String ERROR_PREFIX = "UIV action DeleteSPR execution failed - ";

    @Autowired private CustomerCustomRepository customerRepository;
    @Autowired private SubscriptionCustomRepository subscriptionRepository;
    @Autowired private ProductCustomRepository productRepository;
    @Autowired private LogicalDeviceCustomRepository logicalDeviceRepository;
    @Autowired private LogicalInterfaceCustomRepository logicalInterfaceRepository;
    @Autowired private ServiceCustomRepository serviceCustomRepository;

    @Override
    public Class<?> getActionClass() {
        return DeleteSPRRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error("Executing action {}", ACTION_LABEL);
        DeleteSPRRequest req = (DeleteSPRRequest) actionContext.getObject();

        // -----------------------------
        // 1) Mandatory validations
        // -----------------------------
        try {
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
            validateMandatory(req.getSubscriberName(), "subscriberName");
            validateMandatory(req.getProductType(), "productType");
            validateMandatory(req.getProductSubtype(), "productSubtype");
            validateMandatory(req.getServiceId(), "serviceId");
            validateMandatory(req.getOntSN(), "ontSN");
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
        } catch (BadRequestException bre) {
            // Code5
            return new DeleteSPRResponse(
                    "400",
                    ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage(),
                    now(),
                    "", ""
            );
        }

        // -----------------------------
        // 2) Construct Required Names
        // -----------------------------
        String subscriberNameWithOnt = req.getSubscriberName() + Constants.UNDER_SCORE  + req.getOntSN();
        String subscriptionName = req.getSubscriberName() + Constants.UNDER_SCORE  + req.getServiceId() + Constants.UNDER_SCORE  + req.getOntSN();
        String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
        String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
        String productName = req.getSubscriberName() + Constants.UNDER_SCORE  + req.getProductSubtype() + Constants.UNDER_SCORE  + req.getServiceId();
        String ontName ="ONT" + req.getOntSN();

        if (ontName.length() > 100) {
            // Code6
            return new DeleteSPRResponse(
                    "400",
                    ERROR_PREFIX + "ONT name too long",
                    now(),
                    ontName,
                    subscriptionName
            );
        }

        try {
            // -----------------------------
            // 3) Retrieve Subscriber & determine "last service"
            // -----------------------------
            Optional<Customer> optSubscriber = customerRepository.findByDiscoveredName(subscriberNameWithOnt);
            boolean lastServiceForSubscriber = false;
            if (optSubscriber.isPresent()) {
                Customer sub = optSubscriber.get();
                try {
                    // Prefer a repository count if available; fallback to collection size
                    int subCount = countSubscriptionsByCustomer(sub);
                    lastServiceForSubscriber = (subCount <= 1);
                } catch (Exception ignore) {
                    // fallback null-safe
                    lastServiceForSubscriber = true;
                }
            }

            // -----------------------------
            // 4) Retrieve Objects
            // -----------------------------
            Optional<Subscription> optSubscription = subscriptionRepository.findByDiscoveredName(subscriptionName);
            Optional<Product> optProduct = productRepository.findByDiscoveredName(productName);
            Optional<Service> optCfs = serviceCustomRepository.findByDiscoveredName(cfsName);
            Optional<Service> optRfs = serviceCustomRepository.findByDiscoveredName(rfsName);
            Optional<LogicalDevice> optOnt = logicalDeviceRepository.findByDiscoveredName(ontName);

            // From ONT, try to retrieve parent OLT (if your data model links it via "parent" or property)
            Optional<LogicalDevice> optOlt = Optional.empty();
            if (optOnt.isPresent()) {
                LogicalDevice ont = optOnt.get();
                optOlt = ont.getUsedResource().stream().map(r->(LogicalDevice)r).findFirst();
            }
            // Attempt to retrieve a CPE device named"ONT" + ontSN (optional)
            String optCpeName="ONT" +Constants.UNDER_SCORE+ req.getOntSN();
            Optional<LogicalDevice> optCpe = logicalDeviceRepository.findByDiscoveredName(optCpeName);

            // -----------------------------
            // 5) Delete Fibernet/Broadband Services
            // -----------------------------
            if (equalsAny(req.getProductSubtype(), "Fibernet", "Broadband")) {
                // Clear VEIP/HSI templates on OLT
                optOlt.ifPresent(this::clearVeipAndHsiTemplates);

                // Remove RFS, unlink if needed, Remove CFS, Remove Product if no more CFS, Remove Subscription
                cleanupServiceObjects(optRfs, optCfs, optProduct, optSubscription);
            }

            // -----------------------------
            // 6) Delete Bridged Services
            // -----------------------------
            if ("Bridged".equalsIgnoreCase(req.getProductSubtype())) {
                String ontPort = nullSafe(req.getOntPort());
                // Clear EVPN template on that port in both OLT and ONT
                optOlt.ifPresent(olt -> clearEvpnsOnPort(olt, ontPort));
                optOnt.ifPresent(ont -> clearEvpnsOnOntPort(ont, ontPort));

                // If all EVPN port templates on OLT cleared -> clear EVPN card template
                optOlt.ifPresent(this::maybeClearOntCardTemplateIfAllPortsEmpty);

                // Remove RFS/CFS/Product/Subscription
                cleanupServiceObjects(optRfs, optCfs, optProduct, optSubscription);
            }

            // -----------------------------
            // 7) Delete EVPN/Enterprise/Cloudstarter Services
            // -----------------------------
            if (equalsAny(req.getProductType(), "EVPN", "ENTERPRISE")
                    || "Cloudstarter".equalsIgnoreCase(req.getProductSubtype())) {

                String ontPort = nullSafe(req.getOntPort());
                // Identify current EVPN template on ONT port + VLAN from subscription
                String currentEvpnTemplateVal = optOnt.map(ont -> getPortTemplateValue(ont, ontPort)).orElse("0");
                String subVlan = optSubscription.map(s -> stringProp(s.getProperties(), "evpnVLAN")).orElse("");

                // Remove possible VLAN interfaces (ends with 2..8) based on naming rule
                removePossibleVlanInterfaces(req.getOntSN(), ontPort);

                if ("1".equals(currentEvpnTemplateVal)) {
                    optOlt.ifPresent(olt -> clearEvpnsOnPort(olt, ontPort));
                    optOnt.ifPresent(ont -> {
                        clearEvpnsOnOntPort(ont, ontPort);
                        clearOntCreateTemplate(ont);
                        safeSaveLogicalDevice(ont);
                    });
                    optOlt.ifPresent(this::safeSaveLogicalDevice);

                    if (allEvpnPortsEmpty(optOlt)) {
                        optOlt.ifPresent(this::clearOntCardTemplate);
                        optOnt.ifPresent(this::clearOntMgmtTemplates);
                        optOlt.ifPresent(this::safeSaveLogicalDevice);
                        optOnt.ifPresent(this::safeSaveLogicalDevice);
                    }
                }

                if (!"0".equals(currentEvpnTemplateVal)) {
                    try {
                        int val = Integer.parseInt(currentEvpnTemplateVal);
                        val = Math.max(val - 1, 0);
                        int finalVal = val;
                        optOnt.ifPresent(ont -> {
                            setPortTemplateValue(ont, ontPort, String.valueOf(finalVal));
                            safeSaveLogicalDevice(ont);
                        });
                    } catch (NumberFormatException ignored) {}
                }

                // If subtype NOT in BAAS/SIP/Cloudstarter/IPBH → remove extra VLAN (by ONT desc + sub evpn vlan)
                if (!equalsAny(req.getProductSubtype(), "BAAS", "SIP", "Cloudstarter", "IPBH")) {
                    String ontDesc = optOnt.map(d->d.getProperties().get("description").toString()).orElse("");
                    String nameToRemove = ontDesc +"_"+ subVlan;
                    if (!nameToRemove.isEmpty()) {
                        logicalInterfaceRepository.findByDiscoveredName(nameToRemove)
                                .ifPresent(li -> logicalInterfaceRepository.delete(li));
                    }
                }

                // Remove RFS/CFS/Product/Subscription
                cleanupServiceObjects(optRfs, optCfs, optProduct, optSubscription);
            }

            // -----------------------------
            // 8) Delete VOIP or Voice Services
            // -----------------------------
            if (equalsAny(req.getProductType(), "VOIP", "Voice")) {
                String voipNumber1 = optSubscription.map(s -> stringProp(s.getProperties(), "voipNumber1")).orElse("");

                // ONT POTS numbers
                String pots1 = optOnt.map(ont -> stringProp(ont.getProperties(), "potsPort1Number")).orElse("");
                String pots2 = optOnt.map(ont -> stringProp(ont.getProperties(), "potsPort2Number")).orElse("");

                boolean matchP2 = voipNumber1 != null && !voipNumber1.isEmpty() && voipNumber1.equals(pots2);

                if (matchP2) {
                    optOnt.ifPresent(ont -> {
                        ont.getProperties().put("potsPort2Number", "");
                        safeSaveLogicalDevice(ont);
                    });
                    optOlt.ifPresent(olt -> {
                        olt.getProperties().put("voipPots2Template", "");
                        safeSaveLogicalDevice(olt);
                    });
                    // CPE port2 → Available (if present)
                    setCpePortStateAvailable(optCpe, "POTS_2");
                } else {
                    optOnt.ifPresent(ont -> {
                        ont.getProperties().put("potsPort1Number", "");
                        safeSaveLogicalDevice(ont);
                    });
                    optOlt.ifPresent(olt -> {
                        olt.getProperties().put("voipPots1Template", "");
                        safeSaveLogicalDevice(olt);
                    });
                    // CPE port1 → Available (if present)
                    setCpePortStateAvailable(optCpe, "POTS_1");
                }

                // If both VoIP POTS templates empty on OLT -> clear voipServiceTemplate on OLT + SIMA cust id on subscriber
                if (optOlt.isPresent()) {
                    LogicalDevice olt = optOlt.get();
                    String p1 = stringProp(olt.getProperties(), "voipPots1Template");
                    String p2 = stringProp(olt.getProperties(), "voipPots2Template");
                    if (isEmpty(p1) && isEmpty(p2)) {
                        olt.getProperties().put("voipServiceTemplate", "");
                        safeSaveLogicalDevice(olt);
                        optSubscriber.ifPresent(sub -> {
                            sub.getProperties().put("simaCustId1", "");
                            safeSaveCustomer(sub);
                        });
                    }
                }

                // Remove RFS/CFS/Product/Subscription
                cleanupServiceObjects(optRfs, optCfs, optProduct, optSubscription);
            }

            // -----------------------------
            // 9) Delete OLT and ONT Device if Unused
            // -----------------------------
            boolean shouldDeleteDevices = false;
            if (!"Exist".equalsIgnoreCase(nullSafe(req.getServiceFlag()))) {
                shouldDeleteDevices = true;
            }
            if (lastServiceForSubscriber) {
                shouldDeleteDevices = true;
            }
            if (shouldDeleteDevices) {
                optOnt.ifPresent(logicalDeviceRepository::delete);
                optOlt.ifPresent(logicalDeviceRepository::delete);
            }

            // -----------------------------
            // 10) Delete Subscriber if no more subscriptions
            // -----------------------------
            Optional<Customer>subs=customerRepository.findByDiscoveredName(subscriberNameWithOnt);
            if (subs.isPresent()) {
                Customer sub = subs.get();
                boolean deleteSubscriber = false;
                int remaining = countSubscriptionsByCustomer(sub);
                if(remaining>0)
                {
                    lastServiceForSubscriber=false;
                }

                if ((remaining <= 0 && !"Exist".equalsIgnoreCase(req.getServiceFlag()))
                        || lastServiceForSubscriber) {
                    deleteSubscriber = true;
                }

                if (deleteSubscriber) {
                    customerRepository.delete(sub);
                }

            }
            log.error(Constants.ACTION_COMPLETED);
            // -----------------------------
            // 11) Final Response
            // -----------------------------
            return new DeleteSPRResponse(
                    "200",
                    "UIV action DeleteSPR executed successfully.",
                    now(),
                    ontName,
                    subscriptionName
            );

        } catch (Exception ex) {
            log.error("Unhandled exception in DeleteSPR", ex);
            // Code1
            return new DeleteSPRResponse(
                    "500",
                    ERROR_PREFIX + "Internal server error occurred",
                    now(),
                    ontName,
                    subscriptionName
            );
        }
    }

    private void validateMandatory(String val, String param) throws BadRequestException {
        if (val == null || val.trim().isEmpty()) {
            throw new BadRequestException(param);
        }
    }

    private String now() {
        return java.time.Instant.now().toString();
    }

    private boolean equalsAny(String s, String... opts) {
        if (s == null) return false;
        for (String o : opts) {
            if (s.equalsIgnoreCase(o)) return true;
        }
        return false;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private String stringProp(Map<String, Object> props, String key) {
        if (props == null) return "";
        Object v = props.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private void clearVeipAndHsiTemplates(LogicalDevice olt) {
        Map<String, Object> p = ensureProps(olt);
        p.put("veipServiceTemplate", "");
        p.put("veipHsiTemplate", "");
        olt.setProperties(p);
        safeSaveLogicalDevice(olt);
    }

    private void clearOntCreateTemplate(LogicalDevice ont) {
        ensureProps(ont).put("createTemplate", "");
    }

    private void clearOntMgmtTemplates(LogicalDevice ont) {
        Map<String, Object> p = ensureProps(ont);
        p.put("mgmtTemplate", "");
        p.put("vlanCreateTemplate", "");
        ont.setProperties(p);
    }

    private void clearEvpnsOnPort(LogicalDevice olt, String port) {
        if (isEmpty(port)) return;
        Map<String, Object> p = ensureProps(olt);
        // Map common EVPN OLT port templates (2..5)
        p.put("evpnEthPort" + port + "Template", "");
        olt.setProperties(p);
        safeSaveLogicalDevice(olt);
    }

    private void clearEvpnsOnOntPort(LogicalDevice ont, String port) {
        if (isEmpty(port)) return;
        Map<String, Object> p = ensureProps(ont);
        // clear both create and active templates for the port
        p.put("evpnEthPort" + port + "Template", "");
        p.put("evpnEthPort" + port + "CreateTemplate", "");
        ont.setProperties(p);
        safeSaveLogicalDevice(ont);
    }

    private boolean allEvpnPortsEmpty(Optional<LogicalDevice> optOlt) {
        if (!optOlt.isPresent()) return true;
        Map<String, Object> p = ensureProps(optOlt.get());
        // Check ports 2..5 known in your model
        for (String k : Arrays.asList("evpnEthPort2Template","evpnEthPort3Template","evpnEthPort4Template","evpnEthPort5Template")) {
            String v = stringProp(p, k);
            if (!isEmpty(v)) return false;
        }
        return true;
    }

    private void clearOntCardTemplate(LogicalDevice olt) {
        ensureProps(olt).put("evpnOntCardTemplate", "");
    }

    private Map<String, Object> ensureProps(LogicalDevice d) {
        if (d.getProperties() == null) d.setProperties(new HashMap<>());
        return d.getProperties();
    }

    private void safeSaveLogicalDevice(LogicalDevice d) {
        LogicalDevice tempDevice = logicalDeviceRepository.findByDiscoveredName(d.getDiscoveredName()).get();
        tempDevice.setProperties(d.getProperties());
        logicalDeviceRepository.save(tempDevice, 2);
    }

    private void safeSaveCustomer(Customer c) {
        customerRepository.save(c, 2);
    }

    private Optional<LogicalDevice> getParentOlt(LogicalDevice ont) {
        String oltPos = stringProp(ont.getProperties(), "oltPosition");
        if (!isEmpty(oltPos)) {
            return logicalDeviceRepository.findByDiscoveredName(oltPos);
        }
        return Optional.empty();
    }

    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private String getPortTemplateValue(LogicalDevice ont, String port) {
        if (isEmpty(port)) return "0";
        return stringProp(ensureProps(ont), "evpnEthPort" + port + "Template");
    }

    private void setPortTemplateValue(LogicalDevice ont, String port, String value) {
        if (isEmpty(port)) return;
        ensureProps(ont).put("evpnEthPort" + port + "Template", value == null ? "" : value);
    }

    private void removePossibleVlanInterfaces(String ontSN, String ontPort) {
        if (isEmpty(ontSN) || isEmpty(ontPort)) return;
        for (int suffix = 2; suffix <= 8; suffix++) {
            String vlanName = ontSN + "_P" + ontPort + "_SINGLETAGGED_" + suffix;
            Optional<LogicalInterface> optVlan = logicalInterfaceRepository.findByDiscoveredName(vlanName);
            if (optVlan.isPresent()) {
                logicalInterfaceRepository.delete(optVlan.get());
                break; // stop after first match as per requirement
            }
        }
    }

    private void setCpePortStateAvailable(Optional<LogicalDevice> optCpe, String portType) {
        if (!optCpe.isPresent()) return;
        LogicalDevice cpe = optCpe.get();
        // In many deployments, CPE port state is recorded on contained components.
        // If they’re on properties of device itself, adapt keys accordingly.
        String key = "voipPort" + ("POTS_2".equals(portType) ? "2" : "1");
        cpe.getProperties().put(key,"Available");
        logicalDeviceRepository.save(cpe);
    }

    private void cleanupServiceObjects(Optional<Service> optRfs,
                                       Optional<Service> optCfs,
                                       Optional<Product> optProduct,
                                       Optional<Subscription> optSubscription) {

        // Remove RFS
        optRfs.ifPresent(serviceCustomRepository::delete);

        // Remove CFS
        optCfs.ifPresent(serviceCustomRepository::delete);

        // Remove Subscription
        optSubscription.ifPresent(subscriptionRepository::delete);
        // Remove Product (we cannot check attached CFS count generically here; delete directly as per requirement note)
        Set<Service> connectedCFSs = new HashSet<>();
        Optional<Product> tempProductOpt = productRepository.findByDiscoveredName(optProduct.isPresent()?optProduct.get().getDiscoveredName():"");
        if(tempProductOpt.isPresent()){
            connectedCFSs = tempProductOpt.get().getUsedService().stream().filter(ser->ser.getKind().equals(Constants.SETAR_KIND_SETAR_CFS)).collect(Collectors.toSet());
        }
        if(connectedCFSs.size()==0){
            optProduct.ifPresent(productRepository::delete);
        }
    }

    private int countSubscriptionsByCustomer(Customer customer) {
        try {
            Collection<Subscription> subs = (Collection<Subscription>) customer.getSubscription();
            if (subs != null) return subs.size();
        } catch (Exception ignore) {}
        return 1;
    }
    private void maybeClearOntCardTemplateIfAllPortsEmpty(LogicalDevice oltDevice) {
        // Fetch all logical interfaces linked to the OLT
        String port4temp = oltDevice.getProperties().get("evpnEthPort4Template")!=null?oltDevice.getProperties().get("evpnEthPort4Template").toString():"";
        String port3temp = oltDevice.getProperties().get("evpnEthPort3Template")!=null?oltDevice.getProperties().get("evpnEthPort3Template").toString():"";
        String port2temp = oltDevice.getProperties().get("evpnEthPort2Template")!=null?oltDevice.getProperties().get("evpnEthPort2Template").toString():"";
        boolean allPortsCleared=false;

        if ((port4temp == null || port4temp == "") && (port3temp == null || port3temp == "") && (port2temp == null || port2temp == "") ) {
            oltDevice = logicalDeviceRepository.findByDiscoveredName(oltDevice.getDiscoveredName()).get();
            oltDevice.getProperties().put("evpnOntCardTemplate","");
            allPortsCleared=true;
            logicalDeviceRepository.save(oltDevice);
        }

        if (allPortsCleared) {
            Map<String,Object> oltProps = oltDevice.getProperties();
            oltDevice = logicalDeviceRepository.findByDiscoveredName(oltDevice.getDiscoveredName()).get();
            // Clear the EVPN card template (reset description/properties, etc.)
            oltProps.put("description","EVPN card template cleared");
            oltProps.put("AdministrativeState", "Available");
            oltDevice.setProperties(oltProps);

            logicalDeviceRepository.save(oltDevice, 2);
            log.error("Cleared EVPN card template for OLT {}", oltDevice.getDiscoveredName());
        }
    }
}

