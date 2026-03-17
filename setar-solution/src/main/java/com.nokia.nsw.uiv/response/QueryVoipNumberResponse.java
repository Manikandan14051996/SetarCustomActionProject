package com.nokia.nsw.uiv.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QueryVoipNumberResponse {
    private String status;
    private String message;
    private String timestamp;
    private String voipNumber1;
    private String voipNumber2;
    private String simaCustId;
    private String simaCustId2;
    private String simaSubsId;
    private String simaSubsId2;
    private String simaEndpointId;
    private String simaEndpointId2;
    private String voipCode1;
    private String voipCode2;
    private String voipPackage;
    private String voipPackage2;
    private String firstName;
    private String lastName;
}
