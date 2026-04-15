package com.nokia.nsw.uiv.request;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateServiceCBMBulkRequest {
    @NotNull
    private List<CreateServiceCBMRequest> services;
}
