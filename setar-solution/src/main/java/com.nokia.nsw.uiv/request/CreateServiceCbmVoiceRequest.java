package com.nokia.nsw.uiv.request;

import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class CreateServiceCbmVoiceRequest {

    @NotNull
    private String subscriberName;     // M
    @NotNull
    private String productType;        // M
    private String productSubtype;     // O
    @NotNull
    private String cbmSN;              // M
    @NotNull
    private String cbmMac;             // M
    private String cbmGatewayMac;      // O
    @NotNull
    private String cbmManufacturer;    // M
    @NotNull
    private String cbmType;            // M
    @NotNull
    private String cbmModel;           // M
    private String firstName;          // O
    private String lastName;           // O
    private String subscriberId;       // O
    @NotNull
    private String hhid;               // M
    private String subsAddress;        // O
    private String companyName;        // O
    private String contactPhone;       // O
    @NotNull
    private String serviceId;          // M
    private String customerGroupId;    // O
    private String fxOrderID;          // O
    @NotNull
    private String qosProfile;         // M
    @NotNull
    private String voipNumber1;        // M
    @NotNull
    private String simaCustId;         // M
    @NotNull
    private String simaSubsId;         // M
    @NotNull
    private String simaEndpointId;     // M
    private String voipServiceCode;    // O
    private String cpeMacAddressMTA;   // O
    private String voipPort;           // O
    private String userName;           // O
    private String servicePackage;     // O
    private String kenanUidNo;
    private String createdBy;// O
}
