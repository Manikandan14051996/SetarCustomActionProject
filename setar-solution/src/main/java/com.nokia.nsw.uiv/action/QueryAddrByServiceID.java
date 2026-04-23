package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.repository.*;
import com.nokia.nsw.uiv.request.QueryAddrByServiceIDRequest;
import com.nokia.nsw.uiv.response.QueryAddrByServiceIDResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Component
@Action
@Slf4j
public class QueryAddrByServiceID implements HttpAction {

    private static final String ACTION_LABEL = Constants.QUERY_ADDRBY_SERVICEID;
    private static final String ERROR_PREFIX = "UIV action QueryAddrByServiceID execution failed - ";

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Autowired
    private CustomerCustomRepository customerRepository;

    @Autowired
    private ProductCustomRepository productRepository;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;

    @Override
    public Class<?> getActionClass() {
        return QueryAddrByServiceIDRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error("Executing action {}", ACTION_LABEL);

        QueryAddrByServiceIDRequest request = (QueryAddrByServiceIDRequest) actionContext.getObject();
        try {
            // 1) Mandatory input validation
            try {
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_STARTED);
                Validations.validateMandatory(request.getServiceId(), "serviceId");
                log.error(Constants.MANDATORY_PARAMS_VALIDATION_COMPLETED);
            } catch (BadRequestException bre) {
                // Code5 -> Missing mandatory parameter
                return ResponseEntity.status(400).body(createErrorResponse("400", ERROR_PREFIX + "Missing mandatory parameter: " + bre.getMessage()));
            }

            String serviceId = request.getServiceId().trim();
            log.error("Looking up RFS entries containing service id '{}'", serviceId);

            // 2) Locate the target service (RFS) using SERVICE_ID
            List<Service> rfsListAll = StreamSupport.stream(serviceCustomRepository.findAll().spliterator(),false).filter(service -> service.getDiscoveredName().contains(Constants.RFS)).toList();
            List<Service> rfsList =new ArrayList<>();
            rfsListAll.forEach(rFS -> {
                if (rFS.getDiscoveredName().contains(serviceId) && rFS.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_RFS)) {
                    rfsList.add(rFS);
                }});
            if (rfsList == null || rfsList.isEmpty()) {
                log.error("No RFS entries found containing '{}'", serviceId);
                return ResponseEntity.status(404).body(createErrorResponse("404", ERROR_PREFIX + "No Service Details Found"));
            }

            Service matchedRfs = null;
            for (Service rfs : rfsList) {
                String name = rfs.getDiscoveredName() == null ? "" : rfs.getDiscoveredName();
                String[] tokens = name.split(Constants.UNDER_SCORE , -1);
                if (tokens.length >= 3 && serviceId.equals(tokens[2])) {
                    matchedRfs = serviceCustomRepository.findByDiscoveredName(rfs.getDiscoveredName()).get();
                    log.error("Matched RFS by token check: {}", name);
                    break;
                }
            }

            if (matchedRfs == null) {
                log.error("No RFS found whose 3rd token equals '{}'", serviceId);
                return ResponseEntity.status(404).body(createErrorResponse("404", ERROR_PREFIX + "No Service Details Found"));
            }

            // 3) Resolve related data: product <- cfs <- rfs ; subscription <- product ; subscriber <- subscription
            Service cfs = serviceCustomRepository.findByDiscoveredName(matchedRfs.getDiscoveredName().replace("RFS","CFS")).get(); // assume this getter exists (as used in CreateServiceFibernet)
            if(cfs!=null){
                cfs = serviceCustomRepository.findByDiscoveredName(cfs.getDiscoveredName()).get();
            }
            if (cfs == null) {
                log.error("Matched RFS does not reference a CustomerFacingService");
                return ResponseEntity.status(404).body(createErrorResponse("404", ERROR_PREFIX + "No Service Details Found"));
            }

