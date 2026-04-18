package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.request.DeleteIPTVRequest;
import com.nokia.nsw.uiv.response.DeleteIPTVResponse;
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
public class DeleteIPTV implements HttpAction {
    protected static final String ACTION_LABEL = Constants.DELETE_IPTV;
    private static final String ERROR_PREFIX = "UIV action DeleteIPTV execution failed - ";

    @Autowired private CustomerCustomRepository customerRepository;
    @Autowired private SubscriptionCustomRepository subscriptionRepository;
    @Autowired private ProductCustomRepository productRepository;
    @Autowired private ServiceCustomRepository serviceCustomRepository;
    @Autowired private LogicalDeviceCustomRepository deviceRepository;

    @Override
    public Class getActionClass() {
        return DeleteIPTVRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        DeleteIPTVRequest request = (DeleteIPTVRequest) actionContext.getObject();
        String subscriberName = request.getSubscriberName();
        String productType = request.getProductType();
        String productSubtype = request.getProductSubtype();
        String serviceId = request.getServiceId();
        String serviceFlag = request.getServiceFlag();
        String ontSN = request.getOntSN();

        try {
            // Step 1: Validate mandatory parameters
            try{
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatoryParams(subscriberName, "subscriberName");
                Validations.validateMandatoryParams(productType, "productType");
                Validations.validateMandatoryParams(productSubtype, "productSubtype");
                Validations.validateMandatoryParams(serviceId, "serviceId");
                Validations.validateMandatoryParams(ontSN, "ontSN");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);

            }catch (BadRequestException bre) {
                return ResponseEntity.status(400).body(new DeleteIPTVResponse("400", ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage(),
                        DateTimeUtil.now(), "",""));
            }
            // Step 2: Prepare entity names
            String subscriptionName = subscriberName + Constants.UNDER_SCORE  + serviceId;
            String productName = subscriberName+ Constants.UNDER_SCORE + productSubtype+Constants.UNDER_SCORE + serviceId;
            String cfsName = "CFS" + Constants.UNDER_SCORE + subscriptionName;
            String rfsName = "RFS" + Constants.UNDER_SCORE + subscriptionName;
            String ontName ="ONT" + ontSN;

            if (ontName.length() > 100) {
                return ResponseEntity.status(400).body(errorResponse("400", ERROR_PREFIX + "ONT name too long"));
            }

            if (subscriptionName.length() > 100) {
                return  ResponseEntity.status(400).body(errorResponse("400", ERROR_PREFIX + "Subscription name too long"));
            }

            if (productName.length() > 100) {
                return ResponseEntity.status(400).body(errorResponse("400", ERROR_PREFIX + "Product name too long"));
            }

            // Step 3: Retrieve entities
            Optional<Customer> optCust = customerRepository.findByDiscoveredName(subscriberName);
            Optional<Subscription> optSub = subscriptionRepository.findByDiscoveredName(subscriptionName);
            Optional<Product> optProd = productRepository.findByDiscoveredName(productName);
            Optional<Service> optCfs = serviceCustomRepository.findByDiscoveredName(cfsName);
            Optional<Service> optRfs = serviceCustomRepository.findByDiscoveredName(rfsName);
            Optional<LogicalDevice> optOnt = deviceRepository.findByDiscoveredName(ontName);

            if (optCust.isEmpty() || optSub.isEmpty()) {
                return ResponseEntity.status(404).body(successResponse(subscriptionName, ontName, "No entry found for Delete."));
            }


            LogicalDevice olt = optOnt
                    .map(ont -> (LogicalDevice)ont.getUsedResource().stream().findFirst().orElse(null))
                    .orElse(null);


            // Step 4: Update OLT template values
            if (olt != null) {
                Map<String, Object> props = olt.getProperties();
                props.put("hsiTemplate", "");
                props.put("iptvTemplate", "");
                props.put("igmpTemplate", "");
                deviceRepository.save(olt);
            }

            // Step 5: Reset STB/AP devices
            if (optRfs.isPresent()) {
                Service rfs = optRfs.get();
                rfs.getUsedResource().forEach(res -> {
                    if (res.getDiscoveredName().startsWith("STB") || res.getDiscoveredName().startsWith("AP")) {
                        try {
                            deviceRepository.findByDiscoveredName(res.getDiscoveredName()).ifPresent(dev -> {
                                if(dev.getDiscoveredName().startsWith("STB"))
                                dev.getProperties().put("deviceGroupId", "");
                                dev.getProperties().put("AdministrativeState", "Available");
                                deviceRepository.save(dev);
                            });
                        } catch (Exception ignored) {}
                    }
                });
            }

            // Step 6: Delete RFS, CFS, Product
            optRfs.ifPresent(serviceCustomRepository::delete);
            optCfs.ifPresent(serviceCustomRepository::delete);
            optProd.ifPresent(productRepository::delete);

            // Step 7: Conditional deletion of devices
            // Step 7: Conditional deletion of devices
            if (!"Exist".equalsIgnoreCase(serviceFlag) && optOnt.isPresent()) {

                // Only proceed if OLT exists
                if (olt != null) {

                    // Check if ANY RFS is associated with this OLT
                    List<Service> allServices = (List<Service>) serviceCustomRepository.findAll();

                    boolean oltHasAssociatedRfs = allServices.stream()
                            .filter(s -> s.getDiscoveredName().startsWith("RFS") ||
                                    s.getDiscoveredName().startsWith("RFS_"))
                            .anyMatch(s ->
                                    s.getUsedResource().stream()
                                            .anyMatch(res ->
                                                    res.getDiscoveredName()
                                                            .equalsIgnoreCase(olt.getDiscoveredName()))
                            );

                    // === Required Business Logic ===
                    if (!oltHasAssociatedRfs) {
                        // Delete ONT first
                        deviceRepository.delete(optOnt.get());

                        // Then delete OLT
                        deviceRepository.delete(olt);

                        log.info("Deleted ONT {} and OLT {} as no RFS is associated",
                                optOnt.get().getDiscoveredName(),
                                olt.getDiscoveredName());
                    } else {
                        log.info("OLT {} NOT deleted as associated RFS exists", olt.getDiscoveredName());
                    }
                }
            }


            // Step 8: Conditional deletion of Subscriber & Subscription
            if (optCust.isPresent() && optSub.isPresent()) {
                Customer cust = optCust.get();
                List<Subscription> subs = new ArrayList<>();
                subscriptionRepository.findAll().forEach(subs::add);
                if (subs.size() <= 1) {
                    subscriptionRepository.delete(optSub.get());
                    customerRepository.delete(cust);
                } else {
                    subscriptionRepository.delete(optSub.get());
                }
            }
            log.error(Constants.ACTION_COMPLETED);
            // Step 9: Return success
            return ResponseEntity.status(200).body(successResponse(subscriptionName, ontName, "UIV action DeleteIPTV executed successfully."));

        } catch (Exception ex) {
            return ResponseEntity.status(500).body(errorResponse("500", ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage()));
        }
    }

    private DeleteIPTVResponse errorResponse(String code, String message) {
        return new DeleteIPTVResponse(code, message, DateTimeUtil.now());
    }

    private DeleteIPTVResponse successResponse(String subscriptionId, String ontName, String message) {
        return ResponseEntity.status(200).body(new DeleteIPTVResponse("200", message, DateTimeUtil.now(), subscriptionId,ontName)).getBody();
    }
}
