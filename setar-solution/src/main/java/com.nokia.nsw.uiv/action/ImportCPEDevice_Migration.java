package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.AccessForbiddenException;
import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.exception.ModificationNotAllowedException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.logical.LogicalComponent;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterface;
import com.nokia.nsw.uiv.repository.LogicalComponentCustomRepository;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.repository.LogicalInterfaceCustomRepository;
import com.nokia.nsw.uiv.request.ImportCPEDeviceBulkRequest;
import com.nokia.nsw.uiv.request.ImportCPEDeviceRequest;
import com.nokia.nsw.uiv.response.ImportCPEDeviceResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

@Component
@RestController
@Action
@Slf4j
public class ImportCPEDevice_Migration implements HttpAction {

    protected static final String ACTION_LABEL = Constants.IMPORT_CPE_DEVICE;
    private static final String ERROR_PREFIX = "UIV action ImportCPEDevice execution failed - ";

    @Autowired
    private LogicalDeviceCustomRepository cpeDeviceRepository;

    @Autowired
    private LogicalComponentCustomRepository componentRepository;

    @Autowired
    private LogicalInterfaceCustomRepository logicalInterfaceRepository;

    @Override
    public Class getActionClass() {
        return ImportCPEDeviceBulkRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {

        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);

        List<ImportCPEDeviceResponse> responses = new ArrayList<>();

        List<LogicalDevice> deviceBatch = new ArrayList<>();
        List<LogicalComponent> componentBatch = new ArrayList<>();
        List<LogicalInterface> vlanBatch = new ArrayList<>();

        try {

            ImportCPEDeviceBulkRequest bulkRequest =
                    (ImportCPEDeviceBulkRequest) actionContext.getObject();

            List<ImportCPEDeviceRequest> requests = bulkRequest.getDevices();

            for (ImportCPEDeviceRequest request : requests) {

                try {

                    log.error("Processing SerialNo: {}", request.getCpeSerialNo());

                    // -------- VALIDATION ----------
                    try {
                        Validations.validateMandatoryParams(request.getCpeSerialNo(), "cpeSerialNo");
                        Validations.validateMandatoryParams(request.getCpeModel(), "cpeModel");
                        Validations.validateMandatoryParams(request.getCpeType(), "cpeType");
                        Validations.validateMandatoryParams(request.getCpeMacAddress(), "cpeMacAddress");
                        Validations.validateMandatoryParams(request.getCpeGwMacAddress(), "cpeGwMacAddress");
                    } catch (BadRequestException bre) {
                        responses.add(new ImportCPEDeviceResponse(
                                "400",
                                ERROR_PREFIX + bre.getMessage(),
                                Instant.now().toString()
                        ));
                        continue;
                    }

                    String devName = request.getCpeType() + Constants.UNDER_SCORE + request.getCpeSerialNo();

                    try {
                        Validations.validateLength(devName, "CPEDevice");
                    } catch (BadRequestException bre) {
                        responses.add(new ImportCPEDeviceResponse(
                                "400",
                                ERROR_PREFIX + bre.getMessage(),
                                Instant.now().toString()
                        ));
                        continue;
                    }

                    Optional<LogicalDevice> optDevice =
                            cpeDeviceRepository.findByDiscoveredName(devName);

                    if (optDevice.isPresent()) {
                        log.error("Duplicate device: {}", devName);

                        responses.add(new ImportCPEDeviceResponse(
                                "409",
                                "Duplicate entry for " + devName,
                                Instant.now().toString()
                        ));
                        continue;
                    }

                    // -------- CREATE DEVICE ----------
                    LogicalDevice device = new LogicalDevice();
                    device.setLocalName(Validations.encryptName(devName));
                    device.setDiscoveredName(devName);
                    device.setKind(Constants.SETAR_KIND_CPE_DEVICE);
                    device.setContext(Constants.SETAR);

                    Map<String, Object> properties = new HashMap<>();
                    properties.put("name", devName);
                    properties.put("serialNo", request.getCpeSerialNo());
                    properties.put("deviceModel", request.getCpeModel());
                    properties.put("deviceModelMta", request.getCpeModelMta());
                    properties.put("deviceType", request.getCpeType());
                    properties.put("gatewayMacAddress", request.getCpeGwMacAddress());
                    properties.put("inventoryType", request.getCpeType());
                    properties.put("macAddress", request.getCpeMacAddress());
                    properties.put("macAddressMta", request.getCpeMacAddressMta());
                    properties.put("manufacturer", request.getCpeManufacturer());
                    properties.put("modelSubType", request.getCpeModelSubType());
                    properties.put("createdBy", getCreatedBy(request));
                    properties.put("actionName", ACTION_LABEL);
                    properties.put("OperationalState", "Active");
                    properties.put("AdministrativeState", "Available");

                    device.setProperties(properties);

                    deviceBatch.add(device);

                    // -------- POTS PORTS ----------
                    componentBatch.add(createPotsPort(request, "POTS_1"));
                    componentBatch.add(createPotsPort(request, "POTS_2"));

                    // -------- ETHERNET PORTS ----------
                    int noOfPorts = determineNumberOfEthernetPorts(
                            request.getCpeType(),
                            request.getCpeModel()
                    );

                    for (int i = 1; i <= noOfPorts; i++) {

                        String portType = "ETH_" + i;

                        LogicalComponent ethPort = createEthernetPort(request, portType);
                        componentBatch.add(ethPort);

                        if (!portType.equalsIgnoreCase("ETH_1") &&
                                !portType.equalsIgnoreCase("ETH_2")) {

                            for (int vlanIndex = 1; vlanIndex <= 7; vlanIndex++) {
                                vlanBatch.add(createVlan(request, portType, vlanIndex));
                            }
                        }
                    }

                    responses.add(new ImportCPEDeviceResponse(
                            "201",
                            "CPE Device created: " + devName,
                            Instant.now().toString()
                    ));

                } catch (Exception ex) {

                    log.error("Error processing {}", request.getCpeSerialNo(), ex);

                    responses.add(new ImportCPEDeviceResponse(
                            "500",
                            "Failed for " + request.getCpeSerialNo() + " : " + ex.getMessage(),
                            Instant.now().toString()
                    ));
                }
            }

            // 🔥 BATCH SAVE
            if (!deviceBatch.isEmpty()) {
                cpeDeviceRepository.saveAll(deviceBatch);
            }

            if (!componentBatch.isEmpty()) {
                componentRepository.saveAll(componentBatch);
            }

            if (!vlanBatch.isEmpty()) {
                logicalInterfaceRepository.saveAll(vlanBatch);
            }

            log.error(Constants.ACTION_COMPLETED);
            return responses;

        } catch (Exception ex) {
            log.error("Unhandled exception", ex);

            return Collections.singletonList(
                    new ImportCPEDeviceResponse(
                            "500",
                            ERROR_PREFIX + "Internal error - " + ex.getMessage(),
                            Instant.now().toString()
                    )
            );
        }
    }

