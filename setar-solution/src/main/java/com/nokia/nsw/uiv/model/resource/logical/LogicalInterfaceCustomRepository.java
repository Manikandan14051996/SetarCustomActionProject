package com.nokia.nsw.uiv.model.resource.logical;

import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterface;
import com.nokia.nsw.uiv.model.resource.logical.LogicalInterfaceRepository;
import com.nokia.nsw.uiv.model.service.Subscription;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
@Primary
@Repository
public interface LogicalInterfaceCustomRepository extends LogicalInterfaceRepository {

    // Custom finder methods
    Optional<LogicalInterface>  findByDiscoveredName(String discoveredName);
    long countByDiscoveredNameContaining(String portName);
    List<LogicalInterface> findByDiscoveredNameContaining(String name);
    @Query("""
        MATCH (s)
        WHERE toLower(s.`properties.configuredPort`) = $portNumber
        RETURN count(s)
    """)
    long countVlanByPortNumber(@Param("portNumber") String portNumber);
//    Optional<LogicalInterface>  findByProperty(String key, String value);
}
