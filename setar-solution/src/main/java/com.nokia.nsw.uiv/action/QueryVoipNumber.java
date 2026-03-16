package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.repository.SubscriptionCustomRepository;
import com.nokia.nsw.uiv.request.QueryVoipNumberRequest;
import com.nokia.nsw.uiv.response.QueryVoipNumberResponse;
import com.nokia.nsw.uiv.response.UpdateVOIPServiceResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

@Component
@RestController
@Action
@Slf4j
public class QueryVoipNumber implements HttpAction {

    private static final String ERROR_PREFIX = "UIV action QueryVoipNumber execution failed - ";
    protected static final String ACTION_LABEL = Constants.QUERY_VOIP_NUMBER;
    @Autowired private LogicalDeviceCustomRepository logicalDeviceRepo;
    @Autowired private SubscriptionCustomRepository subscriptionRepo;

    @Override
    public Class<?> getActionClass() {
        return QueryVoipNumberRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        QueryVoipNumberRequest req = (QueryVoipNumberRequest) actionContext.getObject();
        log.error("Executing QueryVoipNumber action...");

        try {
            // Step 1: Mandatory validation
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatoryParams(req.getOntSN(), "ontSN");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (BadRequestException bre) {
                return new QueryVoipNumberResponse(
                        "400",
                        ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage(),
                        Instant.now().toString(),
                        "",
                        "","","","","","","","","","","",""
                );
            }

            // Step 2: Service link
            String linkType = (req.getServiceLink() != null && "CBM".equalsIgnoreCase(req.getServiceLink()))
                    ? "CBM" : "ONT";
            String ontName ="ONT" + req.getOntSN();

            // Step 3: Prepare empty fields
            String voipNumber1 = "";
            String voipNumber2 = "";
            String simaCustId = "";
            String simaCustId2 = "";
            String simaSubsId = "";
            String simaSubsId2 = "";
            String simaEndpointId = "";
            String simaEndpointId2 = "";
            String voipCode1 = "";
            String voipCode2 = "";
            String voipPackage = "";
            String firstName = "";
            String lastName = "";

            // Step 4: Get ONT device (only for ONT link)
            if ("ONT".equals(linkType)) {
                Optional<LogicalDevice> ontOpt = logicalDeviceRepo.findByDiscoveredName(ontName);
                if (ontOpt.isEmpty()) {
                    return errorResponse("404", "ONT device not found: " + ontName);
                }
                LogicalDevice ont = ontOpt.get();
                voipNumber1 = ont.getProperties().getOrDefault("potsPort1Number", "").toString();
                voipNumber2 = ont.getProperties().getOrDefault("potsPort2Number", "").toString();
            }

            // Step 5: Exact subscription lookup
            if (req.getSubscriberName() != null && req.getServiceId() != null) {
                String subscriptionName = ("CBM".equals(linkType))
                        ? req.getSubscriberName() + Constants.UNDER_SCORE  + req.getServiceId()
                        : req.getSubscriberName() + Constants.UNDER_SCORE  + req.getServiceId() + Constants.UNDER_SCORE  + req.getOntSN();

                Optional<Subscription> subsOpt = subscriptionRepo.findByDiscoveredName(subscriptionName);
                if (subsOpt.isPresent()) {
                    Subscription subs = subsOpt.get();
                    simaCustId     = (String) subs.getProperties().get("simaCustId");
                    simaSubsId     = (String) subs.getProperties().get("simaSubsId");
                    simaEndpointId = (String) subs.getProperties().get("simaEndpointId");
                    voipCode1      = (String) subs.getProperties().get("voipServiceCode");
                    voipPackage    = (String) subs.getProperties().get("voipPackage");
                    Customer subscriber = subsOpt.get().getCustomer();
                    firstName      = (String) subscriber.getProperties().get("subscriberFirstName");
                    lastName       = (String) subscriber.getProperties().get("subscriberLastName");

                    if ("CBM".equals(linkType)) {
                        voipNumber1 = (String) subs.getProperties().get("voipNumber1");
                        voipNumber2 = "";
                    }

                }else {
                    // Step 6: Retrieve Subscription by Search on Identifiers (Fallback Path)
                    log.error("Executing fallback subscription search for linkType: {}", linkType);

                    ArrayList<Subscription> allSubscriptions = (ArrayList<Subscription>) subscriptionRepo.findAll();
                    ArrayList<Subscription> matchedSubs = new ArrayList<>();

                    if ("ONT".equals(linkType)) {
                        // Priority 1: ontSN + subtype = VOIP
                        for (Subscription s : allSubscriptions) {
                            String discoveredName = s.getDiscoveredName();
                            String subtype = (String) s.getProperties().getOrDefault("serviceSubType", "");
                            if (discoveredName != null && discoveredName.contains(req.getOntSN()) && "VOIP".equalsIgnoreCase(subtype)) {
                                matchedSubs.add(s);
                            }
                        }

                        // Priority 2: ontSN + subtype = Voice
                        if (matchedSubs.isEmpty()) {
                            for (Subscription s : allSubscriptions) {
                                String discoveredName = s.getDiscoveredName();
                                String subtype = (String) s.getProperties().getOrDefault("serviceSubType", "");
                                if (discoveredName != null && discoveredName.contains(req.getOntSN()) && "Voice".equalsIgnoreCase(subtype)) {
                                    matchedSubs.add(s);
                                }
                            }
                        }
                    } else { // CBM
                        // CBM case: subscriberName + subtype = Voice
                        for (Subscription s : allSubscriptions) {
                            String discoveredName = s.getDiscoveredName();
                            String subtype = (String) s.getProperties().getOrDefault("serviceSubType", "");
                            if (discoveredName != null && discoveredName.contains(req.getSubscriberName()) && "Voice".equalsIgnoreCase(subtype)) {
                                matchedSubs.add(s);
                            }
                        }
                    }

                    log.error("Fallback search found {} matching subscriptions", matchedSubs.size());

                    // 0 → No SIMA ID found
                    if (matchedSubs.isEmpty()) {
                        return errorResponse("404", "No SIMA Customer ID found");
                    }

                    // 1 → Capture primary record
                    if (matchedSubs.size() == 1) {
                        Subscription s = matchedSubs.get(0);
                        simaCustId     = (String) s.getProperties().getOrDefault("simaCustId", "");
                        simaSubsId     = (String) s.getProperties().getOrDefault("simaSubsId", "");
                        simaEndpointId = (String) s.getProperties().getOrDefault("simaEndpointId", "");
                        voipCode1      = (String) s.getProperties().getOrDefault("voipServiceCode", "");
                        voipPackage    = (String) s.getProperties().getOrDefault("voipPackage", "");

                        if ("CBM".equals(linkType)) {
                            voipNumber1 = (String) s.getProperties().getOrDefault("voipNumber1", "");
                        }

                    } else if (matchedSubs.size() == 2) {
                        // 2 → Capture both records (primary and secondary)
                        Subscription s1 = matchedSubs.get(0);
                        Subscription s2 = matchedSubs.get(1);

                        simaCustId     = (String) s1.getProperties().getOrDefault("simaCustId", "");
                        simaSubsId     = (String) s1.getProperties().getOrDefault("simaSubsId", "");
                        simaEndpointId = (String) s1.getProperties().getOrDefault("simaEndpointId", "");
                        voipCode1      = (String) s1.getProperties().getOrDefault("voipServiceCode", "");
                        voipPackage    = (String) s1.getProperties().getOrDefault("voipPackage", "");

                        simaCustId2     = (String) s2.getProperties().getOrDefault("simaCustId2", "");
                        simaSubsId2     = (String) s2.getProperties().getOrDefault("simaSubsId2", "");
                        simaEndpointId2 = (String) s2.getProperties().getOrDefault("simaEndpointId2", "");
                        voipCode2       = (String) s2.getProperties().getOrDefault("voipServiceCode2", "");

                        if ("CBM".equals(linkType)) {
                            voipNumber1 = (String) s1.getProperties().getOrDefault("voipNumber1", "");
                            voipNumber2 = (String) s2.getProperties().getOrDefault("voipNumber2", "");
                        }
                    } else {
                        // If more than 2 → take only first two
                        log.error("More than two subscriptions found. Only first two will be considered.");
                    }
                }

            }
            log.error(Constants.ACTION_COMPLETED);
            // Step 7: Final response
            if (simaCustId != null && !simaCustId.isEmpty()) {
                return new QueryVoipNumberResponse(
                        "200",
                        "UIV action QueryVoipNumber executed successfully.",
                        Instant.now().toString(),
                        voipNumber1, voipNumber2,
                        simaCustId, simaCustId2,
                        simaSubsId, simaSubsId2,
                        simaEndpointId, simaEndpointId2,
                        voipCode1, voipCode2,
                        voipPackage, firstName, lastName
                );
            } else {
                return errorResponse("404", "No SIMA Customer ID found");
            }

        } catch (Exception ex) {
            log.error("Exception in QueryVoipNumber", ex);
            return errorResponse("500", "Error occurred while retrieving VoIP details — " + ex.getMessage());
        }
    }

    private QueryVoipNumberResponse errorResponse(String status, String msg) {
        return new QueryVoipNumberResponse(
                status,
                ERROR_PREFIX + msg,
                Instant.now().toString(),
                "", "", "", "", "", "",
                "", "", "", "", "", "", ""
        );
    }
}
