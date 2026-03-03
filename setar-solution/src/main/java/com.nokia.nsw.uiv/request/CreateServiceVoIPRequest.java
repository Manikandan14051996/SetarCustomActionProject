package com.nokia.nsw.uiv.request;

import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.NotNull;

/**
 * Request class for CreateServiceVoIP action.
 */
@Getter
@Setter
public class CreateServiceVoIPRequest {

    @NotNull
    private String subscriberName;

    @NotNull
    private String productType;

    @NotNull
    private String productSubtype;

    @NotNull
    private String ontSN;

    @NotNull
    private String ontPort;

    @NotNull
    private String oltName;

    @NotNull
    private String voipServiceTemplate;

    @NotNull
    private String simaCustID;

    @NotNull
    private String simaSubsID;

    @NotNull
    private String simaEndpointID;

    @NotNull
    private String voipNumber1;

    @NotNull
    private String templateNameOnt;

    @NotNull
    private String templateNamePots1;

    @NotNull
    private String templateNamePots2;

    @NotNull
    private String hhid;

    @NotNull
    private String ontModel;

    @NotNull
    private String serviceId;

    @NotNull
    private String voipServiceCode;

    @NotNull
    private String voipPackage;

    // Optional
    private String firstName;
    private String lastName;
    private String subsAddress;
    private String companyName;
    private String contactPhone;
    private String fxOrderID;
    private String createdBy;
}
