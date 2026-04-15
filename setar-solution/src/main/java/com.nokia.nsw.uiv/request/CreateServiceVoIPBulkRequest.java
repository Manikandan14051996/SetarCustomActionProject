package com.nokia.nsw.uiv.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Request class for CreateServiceVoIP action.
 */
@Getter
@Setter
public class CreateServiceVoIPBulkRequest {

    @NotNull
    private List<CreateServiceVoIPRequest> services;
}
