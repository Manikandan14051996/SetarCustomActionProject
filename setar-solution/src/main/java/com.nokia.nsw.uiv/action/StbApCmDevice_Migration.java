package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.AccessForbiddenException;
import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.exception.ModificationNotAllowedException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.request.StbApCmDeviceBulkRequest;
import com.nokia.nsw.uiv.request.StbApCmDeviceRequest;
import com.nokia.nsw.uiv.response.ImportCPEDeviceResponse;
import com.nokia.nsw.uiv.response.StbApCmDeviceResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.DuplicateServiceException;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

@Component
@RestController
@Action
@Slf4j
public class StbApCmDevice_Migration implements HttpAction {

    protected static final String ACTION_LABEL = Constants.IMPORT_CPE_DEVICE;
    private static final String ERROR_PREFIX = "UIV action StbApCmDevice execution failed - ";

    @Autowired
    private LogicalDeviceCustomRepository stbDeviceRepository;


    @Autowired
    private StbApCmDevice_Migration self;



    @Override
    public Class getActionClass() {
        return StbApCmDeviceBulkRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {

        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);


        try {

            StbApCmDeviceBulkRequest bulkRequest =
                    (StbApCmDeviceBulkRequest) actionContext.getObject();

            List<StbApCmDeviceRequest> requests = bulkRequest.getDevices();
            List<StbApCmDeviceResponse> responses = new ArrayList<>();

            for (StbApCmDeviceRequest request : requests) {

                try {
                    responses.add(self.singleReqprocess(request));
                }catch (BadRequestException dive) {
                    log.error("Duplicate entry error", dive);

                    responses.add(new StbApCmDeviceResponse(
                            "409",
                            dive.getMessage(),
                            DateTimeUtil.now()
                    ));
                } catch (DuplicateServiceException dive) {
                    log.error("Duplicate entry error", dive);

                    responses.add(new StbApCmDeviceResponse(
                            "409",
                            "Service already exists / Duplicate entry",
                            DateTimeUtil.now()
                    ));
                } catch (Exception ex) {
                    log.error("Unhandled error in CreateServiceFibernet", ex);
                    responses.add(new StbApCmDeviceResponse(
                            "500",
                            "Internal server error occurred - " + ex.getMessage(),
                            DateTimeUtil.now()
                    ));
                }
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

    @Transactional(rollbackFor = Exception.class)
    public StbApCmDeviceResponse singleReqprocess(StbApCmDeviceRequest request) throws BadRequestException, ModificationNotAllowedException, AccessForbiddenException {
        StbApCmDeviceResponse response = null;
        try {

            log.error("Processing SerialNo: {}", request.getSerialNo());

            // -------- VALIDATION ----------
                Validations.validateMandatoryParams(request.getSerialNo(), "serialNo");
                Validations.validateMandatoryParams(request.getDeviceType(), "deviceType");
                Validations.validateMandatoryParams(request.getDeviceModel(), "deviceModel");
                Validations.validateMandatoryParams(request.getMacAddress(), "MacAddress");
                Validations.validateMandatoryParams(request.getManufacturer(), "manufacturer");
//                Validations.validateMandatoryParams(request.getCustomerGroupId(), "customerGroupId");
                Validations.validateMandatoryParams(request.getPresharedKey(), "presharedKey");
                Validations.validateMandatoryParams(request.getAdministrativeState(), "AdminstrativeState");
                Validations.validateMandatoryParams(request.getOperationalState(), "OperationalState");
                Validations.validateMandatoryParams(request.getName(), "name");

            try {
                Validations.validateLength(request.getName(), "STBDevice");
            } catch (BadRequestException bre) {
                throw new BadRequestException("Name Length is too Long");
            }

            Optional<LogicalDevice> optDevice =
                    stbDeviceRepository.findByDiscoveredName(request.getName());

            if (optDevice.isPresent()) {
                log.error("Duplicate device: {}", request.getName());
                throw new DuplicateServiceException("Duplicate entry for \" " + request.getName());
            }

            // -------- CREATE DEVICE ----------
            LogicalDevice device = new LogicalDevice();
            device.setLocalName(request.getName());
            device.setDiscoveredName(request.getName());
            device.setKind(Constants.SETAR_KIND_STB_AP_CM_DEVICE);
            device.setContext(Constants.SETAR);

            Map<String, Object> properties = new HashMap<>();
            properties.put("name", request.getName());
            properties.put("serialNo", request.getSerialNo());
            properties.put("deviceModel", request.getDeviceModel());
            properties.put("deviceType", request.getDeviceType());
            properties.put("gatewayMacAddress", request.getGatewayMacAddress());
            properties.put("macAddress", request.getGatewayMacAddress());
            properties.put("manufacturer", request.getManufacturer());
            if(request.getModelSubType()!=null)
            {
                properties.put("modelSubType", request.getModelSubType());
            }
            properties.put("createdBy", getCreatedBy(request));
            properties.put("actionName", ACTION_LABEL);
            properties.put("OperationalState", request.getOperationalState());
            properties.put("AdministrativeState", request.getAdministrativeState());
            properties.put("presharedKey", request.getPresharedKey());

            device.setProperties(properties);

            stbDeviceRepository.save(device);

            response = new StbApCmDeviceResponse(
                    "201",
                    "STB Device created: " + request.getName(),
                    Instant.now().toString()
            );

        } catch (Exception ex) {
            throw  ex;
        }
        return response;
    }


    private String getCreatedBy(StbApCmDeviceRequest request) {
        return (request.getCreatedBy() != null && !request.getCreatedBy().isEmpty())
                ? request.getCreatedBy()
                : "CA";
    }
}