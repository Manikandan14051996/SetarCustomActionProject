package com.nokia.nsw.uiv.request;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateProductSubscriptionBulkRequest {
    @NotNull
    private List<CreateProductSubscriptionRequest> services;
}
