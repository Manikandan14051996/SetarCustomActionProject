package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.repository.ServiceCustomRepository;
import com.nokia.nsw.uiv.request.QueryAllEquipmentRequest;
import com.nokia.nsw.uiv.response.QueryAllEquipmentResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RestController
@Action
@Slf4j
public class QueryAllEquipment implements HttpAction {
    protected static final String ACTION_LABEL = Constants.QUERY_ALL_EQUIPMENT;
    private static final String ERROR_PREFIX = "UIV action QueryAllEquipment execution failed - ";

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Override
    public Class<?> getActionClass() {
        return QueryAllEquipmentRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("Executing QueryAllEquipment action...");
        QueryAllEquipmentRequest request = (QueryAllEquipmentRequest) actionContext.getObject();
        Map<String,String> deviceData = new HashMap<>();

        try {
            // Step 1: Validate mandatory params
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatoryParams(request.getSubscriberName(), "subscriberName");
                Validations.validateMandatoryParams(request.getServiceId(), "serviceId");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (BadRequestException bre) {

                return new QueryAllEquipmentResponse(
                        "400",
                        ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage(),
                        Instant.now().toString(),
                        deviceData
                );
            }

            // Step 2: Build RFS Name
            String rfsName = "RFS" + Constants.UNDER_SCORE + request.getSubscriberName() + Constants.UNDER_SCORE  + request.getServiceId();

            // Step 4: Fetch RFS
            Optional<Service> optRfs = serviceCustomRepository.findByDiscoveredName(rfsName);
            if (!optRfs.isPresent()) {
                return new QueryAllEquipmentResponse(
                        "404",
                        ERROR_PREFIX + "No entry found for delete",
                        Instant.now().toString(),
                        deviceData
                );
            }
            Service rfs = optRfs.get();

            boolean successFlag = false;
            int stbCounter = 1;
            int apCounter = 1;

            // Step 5: Process linked resources
            Set<com.nokia.nsw.uiv.model.resource.Resource> resources = rfs.getUsedResource();
            QueryAllEquipmentResponse response = new QueryAllEquipmentResponse();
            response.setStatus("200");
            response.setMessage("UIV action QueryAllEquipment executed successfully.");
            response.setTimestamp(Instant.now().toString());

            for (com.nokia.nsw.uiv.model.resource.Resource res : resources) {
                if (res instanceof LogicalDevice) {
                    LogicalDevice dev = (LogicalDevice) res;
                    String name = dev.getDiscoveredName();

                    if (name.startsWith("STB")) {
                        successFlag = true;
                        deviceData.put("STB_SerialNo_" + stbCounter, (String) dev.getProperties().getOrDefault("serialNo", ""));
                        deviceData.put("STB_Model_" + stbCounter, (String) dev.getProperties().getOrDefault("deviceModel", ""));
                        deviceData.put("STB_MacAddr_" + stbCounter, (String) dev.getProperties().getOrDefault("macAddress", ""));
                        deviceData.put("STB_PreShareKey_" + stbCounter, (String) dev.getProperties().getOrDefault("presharedKey", ""));
                        deviceData.put("STB_CustomerGroupID_" + stbCounter, (String) dev.getProperties().getOrDefault("deviceGroupId", ""));
                        stbCounter++;
                    } else if (name.startsWith("AP")) {
                        successFlag = true;
                        deviceData.put("AP_SerialNo_" + apCounter, (String) dev.getProperties().getOrDefault("serialNo", ""));
                        deviceData.put("AP_Model_" + apCounter, (String) dev.getProperties().getOrDefault("deviceModel", ""));
                        deviceData.put("AP_MacAddr_" + apCounter, (String) dev.getProperties().getOrDefault("macAddress", ""));
                        deviceData.put("AP_PreShareKey_" + apCounter, (String) dev.getProperties().getOrDefault("presharedKey", ""));
                        apCounter++;
                    }
                }
            }

            if (!successFlag) {
                return new QueryAllEquipmentResponse(
                        "500",
                        ERROR_PREFIX + "Error, Equipment Not Queried",
                        Instant.now().toString(),
                        deviceData
                );
            }
            log.error(Constants.ACTION_COMPLETED);
            response.setEquipmentsInfo(deviceData);
            return response;

        } catch (Exception ex) {
            log.error("Unhandled exception in QueryAllEquipment", ex);
            return new QueryAllEquipmentResponse(
                    "500",
                    ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage(),
                    Instant.now().toString(),
                    deviceData
            );
        }
    }
}