    // ---------------- HELPER METHODS ----------------

    private LogicalComponent createPotsPort(ImportCPEDeviceRequest request, String portType) throws BadRequestException, AccessForbiddenException, ModificationNotAllowedException {

        String portName = request.getCpeSerialNo() + Constants.UNDER_SCORE + portType;

        LogicalComponent potsPort = new LogicalComponent();
        potsPort.setLocalName(Validations.encryptName(portName));
        potsPort.setDiscoveredName(portName);
        potsPort.setKind(Constants.SETAR_KIND_CPE_PORT);
        potsPort.setContext(Constants.SETAR);

        Map<String, Object> properties = new HashMap<>();
        properties.put("portName", portName);
        properties.put("serialNumber", request.getCpeSerialNo());
        properties.put("portStatus", "Available");
        properties.put("portType", portType);
        properties.put("serviceCount", "0");
        properties.put("description", "Voice Port");
        properties.put("createdBy", getCreatedBy(request));
        properties.put("actionName", ACTION_LABEL);

        potsPort.setProperties(properties);

        return potsPort;
    }

    private LogicalComponent createEthernetPort(ImportCPEDeviceRequest request, String portType) throws BadRequestException, AccessForbiddenException, ModificationNotAllowedException {

        String portName = request.getCpeSerialNo() + Constants.UNDER_SCORE + portType;

        LogicalComponent ethPort = new LogicalComponent();
        ethPort.setLocalName(Validations.encryptName(portName));
        ethPort.setDiscoveredName(portName);
        ethPort.setKind(Constants.SETAR_KIND_CPE_PORT);
        ethPort.setContext(Constants.SETAR);

        Map<String, Object> properties = new HashMap<>();
        properties.put("portName", portName);
        properties.put("serialNumber", request.getCpeSerialNo());
        properties.put("portType", portType);
        properties.put("serviceCount", "0");
        properties.put("portStatus", "Available");
        properties.put("description", "Data Port");
        properties.put("createdBy", getCreatedBy(request));
        properties.put("actionName", ACTION_LABEL);

        ethPort.setProperties(properties);

        return ethPort;
    }

    private LogicalInterface createVlan(ImportCPEDeviceRequest request, String portType, int vlanIndex) throws BadRequestException, AccessForbiddenException, ModificationNotAllowedException {

        String vlanName = request.getCpeSerialNo() + Constants.UNDER_SCORE + portType + "_" + vlanIndex;

        LogicalInterface vlan = new LogicalInterface();
        vlan.setLocalName(Validations.encryptName(vlanName));
        vlan.setDiscoveredName(vlanName);
        vlan.setKind(Constants.SETAR_KIND_VLAN_INTERFACE);
        vlan.setContext(Constants.SETAR);

        Map<String, Object> vlanProps = new HashMap<>();
        vlanProps.put("name", vlanName);
        vlanProps.put("linkedEthPort", portType);
        vlanProps.put("serviceId", "");
        vlanProps.put("serviceType", "");
        vlanProps.put("vlanId", "");
        vlanProps.put("vlanStatus", "Available");
        vlanProps.put("createdBy", getCreatedBy(request));
        vlanProps.put("actionName", ACTION_LABEL);
        vlanProps.put("description", "VLAN Interface for " + portType);

        vlan.setProperties(vlanProps);

        return vlan;
    }

    private int determineNumberOfEthernetPorts(String cpeType, String cpeModel) {
        if ("ONT".equalsIgnoreCase(cpeType)) {
            if ("XS-250WX-A".equalsIgnoreCase(cpeModel)
                    || "XS-250X-A".equalsIgnoreCase(cpeModel)) {
                return 5;
            }
            return 4;
        }
        return 0;
    }

    private String getCreatedBy(ImportCPEDeviceRequest request) {
        return (request.getCreatedBy() != null && !request.getCreatedBy().isEmpty())
                ? request.getCreatedBy()
                : "CA";
    }
}