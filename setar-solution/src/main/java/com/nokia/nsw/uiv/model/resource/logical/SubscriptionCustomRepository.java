package com.nokia.nsw.uiv.model.resource.logical;

import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Primary
@Repository
public interface SubscriptionCustomRepository extends SubscriptionRepository {
    Optional<Subscription> findByDiscoveredName(String discoveredName);
//    Optional<Subscription> findByProperty(String key, String value);
List<Subscription> findByDiscoveredNameContainingAndDiscoveredNameNotContaining(
        String subscriberName,
        String excludedText);
}
