package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterface;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.repository.LogicalInterfaceCustomRepository;
import com.nokia.nsw.uiv.request.QueryCPEDeviceRequest;
import com.nokia.nsw.uiv.response.ChangeStateResponse;
import com.nokia.nsw.uiv.response.QueryCPEDeviceResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Action
@Slf4j
public class QueryCPEDevice implements HttpAction {

    @Autowired
    private LogicalDeviceCustomRepository cpeDeviceRepository;

    @Autowired
    private LogicalInterfaceCustomRepository lanRepository;
    protected static final String ACTION_LABEL = Constants.QUERY_CPE_DEVICE;
    private static final String ERROR_PREFIX = "UIV action QueryDevice execution failed - ";


    @Override
    public Class getActionClass() {
        return QueryCPEDeviceRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        QueryCPEDeviceRequest request = (QueryCPEDeviceRequest) actionContext.getObject();
        // 1. Mandatory validation
        try {
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
            validateMandatory(request.getResourceSN(), "resourceSN");
            validateMandatory(request.getResourceType(), "resourceType");
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
        } catch (BadRequestException bre) {
            return ResponseEntity.status(400).body(new QueryCPEDeviceResponse("400", ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage(),
                    java.time.Instant.now().toString()));
        }

        String resourceType = request.getResourceType();
        if ("Cable_Modem".equalsIgnoreCase(resourceType)) {
            resourceType = "CBM";
        }

        String devName = resourceType + Constants.UNDER_SCORE + request.getResourceSN();
        Optional<LogicalDevice> deviceOpt = cpeDeviceRepository.findByDiscoveredName(devName);
        if (!deviceOpt.isPresent()) {
            return ResponseEntity.status(404).body(new QueryCPEDeviceResponse("404", "CPE Details Not Found", String.valueOf(System.currentTimeMillis())));
        }

        LogicalDevice device = deviceOpt.get();
        QueryCPEDeviceResponse response = new QueryCPEDeviceResponse();
        response.setStatus("200");
        response.setMessage("CPE Details Found.");
        response.setTimestamp(String.valueOf(System.currentTimeMillis()));

        response.setResourceModel((String) device.getProperties().get("deviceModel"));
        response.setResourceModelMTA((String) device.getProperties().get("deviceModelMta"));
        response.setResourceGWMac((String) device.getProperties().get("gatewayMacAddress"));
        response.setResourceInventoryType((String) device.getProperties().get("inventoryType"));
        response.setResourceMac((String) device.getProperties().get("macAddress"));
        response.setResourceMacMTA((String) device.getProperties().get("macAddressMta"));
        response.setResourceManufacturer((String) device.getProperties().get("manufacturer"));
        response.setResourceStatus((String) device.getProperties().get("AdministrativeState"));
        response.setResourceDescription(device.getProperties().getOrDefault("description", "").toString());

        // Map resourceSN from localName (remove prefix)
        response.setResourceSN(device.getDiscoveredName().replaceFirst(resourceType + Constants.UNDER_SCORE , ""));

        // Map voice ports
        response.setResourceVoicePort1(
                device.getProperties().getOrDefault("voipPort1", "Available").toString()
        );
        response.setResourceVoicePort2(
                device.getProperties().getOrDefault("voipPort2", "Available").toString()
        );
        // Device type specific adjustments
        if ("CBM".equalsIgnoreCase(resourceType)) {
            response.setResourceMacMTA((String) device.getProperties().get("macAddressMta"));
            response.setResourceModelMTA((String) device.getProperties().get("deviceModelMta"));
            response.setResourceModelSubtype("HFC");
            for (int portNumber = 1; portNumber <= 5; portNumber++) {
                response.setDataPortStatus(portNumber, "Available");
            }
        } else if ("ONT".equalsIgnoreCase(resourceType)) {
            response.setResourceModelSubtype("GPON");

            Iterable<LogicalInterface> interfaceIterable = lanRepository.findAll();
            List<LogicalInterface> interfaceList = new ArrayList<>();
            interfaceIterable.forEach(interfaceList::add);

            for (int portNumber = 1; portNumber <= 5; portNumber++) {
                String portName = request.getResourceSN() + "_P" + portNumber + "_SINGLETAGGED";
//                String portName = request.getResourceSN() + Constants.UNDER_SCORE+ "ETH_" + portNumber;

                long vlanCount = interfaceList.stream()
                        .filter(in -> in.getDiscoveredName().contains(portName))
                        .count();

                String dataPortStatus = vlanCount < 7 ? "Available" : "Allocated";
                response.setDataPortStatus(portNumber, dataPortStatus);
            }
        }

        log.error(Constants.ACTION_COMPLETED);
        return response;
    }
    private void validateMandatory(String val, String name) throws BadRequestException {
        if (val == null || val.trim().isEmpty()) throw new BadRequestException(name);
    }

    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private boolean equalsAny(String s, String... opts) {
        if (s == null) return false;
        for (String o : opts) if (o != null && s.equalsIgnoreCase(o)) return true;
        return false;
    }

    private String nullSafe(String s) { return s == null ? "" : s; }
}
