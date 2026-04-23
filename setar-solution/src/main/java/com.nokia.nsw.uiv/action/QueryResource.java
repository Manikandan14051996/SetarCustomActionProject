package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.request.QueryResourceRequest;
import com.nokia.nsw.uiv.response.QueryResourceResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
@RestController
@Action
@Slf4j
public class QueryResource implements HttpAction {
    protected static final String ACTION_LABEL = Constants.QUERY_RESOURCE;
    private static final String ERROR_PREFIX = "UIV action QueryResource execution failed - ";

    @Autowired
    private LogicalDeviceCustomRepository deviceRepository;

    @Override
    public Class getActionClass() {
        return QueryResourceRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        QueryResourceRequest request = (QueryResourceRequest) actionContext.getObject();
        String resourceSN = request.getResourceSN();
        String resourceType = request.getResourceType();

        try {
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
            // Step 1: Mandatory Validations
            Validations.validateMandatoryParams(resourceSN, "resourceSN");
            Validations.validateMandatoryParams(resourceType, "resourceType");
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            // Step 2: Construct Device Name
            String devName;
            if ("CBM".equalsIgnoreCase(resourceType) || "ONT".equalsIgnoreCase(resourceType)) {
                devName = resourceType + resourceSN;
            } else {
                devName = resourceType + Constants.UNDER_SCORE  + resourceSN;
            }

            // Step 3: Search Device
            Optional<LogicalDevice> optDev = deviceRepository.findByDiscoveredName(devName);
            if (optDev.isEmpty()) {
                return ResponseEntity.status(404).body(errorResponse("404", ERROR_PREFIX + "Resource not found, SN is: " + resourceSN));
            }

            LogicalDevice device = optDev.get();
            Map<String, Object> props = device.getProperties();
            String devModel = Objects.toString(props.get("deviceModel"), "");
            String devMan = Objects.toString(props.get("manufacturer"), "");
            String devSN = Objects.toString(props.get("serialNo"), resourceSN);
            String devMAC = Objects.toString(props.get("macAddress"), "");
            String gatewayMac = Objects.toString(props.get("gatewayMacAddress"), "");
            String devStatus = Objects.toString(props.get("AdministrativeState"), "");
            String devKEY = Objects.toString(props.get("presharedKey"), "");
            String devDesc = Objects.toString(props.get("description"), "");


            String devGroupID = "NA";
            String devSubTYPE = "";

            // Step 3b: Handle subtype/groupID for AP/STB
            if ("AP".equalsIgnoreCase(resourceType)) {
                devSubTYPE = "Not Applicable";
            } else if ("STB".equalsIgnoreCase(resourceType)) {
                devGroupID = (String) device.getProperties().getOrDefault("deviceGroupId", "NA");
                devSubTYPE = (String) device.getProperties().getOrDefault("modelSubType", "");
            }
            log.error(Constants.ACTION_COMPLETED);
            // Step 4: Final Success Response
            return ResponseEntity.status(200).body(new QueryResourceResponse(
                    "200",
                    "UIV action QueryResource executed successfully.",
                    DateTimeUtil.now(),
                    devSN,
                    devMAC,
                    devStatus,
                    devModel,
                    devSubTYPE,
                    devKEY,
                    resourceType,
                    devMan,
                    devGroupID,
                    devDesc,
                    gatewayMac
            ));

        } catch (BadRequestException bre) {
            return ResponseEntity.status(400).body(errorResponse("400", ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(errorResponse("500", ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage()));
        }
    }

    private QueryResourceResponse errorResponse(String code, String message) {
        return new QueryResourceResponse(
                code,
                message,
                DateTimeUtil.now(),
                "", "", "", "", "", "", "", "", "", "", ""
        );
    }

    private String getCurrentTimestamp() {
        return Instant.now().toString();
    }
}
