package com.nokia.nsw.uiv.model.resource.logical;


import com.nokia.nsw.uiv.model.service.Product;
import com.nokia.nsw.uiv.model.service.ProductRepository;
import com.nokia.nsw.uiv.model.service.Service;
import com.nokia.nsw.uiv.model.service.ServiceRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Primary
@Repository
public interface ProductCustomRepository extends ProductRepository {

    // Custom finder methods
    Optional<Product> findByDiscoveredName(String discoveredName);

//    Optional<Product> findByProperty(String key, String value);
}
