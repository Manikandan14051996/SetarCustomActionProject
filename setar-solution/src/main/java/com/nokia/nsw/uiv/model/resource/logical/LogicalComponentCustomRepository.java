package com.nokia.nsw.uiv.model.resource.logical;

import com.nokia.nsw.uiv.model.resource.logical.LogicalComponent;
import com.nokia.nsw.uiv.model.resource.logical.LogicalComponentRepository;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterface;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Primary
@Repository
public interface LogicalComponentCustomRepository extends LogicalComponentRepository {

    // Custom finder methods
    Optional<LogicalComponent>  findByDiscoveredName(String discoveredName);

//    Optional<LogicalComponent> findByProperty(String key, String value);
}
