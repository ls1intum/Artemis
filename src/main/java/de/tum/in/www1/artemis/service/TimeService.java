package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

@Service
public class TimeService {

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy - hh:mm");

    public ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    /**
     * Converts the dateTime object to a human-readable date in the form of a string
     * @param dateTime that should be converted to a human-readable string
     * @return the converted date string
     */
    public String convertToHumanReadableDate(ZonedDateTime dateTime) {
        return dateTime.format(formatter);
    }
}
