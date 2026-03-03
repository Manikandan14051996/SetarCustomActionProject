package com.nokia.nsw.uiv.request;

import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for CreateServiceEVPN action
 */
@Data
public class CreateServiceEVPNRequest {

    @NotNull
    private String subscriberName;

    @NotNull
    private String productType;

    @NotNull
    private String productSubtype;

    @NotNull
    private String ontSN;

    // optional
    private String ontPort;

    @NotNull
    private String oltName;

    @NotNull
    private String mgmntVlanId;

    @NotNull
    private String serviceId;

    @NotNull
    private String menm;

    @NotNull
    private String hhid;

    @NotNull
    private String ontModel;

    // optional
    private String vlanId;
    private String qosProfile;
    private String templateNameVlan;
    private String templateNameVpls;
    private String templateNameOnt;
    private String firstName;
    private String lastName;
    private String subsAddress;
    private String companyName;
    private String contactPhone;
    private String templateNameCard;
    private String templateNamePort;
    private String templateNameVlanCreate;
    private String templateNameCreate;
    private String templateNameVlanMgmnt;
    private String fxOrderID;
    private String subscriberId;
    private String kenanUidNo;
    private String createdBy;
}
