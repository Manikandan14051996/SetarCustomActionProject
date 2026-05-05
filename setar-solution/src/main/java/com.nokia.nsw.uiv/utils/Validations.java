package com.nokia.nsw.uiv.utils;

import com.nokia.nsw.uiv.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Slf4j
public class Validations {

    public static void validateMandatoryParams(String ParamValue, String ParamName) throws BadRequestException {
        if (StringUtils.isBlank(ParamValue)) {
            String errorMsg = String.format(" Missing mandatory parameter: %s", ParamName);
            throw new BadRequestException(errorMsg);
        }
    }

    public static void validateMandatoryParams(String ParamValue, String ParamName, String appendMsg)
            throws BadRequestException {
        if (StringUtils.isBlank(ParamValue)) {
            String errorMsg = String.format(" Missing mandatory parameter: %s %s", ParamName, appendMsg);
            throw new BadRequestException(errorMsg);
        }
    }

    public static void validateMandatory(String val, String name) throws BadRequestException {
        if (val == null || val.trim().isEmpty())
            throw new BadRequestException("Missing Mandatory Parameter : "+name);
    }

    public static boolean equalsAny(String data, String... args) {
        for (String arg : args) {
            if (data.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    public static String getGlobalName(String localName) {
        if (localName.trim().isEmpty() || localName == null) {
            return "";
        }
        return Constants.SETAR + Constants.COMMA + localName;
    }

    public static String encryptName(String value) {
        if (value == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static void validateLength(String str, String type) throws BadRequestException {
        if (str.length() > 100) {
            throw new BadRequestException(type + " name too long");
        }
    }

    public static String getCurrentTimestamp() {
        return Instant.now().toString();
    }
}
