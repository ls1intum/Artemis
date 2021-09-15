package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

/**
 * Corresponds to the client artemis-date.pipe but for the server
 * intended to be able to use human readable dates on the server
 * e.g. for email templates
 */
@Service
public class ServerDateConverterService {

    private static final String defaultLongDateTimeFormat = "YYYY-MM-DD HH:mm:ss";

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy - HH:mm z");

    public String convertToHumanReadableDate(ZonedDateTime dateTime) {

    }

}
