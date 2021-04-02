package de.tum.in.www1.artemis.h2;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DateTimeFunctions {

    public static LocalDateTime utcTimestamp() {
        return ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
    }
}
