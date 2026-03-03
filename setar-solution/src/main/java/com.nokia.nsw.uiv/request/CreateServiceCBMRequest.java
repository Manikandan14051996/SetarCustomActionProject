package com.nokia.nsw.uiv.request;

import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class CreateServiceCBMRequest {
    @NotNull
    private String subscriberName;
    @NotNull
    private String serviceId;
    @NotNull
    private String productType;
    private String productSubtype;

    @NotNull
    private String cbmSN;
    @NotNull
    private String cbmMac;
    private String cbmGatewayMac;
    @NotNull
    private String cbmManufacturer;
    @NotNull
    private String cbmType;
    @NotNull
    private String cbmModel;

    // Optional subscriber fields
    private String firstName;
    private String lastName;
    private String companyName;
    private String contactPhone;
    private String subsAddress;
    private String userName;
    private String fxOrderID;
    private String hhid;
    private String customerGroupId;
    private String subscriberId;
    private String servicePackage;
    private String qosProfile;
    private String kenanUidNo;
    private String email;
    private String emailPassword;
    private String createdBy;
}

