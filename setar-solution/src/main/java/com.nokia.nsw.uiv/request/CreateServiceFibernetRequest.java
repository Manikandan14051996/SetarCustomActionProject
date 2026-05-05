package com.nokia.nsw.uiv.request;

import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class CreateServiceFibernetRequest {
    @NotNull
    private String subscriberName;
    @NotNull
    private String productType;
    @NotNull
    private String productSubtype;
    @NotNull
    private String ontSN;
    private String ontPort;
    @NotNull
    private String oltName;
    private String templateNameONT;
    private String templateNameVEIP;
    private String templateNameHSI;
    @NotNull
    private String qosProfile;
    @NotNull
    private String vlanID;
    private String email;
    private String emailPassword;
    private String firstName;
    private String lastName;
    private String subscriberID;
    @NotNull
    private String menm;
    @NotNull
    private String hhid;
    private String subscriptionStatus;
    private String subscriberStatus;
    private String subsAddress;
    private String companyName;
    private String contactPhone;
    @NotNull
    private String serviceID;
    @NotNull
    private String ontModel;
    private String fxOrderID;
    private String kenanUidNo;
    private String createdBy;
}