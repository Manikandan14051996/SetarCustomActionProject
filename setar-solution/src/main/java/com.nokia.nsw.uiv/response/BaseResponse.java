package com.nokia.nsw.uiv.response;

import lombok.Data;

@Data
public class BaseResponse {

    private String status;
    private String message;
    private String timestamp;
}