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

    public String convertToHumanReadableDate(ZonedDateTime dateTime) {
        return dateTime.format(formatter);
    }
}
