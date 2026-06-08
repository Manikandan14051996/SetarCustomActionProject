package com.nokia.nsw.uiv.model.resource.logical;


import com.nokia.nsw.uiv.model.common.party.Customer;
import com.nokia.nsw.uiv.model.common.party.CustomerRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Primary
@Repository
public interface CustomerCustomRepository extends CustomerRepository {

    // Add your new method here
    Optional<Customer> findByDiscoveredName(String discoveredName);

    List<Customer> findByDiscoveredNameContaining(String discoveredName);
}

