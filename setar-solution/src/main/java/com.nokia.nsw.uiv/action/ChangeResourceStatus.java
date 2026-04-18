package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.AdministrativeState;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.request.ChangeResourceStatusRequest;
import com.nokia.nsw.uiv.response.ChangeResourceStatusResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.Validations;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RestController
@Action
@Slf4j
public class ChangeResourceStatus implements HttpAction {
    protected static final String ACTION_LABEL = Constants.CHANGE_RESOURCE_STATUS;
    private static final String ERROR_PREFIX = "UIV action ChangeResourceStatus execution failed - ";

    @Autowired
    private LogicalDeviceCustomRepository stbRepo;

    @Override
    public Class<?> getActionClass() {
        return ChangeResourceStatusRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("------------Test Trace # 1--------------- ChangeResourceStatus started");
        ChangeResourceStatusRequest req = (ChangeResourceStatusRequest) actionContext.getObject();

        try {
            // 1. Mandatory validation
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatory(req.getResourceSn(), "resourceSn");
                Validations.validateMandatory(req.getResourceType(), "resourceType");
                Validations.validateMandatory(req.getResourceStatus(), "resourceStatus");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (Exception bre) {
                log.error("------------Test Trace # 2--------------- Missing param: " + bre.getMessage());
                return ResponseEntity.status(400).body(new ChangeResourceStatusResponse(
                        "400",
                        ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage(),
                        DateTimeUtil.now(),
                        "", "", "", "", ""
                ));
            }

            String sn = req.getResourceSn();
            String type = req.getResourceType();
            String targetStatus = req.getResourceStatus();

            log.error("------------Test Trace # 3--------------- Inputs: SN=" + sn + ", Type=" + type + ", TargetStatus=" + targetStatus);


            // 3. Derive Device Name
            String devName = type + Constants.UNDER_SCORE  + sn;
            log.error("------------Test Trace # 4--------------- Derived device name: " + devName);

            // 4. Locate device
            Optional<LogicalDevice> devOpt = stbRepo.findByDiscoveredName(devName);
            if (!devOpt.isPresent()) {
                log.error("------------Test Trace # 5--------------- Device not found: " + devName);
                return ResponseEntity.status(404).body(new ChangeResourceStatusResponse(
                        "404",
                        ERROR_PREFIX + type + sn + " not found",
                        DateTimeUtil.now(),
                        sn, "", "", "", type
                ));
            }

            LogicalDevice device = devOpt.get();
            String currentStatus = device.getProperties().get("AdministrativeState")!=null?device.getProperties().get("AdministrativeState").toString():null;
            String model = device.getProperties().get("deviceModel") == null ? "" : device.getProperties().get("deviceModel").toString();
            String mac = device.getProperties().get("macAddress") == null ? "" : device.getProperties().get("macAddress").toString() ;

            log.error("------------Test Trace # 6--------------- Device found. Current status=" + currentStatus);

            // 5. Apply status change
            if (targetStatus.equalsIgnoreCase(currentStatus)) {
                log.error("------------Test Trace # 7--------------- No change required");
                return ResponseEntity.status(404).body(new ChangeResourceStatusResponse(
                        "404",
                        ERROR_PREFIX + "No change required. Resource already in status " + targetStatus,
                        DateTimeUtil.now(),
                        sn, mac, currentStatus, model, type
                ));
            }

            Map<String, Object> deviceProps = device.getProperties();
            deviceProps.put("AdministrativeState",targetStatus);
            device.setProperties(deviceProps);
            stbRepo.save(device);

            log.error("------------Test Trace # 8--------------- Device status updated to " + targetStatus);
            log.error(Constants.ACTION_COMPLETED);
            // 6. Success response
            return new ChangeResourceStatusResponse(
                    "200",
                    "UIV action ChangeResourceStatus executed successfully.",
                    DateTimeUtil.now(),
                    sn,
                    mac,
                    targetStatus,
                    model,
                    type
            );

        } catch (Exception ex) {
            log.error("Unhandled exception in ChangeResourceStatus", ex);
            return ResponseEntity.status(500).body(new ChangeResourceStatusResponse(
                    "500",
                    ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage(),
                    DateTimeUtil.now(),
                    "", "", "", "", ""
            ));
        }
    }
}
