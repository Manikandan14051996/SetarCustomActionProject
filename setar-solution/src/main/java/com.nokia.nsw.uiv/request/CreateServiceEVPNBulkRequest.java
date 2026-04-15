package com.nokia.nsw.uiv.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateServiceEVPNBulkRequest {

    @NotNull
    private List<CreateServiceEVPNRequest> services;
}
