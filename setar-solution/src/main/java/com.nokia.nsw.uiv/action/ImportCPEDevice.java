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
public class ImportCPEDevice implements HttpAction {

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
        return ImportCPEDeviceRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);

        ImportCPEDeviceRequest request = (ImportCPEDeviceRequest) actionContext.getObject();
        try {
            log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
            try {
                Validations.validateMandatoryParams(request.getCpeSerialNo(), "cpeSerialNo");
                Validations.validateMandatoryParams(request.getCpeModel(), "cpeModel");
                Validations.validateMandatoryParams(request.getCpeType(), "cpeType");
                Validations.validateMandatoryParams(request.getCpeMacAddress(), "cpeMacAddress");
                Validations.validateMandatoryParams(request.getCpeGwMacAddress(), "cpeGwMacAddress");
            } catch (BadRequestException bre) {
                return new ImportCPEDeviceResponse("400", ERROR_PREFIX +  bre.getMessage(),
                        Instant.now().toString());
            }

            log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);

            String devName = request.getCpeType() + Constants.UNDER_SCORE + request.getCpeSerialNo();
            log.error("devName :: {}", devName);
            try {
                Validations.validateLength(devName, "CPEDevice");
            } catch (BadRequestException bre) {
                return new ImportCPEDeviceResponse("400", ERROR_PREFIX + " : " + bre.getMessage(),
                        Instant.now().toString());
            }
            Optional<LogicalDevice> optDevice = cpeDeviceRepository.findByDiscoveredName(devName);
            LogicalDevice cpeDevice;
            if (optDevice.isPresent()) {
                cpeDevice = optDevice.get();
                log.error("Found existing CPE device: {}", devName);
                return new ImportCPEDeviceResponse("409", "Service already exist/Duplicate entry", Instant.now().toString());
            } else {
                log.error("Creating new CPE device: {}", devName);
                cpeDevice = new LogicalDevice();
                cpeDevice.setLocalName(Validations.encryptName(devName));
                cpeDevice.setDiscoveredName(devName);
                cpeDevice.setKind(Constants.SETAR_KIND_CPE_DEVICE);
                cpeDevice.setContext(Constants.SETAR);
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
                properties.put("createdBy",
                        request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                ? request.getCreatedBy()
                                : "CA"
                );
                properties.put("actionName", ACTION_LABEL);
                properties.put("OperationalState", "Active");
                properties.put("AdministrativeState", "Available");

                cpeDevice.setProperties(properties);
                cpeDeviceRepository.save(cpeDevice, 2);
                log.error("Saved new CPE device: {}", devName);
            }

            // Create POTS ports
            log.error("-----------------Create POTS ports------------------");
            createPotsPort(request, "POTS_1", cpeDevice);
            createPotsPort(request, "POTS_2", cpeDevice);

            // Create Ethernet ports
            log.error("-----------------Create Ethernet ports------------------");
            int noOfPorts = determineNumberOfEthernetPorts(request.getCpeType(), request.getCpeModel());
            for (int i = 1; i <= noOfPorts; i++) {
                createEthernetPort(request, "ETH_" + i, cpeDevice);
            }

            log.error(Constants.ACTION_COMPLETED);
            return new ImportCPEDeviceResponse("201", "CPE Device created: "+cpeDevice.getDiscoveredName(), getCurrentTimestamp());

        } catch (BadRequestException bre) {
            log.error("Validation error: {}", bre.getMessage(), bre);
            String msg = "UIV action ImportCPEDevice execution failed - Missing mandatory parameter : " + bre.getMessage();
            return new ImportCPEDeviceResponse("400", msg, String.valueOf(System.currentTimeMillis()));
        } catch (AccessForbiddenException | ModificationNotAllowedException ex) {
            log.error("Access or modification error: {}", ex.getMessage(), ex);
            String msg = "UIV action ImportCPEDevice execution failed - " + ex.getMessage();
            return new ImportCPEDeviceResponse("403", msg, String.valueOf(System.currentTimeMillis()));
        } catch (Exception ex) {
            log.error("Unhandled exception during ImportCPEDevice", ex);
            String msg = "UIV action ImportCPEDevice execution failed - Internal server error occurred";
            return new ImportCPEDeviceResponse("500", msg + " - " + ex.getMessage(), String.valueOf(System.currentTimeMillis()));
        }
    }

    private void createPotsPort(ImportCPEDeviceRequest request, String portType, LogicalDevice cpeDevice)
            throws BadRequestException, AccessForbiddenException, ModificationNotAllowedException {
        log.error("-----------------Create POTS ports-Started------------------");
        String portName = request.getCpeSerialNo() + Constants.UNDER_SCORE + portType;
        Optional<LogicalComponent> optPort = componentRepository.findByDiscoveredName(portName);

        if (!optPort.isPresent()) {
            log.error("Creating POTS port: {}", portName);
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
            properties.put("description","Voice Port");
            properties.put("createdBy",
                    request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                            ? request.getCreatedBy()
                            : "CA"
            );
            properties.put("actionName", ACTION_LABEL);
            potsPort.setProperties(properties);

            componentRepository.save(potsPort, 2);
//            cpeDevice.setContained(new HashSet<>(List.of(potsPort)));
            cpeDeviceRepository.save(cpeDevice, 2);
            log.error("POTS port created and associated: {}", portName);
        } else {
            log.error("POTS port already exists: {}", portName);
        }
        log.error("-----------------Create POTS ports-Completed------------------");
    }

    private void createEthernetPort(ImportCPEDeviceRequest request, String portType, LogicalDevice cpeDevice)
            throws BadRequestException, AccessForbiddenException, ModificationNotAllowedException {

        String portName = request.getCpeSerialNo() + Constants.UNDER_SCORE + portType;
        Optional<LogicalComponent> optPort = componentRepository.findByDiscoveredName(portName);
        if (!optPort.isPresent()) {
            log.error("Creating Ethernet port: {}", portName);
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
            properties.put("description","Data Port");
            properties.put("createdBy",
                    request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                            ? request.getCreatedBy()
                            : "CA"
            );
            properties.put("actionName", ACTION_LABEL);
            ethPort.setProperties(properties);
            componentRepository.save(ethPort, 2);
//            cpeDevice.setContained(new HashSet<>(List.of(ethPort)));
            cpeDeviceRepository.save(cpeDevice, 2);
            log.error("Ethernet port created and associated: {}", portName);

            // VLAN interfaces (LogicalInterface)
            if (!portType.equalsIgnoreCase("ETH_1") && !portType.equalsIgnoreCase("ETH_2")) {

                boolean vlanCreated = false;

                for (int vlanIndex = 1; vlanIndex <= 7; vlanIndex++) {

                    String vlanName = portName + "_" + vlanIndex;
                    Optional<LogicalInterface> optVlan = logicalInterfaceRepository.findByDiscoveredName(vlanName);

                    if (!optVlan.isPresent()) {

                        vlanCreated = true;

                        LogicalInterface vlan = new LogicalInterface();
                        vlan.setLocalName(Validations.encryptName(vlanName));
                        vlan.setDiscoveredName(vlanName);
                        vlan.setKind(Constants.SETAR_KIND_VLAN_INTERFACE);
                        vlan.setContext(Constants.SETAR);

                        Map<String, Object> vlanProps = new HashMap<>();
                        vlanProps.put("name", vlanName);
                        vlanProps.put("linkedEthPort", portName);
                        vlanProps.put("serviceId", "");
                        vlanProps.put("serviceType", "");
                        vlanProps.put("vlanId", "");
                        vlanProps.put("vlanStatus", "Available");
                        vlanProps.put("createdBy",
                                request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                                        ? request.getCreatedBy()
                                        : "CA"
                        );
                        vlanProps.put("actionName", ACTION_LABEL);
                        vlanProps.put("description","VLAN Interface for " + portName);
                        vlan.setProperties(vlanProps);
                        logicalInterfaceRepository.save(vlan, 2);
//                        ethPort.setContainedinterface(new HashSet<>(List.of(vlan)));
                    }
                }
                try {
                    Map<String, Object> props = ethPort.getProperties();
                    if (vlanCreated) {
                        props.put("portStatus", "Allocated");
                        log.error("Port status updated to Allocated for: {}", portName);
                    } else {
                        props.put("portStatus", "Available");
                        log.error("No VLAN created. Port status updated to Available for: {}", portName);
                    }
                    componentRepository.save(ethPort);
                } catch (Exception e) {
                    Map<String, Object> props = ethPort.getProperties();
                    props.put("portStatus", "Available");
                    componentRepository.save(ethPort);
                }

            } else {
                componentRepository.save(ethPort);
            }
        }
    }

    private int determineNumberOfEthernetPorts(String cpeType, String cpeModel) {
        if ("ONT".equalsIgnoreCase(cpeType)) {
            if ("XS-250WX-A".equalsIgnoreCase(cpeModel) || "XS-250X-A".equalsIgnoreCase(cpeModel)) {
                return 5;
            }
            return 4;
        }
        return 0;
    }

    private String getCurrentTimestamp() {
        return Instant.now().toString();
    }
}
