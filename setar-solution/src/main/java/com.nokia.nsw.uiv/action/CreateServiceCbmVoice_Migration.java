package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.*;
import com.nokia.nsw.uiv.framework.action.*;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.service.*;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.*;
import com.nokia.nsw.uiv.response.CreateServiceCbmVoiceResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Action
@Slf4j
public class CreateServiceCbmVoice_Migration implements HttpAction {

    protected static final String ACTION_LABEL = Constants.CREATE_SERVICE_CBM_VOICE;

    private static final String ERROR_PREFIX = "UIV action CreateServiceCBMVoice execution failed - ";
    private static final String CODE_SUCCESS = "201";
    private static final String CODE_MISSING_PARAMS = "400";
    private static final String CODE_ALREADY_EXISTS = "409";
    private static final String CODE_PERSISTENCE_ERROR = "500";
    private static final String CODE_CPE_NOT_FOUND = "404";
    private static final String CODE_NAME_TOO_LONG = "400";
    private static final String CODE_EXCEPTION = "500";

    @Autowired private CustomerCustomRepository subscriberRepository;
    @Autowired private SubscriptionCustomRepository subscriptionRepository;
    @Autowired private ProductCustomRepository productRepository;
    @Autowired private ServiceCustomRepository serviceCustomRepository;
    @Autowired private LogicalDeviceCustomRepository cbmDeviceRepository;
    @Autowired private LogicalDeviceCustomRepository cpeDeviceRepository;

    @Override
    public Class<?> getActionClass() {
        return CreateServiceCbmVoiceBulkRequest.class;
    }