            // From CFS to Product
            String productDiscoveredName = cfs.getUsingService().stream().filter(ser->ser.getKind().equalsIgnoreCase(Constants.SETAR_KIND_SETAR_PRODUCT)).findFirst().get().getDiscoveredName();
            Product product = productRepository.findByDiscoveredName(productDiscoveredName).get();
            if (product == null) {
                log.error("CustomerFacingService does not reference Product");
                return ResponseEntity.status(404).body(createErrorResponse("404", ERROR_PREFIX + "No Service Details Found"));
            }
            // Product name: Try typed access then fallback to properties map
            String productName = null;
            try {
                // Try product.getProductType().getName() if productType object exists
                if (product.getProperties().get("productType") != null) {
                    productName = product.getProperties().get("productType").toString();
                }
            } catch (Exception e) {
                log.debug("product.getProductType() access failed, will try properties map");
            }
            if (productName == null || productName.trim().isEmpty()) {
                Map<String, Object> prodProps = product.getProperties();
                if (prodProps != null && prodProps.get("productType") != null) {
                    productName = prodProps.get("productType").toString();
                }
            }

            // Subscription: try typed getter, fallback to product.properties
            Subscription subscription = product.getSubscription().stream().findFirst().get();
            if(subscription!=null){
                subscription = subscriptionRepository.findByDiscoveredName(subscription.getDiscoveredName()).get();
            }
            if (subscription == null) {
                // fallback: maybe product.properties contains subscription id or link - but per spec this is required
                log.error("Product does not have Subscription reference");
                return ResponseEntity.status(404).body(createErrorResponse("404", ERROR_PREFIX + "No Service Details Found"));
            }

            // Service link: prefer subscription.getServiceLink(); fallback to properties
            String serviceLink = null;
            try {
                if (subscription.getProperties().get("serviceLink") != null) serviceLink = subscription.getProperties().get("serviceLink").toString();
            } catch (Exception e) {
                log.debug("subscription.getServiceLink() access failed, will try properties map");
            }
            if ((serviceLink == null || serviceLink.trim().isEmpty()) && subscription.getProperties() != null) {
                Object sl = subscription.getProperties().get("serviceLink");
                if (sl != null) serviceLink = sl.toString();
            }

            // Subscriber -> address
            Customer subscriber = subscription.getCustomer(); // in CreateServiceFibernet subscription.setCustomer(subscriber)
            if(subscriber!=null){
                subscriber = customerRepository.findByDiscoveredName(subscriber.getDiscoveredName()).get();
            }
            String address = null;
            if (subscriber != null) {
                try {
                    if (subscriber.getProperties().get("subscriberAddress") != null) address = subscriber.getProperties().get("subscriberAddress").toString();
                } catch (Exception e) {
                    log.debug("subscriber.getAddress() failed, will try properties map");
                }
                if ((address == null || address.trim().isEmpty()) && subscriber.getProperties() != null) {
                    Object a = subscriber.getProperties().get("subscriberAddress");
                    if (a != null) address = a.toString();
                }
            } else {
                log.error("Subscription does not reference a Subscriber");
            }

            // Validate final outputs - if any null/empty -> treat as not found per spec
            boolean missing = (productName == null || productName.trim().isEmpty())
                    || (address == null || address.trim().isEmpty())
                    || (serviceLink == null || serviceLink.trim().isEmpty());

            if (missing) {
                log.error("One or more resolved values are missing: productName='{}', address='{}', serviceLink='{}'",
                        productName, address, serviceLink);
                return ResponseEntity.status(404).body(createErrorResponse("404", ERROR_PREFIX + "No Service Details Found"));
            }
            log.error(Constants.ACTION_COMPLETED);
            // 4) Build success response
            QueryAddrByServiceIDResponse resp = new QueryAddrByServiceIDResponse();
            resp.setStatus("200");
            resp.setMessage("Service Details Found.");
            resp.setTimestamp(DateTimeUtil.now());
            resp.setProductName(productName == null ? "" : productName);
            resp.setAddress(address == null ? "" : address);
            resp.setServiceLink(serviceLink == null ? "" : serviceLink);
            return resp;

        } catch (Exception ex) {
            log.error("Unhandled error in {}", ACTION_LABEL, ex);
            return ResponseEntity.status(500).body(createErrorResponse("500", ERROR_PREFIX + ex.getMessage()));
        }
    }

    private QueryAddrByServiceIDResponse createErrorResponse(String status, String message) {
        QueryAddrByServiceIDResponse resp = new QueryAddrByServiceIDResponse();
        resp.setStatus(status);
        resp.setMessage(message);
        resp.setTimestamp(DateTimeUtil.now());
        resp.setProductName("");
        resp.setAddress("");
        resp.setServiceLink("");
        return resp;
    }
}
