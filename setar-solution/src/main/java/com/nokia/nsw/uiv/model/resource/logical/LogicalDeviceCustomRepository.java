package com.nokia.nsw.uiv.model.resource.logical;


import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import com.nokia.nsw.uiv.model.service.Subscription;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public interface LogicalDeviceCustomRepository extends LogicalDeviceRepository {

    // Add your new method here
    Optional<LogicalDevice> findByDiscoveredName(String discoveredName);
    List<LogicalDevice> findByKindAndDiscoveredNameStartingWith(
            String kind,
            String discoveredNamePrefix);
    //    Optional<LogicalDevice> findByProperty(String key,String value);
    List<LogicalDevice> findByDiscoveredNameContaining(String name);
}

