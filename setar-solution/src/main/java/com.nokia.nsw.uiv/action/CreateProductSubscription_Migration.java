package com.nokia.nsw.uiv.action;

import com.nokia.nsw.uiv.exception.AccessForbiddenException;
import com.nokia.nsw.uiv.exception.BadRequestException;
import com.nokia.nsw.uiv.exception.ModificationNotAllowedException;
import com.nokia.nsw.uiv.framework.action.Action;
import com.nokia.nsw.uiv.framework.action.ActionContext;
import com.nokia.nsw.uiv.framework.action.HttpAction;
import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.repository.CustomerCustomRepository;
import com.nokia.nsw.uiv.repository.ProductCustomRepository;
import com.nokia.nsw.uiv.repository.SubscriptionCustomRepository;
import com.nokia.nsw.uiv.request.CreateProductSubscriptionBulkRequest;
import com.nokia.nsw.uiv.request.CreateProductSubscriptionRequest;
import com.nokia.nsw.uiv.response.CreateProductSubscriptionResponse;
import com.nokia.nsw.uiv.utils.Constants;
import com.nokia.nsw.uiv.utils.DateTimeUtil;
import com.nokia.nsw.uiv.utils.DuplicateServiceException;
import com.nokia.nsw.uiv.utils.Validations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Action
@Slf4j
public class CreateProductSubscription_Migration implements HttpAction {

    protected static final String ACTION_LABEL = "CreateProductSubscription";
    private static final String ERROR_PREFIX = "UIV action CreateProductSubscription execution failed - ";

    @Autowired
    private CustomerCustomRepository subscriberRepository;

    @Autowired
    private SubscriptionCustomRepository subscriptionRepository;

    @Autowired
    private ProductCustomRepository productRepository;

    @Autowired
    private CreateProductSubscription_Migration self;

    @Override
    public Class<?> getActionClass() {
        return CreateProductSubscriptionBulkRequest.class;
    }

