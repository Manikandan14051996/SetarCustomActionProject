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
import java.util.Map;
import java.util.Objects;
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
                        "","","","","","","","","","","","",""
                );
            }

            // Step 2: Service link
            String linkType = (req.getServiceLink() != null && "Cable_Modem".equalsIgnoreCase(req.getServiceLink()))
                    ? "Cable_Modem" : "ONT";
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
            String voipPackage2 = "";
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
            if (req.getSubscriberName() != null) {
                String subscriptionName = ("Cable_Modem".equals(linkType))
                        ? req.getSubscriberName() + Constants.UNDER_SCORE  + req.getServiceId()
                        : req.getSubscriberName() + Constants.UNDER_SCORE  + req.getServiceId() + Constants.UNDER_SCORE  + req.getOntSN();

                Optional<Subscription> subsOpt = subscriptionRepo.findByDiscoveredName(subscriptionName);
                if (subsOpt.isPresent()) {
                    Subscription subs = subsOpt.get();
                    simaCustId     = (String) subs.getProperties().getOrDefault("simaCustId1","");
                    simaSubsId     = (String) subs.getProperties().getOrDefault("simaSubsId1","");
                    simaEndpointId = (String) subs.getProperties().getOrDefault("simaEndpointId1","");
                    voipCode1      = (String) subs.getProperties().getOrDefault("voipServiceCode1","");
                    voipPackage    = (String) subs.getProperties().getOrDefault("voipPackage1","");
                    voipPackage2    = (String) subs.getProperties().getOrDefault("voipPackage2","");
                    simaCustId2     = (String) subs.getProperties().getOrDefault("simaCustId2","");
                    simaSubsId2     = (String) subs.getProperties().getOrDefault("simaSubsId2","");
                    simaEndpointId2 = (String) subs.getProperties().getOrDefault("simaEndpointId2","");
                    voipCode2      = (String) subs.getProperties().getOrDefault("voipServiceCode2","");

                    Customer subscriber = subsOpt.get().getCustomer();

                    if (subscriber != null && subscriber.getProperties() != null) {
                        firstName = Objects.toString(subscriber.getProperties().get("subscriberFirstName"), "");
                        lastName  = Objects.toString(subscriber.getProperties().get("subscriberLastName"), "");
                    } else {
                        firstName = "";
                        lastName = "";
                    }


                    if ("Cable_Modem".equals(linkType)) {
                        voipNumber1 = (String) subs.getProperties().get("voipNumber1");
                        voipNumber2 = (String) subs.getProperties().get("voipNumber2");
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
                        simaCustId     = (String) s.getProperties().getOrDefault("simaCustId1", "");
                        simaSubsId     = (String) s.getProperties().getOrDefault("simaSubsId1", "");
                        simaEndpointId = (String) s.getProperties().getOrDefault("simaEndpointId1", "");
                        voipCode1      = (String) s.getProperties().getOrDefault("voipServiceCode1", "");
                        voipPackage    = (String) s.getProperties().getOrDefault("voipPackage1", "");

                        if ("Cable_Modem".equals(linkType)) {
                            voipNumber1 = (String) s.getProperties().getOrDefault("voipNumber1", "");
                        }

                    } else {
                        // 2 → Capture both records (primary and secondary)
                        for (Subscription sub : matchedSubs) {
                            Map<String, Object> props = sub.getProperties();

                            if (props.containsKey("simaCustId1")) {
                                simaCustId     = (String) props.getOrDefault("simaCustId1", "");
                                simaSubsId     = (String) props.getOrDefault("simaSubsId1", "");
                                simaEndpointId = (String) props.getOrDefault("simaEndpointId1", "");
                                voipCode1      = (String) props.getOrDefault("voipServiceCode1", "");
                                voipPackage    = (String) props.getOrDefault("voipPackage1", "");
                            }

                            else if (props.containsKey("simaCustId2")) {
                                simaCustId2     = (String) props.getOrDefault("simaCustId2", "");
                                simaSubsId2     = (String) props.getOrDefault("simaSubsId2", "");
                                simaEndpointId2 = (String) props.getOrDefault("simaEndpointId2", "");
                                voipCode2       = (String) props.getOrDefault("voipServiceCode2", "");
                                voipPackage2    = (String) props.getOrDefault("voipPackage2", "");
                            }
                            if ("Cable_Modem".equals(linkType)) {

                                if (props.containsKey("voipNumber1")) {
                                    voipNumber1 = (String) props.getOrDefault("voipNumber1", "");
                                }

                                if (props.containsKey("voipNumber2")) {
                                    voipNumber2 = (String) props.getOrDefault("voipNumber2", "");
                                }
                            }
                            Customer subscriber = sub.getCustomer();

                            if (subscriber != null && subscriber.getProperties() != null) {
                                firstName = Objects.toString(subscriber.getProperties().get("subscriberFirstName"), "");
                                lastName  = Objects.toString(subscriber.getProperties().get("subscriberLastName"), "");
                            } else {
                                firstName = "";
                                lastName = "";
                            }
                        }


                    }
                }

            }
            log.error(Constants.ACTION_COMPLETED);
            // Step 7: Final response
            if (simaCustId != null && !simaCustId.isEmpty() || simaCustId2 != null && !simaCustId2.isEmpty()) {
                return new QueryVoipNumberResponse(
                        "200",
                        "UIV action QueryVoipNumber executed successfully.",
                        Instant.now().toString(),
                        Objects.toString(voipNumber1, ""),
                        Objects.toString(voipNumber2, ""),
                        Objects.toString(simaCustId, ""),
                        Objects.toString(simaCustId2, ""),
                        Objects.toString(simaSubsId, ""),
                        Objects.toString(simaSubsId2, ""),
                        Objects.toString(simaEndpointId, ""),
                        Objects.toString(simaEndpointId2, ""),
                        Objects.toString(voipCode1, ""),
                        Objects.toString(voipCode2, ""),
                        Objects.toString(voipPackage, ""),
                        Objects.toString(voipPackage2, ""),
                        Objects.toString(firstName, ""),
                        Objects.toString(lastName, "")
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
                "", "", "", "", "","", "", ""
        );
    }
}