    @Override
    public Object doPost(ActionContext ctx) {

        CreateServiceCbmVoiceBulkRequest bulk =
                (CreateServiceCbmVoiceBulkRequest) ctx.getObject();

        List<CreateServiceCbmVoiceResponse> responses = new ArrayList<>();

        for (CreateServiceCbmVoiceRequest request : bulk.getServices()) {

            try {
                log.error("Processing request: {}", request.getServiceId());

                /* ================= VALIDATION ================= */
                try {
                    Validations.validateMandatoryParams(request.getSubscriberName(), "subscriberName");
                    Validations.validateMandatoryParams(request.getProductType(), "productType");
                    Validations.validateMandatoryParams(request.getCbmSN(), "cbmSN");
                    Validations.validateMandatoryParams(request.getCbmMac(), "cbmMac");
                    Validations.validateMandatoryParams(request.getCbmManufacturer(), "cbmManufacturer");
                    Validations.validateMandatoryParams(request.getCbmType(), "cbmType");
                    Validations.validateMandatoryParams(request.getCbmModel(), "cbmModel");
                    Validations.validateMandatoryParams(request.getHhid(), "hhid");
                    Validations.validateMandatoryParams(request.getServiceId(), "serviceId");
                    Validations.validateMandatoryParams(request.getVoipNumber1(), "voipNumber1");
                    Validations.validateMandatoryParams(request.getSimaCustId(), "simaCustId");
                    Validations.validateMandatoryParams(request.getSimaSubsId(), "simaSubsId");
                    Validations.validateMandatoryParams(request.getSimaEndpointId(), "simaEndpointId");
                } catch (BadRequestException bre) {
                    responses.add(error(CODE_MISSING_PARAMS, bre.getMessage()));
                    continue;
                }

                /* ================= CPE CHECK ================= */
                String cpeDeviceName = "CBM_" + request.getCbmMac();
                Optional<LogicalDevice> cpeOpt = cpeDeviceRepository.findByDiscoveredName(cpeDeviceName);
                if (!cpeOpt.isPresent()) {
                    responses.add(error(CODE_CPE_NOT_FOUND, "CPE not found: " + cpeDeviceName));
                    continue;
                }

                /* ================= NAME BUILD ================= */
                String macClean = request.getCbmMac().replace(":", "");
                String subscriberName = request.getSubscriberName() + "_" + macClean;

                String subscriptionName = request.getSubscriberName() + "_" + request.getServiceId();
                String productName = request.getSubscriberName() + "_" + request.getProductSubtype() + "_" + request.getServiceId();
                String cfsName = "CFS_" + subscriptionName;
                String rfsName = "RFS_" + subscriptionName;
                String cbmName = "CBM" + request.getServiceId();

                if (subscriberName.length() > 100 || subscriptionName.length() > 100 ||
                        cfsName.length() > 100 || rfsName.length() > 100 || cbmName.length() > 100) {
                    responses.add(error(CODE_NAME_TOO_LONG, "Name length exceeded"));
                    continue;
                }

                AtomicBoolean subExist = new AtomicBoolean(true);
                AtomicBoolean subsExist = new AtomicBoolean(true);
                AtomicBoolean prodExist = new AtomicBoolean(true);

                /* ================= SUBSCRIBER ================= */
                Customer subscriber = subscriberRepository.findByDiscoveredName(subscriberName)
                        .orElseGet(() -> {
                            subExist.set(false);
                            Customer s = new Customer();
                            try {
                                s.setLocalName(Validations.encryptName(subscriberName));
                                s.setDiscoveredName(subscriberName);
                                s.setContext(Constants.SETAR);
                                s.setKind(Constants.SETAR_KIND_SETAR_SUBSCRIBER);
                            } catch (Exception e) { throw new RuntimeException(e); }

                            Map<String, Object> subProps = new HashMap<>();
                            subProps.put("subscriberStatus", "Active");
                            subProps.put("subscriberType", "Regular");
                            subProps.put("accountNumber", request.getSubscriberName());
                            subProps.put("houseHoldId", request.getHhid());

                            if (request.getUserName() != null && !request.getUserName().trim().isEmpty()) {
                                subProps.put("userName", request.getUserName());
                            }

                            subProps.put("createdBy",
                                    request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                            ? request.getCreatedBy()
                                            : "CA"
                            );
                            subProps.put("actionName", ACTION_LABEL);
                            s.setProperties(subProps);

                            subscriberRepository.save(s,2);
                            return s;
                        });

                /* ================= SUBSCRIPTION ================= */
                Subscription subscription = subscriptionRepository.findByDiscoveredName(subscriptionName)
                        .orElseGet(() -> {
                            subsExist.set(false);
                            Subscription s = new Subscription();
                            try {
                                s.setLocalName(Validations.encryptName(subscriptionName));
                                s.setDiscoveredName(subscriptionName);
                                s.setContext(Constants.SETAR);
                                s.setKind(Constants.SETAR_KIND_SETAR_SUBSCRIPTION);
                            } catch (Exception e) { throw new RuntimeException(e); }

                            Map<String, Object> props = new HashMap<>();
                            props.put("subscriptionStatus", "Active");
                            props.put("serviceSubType", request.getProductSubtype());
                            props.put("serviceLink", "Cable_Modem");
                            props.put("serviceSN", request.getCbmSN());
                            props.put("serviceMAC", request.getCbmMac());
                            props.put("serviceID", request.getServiceId());
                            props.put("householdId", request.getHhid());

                            if (request.getQosProfile() != null)
                                props.put("qosProfile", request.getQosProfile());

                            if (request.getCustomerGroupId() != null && !"NA".equalsIgnoreCase(request.getCustomerGroupId()))
                                props.put("customerGroupId", request.getCustomerGroupId());

                            if (request.getSubscriberId() != null)
                                props.put("subscriberID_CableModem", request.getSubscriberId());

                            if (request.getKenanUidNo() != null)
                                props.put("billingId", request.getKenanUidNo());
                            String voipPort = request.getVoipPort();
                            if (voipPort != null) {
                                int port = Integer.parseInt(voipPort);

                                if (port == 1) {
                                    props.put("simaCustId1", request.getSimaCustId());
                                    props.put("voipNumber1", request.getVoipNumber1());
                                    props.put("simaSubsId1", request.getSimaSubsId());
                                    props.put("simaEndpointId1", request.getSimaEndpointId());
                                    props.put("voipServiceCode1", request.getVoipServiceCode());

                                    if (request.getServicePackage() != null)
                                        props.put("voipPackage1", request.getServicePackage());

                                } else if (port == 2) {
                                    props.put("simaCustId2", request.getSimaCustId());
                                    props.put("voipNumber2", request.getVoipNumber1());
                                    props.put("simaSubsId2", request.getSimaSubsId());
                                    props.put("simaEndpointId2", request.getSimaEndpointId());
                                    props.put("voipServiceCode2", request.getVoipServiceCode());

                                    if (request.getServicePackage() != null)
                                        props.put("voipPackage2", request.getServicePackage());
                                }
                            }
                            s.setProperties(props);
                            s.setCustomer(subscriber);

                            subscriptionRepository.save(s,2);
                            return s;
                        });

                /* ================= PRODUCT ================= */
                Product product = productRepository.findByDiscoveredName(productName)
                        .orElseGet(() -> {
                            prodExist.set(false);
                            Product p = new Product();
                            try {
                                p.setLocalName(Validations.encryptName(productName));
                                p.setDiscoveredName(productName);
                                p.setContext(Constants.SETAR);
                                p.setKind(Constants.SETAR_KIND_SETAR_PRODUCT);
                            } catch (Exception e) { throw new RuntimeException(e); }

                            Map<String, Object> prodProps = new HashMap<>();
                            prodProps.put("productStatus", "Active");
                            prodProps.put("productType", request.getProductType());

                            prodProps.put("createdBy",
                                    request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                            ? request.getCreatedBy()
                                            : "CA"
                            );
                            prodProps.put("actionName", ACTION_LABEL);
                            p.setProperties(prodProps);
                            p.setCustomer(subscriber);

                            productRepository.save(p,2);
                            return p;
                        });

                if (subExist.get() && subsExist.get() && prodExist.get()) {
                    responses.add(new CreateServiceCbmVoiceResponse(
                            CODE_ALREADY_EXISTS,
                            "Duplicate entry",
                            Instant.now().toString(),
                            subscriptionName,
                            cbmName
                    ));
                    continue;
                }

                subscription.setService(new HashSet<>(List.of(product)));
                subscriptionRepository.save(subscription,2);

                /* ================= CFS ================= */
                Service cfs = new Service();
                cfs.setDiscoveredName(cfsName);
                Map<String, Object> cfsProps = new HashMap<>();
                cfsProps.put("serviceStatus", "Active");
                cfsProps.put("serviceType", request.getProductType());
                cfsProps.put("serviceStartDate", Instant.now().toString());

                if (request.getFxOrderID() != null)
                    cfsProps.put("transactionId", request.getFxOrderID());

                cfsProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                cfsProps.put("actionName", ACTION_LABEL);
                cfs.setProperties(cfsProps);
                cfs.setUsingService(Set.of(product));
                serviceCustomRepository.save(cfs,2);

                /* ================= RFS ================= */
                Service rfs = new Service();
                rfs.setDiscoveredName(rfsName);
                Map<String, Object> rfsProps = new HashMap<>();
                rfsProps.put("serviceStatus", "Active");
                rfsProps.put("serviceType", request.getProductType());
                rfsProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                rfsProps.put("actionName", ACTION_LABEL);
                rfs.setProperties(rfsProps);
                rfs.setUsedService(Set.of(cfs));
                serviceCustomRepository.save(rfs,2);

                /* ================= DEVICE ================= */
                LogicalDevice device = new LogicalDevice();
                device.setDiscoveredName(cbmName);
                Map<String, Object> deviceProps = new HashMap<>();
                deviceProps.put("serialNo", request.getCbmSN());
                deviceProps.put("macAddress", request.getCbmMac());
                deviceProps.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                deviceProps.put("actionName", ACTION_LABEL);
                if (request.getCbmGatewayMac() != null) deviceProps.put("gatewayMacAddress", request.getCbmGatewayMac());
                if (request.getCbmType() != null) deviceProps.put("deviceType", request.getCbmType());
                if (request.getCbmManufacturer() != null) deviceProps.put("manufacturer", request.getCbmManufacturer());
                if (request.getCbmModel() != null) deviceProps.put("deviceModel", request.getCbmModel());
                deviceProps.put("OperationalState", "Active");
                device.setProperties(deviceProps);
                device.setUsingService(Set.of(rfs));
                cbmDeviceRepository.save(device,2);

                /* ================= VOICE UPDATE ================= */
                if ("Voice".equalsIgnoreCase(request.getProductSubtype())) {
                    LogicalDevice cpe = cpeOpt.get();
                    Map<String,Object> props = cpe.getProperties()==null?new HashMap<>():cpe.getProperties();
                    props.put("voipPort1",request.getVoipNumber1());
                    cpe.setProperties(props);
                    cpeDeviceRepository.save(cpe,2);
                }

                responses.add(new CreateServiceCbmVoiceResponse(
                        CODE_SUCCESS,
                        "Success",
                        Instant.now().toString(),
                        subscriptionName,
                        cbmName
                ));

            } catch (Exception ex) {
                log.error("Error", ex);
                responses.add(error(CODE_EXCEPTION, ex.getMessage()));
            }
        }

        return responses;
    }

    private CreateServiceCbmVoiceResponse error(String code, String msg) {
        CreateServiceCbmVoiceResponse r = new CreateServiceCbmVoiceResponse();
        r.setStatus(code);
        r.setMessage(ERROR_PREFIX + msg);
        r.setTimestamp(new Date().toString());
        return r;
    }
}