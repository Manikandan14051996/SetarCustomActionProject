package com.nokia.nsw.uiv.model.resource.logical;

import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterface;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterfaceRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Primary
@Repository
public interface LogicalInterfaceCustomRepository extends LogicalInterfaceRepository {

    // Custom finder methods
    Optional<LogicalInterface>  findByDiscoveredName(String discoveredName);

//    Optional<LogicalInterface>  findByProperty(String key, String value);
}
