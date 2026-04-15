package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.request.UpdatedevicepropertyRequest;
import com.nokia.nsw.uiv.response.UpdatedevicepropertyResponse;
import com.nokia.nsw.uiv.utils.Constants;
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
public class Updatedeviceproperty implements HttpAction {
    protected static final String ACTION_LABEL = Constants.UPDATE_DEVICE_PROPERTY;
    private static final String ERROR_PREFIX = "UIV action Updatedeviceproperty execution failed - ";

    @Autowired
    private LogicalDeviceCustomRepository stbRepo;

    @Override
    public Class<?> getActionClass() {
        return UpdatedevicepropertyRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("------------Test Trace # 1--------------- Updatedeviceproperty started");
        UpdatedevicepropertyRequest req = (UpdatedevicepropertyRequest) actionContext.getObject();

        try {
            // 1. Mandatory validation
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatory(req.getStbSn1(), "stbSn1");
                Validations.validateMandatory(req.getCustomerGroupId(), "customerGroupId");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (Exception bre) {
                log.error("------------Test Trace # 2--------------- Missing param: " + bre.getMessage());
                return ResponseEntity.status(400).body(new UpdatedevicepropertyResponse(
                        "400",
                        ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage(),
                        Instant.now().toString(),
                        "", ""
                ));
            }

            String stbSn = req.getStbSn1();
            String custGroupId = req.getCustomerGroupId();
            log.error("------------Test Trace # 3--------------- Inputs: stbSn=" + stbSn + ", customerGroupId=" + custGroupId);

            // 2. Derive device name
            String stbName = "STB_" + stbSn;
            log.error("------------Test Trace # 4--------------- Derived device name: " + stbName);

            // 3. Locate STB
            Optional<LogicalDevice> stbOpt = stbRepo.findByDiscoveredName(stbName);
            if (!stbOpt.isPresent()) {
                log.error("------------Test Trace # 5--------------- STB not found: " + stbName);
                return ResponseEntity.status(404).body(new UpdatedevicepropertyResponse(
                        "404",
                        ERROR_PREFIX + "Error, No STB found with Allocated state to update.",
                        Instant.now().toString(),
                        stbSn,
                        custGroupId
                ));
            }

            LogicalDevice stb = stbOpt.get();
            String currentState = stb.getProperties().get("AdministrativeState")!=null?stb.getProperties().get("AdministrativeState").toString():null;
            log.error("------------Test Trace # 6--------------- STB found. Current state=" + currentState);

            // 4. Validate Allocated state
            if ("Allocated".equalsIgnoreCase(currentState)) {
                // 5. Update Device Property
                Map<String,Object>props= stb.getProperties();
                props.put("deviceGroupId",custGroupId);
                stb.setProperties(props);
                stbRepo.save(stb);
                log.error("------------Test Trace # 8--------------- CustomerGroupId updated to " + custGroupId);
                log.error(Constants.ACTION_COMPLETED);
            }else{
                log.error("------------Test Trace # 7--------------- STB not in Allocated state");
                return ResponseEntity.status(404).body(new UpdatedevicepropertyResponse(
                        "404",
                        ERROR_PREFIX + "Error, No STB found with Allocated state to update.",
                        Instant.now().toString(),
                        stbSn,
                        custGroupId
                ));
            }
            // 6. Success response
            return ResponseEntity.status(200).body(new UpdatedevicepropertyResponse(
                    "200",
                    "UIV action Updatedeviceproperty executed successfully.",
                    Instant.now().toString(),
                    stbSn,
                    custGroupId
            ));

        } catch (Exception ex) {
            log.error("Unhandled exception in Updatedeviceproperty", ex);
            return ResponseEntity.status(500).body(new UpdatedevicepropertyResponse(
                    "500",
                    ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage(),
                    Instant.now().toString(),
                    "", ""
            ));
        }
    }
}
