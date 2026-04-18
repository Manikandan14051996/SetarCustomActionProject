package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.resource.Resource;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.AccountTransferByServiceIDRequest;
import com.nokia.nsw.uiv.response.AccountTransferByServiceIDResponse;

import com.nokia.nsw.uiv.response.QueryAllServicesByCPEResponse;
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.nokia.nsw.uiv.utils.Validations.getCurrentTimestamp;

@Component
@RestController
@Action
@Slf4j
public class AccountTransferByServiceID implements HttpAction {
    protected static final String ACTION_LABEL = Constants.ACCOUNT_TRANSFER_BY_SERVICE_ID;
    private static final String ERROR_PREFIX = "UIV action AccountTransferByServiceID execution failed - ";

    @Autowired private SubscriptionCustomRepository subsRepo;
    @Autowired private ProductCustomRepository prodRepo;
    @Autowired private CustomerCustomRepository custRepo;
    @Autowired private LogicalDeviceCustomRepository cbmDeviceRepository;
    @Autowired private ServiceCustomRepository serviceRepository;

    @Override
    public Class<?> getActionClass() {
        return AccountTransferByServiceIDRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws BadRequestException {
        log.error(Constants.EXECUTING_ACTION, ACTION_LABEL);
        AccountTransferByServiceIDRequest req = (AccountTransferByServiceIDRequest) actionContext.getObject();
        log.error("------Trace #1: Starting AccountTransferByServiceID");

            Validations.validateMandatoryParams(req.getSubscriberNameOld(), "subscriberNameOld");
            Validations.validateMandatoryParams(req.getSubscriberName(), "subscriberName");
            Validations.validateMandatoryParams(req.getServiceId(), "serviceId");
        try {
            // Step 1: Mandatory validations
            log.error("------Trace #2: Validated mandatory params");

            String oldSubscriberName = req.getSubscriberNameOld();
            String subscriberName = req.getSubscriberName();
            // Step 2: Search for CFS containing old subscriber and service ID
            List<Service> serviceList = StreamSupport
                    .stream(serviceRepository.findAll().spliterator(), false).filter(service->service.getDiscoveredName().contains(Constants.CFS))
                    .collect(Collectors.toList());
            List<Service> cfsList = new ArrayList<>();
            for(Service cfs:serviceList)
            {
                if(cfs.getDiscoveredName().contains(oldSubscriberName))
                {
                    log.error("Cfs found containing oldSuscriberName: "+cfs.getDiscoveredName());
                    cfsList.add(cfs);
                }
            }

            cfsList.removeIf(cfs -> !cfs.getDiscoveredName().contains(req.getServiceId()));
            if (cfsList.isEmpty()) {
                return ResponseEntity.status(404).body(errorResponse("404", "No entry found for update", DateTimeUtil.now()));
            }
            log.error("------Trace #3: Matching CFS found count=" + cfsList.size());

            boolean updated = false;
            Product childProd = null;
            Subscription childSubs = null;
            Service childCfs = null;
            Service childRfs = null;
            LogicalDevice childDevice = null;

            // Step 3: Iterate matching CFS
            for (Service cfs : cfsList) {
                String cfsName = cfs.getDiscoveredName();
                String rfsName = cfsName.replace("CFS", "RFS");
                Optional<Service> rfsOpt = serviceRepository.findByDiscoveredName(rfsName);
                Service rfs = rfsOpt.get();
//                Product prod = (Product)cfs.getUsingService().stream().findFirst().get();
                String productDiscName  = cfs.getUsingService().stream().filter(ser->ser.getKind().equals(Constants.SETAR_KIND_SETAR_PRODUCT)).findFirst().get().getDiscoveredName();
                Product prod = prodRepo.findByDiscoveredName(productDiscName).get();
                Subscription subs = prod.getSubscription().stream().findFirst().get();
                Customer oldCust = prod.getCustomer();
//                Customer oldCust1 = customerCustomRepository.findByDiscoveredName(oldSubscriberName);

                if (subs == null || prod == null || oldCust == null) {
                    continue;
                }
                boolean created=false;
                Product newProd = new Product();
                try {

                    Customer newCust = custRepo.findByDiscoveredName(subscriberName)
                            .orElseThrow(() ->
                                    new RuntimeException("New Customer not found: " + subscriberName));


                    // 1. Break old associations
                    subs.setCustomer(null);
                    subs.setService(null);
                    subsRepo.save(subs);

                    // 2. Delete old subscription
                    subsRepo.delete(subs);

                    // 3. Create new subscription
                    Subscription newSubscription = new Subscription();
                    newSubscription.setLocalName(subs.getLocalName());
                    newSubscription.setKind(subs.getKind());
                    newSubscription.setCustomer(newCust);
                    newSubscription.setDiscoveredName(
                            subs.getDiscoveredName().replace(oldSubscriberName, subscriberName)
                    );

                    Map<String, Object> subsProps = new HashMap<>(subs.getProperties());
                    String serviceSubType = String.valueOf(subsProps.get("serviceSubType"));

                    if ("Broadband".equalsIgnoreCase(serviceSubType)) {
                        subsProps.put("kenanSubscriberId", req.getKenanUidNo());
                    } else if ("IPTV".equalsIgnoreCase(serviceSubType)) {
                        subsProps.put("subscriberId_cableModem", subscriberName);
                    }
                    newSubscription.setProperties(subsProps);
                    subsRepo.save(newSubscription, 2);


                    if (prod != null) {

                        // 4. Break old associations
                        prod.setCustomer(null);
                        prod.setSubscription(null);
                        prod.setUsingService(null);
                        prodRepo.save(prod);

                        // 5. Delete old product
                        prodRepo.delete(prod);

                        // 6. Create new product
                        newProd.setKind(prod.getKind());
                        newProd.setLocalName(prod.getLocalName());
                        newProd.setCustomer(newCust);

                        newProd.setDiscoveredName(
                                prod.getDiscoveredName().replace(oldSubscriberName, subscriberName)
                        );
                        newProd.setProperties(new HashMap<>(prod.getProperties()));
                        newSubscription.setService(new HashSet<>(List.of(newProd)));
                        subsRepo.save(newSubscription);
                        prodRepo.save(newProd, 2);
                        created=true;
                    }

                }
                catch (Exception e){
                    if(subs!=null){

                        subs.setDiscoveredName(subs.getDiscoveredName().replace(oldSubscriberName,subscriberName));
                        Map<String, Object> subsProps = subs.getProperties();
                        String serviceSubType = subsProps.get("serviceSubType")!=null?subsProps.get("serviceSubType").toString():"";
                        if(serviceSubType!=null && serviceSubType.equalsIgnoreCase("Broadband")){
                            subsProps.put("kenanSubscriberId",req.getKenanUidNo());
                        }else if(serviceSubType!=null && serviceSubType.equalsIgnoreCase("IPTV")){
                            subsProps.put("subscriberID_CableModem",subscriberName);
                        }
                        log.error("Subscription updated: "+subs.getDiscoveredName());
                        subs.setProperties(subsProps);
                        subsRepo.save(subs,2);
                    }
                    if(oldCust != null){
                        if(oldCust.getDiscoveredName().contains(Constants.UNDER_SCORE)){

                            String[] subscriberNameArray = oldCust.getDiscoveredName().split(Constants.UNDER_SCORE);
                            if(subscriberNameArray.length>1){
                                String	ontSerial=subscriberNameArray[1];
                                oldCust.setDiscoveredName(subscriberName+Constants.UNDER_SCORE + ontSerial);
                            }
                        }else{
                            oldCust.setDiscoveredName(oldCust.getDiscoveredName().replace(oldSubscriberName, subscriberName));
                        }
                        Map<String,Object> oldCustProps = oldCust.getProperties();
                        oldCustProps.put("accountNumber",subscriberName);
                        log.error("Subscriber updated: "+oldCust.getDiscoveredName());
                        oldCust.setProperties(oldCustProps);
                        custRepo.save(oldCust,2);
                    }

                    if(prod != null){
                        prod = prodRepo.findByDiscoveredName(prod.getDiscoveredName()).get();
                        prod.setDiscoveredName(prod.getDiscoveredName().replace(oldSubscriberName,subscriberName));
                        log.error("Product updated: "+prod.getDiscoveredName());
                        serviceRepository.save(prod,2);
                    }
                }
                if(cfs!=null){
                    cfs = serviceRepository.findByDiscoveredName(cfs.getDiscoveredName()).get();
                    cfs.setDiscoveredName(cfs.getDiscoveredName().replace(oldSubscriberName,subscriberName));
                    log.error("CFS updated: "+cfs.getDiscoveredName());
                    if(created)
                    {
                        cfs.setUsingService(new HashSet<>(List.of(newProd)));
                    }
                    serviceRepository.save(cfs,2);
                }
                if(rfs!=null){
                    rfs = serviceRepository.findByDiscoveredName(rfs.getDiscoveredName()).get();
                    rfs.setDiscoveredName(rfs.getDiscoveredName().replace(oldSubscriberName,subscriberName));
                    log.error("RFS updated: "+rfs.getDiscoveredName());
                    serviceRepository.save(rfs,2);
                }
                updated = true;
            }

            if (!updated) {
                return ResponseEntity.status(404).body(errorResponse("404", "Error, No Account found", DateTimeUtil.now()));
            }
            log.error(Constants.ACTION_COMPLETED);
            AccountTransferByServiceIDResponse resp = new AccountTransferByServiceIDResponse();
            resp.setStatus("200");
            resp.setMessage("AccountNumber successfully updated.");
            resp.setTimestamp(DateTimeUtil.now());
            return ResponseEntity.status(200).body(resp);

        } catch (Exception ex) {
            log.error("Exception in AccountTransferByServiceID", ex);
            return ResponseEntity.status(500).body(errorResponse("500", "Unexpected error - " + ex.getMessage(), DateTimeUtil.now()));
        }
    }

    private AccountTransferByServiceIDResponse errorResponse(String status, String msg,String time) {
        AccountTransferByServiceIDResponse resp = new AccountTransferByServiceIDResponse();
        resp.setStatus(status);
        resp.setMessage(ERROR_PREFIX + msg);
        resp.setTimestamp(time);
        return resp;
    }
}
