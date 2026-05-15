package com.nokia.nsw.uiv.response;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class StbApCmDeviceResponse {
    private String status;
    private String message;
    private String timestamp;
}
