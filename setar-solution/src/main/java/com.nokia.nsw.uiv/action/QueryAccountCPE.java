package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.repository.SubscriptionCustomRepository;
import com.nokia.nsw.uiv.request.QueryAccountCPERequest;
import com.nokia.nsw.uiv.response.QueryAccountCPEResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.Validations;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * QueryAccountCPE retrieves broadband CPE identifiers for a given subscriberName + serviceId.
 */
@Component
@RestController
@Action
@Slf4j
public class QueryAccountCPE implements HttpAction {
    protected static final String ACTION_LABEL = Constants.QUERY_ACCOUNT_CPE;
    private static final String ERROR_PREFIX = "UIV action QueryAccountCPE execution failed - ";

    @Autowired private SubscriptionCustomRepository subscriptionRepo;
    @Autowired private LogicalDeviceCustomRepository deviceRepo;

    @Override
    public Class<?> getActionClass() {
        return QueryAccountCPERequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        log.error("------------Trace # 1--------------- QueryAccountCPE started");
        QueryAccountCPERequest req = (QueryAccountCPERequest) actionContext.getObject();
        try {

            // Step 1: Validate mandatory parameters
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatory(req.getSubscriberName(), "subscriberName");
                Validations.validateMandatory(req.getServiceId(), "serviceId");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (BadRequestException bre) {
                return ResponseEntity.status(400).body(new QueryAccountCPEResponse(
                        "400",
                        ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage(),
                        DateTimeUtil.now(), "", "", "", "", "", "", "", "", "", "", "", ""
                ));

            }

            String accountNumber = req.getSubscriberName();
            String serviceId = req.getServiceId();
            Subscription matchedSub = null;

            // Step 2: Find candidate subscriptions (Name CONTAINS pattern)
            String pattern;

            List<Subscription> subscriptionList = new ArrayList<>();
            List<Subscription> subscriptions= (List<Subscription>) subscriptionRepo.findAll();
            for(Subscription s:subscriptions) {
                if (accountNumber != null && serviceId != null) {
                    pattern = accountNumber + Constants.UNDER_SCORE + serviceId;
                    if(s.getDiscoveredName().equalsIgnoreCase(pattern))
                    {
                      subscriptionList.add(s);
                    } else if (s.getDiscoveredName().contains("-"+serviceId))
                    {
                        subscriptionList.add(s);
                    } else if(s.getDiscoveredName().contains(accountNumber))
                    {
                        subscriptionList.add(s);
                    }
                }
            }

            // Step 3: Select matching subscription
            for (Subscription s : subscriptionList) {
                String sid = (String) s.getProperties().get("serviceID");
                if (sid != null && sid.equals(serviceId)) {
                    matchedSub = s;
                    break;
                }
            }

            if (matchedSub == null) {
                return ResponseEntity.status(404).body(new QueryAccountCPEResponse(
                        "404",
                        ERROR_PREFIX + "Service Details Not Found.",
                        DateTimeUtil.now(), "", "", "", "", "", "", "", "", "", "", "", ""
                ));
            }

            String serviceLink = safeStr(matchedSub.getProperties().get("serviceLink"));
            String gatewayMac = safeStr(matchedSub.getProperties().get("gatewayMacAddress"));
            String serviceId1 = safeStr(matchedSub.getProperties().get("serviceID"));
            String accountNumber1 = matchedSub.getDiscoveredName().split(Constants.UNDER_SCORE )[0];

            String ontSN = "";
            String cbmMac = "";
            String deviceModel = "";
            String mtaMac = "";
            String v1 = "";
            String v2 = "";

            // Step 4: Derive identifiers
            if ("ONT".equalsIgnoreCase(serviceLink) || "SRX".equalsIgnoreCase(serviceLink)) {
                String subtype = safeStr(matchedSub.getProperties().get("serviceSubType"));
                if ("IPTV".equalsIgnoreCase(subtype)) {
                    ontSN = safeStr(matchedSub.getProperties().get("serviceSN"));
                } else {
                    String[] parts = matchedSub.getDiscoveredName().split(Constants.UNDER_SCORE );
                    ontSN = parts[parts.length - 1];
                }
            } else if ("Cable_Modem".equalsIgnoreCase(serviceLink)) {
                cbmMac = safeStr(matchedSub.getProperties().get("serviceMAC"));
            }

            // Step 5: Enrich from CPE Device
            String cpeName = "";
            if ("ONT".equalsIgnoreCase(serviceLink)) cpeName ="ONT" + Constants.UNDER_SCORE + ontSN;
            if ("SRX".equalsIgnoreCase(serviceLink)) cpeName = "SRX" + Constants.UNDER_SCORE + ontSN;
            if ("Cable_Modem".equalsIgnoreCase(serviceLink)) cpeName = "CBM" + Constants.UNDER_SCORE +cbmMac;

            if (!cpeName.isEmpty()) {
                Optional<LogicalDevice> devOpt = deviceRepo.findByDiscoveredName(cpeName);
                if (devOpt.isPresent()) {
                    LogicalDevice dev = devOpt.get();
                    if (gatewayMac.isEmpty()) {
                        gatewayMac = safeStr(dev.getProperties().get("gatewayMacAddress"));
                    }
                    deviceModel = safeStr(dev.getProperties().get("deviceModel"));
                    if ("Cable_Modem".equalsIgnoreCase(serviceLink)) {
                        mtaMac = safeStr(dev.getProperties().get("macAddressMta"));
                    }
                    v1 = safeStr(dev.getProperties().get("voipPort1"));
                    v2 = safeStr(dev.getProperties().get("voipPort2"));
                }
            }
            log.error(Constants.ACTION_COMPLETED);
            // Step 7: Build response
            return ResponseEntity.status(200).body(new QueryAccountCPEResponse(
                    "200",
                    "UIV action QueryAccountCPE executed successfully.",
                    DateTimeUtil.now(),
                    "ONT".equalsIgnoreCase(serviceLink) ? ontSN : null,
                    "SRX".equalsIgnoreCase(serviceLink) ? ontSN : null,
                    cbmMac.isEmpty() ? null : cbmMac,
                    (!ontSN.isEmpty() ? ontSN : (!cbmMac.isEmpty() ? cbmMac : null)),
                    serviceLink,
                    gatewayMac,
                    accountNumber1,
                    serviceId1,
                    deviceModel,
                    mtaMac.isEmpty() ? null : mtaMac,
                    v1.isEmpty() ? null : v1,
                    v2.isEmpty() ? null : v2
            ));

        } catch (Exception ex) {
            log.error("Exception in QueryAccountCPE", ex);
            return ResponseEntity.status(500).body(new QueryAccountCPEResponse(
                    "500",
                    ERROR_PREFIX + ex.getMessage(),
                    DateTimeUtil.now(), "", "", "", "", "", "", "", "", "", "", "", ""
            ));

        }
    }

    private String safeStr(Object o) {
        return (o == null) ? "" : o.toString();
    }
}
