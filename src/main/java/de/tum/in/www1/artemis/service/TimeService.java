package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

@Service
public class TimeService {

    public ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy - hh:mm");

    public String convertToHumanReadableDate(ZonedDateTime dateTime) {
        return dateTime.format(formatter);
    }
}
