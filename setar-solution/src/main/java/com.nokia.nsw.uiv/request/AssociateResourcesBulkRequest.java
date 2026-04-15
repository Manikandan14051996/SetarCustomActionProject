package com.nokia.nsw.uiv.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Request class for AssociateResources custom action.
 */
@Getter
@Setter
public class AssociateResourcesBulkRequest {
    @NotNull
    private List<AssociateResourcesRequest> services;
}
