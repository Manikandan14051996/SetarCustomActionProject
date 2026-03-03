package com.nokia.nsw.uiv.request;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class CreateServiceIPTVRequest {

    @NotNull
    private String subscriberName;
    @NotNull
    private String productType;
    @NotNull
    private String productSubtype;
    @NotNull
    private String ontSN;
    @NotNull
    private String oltName;
    private String templateNameONT;
    private String templateNameVEIP;
    private String templateNameIPTV;
    private String templateNameIGMP;
    @NotNull
    private String qosProfile;
    @NotNull
    private String vlanID;
    private String ontMacAddr;
    private String ontModel;
    private String firstName;
    private String lastName;
    @NotNull
    private String menm;
    @NotNull
    private String hhid;
    private String subsAddress;
    private String companyName;
    private String contactPhone;
    @NotNull
    private String serviceID;
    @NotNull
    private String customerGroupID;
    private String servicePackage;
    private String fxOrderID;
    private String kenanUidNo;
    private String gatewayMac;
    private String createdBy;
}
