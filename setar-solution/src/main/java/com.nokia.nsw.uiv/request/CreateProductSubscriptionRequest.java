package com.nokia.nsw.uiv.request;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class CreateProductSubscriptionRequest {
    @NotNull
    private String subscriberName;
    @NotNull
    private String productType;
    @NotNull
    private String serviceID;
    @NotNull
    private String componentName;
    @NotNull
    private String productVariant;
    @NotNull
    private String product;
    @NotNull
    private String referenceID;

    private String createdBy;
}
