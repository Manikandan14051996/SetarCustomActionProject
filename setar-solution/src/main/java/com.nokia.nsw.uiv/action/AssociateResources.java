package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.Resource;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.repository.ServiceCustomRepository;
import com.nokia.nsw.uiv.request.AssociateResourcesRequest;
import com.nokia.nsw.uiv.response.AssociateResourcesResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.Validations;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

@Component
@RestController
@Action
@Slf4j
public class AssociateResources implements HttpAction {
    protected static final String ACTION_LABEL = Constants.ASSOCIATE_RESOURCES;
    private static final String ERROR_PREFIX = "UIV action AssociateResources execution failed - ";

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Autowired
    private LogicalDeviceCustomRepository deviceRepository;
    private String[] customerGroupIds;


    @Override
    public Class<?> getActionClass() {
        return AssociateResourcesRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("Executing AssociateResources action...");
        log.error("----Trace #1: Entered AssociateResources Action ----");

        AssociateResourcesRequest request = (AssociateResourcesRequest) actionContext.getObject();

        try {
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
            // Step 1: Mandatory validations
            log.error("----Trace #2: Validating mandatory params ----");
            try {
                Validations.validateMandatoryParams(request.getSubscriberName(), "subscriberName");
                Validations.validateMandatoryParams(request.getServiceId(), "serviceId");
                Validations.validateMandatoryParams(request.getProductSubtype(), "productSubtype");
                if (request.getProductSubtype().equalsIgnoreCase("IPTV")) {
                    Validations.validateMandatoryParams(request.getApSN1(), "apSN1");
                    Validations.validateMandatoryParams(request.getApSN2(), "apSN2");
                    Validations.validateMandatoryParams(request.getApSN3(), "apSN3");
                    Validations.validateMandatoryParams(request.getApSN4(), "apSN4");
                    Validations.validateMandatoryParams(request.getApSN5(), "apSN5");
                    Validations.validateMandatoryParams(request.getStbSN1(), "stbSN1");
                    Validations.validateMandatoryParams(request.getStbSN2(), "stbSN2");
                    Validations.validateMandatoryParams(request.getStbSN3(), "stbSN3");
                    Validations.validateMandatoryParams(request.getStbSN4(), "stbSN4");
                    Validations.validateMandatoryParams(request.getStbSN5(), "stbSN5");
                }
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (BadRequestException bre) {
                return ResponseEntity.status(400).body(new AssociateResourcesResponse(
                        "400",
                        ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage(),
                        DateTimeUtil.now(),
                        ""
                ));
            }

            // Step 2: Prepare entity names
            String subscriberName = request.getSubscriberName();
            String subscriptionName;
            String rfsName;
            log.error("----Trace #3: Preparing entity names ----");
            if ("IPTV".equalsIgnoreCase(request.getProductSubtype())) {
                subscriptionName = subscriberName + Constants.UNDER_SCORE + request.getServiceId();
                rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
            } else if (request.getOntSN() != null && !"NA".equalsIgnoreCase(request.getOntSN())) {
                subscriptionName = subscriberName + Constants.UNDER_SCORE + request.getServiceId() + Constants.UNDER_SCORE + request.getOntSN();
                rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
            } else if (request.getCbmSN() != null && !"NA".equalsIgnoreCase(request.getCbmSN())) {
                subscriptionName = subscriberName + Constants.UNDER_SCORE + request.getServiceId() + Constants.UNDER_SCORE + request.getCbmSN();
                rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
            } else {
                return  ResponseEntity.status(400).body(new AssociateResourcesResponse(
                        "400",
                        ERROR_PREFIX + "Invalid combination of identifiers",
                        DateTimeUtil.now(),
                        ""
                ));
            }

            // Step 3: Retrieve RFS and Admin State
            log.error("----Trace #4: Retrieving RFS and AdminState ----");
            Optional<Service> optRfs = serviceCustomRepository.findByDiscoveredName(rfsName);
            if (!optRfs.isPresent()) {
                return  ResponseEntity.status(404).body(new AssociateResourcesResponse(
                        "404",
                        ERROR_PREFIX + "Required entity not found: " + rfsName,
                        DateTimeUtil.now(),
                        ""
                ));
            }
            Service rfs = optRfs.get();
            Map<String, Object> rfsProps = rfs.getProperties();
            rfsProps.put("transactionId", request.getFxOrderID());
            serviceCustomRepository.save(rfs, 2);
            log.error("----Trace #9: Saving RFS changes ----");

            // Step 4: IPTV logic
            boolean deviceUpdated = false;
            int stbSerialsCount = 0;
            int apSerialsCount = 0;
            if ("IPTV".equalsIgnoreCase(request.getProductSubtype())) {
                log.error("----Trace #5: Executing IPTV device association ----");

                // STBs
                String[] stbSerials = {
                        request.getStbSN1(), request.getStbSN2(), request.getStbSN3(),
                        request.getStbSN4(), request.getStbSN5(), request.getStbSN6(),
                        request.getStbSN7(), request.getStbSN8(), request.getStbSN9(),
                        request.getStbSN10(), request.getStbSN11(), request.getStbSN12(),
                        request.getStbSN13(), request.getStbSN14(), request.getStbSN15(),
                        request.getStbSN16(), request.getStbSN17(), request.getStbSN18(),
                        request.getStbSN19(), request.getStbSN20()
                };
                String[] customerGroupIds = {
                        request.getCustomerGroupID1(), request.getCustomerGroupID2(), request.getCustomerGroupID3(),
                        request.getCustomerGroupID4(), request.getCustomerGroupID5(), request.getCustomerGroupID6(),
                        request.getCustomerGroupID7(), request.getCustomerGroupID8(), request.getCustomerGroupID9(),
                        request.getCustomerGroupID10(), request.getCustomerGroupID11(), request.getCustomerGroupID12(),
                        request.getCustomerGroupID13(), request.getCustomerGroupID14(), request.getCustomerGroupID15(),
                        request.getCustomerGroupID16(), request.getCustomerGroupID17(), request.getCustomerGroupID18(),
                        request.getCustomerGroupID19(), request.getCustomerGroupID20()
                };


                String[] apSerials = {
                        request.getApSN1(), request.getApSN2(), request.getApSN3(),
                        request.getApSN4(), request.getApSN5(), request.getApSN6(),
                        request.getApSN7(), request.getApSN8(), request.getApSN9(),
                        request.getApSN10(), request.getApSN11(), request.getApSN12(),
                        request.getApSN13(), request.getApSN14(), request.getApSN15(),
                        request.getApSN16(), request.getApSN17(), request.getApSN18(),
                        request.getApSN19(), request.getApSN20()
                };

                for (int i = 0; i < stbSerials.length; i++) {
                    String sn = stbSerials[i];
                    if (sn != null && !"NA".equalsIgnoreCase(sn) && !sn.isEmpty()) {
                        String devName = "STB_" + sn;
                        log.error("----Trace #6: Processing STB device: " + devName + " ----");
                        Optional<LogicalDevice> optDev = deviceRepository.findByDiscoveredName(devName);
                        if (!optDev.isPresent()) {
                            return  ResponseEntity.status(404).body(new AssociateResourcesResponse(
                                    "404",
                                    ERROR_PREFIX + "Device not found: " + devName,
                                    DateTimeUtil.now(),
                                    ""
                            ));
                        }
                        LogicalDevice device = optDev.get();
                        device.getProperties().put("deviceGroupId", customerGroupIds[i]!=null?customerGroupIds[i]:"");
                        device.getProperties().put("AdministrativeState", "Allocated");
                        if (request.getOntSN() != null && !"NA".equalsIgnoreCase(request.getOntSN())) {
                            if(request.getOntSN().contains("ONT")){
                                device.getProperties().put("description",request.getServiceId() + request.getOntSN().replace("ONT", Constants.UNDER_SCORE));
                            }else{
                                device.getProperties().put("description",request.getServiceId()+Constants.UNDER_SCORE+request.getOntSN());
                            }
                        }
                        else {
                            device.getProperties().put("description",request.getServiceId());
                        }
                        device.setUsingService(new HashSet<>(List.of(rfs)));
                        deviceRepository.save(device);
                        deviceUpdated = true;
                    }
                }

                for (String sn : apSerials) {
                    if (sn != null && !"NA".equalsIgnoreCase(sn) && !sn.isEmpty()) {
                        String devName = "AP_" + sn;
                        log.error("----Trace #7: Processing AP device: " + devName + " ----");
                        Optional<LogicalDevice> optDev = deviceRepository.findByDiscoveredName(devName);
                        if (!optDev.isPresent()) {
                            return ResponseEntity.status(404).body(new AssociateResourcesResponse(
                                    "404",
                                    ERROR_PREFIX + "Device not found: " + devName,
                                    Instant.now().toString(),
                                    ""
                            ));
                        }
                        LogicalDevice device = optDev.get();
                        device.getProperties().put("AdministrativeState", "Allocated");
                        device.getProperties().put("description",request.getServiceId());
                        device.setUsingService(new HashSet<>(List.of(rfs)));
                        deviceRepository.save(device);
                        deviceUpdated = true;
                    }
                }
            } else {
                // Step 6: Non-IPTV (ONT/CBM)
                log.error("----Trace #8: Executing Non-IPTV device association ----");
                String devName = null;
                if (request.getOntSN() != null && !"NA".equalsIgnoreCase(request.getOntSN())) {
                    devName = "ONT" + request.getOntSN();
                } else if (request.getCbmSN() != null && !"NA".equalsIgnoreCase(request.getCbmSN())) {
                    devName = "CBM" + request.getCbmSN();
                }

                if (devName != null) {
                    Optional<LogicalDevice> optDev = deviceRepository.findByDiscoveredName(devName);
                    if (!optDev.isPresent()) {
                        return ResponseEntity.status(404).body(new AssociateResourcesResponse(
                                "404",
                                ERROR_PREFIX + "Device not found: " + devName,
                                DateTimeUtil.now(),
                                ""
                        ));
                    }
                    LogicalDevice device = optDev.get();
                    Map<String, Object> props = new HashMap<>();
                    device.getProperties().put("AdministrativeState", "Allocated");
                    device.getProperties().put("description",request.getServiceId());
                    device.setUsingService(new HashSet<>(List.of(rfs)));
                    deviceRepository.save(device);
                    deviceUpdated = true;
                }
            }

            // Step 7: Persist RFS changes
            if (deviceUpdated) {
                rfs = serviceCustomRepository.findByDiscoveredName(rfs.getDiscoveredName()).get();
                Map<String, Object> rfsProp = rfs.getProperties();
                rfsProp.put("transactionId", request.getFxOrderID());
                serviceCustomRepository.save(rfs, 2);
                log.error("----Trace #9: Saving RFS changes ----");
                log.error(Constants.ACTION_COMPLETED);
                return new AssociateResourcesResponse(
                        "200",
                        "UIV action AssociateResources executed successfully.",
                        DateTimeUtil.now(),
                        subscriptionName
                );
            } else {
                return  ResponseEntity.status(409).body(new AssociateResourcesResponse(
                        "409",
                        ERROR_PREFIX + "Resource not attached",
                        DateTimeUtil.now(),
                        ""
                ));
            }

        } catch (Exception ex) {
            log.error("Unhandled exception during AssociateResources", ex);
            return ResponseEntity.status(500).body(new AssociateResourcesResponse(
                    "500",
                    ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage(),
                    DateTimeUtil.now(),
                    ""
            ));
        }
    }
}
