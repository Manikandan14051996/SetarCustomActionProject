package com.nokia.nsw.uiv.model.resource.logical;

import com.nokia.nsw.uiv.model.service.Subscription;
import com.nokia.nsw.uiv.model.service.SubscriptionRepository;
import org.springframework.context.annotation.Primary;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.repository.query.Param;
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

    List<Subscription> findByDiscoveredNameContaining(String name);
    @Query("""
        MATCH (s)
        WHERE toLower(s.`properties.serviceSubType`) = 'iptv'
        AND (
            s.`properties.serviceSN` = $ontsn
            OR s.`properties.serviceMAC` = $ontsn
        )
        RETURN count(s)
    """)
    long countIptvByOntSn(@Param("ontsn") String ontsn);
}
