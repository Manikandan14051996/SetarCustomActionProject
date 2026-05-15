package com.nokia.nsw.uiv.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class StbApCmDeviceRequest {
    @NotNull
    private String serialNo;
    @NotNull
    private String macAddress;
    @NotNull
    private String gatewayMacAddress;
    @NotNull
    private String deviceModel;
    @NotNull
    private String manufacturer;
    @NotNull
    private String customerGroupId;
    private String modelSubType;
    @NotNull
    private String presharedKey;
    @NotNull
    private String deviceType;
    @JsonProperty("AdministrativeState")
    private String administrativeState;
    @NotNull
    @JsonProperty("OperationalState")
    private String operationalState;
    private String name;

    private String createdBy;
}
