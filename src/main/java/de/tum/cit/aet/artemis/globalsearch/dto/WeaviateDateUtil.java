package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Centralizes date formatting for the Weaviate search index so that both the
 * write side (entity → Weaviate property map) and the read side (Weaviate
 * property map → REST response) use the same consistent ISO 8601 format.
 * <p>
 * Format: {@code yyyy-MM-dd'T'HH:mm:ss.SSSXXX} (always three fractional digits).
 */
public final class WeaviateDateUtil {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private WeaviateDateUtil() {
    }

    /**
     * Formats a {@link ZonedDateTime} for storage in Weaviate.
     *
     * @param dateTime the date-time to format, may be {@code null}
     * @return the formatted string, or {@code null} if the input is {@code null}
     */
    public static String format(ZonedDateTime dateTime) {
        return dateTime != null ? dateTime.format(FORMAT) : null;
    }

    /**
     * Normalizes a date value read back from Weaviate to the consistent format.
     * Weaviate may return dates as {@link OffsetDateTime}, {@link ZonedDateTime},
     * or {@link String} depending on the client version; this method handles all three.
     *
     * @param value the raw value from the Weaviate property map, may be {@code null}
     * @return the normalized date string, or {@code null} if the input is {@code null}
     */
    public static String normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.format(FORMAT);
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.format(FORMAT);
        }
        if (value instanceof String str) {
            try {
                OffsetDateTime parsed = OffsetDateTime.parse(str);
                return parsed.format(FORMAT);
            }
            catch (Exception e) {
                return str;
            }
        }
        return value.toString();
    }
}
