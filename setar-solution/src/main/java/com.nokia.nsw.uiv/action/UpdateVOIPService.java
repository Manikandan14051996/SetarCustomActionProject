package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.repository.CustomerCustomRepository;
import com.nokia.nsw.uiv.repository.SubscriptionCustomRepository;
import com.nokia.nsw.uiv.request.UpdateVOIPServiceRequest;
import com.nokia.nsw.uiv.response.UpdateVOIPServiceResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;

import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;

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
public class UpdateVOIPService implements HttpAction {
    protected static final String ACTION_LABEL = Constants.UPDATE_VOIP_SERVICE;
    private static final String ERROR_PREFIX = "UIV action UpdateVOIPService execution failed - ";

    @Autowired private CustomerCustomRepository customerRepo;
    @Autowired private SubscriptionCustomRepository subscriptionRepo;

    @Override
    public Class<?> getActionClass() {
        return UpdateVOIPServiceRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("Executing UpdateVOIPService...");
        UpdateVOIPServiceRequest req = (UpdateVOIPServiceRequest) actionContext.getObject();

        try {
            // Step 1: Validate mandatory params
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatoryParams(req.getSubscriberName(), "subscriberName");
                Validations.validateMandatoryParams(req.getOntSN(), "ontSN");
                Validations.validateMandatoryParams(req.getSimaSubsId(), "simaSubsId");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (BadRequestException bre) {
                return ResponseEntity.status(400).body(new UpdateVOIPServiceResponse(
                        "400",
                        ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage(),
                        Instant.now().toString(),
                        null,
                        null
                ));
            }

            // Step 2: Construct Names
            String subscriptionName = req.getSubscriberName() + Constants.UNDER_SCORE  + req.getServiceId() + Constants.UNDER_SCORE  + req.getOntSN();
            String subscriberNameStr = req.getSubscriberName() + Constants.UNDER_SCORE  + req.getOntSN();

            boolean updatedFlag = false;

            // Step 4: Locate existing records
            Optional<Subscription> subscriptionOpt = subscriptionRepo.findByDiscoveredName(subscriptionName);
            Optional<Customer> subscriberOpt = customerRepo.findByDiscoveredName(subscriberNameStr);

            if (subscriptionOpt.isEmpty() && subscriberOpt.isEmpty()) {
                return ResponseEntity.status(404).body(new UpdateVOIPServiceResponse(
                        "404",
                        ERROR_PREFIX + "No entry found for update",
                        Instant.now().toString(),
                        null,
                        null
                ));
            }

            // Step 5: Update Subscriber
            if (subscriberOpt.isPresent() && req.getSimaCustId() != null && !req.getSimaCustId().isEmpty()) {
                Customer subscriber = subscriberOpt.get();
                Map<String, Object> props = subscriber.getProperties();
                props.put("simaCustId1", req.getSimaCustId());
                subscriber.setProperties(props);
                try {
                    customerRepo.save(subscriber);
                    updatedFlag = true;
                } catch (Exception e) {
                    return ResponseEntity.status(500).body(new UpdateVOIPServiceResponse(
                            "500",
                            ERROR_PREFIX + "Persistence error while saving subscriber",
                            Instant.now().toString(),
                            null,
                            null
                    ));
                }
            }

            // Step 6: Update Subscription
            if (subscriptionOpt.isPresent()) {
                subscriptionOpt = subscriptionRepo.findByDiscoveredName(subscriptionName);
                Subscription subs = subscriptionOpt.get();
                Map<String, Object> props = subs.getProperties();

                if (req.getSimaCustId() != null && !req.getSimaCustId().isEmpty()) {
                    props.put("simaCustId1", req.getSimaCustId());
                    updatedFlag = true;
                }
                if (req.getSimaSubsId() != null && !req.getSimaSubsId().isEmpty()) {
                    props.put("simaSubsId1", req.getSimaSubsId());
                    updatedFlag = true;
                }
                if (req.getSimaEndpointId() != null && !req.getSimaEndpointId().isEmpty()) {
                    props.put("simaEndpointId1", req.getSimaEndpointId());
                    updatedFlag = true;
                }

                subs.setProperties(props);

                try {
                    subscriptionRepo.save(subs);
                } catch (Exception e) {
                    return ResponseEntity.status(500).body( new UpdateVOIPServiceResponse(
                            "500",
                            ERROR_PREFIX + "Persistence error while saving subscription",
                            Instant.now().toString(),
                            null,
                            null
                    ));
                }
            }

            // Step 8: Final Response
            if (updatedFlag) {
                log.error(Constants.ACTION_COMPLETED);
                return ResponseEntity.status(200).body(new UpdateVOIPServiceResponse(
                        "200",
                        "UIV action UpdateVOIPService executed successfully.",
                        Instant.now().toString(),
                        req.getSubscriberName(),
                        req.getServiceId()
                ));
            } else {
                return ResponseEntity.status(404).body(new UpdateVOIPServiceResponse(
                        "404",
                        ERROR_PREFIX + "No subscription or subscriber found to update",
                        Instant.now().toString(),
                        null,
                        null
                ));
            }

        } catch (Exception ex) {
            log.error("Exception in UpdateVOIPService", ex);
            return ResponseEntity.status(500).body(new UpdateVOIPServiceResponse(
                    "500",
                    ERROR_PREFIX + "Error occurred during update - " + ex.getMessage(),
                    Instant.now().toString(),
                    null,
                    null
            ));
        }
    }
}
