package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Normalizes date values read back from Weaviate to a consistent ISO 8601 format
 * ({@code yyyy-MM-dd'T'HH:mm:ss.SSSXXX}, always three fractional digits).
 * <p>
 * Weaviate may return DATE properties as {@link OffsetDateTime}, {@link ZonedDateTime},
 * or {@link String} depending on the client version. This utility normalizes them all
 * to uniform strings at the read boundary so downstream consumers don't need to handle
 * type variations.
 */
public final class WeaviateDateUtil {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /**
     * All property keys in the {@link SearchableEntitySchema} that use the Weaviate DATE type.
     */
    private static final Set<String> DATE_PROPERTY_KEYS = Set.of(SearchableEntitySchema.Properties.RELEASE_DATE, SearchableEntitySchema.Properties.START_DATE,
            SearchableEntitySchema.Properties.END_DATE, SearchableEntitySchema.Properties.DUE_DATE, SearchableEntitySchema.Properties.VISIBLE_DATE,
            SearchableEntitySchema.Properties.EXAM_VISIBLE_DATE, SearchableEntitySchema.Properties.EXAM_START_DATE, SearchableEntitySchema.Properties.EXAM_END_DATE);

    private WeaviateDateUtil() {
    }

    /**
     * Normalizes all date properties in a Weaviate property map in-place.
     * Weaviate may return dates as {@link OffsetDateTime}, {@link ZonedDateTime},
     * or {@link String} depending on the client version; this method converts them
     * all to the consistent format so downstream consumers don't need to handle
     * type variations.
     *
     * @param properties the mutable property map returned by Weaviate
     */
    public static void normalizeDateProperties(Map<String, Object> properties) {
        for (String key : DATE_PROPERTY_KEYS) {
            Object value = properties.get(key);
            if (value == null) {
                continue;
            }
            String normalized = normalizeValue(value);
            if (normalized != null) {
                properties.put(key, normalized);
            }
        }
    }

    private static String normalizeValue(Object value) {
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
