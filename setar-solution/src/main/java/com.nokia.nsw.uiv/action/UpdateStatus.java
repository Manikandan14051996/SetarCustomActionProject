package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.logical.LogicalComponent;
import com.nokia.nsw.uiv.repository.LogicalComponentCustomRepository;
import com.nokia.nsw.uiv.request.UpdateStatusRequest;
import com.nokia.nsw.uiv.response.CreateProductSubscriptionResponse;
import com.nokia.nsw.uiv.response.UpdateStatusResponse;
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
import java.util.Optional;

@Component
@RestController
@Action
@Slf4j
public class UpdateStatus implements HttpAction {

    protected static final String ACTION_LABEL = "UpdateStatus";
    private static final String ERROR_PREFIX = "UIV action UpdateStatus execution failed - ";

    @Autowired
    private LogicalComponentCustomRepository portRepository;

    @Override
    public Class<?> getActionClass() {
        return UpdateStatusRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {

        log.info(Constants.EXECUTING_ACTION, ACTION_LABEL);

        UpdateStatusRequest request = (UpdateStatusRequest) actionContext.getObject();
        String portName = "";
        String portStatus = "";

        try {
            // ---------------------------
            // Validate Mandatory Params
            // ---------------------------
            log.info("Validating mandatory parameters...");

            Validations.validateMandatoryParams(request.getSerialNumber(), "serialNumber");
            Validations.validateMandatoryParams(request.getPortType(), "portType");
            Validations.validateMandatoryParams(request.getPortNumber(), "portNumber");
            Validations.validateMandatoryParams(request.getPortStatus(), "portStatus");

            // ---------------------------
            // Build Port Name
            // ---------------------------
            portName = request.getSerialNumber()
                    + Constants.UNDER_SCORE
                    + request.getPortType()
                    + Constants.UNDER_SCORE
                    + request.getPortNumber();

            portStatus = request.getPortStatus();

            if (portName.length() > 100) {
                throw new BadRequestException("Port name too long");
            }

            // ---------------------------
            // Find Port in DB
            // ---------------------------
            Optional<LogicalComponent> optPort = portRepository.findByDiscoveredName(portName);

            if (!optPort.isPresent()) {
                throw new BadRequestException("Port Entity not present for this port: " + portName);
            }

            LogicalComponent port = optPort.get();

            // ---------------------------
            // Update portStatus property
            // ---------------------------
            log.info("Updating port {} with status {}", portName, portStatus);
            Map<String, Object> props = port.getProperties();
            props.put("portStatus", portStatus);
            port.setProperties(props);
            portRepository.save(port, 2);

            // ---------------------------
            // Success response
            // ---------------------------
            return ResponseEntity.status(201).body(new UpdateStatusResponse(
                    "201",
                    "Port Status Updated Successfully",
                    DateTimeUtil.now(),
                    portName,
                    portStatus
            ));

        } catch (BadRequestException bre) {
            String msg = ERROR_PREFIX + bre.getMessage();
            log.error(msg);
            return ResponseEntity.status(400).body(new UpdateStatusResponse(
                    "400",
                    msg,
                    DateTimeUtil.now(),
                    portName,
                    portStatus
            ));

        }catch (Exception ex) {
            String msg = "UIV action UpdateStatus execution failed - Internal server error occurred";
            return ResponseEntity.status(500).body(new UpdateStatusResponse("500", msg + " - " + ex.getMessage(),
                    DateTimeUtil.now(), "", ""));
        }
    }
}
