package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.AccessForbiddenException;
import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.exception.ModificationNotAllowedException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.repository.ProductCustomRepository;
import com.nokia.nsw.uiv.repository.ServiceCustomRepository;
import com.nokia.nsw.uiv.request.DeleteProductSubscriptionRequest;
import com.nokia.nsw.uiv.response.DeleteProductSubscriptionResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

@Component
@RestController
@Action
@Slf4j
public class DeleteProductSubscription implements HttpAction {

    protected static final String ACTION_LABEL = "DeleteProductSubscription";
    private static final String ERROR_PREFIX = "UIV action DeleteProductSubscription execution failed - ";

    @Autowired
    private ProductCustomRepository productRepository;

    @Autowired
    private ServiceCustomRepository serviceCustomRepository;

    @Override
    public Class<?> getActionClass() {
        return DeleteProductSubscriptionRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) throws Exception {
        log.error("Executing action: {}", ACTION_LABEL);

        DeleteProductSubscriptionRequest request = (DeleteProductSubscriptionRequest) actionContext.getObject();

        try {
            log.error("Mandatory parameter validation started...");
            try{
                Validations.validateMandatoryParams(request.getSubscriberName(), "subscriberName");
                Validations.validateMandatoryParams(request.getServiceID(), "serviceID");
                Validations.validateMandatoryParams(request.getProductType(), "productType");
                Validations.validateMandatoryParams(request.getComponentName(), "componentName");
            }catch (BadRequestException bre) {
                return ResponseEntity.status(400).body(new DeleteProductSubscriptionResponse("400", ERROR_PREFIX + "Missing mandatory parameter : " + bre.getMessage(),
                        Instant.now().toString(), ""));
            }
            log.error("Mandatory parameter validation completed");

            // ========== Construct Product Name ==========
            String subscriberName = request.getSubscriberName();
            String subscriptionName = subscriberName + Constants.UNDER_SCORE  + request.getServiceID();
            String productName = request.getServiceID() + Constants.UNDER_SCORE  + request.getComponentName();
            if (productName.length() > 100) {
                throw new BadRequestException("Product Name String exceeds 100 characters");
            }

            // ========== RFS Update ==========

                String rfsName = "RFS" + Constants.UNDER_SCORE + request.getSubscriberName() + Constants.UNDER_SCORE  + request.getServiceID();
                Optional<Service> optRfs = serviceCustomRepository.findByDiscoveredName(rfsName);

                if (optRfs.isPresent()) {
                    Service rfs = optRfs.get();
                    rfs.getProperties().put("transactionType", "DeleteProductSubscription");
                    if (request.getFxOrderID() != null && !request.getFxOrderID().isEmpty()) {
                        rfs.getProperties().put("transactionId", request.getFxOrderID());
                    }
                    serviceCustomRepository.save(rfs);
                    log.error("RFS updated successfully for {}", rfsName);
                } else {
                    log.error("No RFS found for name {}", rfsName);
                }


            // ========== Delete Product ==========
            Optional<Product> optProduct = productRepository.findByDiscoveredName(productName);

            if (optProduct.isPresent()) {
                Product product = optProduct.get();
                productRepository.delete(product);
                log.error("Deleted Product Subscription {}", productName);

                return new DeleteProductSubscriptionResponse(
                        "200",
                        "UIV action DeleteProductSubscription executed successfully. Product Subscription Deleted.",
                        Instant.now().toString(),
                        productName
                );
            } else {
                String msg = "UIV action DeleteProductSubscription execution failed - " +
                        "Error, Product Subscription with name " + productName + " not found.";
                return ResponseEntity.status(404).body(new DeleteProductSubscriptionResponse("404", msg,
                        Instant.now().toString(), ""));
            }

        } catch (BadRequestException bre) {
            String msg = "UIV action DeleteProductSubscription execution failed - " + bre.getMessage();
            return ResponseEntity.status(400).body(new DeleteProductSubscriptionResponse("400", msg,
                    Instant.now().toString(), ""));
        } catch (Exception ex) {
            log.error("Unhandled exception during DeleteProductSubscription", ex);
            String msg = "UIV action DeleteProductSubscription execution failed - Error while deleting Product Subscription " + ex.getMessage();
            return ResponseEntity.status(400).body(new DeleteProductSubscriptionResponse("500", msg,
                    Instant.now().toString(), ""));
        }
    }
}

