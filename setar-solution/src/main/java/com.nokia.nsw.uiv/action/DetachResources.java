package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.AccessForbiddenException;
import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.exception.ModificationNotAllowedException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;
import com.nokia.nsw.uiv.model.resource.Resource;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.DetachResourcesRequest;
import com.nokia.nsw.uiv.response.DetachResourcesResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

@Component
@RestController
@Action
@Slf4j
public class DetachResources implements HttpAction {

    protected static final String ACTION_LABEL = Constants.DETACH_RESOURCES;
    private static final String ERROR_PREFIX = "UIV action DetachResources execution failed - ";

    @Autowired
    private CustomerCustomRepository subscriberRepository;
    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;
    @Autowired
    private ProductCustomRepository productRepository;
    @Autowired
    private ServiceCustomRepository serviceCustomRepository;
    @Autowired
    private LogicalDeviceCustomRepository deviceRepository;

    @Override
    public Class<?> getActionClass() {
        return DetachResourcesRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error("Executing action: {}", ACTION_LABEL);

        DetachResourcesRequest request = (DetachResourcesRequest) actionContext.getObject();
        String subscriptionName = request.getSubscriberName() + Constants.UNDER_SCORE + request.getServiceID();
        String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
        String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
        String productName = request.getSubscriberName() + Constants.UNDER_SCORE + request.getProductSubtype() + Constants.UNDER_SCORE + request.getServiceID();

        try {
            // 1. Mandatory validation
            log.error("Validating mandatory parameters...");
            Validations.validateMandatoryParams(request.getSubscriberName(), "subscriberName");
            Validations.validateMandatoryParams(request.getOntSN(), "ontSN");
            Validations.validateMandatoryParams(request.getServiceID(), "serviceID");
            Validations.validateMandatoryParams(request.getProductSubtype(), "productSubtype");
            Validations.validateMandatoryParams(request.getStbSN1(), "StbSN1");
            Validations.validateMandatoryParams(request.getStbSN2(), "StbSN2");
            Validations.validateMandatoryParams(request.getStbSN3(), "StbSN3");
            Validations.validateMandatoryParams(request.getStbSN4(), "StbSN4");
            Validations.validateMandatoryParams(request.getStbSN5(), "StbSN5");
            Validations.validateMandatoryParams(request.getApSN1(), "ApSN1");
            Validations.validateMandatoryParams(request.getApSN2(), "ApSN2");
            Validations.validateMandatoryParams(request.getApSN3(), "ApSN3");
            Validations.validateMandatoryParams(request.getApSN4(), "ApSN4");
            Validations.validateMandatoryParams(request.getApSN5(), "ApSN5");
            // 2. Fetch entities
            Optional<Customer> subscriber = subscriberRepository.findByDiscoveredName(request.getSubscriberName());
            Optional<Subscription> subscription = subscriptionRepository.findByDiscoveredName(subscriptionName);
            Optional<Product> product = productRepository.findByDiscoveredName(productName);
            Optional<Service> cfs = serviceCustomRepository.findByDiscoveredName(cfsName);
            Optional<Service> rfs = serviceCustomRepository.findByDiscoveredName(rfsName);

            if (!subscriber.isPresent() || !subscription.isPresent() || !product.isPresent() || !cfs.isPresent() || !rfs.isPresent()) {
                return ResponseEntity.status(404).body( new DetachResourcesResponse("404", ERROR_PREFIX + "No entry found for Delete.",
                        DateTimeUtil.now(), subscriptionName));
            }

            Service rfsEntity = rfs.get();
            boolean deviceUpdated = false;

            // 3. Handle devices
            List<String> stbSerials = Arrays.asList(request.getStbSN1(), request.getStbSN2(),
                    request.getStbSN3(), request.getStbSN4(), request.getStbSN5());
            List<String> apSerials = Arrays.asList(request.getApSN1(), request.getApSN2(),
                    request.getApSN3(), request.getApSN4(), request.getApSN5());
            List<String>devNames=new ArrayList<>();

            for (String serial : stbSerials) {
                if (serial != null && !serial.equalsIgnoreCase("NA")) {
                    String devName = "STB_" + serial;
                    devNames.add(devName);
                    deviceUpdated |= detachDevice(devName, rfsEntity, true);
                }
            }

            for (String serial : apSerials) {
                if (serial != null && !serial.equalsIgnoreCase("NA")) {
                    String devName = "AP_" + serial;
                    devNames.add(devName);
                    deviceUpdated |= detachDevice(devName, rfsEntity, false);
                }
            }

            if (deviceUpdated) {
                rfsEntity = serviceCustomRepository.findByDiscoveredName(rfsEntity.getDiscoveredName()).get();
                Map<String,Object> rfsProps = rfsEntity.getProperties();
                rfsProps.put("transactionId",request.getFxOrderId());
                rfsProps.put("transactionType","DetachResources");
                serviceCustomRepository.save(rfsEntity, 2);
                return new DetachResourcesResponse("200",
                        "UIV action DetachResources executed successfully.",
                        DateTimeUtil.now(),
                        subscriptionName);
            } else {
                return ResponseEntity.status(409).body(new DetachResourcesResponse("409", ERROR_PREFIX + "Error, Resources not detached.",
                        DateTimeUtil.now(), subscriptionName));
            }

        } catch (BadRequestException bre) {
            log.error("Validation error: {}", bre.getMessage());
            return ResponseEntity.status(400).body(new DetachResourcesResponse("400", ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage(),
                    DateTimeUtil.now(), ""));
        } catch (Exception ex) {
            log.error("Unhandled exception in DetachResources", ex);
            return ResponseEntity.status(500).body(new DetachResourcesResponse("500", ERROR_PREFIX + "Internal server error occurred",
                    DateTimeUtil.now(), ""));
        }
    }

    private boolean detachDevice(String devName, Service rfsEntity, boolean isSTB)
            throws ModificationNotAllowedException, BadRequestException, AccessForbiddenException {

        Optional<LogicalDevice> optDevice = deviceRepository.findByDiscoveredName(devName);
        if (optDevice.isEmpty()) {
            return false;
        }

        LogicalDevice oldDevice = optDevice.get();

        LogicalDevice newDevice = new LogicalDevice();

        newDevice.setKind(oldDevice.getKind());
        newDevice.setLocalName(oldDevice.getLocalName());
        newDevice.setDiscoveredName(oldDevice.getDiscoveredName());

        Map<String, Object> props = new HashMap<>(oldDevice.getProperties());

        if (isSTB) {
            props.put("deviceGroupId", "");
        }
        props.put("AdministrativeState", "Available");
        props.put("description", "");

        newDevice.setProperties(props);

        deviceRepository.save(newDevice, 1);

        deviceRepository.delete(oldDevice);

        return true;
    }

    private String getCurrentTimestamp() {
        return Instant.now().toString();
    }
}
