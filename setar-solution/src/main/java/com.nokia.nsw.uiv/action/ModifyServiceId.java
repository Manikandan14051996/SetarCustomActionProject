package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.ModifyServiceIdRequest;
import com.nokia.nsw.uiv.response.ModifyServiceIdResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;

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
public class ModifyServiceId implements HttpAction {
    protected static final String ACTION_LABEL = Constants.MODIFY_SERVICE_ID;
    private static final String ERROR_PREFIX = "UIV action ModifyServiceId execution failed - ";

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Autowired
    private ProductCustomRepository productRepo;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepo;

    @Autowired
    private CustomerCustomRepository customerRepo;

    @Autowired
    private LogicalDeviceCustomRepository logicalDeviceRepository;

    @Override
    public Class<?> getActionClass() {
        return ModifyServiceIdRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("------------Test Trace # 1--------------- ModifyServiceId started");
        ModifyServiceIdRequest req = (ModifyServiceIdRequest) actionContext.getObject();

        try {
            // 1. Mandatory validation
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatory(req.getServiceId(), "serviceId");
                Validations.validateMandatory(req.getServiceIdNew(), "serviceIdNew");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (Exception bre) {
                log.error("------------Test Trace # 2--------------- Missing mandatory param: " + bre.getMessage());
                return ResponseEntity.status(400).body(new ModifyServiceIdResponse(
                        "400",
                        ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage(),
                        Instant.now().toString()
                ));
            }
            try {
                Validations.validateLength(req.getServiceId(), "Existing Entity");
                Validations.validateLength(req.getServiceIdNew(), "New Entity");
            } catch (Exception bre) {
                return ResponseEntity.status(400).body( new ModifyServiceIdResponse(
                        "400",
                        ERROR_PREFIX  + bre.getMessage(),
                        Instant.now().toString()
                ));
            }
            String oldServiceId = req.getServiceId();
            String newServiceId = req.getServiceIdNew();

            boolean updatesApplied = false;

            log.error("------------Test Trace # 3--------------- old=" + oldServiceId + ", new=" + newServiceId);

            // 2. Locate CFS candidates
            List<Service> cfsList1 = (List<Service>) serviceCustomRepository.findAll();
            List<Service> cfsList = new ArrayList<>();
            for(Service cfs:cfsList1)
            {
                if(cfs.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_CFS)) {
                    if (cfs.getDiscoveredName().contains(oldServiceId)) {
                        cfsList.add(cfs);
                    }
                }
            }
            log.error("------------Test Trace # 4--------------- Found CFS candidates: " + cfsList.size());

            for (Service cfs : cfsList) {
                String cfsName = cfs.getDiscoveredName();

                // Matching Rule A/B
                boolean matches = false;
                if (oldServiceId.contains(Constants.UNDER_SCORE )) {
                    int first = cfsName.indexOf(Constants.UNDER_SCORE );
                    int last = cfsName.lastIndexOf(Constants.UNDER_SCORE );
                    if (first >= 0 && last > first) {
                        String between = cfsName.substring(first + 1, last);
                        if (between.equalsIgnoreCase(oldServiceId)) {
                            matches = true;
                        }
                    }
                } else {
                    String[] tokens = cfsName.split(Constants.UNDER_SCORE );
                    if (tokens.length >= 3 && tokens[2].equalsIgnoreCase(oldServiceId)) {
                        matches = true;
                    }
                }

                if (!matches) continue;
                log.error("Customer Facing Service match found: "+cfsName);
                log.error("------------Test Trace # 5--------------- Processing CFS: " + cfsName);

                // Locate RFS
                String rfsName = cfsName.replace("CFS", "RFS");
                Optional<Service> rfsOpt = serviceCustomRepository.findByDiscoveredName(rfsName);

                // Retrieve product
                String productDiscoveredName = cfs.getUsingService().stream().filter(ser->ser.getKind().equals(Constants.SETAR_KIND_SETAR_PRODUCT)).findFirst().get().getDiscoveredName();
                Optional<Product> productOpt =productRepo.findByDiscoveredName(productDiscoveredName); // adjust association
                Optional<Subscription> subsOpt = Optional.empty();
                Optional<Customer> custOpt = Optional.empty();

                if (productOpt.isPresent()) {
                    Product prod = productOpt.get();
                    subsOpt = prod.getSubscription().stream().findFirst();
                    subsOpt = subscriptionRepo.findByDiscoveredName(subsOpt.get().getDiscoveredName());
                            // adjust association
                    custOpt = Optional.of(subsOpt.get().getCustomer()); // adjust association
                }

                // Update subscription
                if (subsOpt.isPresent()) {
                    Subscription subs = subscriptionRepo.findByDiscoveredName(subsOpt.get().getDiscoveredName()).get();
                    String newName = subs.getDiscoveredName().replace(oldServiceId, newServiceId);
                    subs.setDiscoveredName(newName);

                    Map<String, Object> props = subs.getProperties() == null ? new HashMap<>() : new HashMap<>(subs.getProperties());
                    props.put("serviceID", newServiceId);
                    subs.setProperties(props);
                    log.error("Subscription updated successfully with the updated name: "+newName);
                    subscriptionRepo.save(subs);
                    updatesApplied = true;
                    log.error("------------Test Trace # 6--------------- Subscription updated: " + newName);
                }

                // Update product
                if (productOpt.isPresent()) {
                    Product prod = productRepo.findByDiscoveredName(productOpt.get().getDiscoveredName()).get();
                    String newName = prod.getDiscoveredName().replace(oldServiceId, newServiceId);
                    prod.setDiscoveredName(newName);
                    log.error("Product updated successfully with the updated name: "+newName);
                    productRepo.save(prod);
                    updatesApplied = true;
                    log.error("------------Test Trace # 7--------------- Product updated: " + newName);
                }

                // Update subscriber
                if (custOpt.isPresent()) {
                    Customer cust = customerRepo.findByDiscoveredName(custOpt.get().getDiscoveredName()).get();
                    String newName = cust.getDiscoveredName().replace(oldServiceId, newServiceId);
                    cust.setDiscoveredName(newName);

                    Map<String, Object> custProps = cust.getProperties() == null ? new HashMap<>() : new HashMap<>(cust.getProperties());
                    custProps.put("accountNumber", newServiceId);
                    cust.setProperties(custProps);
                    log.error("Subscriber updated successfully with the updated name: "+newName);
                    customerRepo.save(cust);
                    updatesApplied = true;
                    log.error("------------Test Trace # 8--------------- Subscriber updated: " + newName);
                }

                // Update RFS + resources
                if (rfsOpt.isPresent()) {
                    Service rfs = serviceCustomRepository.findByDiscoveredName(rfsOpt.get().getDiscoveredName()).get();

                    rfs.getUsedResource().forEach(res -> {
                        if (res instanceof LogicalDevice) {
                            LogicalDevice ont = (LogicalDevice) res;
                            Map<String,Object> props = ont.getProperties();
                            if(ont.getDiscoveredName().contains("ONT")){
                                if ((props.get("potsPort1Number")!=null?props.get("potsPort1Number").toString():"").contains(oldServiceId)) {
                                    log.error("OntDevice updated successfully for the property potsPort1Number: "+ont.getDiscoveredName());
                                    props.put("potsPort1Number",newServiceId);
                                }
                                if ((props.get("potsPort2Number")!=null?props.get("potsPort2Number").toString():"").contains(oldServiceId)) {
                                    log.error("OntDevice updated successfully for the property potsPort2Number: "+ont.getDiscoveredName());
                                    props.put("potsPort2Number",newServiceId);
                                }

                                logicalDeviceRepository.save(ont);
                                log.error("------------Test Trace # 9--------------- ONT updated: " + ont.getDiscoveredName());
                            }else if(ont.getDiscoveredName().contains("CBM")){
                                LogicalDevice cbm = (LogicalDevice) res;
                                Map<String,Object> prop = res.getProperties();
                                String newDevName = cbm.getDiscoveredName().replace(oldServiceId, newServiceId);
                                log.error("CBM Device updated successfully with the updated name: "+newDevName);
                                cbm.setDiscoveredName(newDevName);
                                prop.put("serialNo",newServiceId);
                                cbm.setProperties(prop);
                                logicalDeviceRepository.save(cbm);
                                log.error("------------Test Trace # 10--------------- CBM updated: " + cbm.getDiscoveredName());
                            }
                        }
                    });

                    String newRfsName = rfs.getDiscoveredName().replace(oldServiceId, newServiceId);
                    rfs.setDiscoveredName(newRfsName);
                    serviceCustomRepository.save(rfs);
                    updatesApplied = true;
                    log.error("------------Test Trace # 11--------------- RFS updated: " + newRfsName);
                }

                // Update CFS
                cfs = serviceCustomRepository.findByDiscoveredName(cfs.getDiscoveredName()).get();
                String newCfsName = cfs.getDiscoveredName().replace(oldServiceId, newServiceId);
                cfs.setDiscoveredName(newCfsName);
                log.error("CFS updated successfully with the updated name: "+newCfsName);
                serviceCustomRepository.save(cfs);
                if(cfs!=null){
                    updatesApplied = true;
                }
                log.error("------------Test Trace # 12--------------- CFS updated: " + newCfsName);
            }

            // 5. Generate response
            if (updatesApplied) {
                log.error(Constants.ACTION_COMPLETED);
                return new ModifyServiceIdResponse("200",
                        "ServiceID successfully updated",
                        Instant.now().toString());
            } else {
                return ResponseEntity.status(404).body(new ModifyServiceIdResponse("404",
                        ERROR_PREFIX + "Error, No Service found.",
                        Instant.now().toString()));
            }

        } catch (Exception ex) {
            log.error("Unhandled exception in ModifyServiceId", ex);
            return ResponseEntity.status(500).body(new ModifyServiceIdResponse("500",
                    ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage(),
                    Instant.now().toString()));
        }
    }
}
