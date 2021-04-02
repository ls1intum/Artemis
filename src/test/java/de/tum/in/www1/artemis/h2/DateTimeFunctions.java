package de.tum.in.www1.artemis.h2;

import java.time.*;

public class DateTimeFunctions {

    public static LocalDateTime utcTimestamp() {
        return ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
    }
}
