package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile(PROFILE_CORE)
@Service
public class TimeService {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd - hh:mm");

    public ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    /**
     * Converts the dateTime object to a human-readable date in the form of a string
     *
     * @param dateTime that should be converted to a human-readable string
     * @return the converted date string
     */
    public String convertToHumanReadableDate(ZonedDateTime dateTime) {
        return dateTime.format(formatter);
    }
}
