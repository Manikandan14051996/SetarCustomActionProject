package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.ModifySubscriberRequest;
import com.nokia.nsw.uiv.response.ModifySubscriberResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
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
public class ModifySubscriber implements HttpAction {
    protected static final String ACTION_LABEL = Constants.MODIFY_SUBSCRIBER;
    private static final String ERROR_PREFIX = "UIV action ModifySubscriber execution failed - ";

    @Autowired
    private SubscriptionCustomRepository subscriptionCustomRepo;

    @Autowired
    private ProductCustomRepository productCustomRepo;

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Autowired
    private CustomerCustomRepository customerRepo;

    @Override
    public Class<?> getActionClass() {
        return ModifySubscriberRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error("------------Test Trace # 1--------------- ModifySubscriber started");
        ModifySubscriberRequest req = (ModifySubscriberRequest) actionContext.getObject();

        try {
            // 1. Validate mandatory params
            try {
                Validations.validateMandatory(req.getSubscriberName(), "subscriberName");
                Validations.validateMandatory(req.getSubscriberNameOld(), "subscriberNameOld");
            } catch (Exception bre) {
                log.error("------------Test Trace # 2--------------- Missing mandatory param: " + bre.getMessage());
                return ResponseEntity.status(400).body(new ModifySubscriberResponse(
                        "400",
                        ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage(),
                        DateTimeUtil.now()
                ));
            }

            String oldSubscriberName = req.getSubscriberNameOld();
            String newSubscriberName = req.getSubscriberName();
            try {
                Validations.validateLength(oldSubscriberName, "Existing Subscriber");
                Validations.validateLength(newSubscriberName, "New Subscriber");
            } catch (Exception bre) {
                log.error("------------Test Trace # 2--------------- Missing mandatory param: " + bre.getMessage());
                return ResponseEntity.status(400).body(new ModifySubscriberResponse(
                        "400",
                        ERROR_PREFIX + bre.getMessage(),
                        DateTimeUtil.now()
                ));
            }

            log.error("------------Test Trace # 3--------------- old=" + oldSubscriberName + ", new=" + newSubscriberName);

            boolean updatesApplied = false;

            // 3. Locate CFS containing old subscriber
            List<Service> cfsList1 = (List<Service>) serviceCustomRepository.findAll();
            List<Service> cfsList = new ArrayList<>();
            for(Service cfs:cfsList1)
            {
                if(cfs.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_CFS)) {
                    if (cfs.getDiscoveredName().contains(oldSubscriberName)) {
                        cfsList.add(cfs);
                    }
                }
            }
            log.error("------------Test Trace # 4--------------- CFS candidates found: " + cfsList.size());

            for (Service cfs : cfsList) {
                String cfsName = cfs.getDiscoveredName();
                if (!cfsName.contains(oldSubscriberName)) continue;

                log.error("------------Test Trace # 5--------------- Processing CFS: " + cfsName);
                log.error("CFS found with old subscriber name: "+cfsName);
                // Derive RFS name
                String rfsName = cfsName.replace("CFS", "RFS");
                Optional<Service> rfsOpt = serviceCustomRepository.findByDiscoveredName(rfsName);

                // Retrieve product linked (via properties or association)
                String productName = cfs.getUsingService().stream().filter(ser->ser.getKind().equals(Constants.SETAR_KIND_SETAR_PRODUCT)).findFirst().get().getDiscoveredName();
                Optional<Product> productOpt = productCustomRepo.findByDiscoveredName(productName); // adjust to actual association
                Optional<Subscription> subsOpt = Optional.empty();
                Optional<Customer> oldCustOpt = Optional.empty();

                if (productOpt.isPresent()) {
                    productOpt = productCustomRepo.findByDiscoveredName(productOpt.get().getDiscoveredName());
                    subsOpt = subscriptionCustomRepo.findByDiscoveredName(productOpt.get().getSubscription().stream().findFirst().get().getDiscoveredName());
                    // adjust to actual association
                    oldCustOpt = customerRepo.findByDiscoveredName(subsOpt.get().getCustomer().getDiscoveredName()); // adjust to actual association
                }

                // Try to find new subscriber
                Optional<Customer> newCustOpt = customerRepo.findByDiscoveredName(newSubscriberName);

                if (newCustOpt.isPresent()) {
                    Customer newCust = newCustOpt.get();
                    log.error("------------Test Trace # 6--------------- New subscriber found: " + newCust.getLocalName());
                    log.error("subscriber found with new subscriber name: "+newSubscriberName);

                    // Update subscription
                    if (subsOpt.isPresent()) {
                        Subscription subs = subsOpt.get();
                        String newSubName = subs.getDiscoveredName().replace(oldSubscriberName, newSubscriberName);
                        subs.setDiscoveredName(newSubName);
                        subs.setCustomer(newCust);
                        log.error("subscription updated successfully with the updated name: "+newSubName);
                        subscriptionCustomRepo.save(subs);
                        updatesApplied = true;
                        log.error("------------Test Trace # 7--------------- Subscription updated: " + newSubName);
                    }

                    // Update product
                    if (productOpt.isPresent()) {
                        Product prod = productOpt.get();
                        String newProdName = prod.getDiscoveredName().replace(oldSubscriberName, newSubscriberName);
                        prod.setDiscoveredName(newProdName);
                        prod.setCustomer(newCust);
                        log.error("product updated successfully with the updated name: "+newProdName);
                        productCustomRepo.save(prod);
                        updatesApplied = true;
                        log.error("------------Test Trace # 8--------------- Product updated: " + newProdName);
                    }

                } else {
                    log.error("------------Test Trace # 9--------------- New subscriber not found → fallback mode");
                    if (oldCustOpt.isPresent()) {
                        Customer oldCust = oldCustOpt.get();
                        String newName;
                        if (oldCust.getDiscoveredName().contains(Constants.UNDER_SCORE )) {
                            String[] parts = oldCust.getDiscoveredName().split(Constants.UNDER_SCORE );
                            newName = newSubscriberName + Constants.UNDER_SCORE  + parts[1];
                        } else {
                            newName = oldCust.getDiscoveredName().replace(oldSubscriberName, newSubscriberName);
                        }
                        log.error("subscriber updated successfully with the updated name: "+newName);
                        oldCust.setDiscoveredName(newName);
                        Map<String, Object> custProps = oldCust.getProperties() == null ? new HashMap<>() : new HashMap<>(oldCust.getProperties());
                        custProps.put("accountNumber", newSubscriberName);
                        oldCust.setProperties(custProps);

                        customerRepo.save(oldCust);
                        updatesApplied = true;
                        log.error("------------Test Trace # 11--------------- Subscriber updated (fallback): " + newName);
                    }
                    // Fallback: update subscription, subscriber, product with renaming
                    if (subsOpt.isPresent()) {
                        Subscription subs = subscriptionCustomRepo.findByDiscoveredName(subsOpt.get().getDiscoveredName()).get();
                        String newSubName = subs.getDiscoveredName().replace(oldSubscriberName, newSubscriberName);
                        subs.setDiscoveredName(newSubName);
                        log.error("subscription updated successfully with the updated name: "+newSubName);
                        subscriptionCustomRepo.save(subs);
                        updatesApplied = true;
                        log.error("------------Test Trace # 10--------------- Subscription renamed (fallback): " + newSubName);
                    }

                    if (productOpt.isPresent()) {
                        Product prod = productCustomRepo.findByDiscoveredName(productOpt.get().getDiscoveredName()).get();
                        String newProdName = prod.getDiscoveredName().replace(oldSubscriberName, newSubscriberName);
                        prod.setDiscoveredName(newProdName);
                        log.error("product updated successfully with the updated name: "+newProdName);
                        productCustomRepo.save(prod);
                        updatesApplied = true;
                        log.error("------------Test Trace # 12--------------- Product updated (fallback): " + newProdName);
                    }
                }
                // Update CFS
                cfs = serviceCustomRepository.findByDiscoveredName(cfs.getDiscoveredName()).get();
                String newCfsName = cfs.getDiscoveredName().replace(oldSubscriberName, newSubscriberName);
                cfs.setDiscoveredName(newCfsName);
                log.error("CFS updated successfully with the updated name: "+newCfsName);
                serviceCustomRepository.save(cfs);
                updatesApplied = true;
                log.error("update applied successfully");
                log.error("------------Test Trace # 14--------------- CFS updated: " + newCfsName);

                // Update RFS
                if (rfsOpt.isPresent()) {
                    Service rfs = rfsOpt.get();
                    rfs = serviceCustomRepository.findByDiscoveredName(rfs.getDiscoveredName()).get();
                    String newRfsName = rfs.getDiscoveredName().replace(oldSubscriberName, newSubscriberName);
                    rfs.setDiscoveredName(newRfsName);
                    log.error("RFS updated successfully with the updated name: "+newRfsName);
                    serviceCustomRepository.save(rfs);
                    updatesApplied = true;
                    log.error("------------Test Trace # 13--------------- RFS updated: " + newRfsName);
                }


            }

            // 5. Generate response
            if (updatesApplied) {
                return ResponseEntity.status(200).body(new ModifySubscriberResponse("200",
                        "AccountNumber successfully updated",
                        DateTimeUtil.now()));
            } else {
                return ResponseEntity.status(404).body(new ModifySubscriberResponse("404",
                        ERROR_PREFIX + "Error, No Account found.",
                        DateTimeUtil.now()));
            }

        } catch (Exception ex) {
            log.error("Unhandled exception in ModifySubscriber", ex);
            return ResponseEntity.status(500).body(new ModifySubscriberResponse("500",
                    ERROR_PREFIX + "Internal server error occurred - " + ex.getMessage(),
                    DateTimeUtil.now()));
        }
    }
}
