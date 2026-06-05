package com.nokia.nsw.uiv.model.resource.logical;


import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalDeviceRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Primary
@Repository
public interface LogicalDeviceCustomRepository extends LogicalDeviceRepository {

    // Add your new method here
    Optional<LogicalDevice> findByDiscoveredName(String discoveredName);
//    Optional<LogicalDevice> findByProperty(String key,String value);
}

