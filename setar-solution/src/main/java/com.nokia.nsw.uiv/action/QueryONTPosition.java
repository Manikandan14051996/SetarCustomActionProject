package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.request.QueryONTPositionRequest;
import com.nokia.nsw.uiv.response.QueryONTPositionResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RestController
@Action
@Slf4j
public class QueryONTPosition implements HttpAction {
    protected static final String ACTION_LABEL = Constants.QUERY_ONT_POSITION;
    private static final String ERROR_PREFIX = "UIV action QueryONTPosition execution failed - ";

    @Autowired
    private LogicalDeviceCustomRepository ontRepo;

    @Override
    public Class<?> getActionClass() {
        return QueryONTPositionRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("------------Trace # 1--------------- QueryONTPosition started");
        QueryONTPositionRequest req = (QueryONTPositionRequest) actionContext.getObject();

        try {
            // 1. Mandatory validation
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatory(req.getOntSn(), "ontSn");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (Exception bre) {
                log.error("------------Trace # 2--------------- Missing param: " + bre.getMessage());
                return ResponseEntity.status(400).body(new QueryONTPositionResponse(
                        "400",
                        ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage(),
                        Instant.now().toString(),
                        ""
                ));
            }

            String ontSn = req.getOntSn();
            log.error("------------Trace # 3--------------- Input ontSn=" + ontSn);

            // 2. Build ONT Name and length check
            String ontName ="ONT" + ontSn;
            log.error("------------Trace # 4--------------- Constructed ONT name: " + ontName);

            if (ontName.length() > 100) {
                log.error("------------Trace # 5--------------- ONT name too long");
                return ResponseEntity.status(400).body(new QueryONTPositionResponse(
                        "400",
                        ERROR_PREFIX + "ONT name too long.",
                        Instant.now().toString(),
                        ""
                ));
            }

            // 3. Locate ONT
            Optional<LogicalDevice> ontOpt = ontRepo.findByDiscoveredName(ontName);
            if (!ontOpt.isPresent()) {
                log.error("------------Trace # 6--------------- No ONT found with name=" + ontName);
                return ResponseEntity.status(404).body(new QueryONTPositionResponse(
                        "404",
                        ERROR_PREFIX + "No ONT found.",
                        Instant.now().toString(),
                        ""
                ));
            }

            LogicalDevice ont = ontOpt.get();
            log.error("------------Trace # 7--------------- ONT found, checking linked OLT");

            Set<LogicalDevice> managingDevices =  ont.getUsedResource().stream().map(r->(LogicalDevice)r).collect(Collectors.toSet());
            LogicalDevice olt= null;
            try {
                olt= managingDevices.stream().findFirst().get();
            } catch (Exception e) {
                return ResponseEntity.status(404).body(new QueryONTPositionResponse(
                        "404",
                        ERROR_PREFIX + "No ONT Object ID found.",
                        Instant.now().toString(),
                        ""
                ));
            }
            if (olt == null) {
                log.error("------------Trace # 8--------------- No OLT linked to ONT=" + ontName);
                return ResponseEntity.status(404).body(new QueryONTPositionResponse(
                        "404",
                        ERROR_PREFIX + "No ONT Object ID found.",
                        Instant.now().toString(),
                        ""
                ));
            }

            // 4. Determine OLT Object ID
            String objectId = olt.getProperties().get("oltPosition")==null?"":olt.getProperties().get("oltPosition").toString();
            if (objectId == null || objectId.isEmpty()) {
                objectId = olt.getDiscoveredName();
            }

            if (objectId == null || objectId.isEmpty()) {
                log.error("------------Trace # 9--------------- OLT Object ID is empty");
                return ResponseEntity.status(404).body(new QueryONTPositionResponse(
                        "404",
                        ERROR_PREFIX + "No ONT Object ID found.",
                        Instant.now().toString(),
                        ""
                ));
            }

            log.error("------------Trace # 10--------------- Resolved OLT Object ID=" + objectId);

            // 5. Success response
            log.error(Constants.ACTION_COMPLETED);
            return ResponseEntity.status(200).body(new QueryONTPositionResponse(
                    "200",
                    "UIV action QueryONTPosition executed successfully.",
                    Instant.now().toString(),
                    objectId
            ));

        } catch (Exception ex) {
            log.error("Unhandled exception in QueryONTPosition", ex);
            return ResponseEntity.status(500).body(new QueryONTPositionResponse(
                    "500",
                    ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage(),
                    Instant.now().toString(),
                    ""
            ));
        }
    }
}
