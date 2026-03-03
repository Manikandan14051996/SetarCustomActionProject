package com.nokia.nsw.uiv.request;

import com.nokia.nsw.uiv.model.resource.logical.LogicalDevice;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class ImportCPEDeviceRequest{
    @NotNull
    private String cpeSerialNo;
    @NotNull
    private String cpeModel;
    private String cpeModelMta;
    @NotNull
    private String cpeType;
    @NotNull
    private String cpeMacAddress;
    @NotNull
    private String cpeGwMacAddress;
    private String cpeMacAddressMta;
    private String cpeManufacturer;
    private String cpeModelSubType;
    private String createdBy;
}
