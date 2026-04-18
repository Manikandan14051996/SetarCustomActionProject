package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterface;
import com.nokia.nsw.uiv.repository.LogicalDeviceCustomRepository;
import com.nokia.nsw.uiv.repository.LogicalInterfaceCustomRepository;
import com.nokia.nsw.uiv.request.QueryTemplateNameVLANRequest;
import com.nokia.nsw.uiv.response.QueryTemplateNameVLANResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Action
@Slf4j
public class    QueryTemplateNameVLAN implements HttpAction {

    private static final String ACTION_LABEL = Constants.QUERY_TEMPLATENAME_VLAN;
    private static final String ERROR_PREFIX = "UIV action QueryTemplateNameVLAN execution failed - ";

    @Autowired
    private LogicalDeviceCustomRepository logicalDeviceRepository;

    @Autowired
    private LogicalInterfaceCustomRepository logicalInterfaceRepository;

    @Override
    public Class<?> getActionClass() {
        return QueryTemplateNameVLANRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error("Executing action {}", ACTION_LABEL);

        QueryTemplateNameVLANRequest request = (QueryTemplateNameVLANRequest) actionContext.getObject();
        try {
            // 1) Mandatory and optional input validations
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatory(request.getOntSN(), "ontSN");
                Validations.validateMandatory(request.getOntPort(), "ontPort");
                Validations.validateMandatory(request.getMenm(), "menm");
                Validations.validateMandatory(request.getTemplateNameVlan(), "templateNameVlan");
                Validations.validateMandatory(request.getTemplateNameVlanCreate(), "templateNameVlanCreate");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (BadRequestException bre) {
                // Code5 -> Missing mandatory parameter
                return ResponseEntity.status(400).body(createErrorResponse("400", ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage()));
            }

            // 2) Build ONT name and validate length (Code6)
            String ontName ="ONT" + request.getOntSN();
            if (ontName.length() > 100) {
                return ResponseEntity.status(400).body(createErrorResponse("400", ERROR_PREFIX + "Identifier exceeds allowed character length"));
            }

            // 2a) Collect VPLS templates already used on this ONT
            List<String> vplsList = new ArrayList<>();
            Optional<LogicalDevice> optOnt = logicalDeviceRepository.findByDiscoveredName(ontName);
            if (optOnt.isPresent()) {
                // NOTE: replace method below with your actual repo method if different.
                LogicalDevice ldn=optOnt.get();
                List<LogicalInterface> ifaceSet = ldn.getContained().stream()
                        .filter(res -> res instanceof LogicalInterface)
                        .map(res -> (LogicalInterface) res)
                        .collect(Collectors.toList());
                if (ifaceSet != null && !ifaceSet.isEmpty()) {
                    for (LogicalInterface iface : ifaceSet) {
                        String ifaceName = iface.getDiscoveredName() == null ? "" : iface.getDiscoveredName();
                        if (!ifaceName.contains("ALCL") && !ifaceName.isEmpty()) {
                            if (!vplsList.contains(ifaceName)) vplsList.add(ifaceName);
                        }
                    }
                }
            } else {
                // ONT not found is not fatal — continue with empty vpls list
                log.error("ONT {} not present in repository, proceeding.", ontName);
            }

            // Resolve configuredVPLSTemplate from each VLAN/iface collected
            List<String> vplsFinalList = new ArrayList<>();
            for (String name : vplsList) {
                Optional<LogicalInterface> optIf = logicalInterfaceRepository.findByDiscoveredName(name);
                if (optIf.isPresent()) {
                    LogicalInterface li = optIf.get();
                    Map<String, Object> props = li.getProperties();
                    if (props != null) {
                        Object cfg = props.get("configuredVplsTemplate");
                        if (cfg != null) {
                            String tmpl = cfg.toString();
                            if (!vplsFinalList.contains(tmpl)) vplsFinalList.add(tmpl);
                        }
                    }
                }
            }

            // 3) Build candidate VPLS template names (2..22), exclude existing and log
            List<String> vplsFinalReturnList = new ArrayList<>();
            Set<String> vplsFinalReturnListExist = new LinkedHashSet<>();
            String baseVpls = request.getTemplateNameVpls() == null ? "" : request.getTemplateNameVpls();
            if (!baseVpls.isEmpty()) {
                for (int i = 2; i <= 22; i++) {
                    String vplsName = baseVpls + " " + i;
                    vplsFinalReturnList.add(vplsName);
                    if (vplsFinalList.contains(vplsName)) vplsFinalReturnListExist.add(vplsName);
                }
                if (!vplsFinalReturnListExist.isEmpty()) {
                    vplsFinalReturnList.removeAll(vplsFinalReturnListExist);
                }
                for (String n : vplsFinalReturnList) log.debug(" the vpls here {}", n);
                for (String n : vplsFinalReturnListExist) log.debug(" the from service {}", n);
            }
            String templateVpls = vplsFinalReturnList.isEmpty() ? "" : vplsFinalReturnList.get(0);

            // 4) Determine VLAN search range (default 1000..4000 exclusive)
            int rangeStart = (request.getVlanRangeStart() == null || request.getVlanRangeStart() == 0)
                    ? 1000 : request.getVlanRangeStart();
            int rangeEnd = (request.getVlanRangeEnd() == null || request.getVlanRangeEnd() == 0)
                    ? 4000 : request.getVlanRangeEnd();
//            if (rangeStart < 0 || rangeEnd <= rangeStart) {
//                return createErrorResponse("400", ERROR_PREFIX + "Invalid VLAN range");
//            }

            // 5) Find next free VLAN ID for this MENM prefix
            String freeVLAN = "";
            for (int v = rangeStart; v < rangeEnd; v++) {
                String vlanName = request.getMenm() + Constants.UNDER_SCORE  + v;
                Optional<LogicalInterface> optVlanIf = logicalInterfaceRepository.findByDiscoveredName(vlanName);
                if (!optVlanIf.isPresent()) {
                    freeVLAN = String.valueOf(v);
                    break;
                }
            }
            if (freeVLAN.isEmpty()) {
                // Code8 -> No free VLAN found
                return ResponseEntity.status(404).body(createErrorResponse("404", ERROR_PREFIX + "No free VLAN found in the specified range"));
            }

            // 6) Choose per-port EVPN template suffix (2..9), max usable 2..8 (8 templates)
            String templateName = "";
            String templateCreate = "";
            String perPortBase = request.getOntSN() + "_P" + request.getOntPort() + "_SINGLETAGGED_";
            String success = "false";
            for (int n = 2; n <= 9; n++) {
                String tempName = perPortBase + n;
                Optional<LogicalInterface> optTempIf = logicalInterfaceRepository.findByDiscoveredName(tempName);
                if (!optTempIf.isPresent()) {
                    if (n == 9) {
                        success = "full"; // indicates >8 VLANs already present
                    } else {
                        templateName = request.getTemplateNameVlan() + " " + n;
                        templateCreate = request.getTemplateNameVlanCreate() + " " + n;
                        success = "true";
                    }
                    break;
                }
            }

            if ("full".equals(success)) {
                // Code7 -> More than 8 Vlans not allowed on port
                return ResponseEntity.status(400).body(createErrorResponse("400", ERROR_PREFIX + "More than 8 Vlans not allowed on port."));
            }
            log.error(Constants.ACTION_COMPLETED);
            // 7) Final response (success)
            QueryTemplateNameVLANResponse resp = new QueryTemplateNameVLANResponse();
            resp.setStatus("200");
            resp.setMessage("Next Free VLAN ID is " + freeVLAN + " and Template name is " + templateName + " and Template VPLS is " + templateVpls);
            resp.setTimestamp(DateTimeUtil.now());
            resp.setVlanId(freeVLAN);
            resp.setVlanTemplateName(templateName);
            resp.setVlanTemplateCreateName(templateCreate);
            resp.setVplsTemplateName(templateVpls);

            return resp;

        } catch (Exception ex) {
            log.error("Unhandled error in {}", ACTION_LABEL, ex);
            return ResponseEntity.status(500).body(createErrorResponse("500", ERROR_PREFIX + ex.getMessage()));
        }
    }

    private QueryTemplateNameVLANResponse createErrorResponse(String status, String message) {
        QueryTemplateNameVLANResponse resp = new QueryTemplateNameVLANResponse();
        resp.setStatus(status);
        resp.setMessage(message);
        resp.setTimestamp(DateTimeUtil.now());
        resp.setVlanId("");
        resp.setVlanTemplateName("");
        resp.setVlanTemplateCreateName("");
        resp.setVplsTemplateName("");
        return resp;
    }
}
