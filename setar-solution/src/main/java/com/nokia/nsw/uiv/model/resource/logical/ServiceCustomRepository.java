package com.nokia.nsw.uiv.model.resource.logical;

import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.ServiceRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Primary
@Repository
public interface ServiceCustomRepository extends ServiceRepository {

    // Custom finder methods
    Optional<Service> findByDiscoveredName(String discoveredName);
    List<Service> findByDiscoveredNameContainingAndKindIgnoreCase(
            String discoveredName,
            String kind);
//    Optional<Service> findByProperty(String key, String value);
}
