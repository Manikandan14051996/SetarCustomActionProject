package com.nokia.nsw.uiv.utils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {

    public static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss.SSS z yyyy");

    public static String now() {
        return ZonedDateTime.now().format(FORMATTER);
    }
}