    @Override
    public Object doPost(ActionContext actionContext) {

        CreateProductSubscriptionBulkRequest bulk =
                (CreateProductSubscriptionBulkRequest) actionContext.getObject();

        List<CreateProductSubscriptionResponse> responses = new ArrayList<>();

        for (CreateProductSubscriptionRequest request : bulk.getServices()) {
            try {
                responses.add(self.singleReqprocess(request));

            } catch (BadRequestException bre) {
                responses.add(new CreateProductSubscriptionResponse(
                        "400",
                        ERROR_PREFIX + bre.getMessage(),
                        DateTimeUtil.now(),
                        "",
                        ""
                ));
            } catch (AccessForbiddenException | ModificationNotAllowedException ex) {
                responses.add(new CreateProductSubscriptionResponse(
                        "403",
                        ERROR_PREFIX + ex.getMessage(),
                        DateTimeUtil.now(),
                        "",
                        ""
                ));
            } catch (Exception ex) {
                responses.add(new CreateProductSubscriptionResponse(
                        "500",
                        ERROR_PREFIX + "Internal server error - " + ex.getMessage(),
                        DateTimeUtil.now(),
                        "",
                        ""
                ));
            }
        }

        return responses;
    }
    @Transactional(rollbackFor = Exception.class)
    public CreateProductSubscriptionResponse singleReqprocess(CreateProductSubscriptionRequest request) throws BadRequestException, AccessForbiddenException, ModificationNotAllowedException {
        log.error("Processing request: {}", request.getServiceID());
        CreateProductSubscriptionResponse response=null;
        AtomicBoolean isSubscriberExist = new AtomicBoolean(true);
        AtomicBoolean isSubscriptionExist = new AtomicBoolean(true);
        AtomicBoolean isProductExist = new AtomicBoolean(true);
        /* ================= VALIDATION ================= */
            Validations.validateMandatoryParams(request.getSubscriberName(), "subscriberName");
            Validations.validateMandatoryParams(request.getProductType(), "productType");
            Validations.validateMandatoryParams(request.getServiceID(), "serviceID");
            Validations.validateMandatoryParams(request.getComponentName(), "componentName");
            Validations.validateMandatoryParams(request.getProductVariant(), "productVariant");
            Validations.validateMandatoryParams(request.getProduct(), "product");
            Validations.validateMandatoryParams(request.getReferenceID(), "referenceID");


        /* ================= SUBSCRIBER ================= */
        String subscriberName = request.getSubscriberName();

        if (subscriberName.length() > 100) {
            throw new BadRequestException("Subscriber name too long");
        }

        Optional<Customer> optSubscriber =
                subscriberRepository.findByDiscoveredName(subscriberName);

        Customer subscriber;

        if (optSubscriber.isPresent()) {
            subscriber = optSubscriber.get();
        } else {
            isSubscriberExist.set(false);

            subscriber = new Customer();
            subscriber.setLocalName(Validations.encryptName(subscriberName));
            subscriber.setDiscoveredName(subscriberName);
            subscriber.setKind("SetarSubscriber");
            subscriber.setContext(Constants.SETAR);

            Map<String, Object> props = new HashMap<>();
            props.put("name", subscriberName);
            if(request.getSubscriberStatus()!=null){
                props.put("subscriberStatus", request.getSubscriberStatus());
            }else{
                props.put("subscriberStatus", "Active");
            }
            props.put("subscriberType", "Regular");
            props.put("createdBy",
                    request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                            ? request.getCreatedBy()
                            : "CA"
            );
            props.put("actionName", ACTION_LABEL);

            subscriber.setProperties(props);
            subscriberRepository.save(subscriber, 2);
        }

        /* ================= SUBSCRIPTION ================= */
        String subscriptionName =
                subscriberName + Constants.UNDER_SCORE + request.getServiceID();

        if (subscriptionName.length() > 100) {
            throw new BadRequestException("Subscription name too long");
        }

        Optional<Subscription> optSubscription =
                subscriptionRepository.findByDiscoveredName(subscriptionName);

        Subscription subscription;

        if (optSubscription.isPresent()) {
            subscription = optSubscription.get();
        } else {
            isSubscriptionExist.set(false);

            subscription = new Subscription();
            subscription.setLocalName(Validations.encryptName(subscriptionName));
            subscription.setDiscoveredName(subscriptionName);
            subscription.setKind("SetarSubscription");
            subscription.setContext(Constants.SETAR);

            Map<String, Object> props = new HashMap<>();
            props.put("name", subscriptionName);
            if(request.getSubscriberStatus()!=null)
            {
                props.put("subscriptionStatus", request.getSubscriptionStatus());
            }else{
                props.put("subscriptionStatus", "Active");
            }
            props.put("serviceID", request.getServiceID());
            props.put("createdBy",
                    request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                            ? request.getCreatedBy()
                            : "CA"
            );
            props.put("actionName", ACTION_LABEL);

            subscription.setProperties(props);
            subscription.setCustomer(subscriber);

            subscriptionRepository.save(subscription, 2);
        }

        /* ================= PRODUCT ================= */
        String productName =
                request.getServiceID() + Constants.UNDER_SCORE + request.getComponentName();

        if (productName.length() > 100) {
            throw new BadRequestException("Product name too long");
        }

        if(request.getServiceID().equalsIgnoreCase("15763239"))
        {
            if(true)
            {
                throw  new RuntimeException("Checking Purpose im throwing this exception");
            }
        }

        Optional<Product> optProduct =
                productRepository.findByDiscoveredName(productName);

        Product product;

        if (optProduct.isPresent()) {
            product = optProduct.get();
        } else {
            isProductExist.set(false);

            product = new Product();
            product.setLocalName(Validations.encryptName(productName));
            product.setDiscoveredName(productName);
            product.setKind("SetarProduct");
            product.setContext(Constants.SETAR);

            Map<String, Object> props = new HashMap<>();
            props.put("name", productName);
            props.put("productStatus", "Active");
            props.put("productType", request.getProductType());
            props.put("productId", request.getReferenceID());
            props.put("catalogItemName", request.getProduct());
            props.put("catalogItemVersion", request.getProductVariant());
            props.put("createdBy",
                    request.getCreatedBy() != null && !request.getCreatedBy().isEmpty()
                            ? request.getCreatedBy()
                            : "CA"
            );
            props.put("actionName", ACTION_LABEL);

            product.setProperties(props);
            product.setCustomer(subscriber);

            productRepository.save(product, 2);
        }

        /* ================= DUPLICATE CHECK ================= */
        if (isSubscriberExist.get() && isSubscriptionExist.get() && isProductExist.get()) {
            throw  new DuplicateServiceException("Service already exist/Duplicate entry");
        }
        /* ================= LINK PRODUCT ================= */
        product =
                productRepository.findByDiscoveredName(product.getDiscoveredName()).get();
        if (isSubscriptionExist.get()) {
            subscription = subscriptionRepository
                    .findByDiscoveredName(subscription.getDiscoveredName()).get();

            Set<Service> existingServices = subscription.getService();
            existingServices.add(product);
            subscription.setService(existingServices);

        } else {
            subscription = subscriptionRepository
                    .findByDiscoveredName(subscription.getDiscoveredName()).get();
            subscription.setService(new HashSet<>(List.of(product)));
        }

        subscriptionRepository.save(subscription, 2);

        /* ================= SUCCESS ================= */
        response=new CreateProductSubscriptionResponse(
                "201",
                "ProductSubscription created",
                Instant.now().toString(),
                subscriptionName,
                productName
        );
        return response;
    }
}