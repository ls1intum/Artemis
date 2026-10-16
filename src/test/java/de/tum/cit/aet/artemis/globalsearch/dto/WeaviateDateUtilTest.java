package de.tum.cit.aet.artemis.globalsearch.dto;

import static de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema.Properties.DUE_DATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WeaviateDateUtil}.
 */
class WeaviateDateUtilTest {

    @Test
    void format_keepsSecondsForExactMinuteTimes() {
        // Weaviate requires full RFC3339 with seconds; formatters like ISO_OFFSET_DATE_TIME or toString()
        // drop the seconds for exact-minute times, which Weaviate rejects with HTTP 422
        ZonedDateTime date = ZonedDateTime.of(2026, 4, 23, 11, 0, 0, 0, ZoneOffset.UTC);

        assertThat(WeaviateDateUtil.format(date)).isEqualTo("2026-04-23T11:00:00.000Z");
    }

    @Test
    void format_includesOffsetForNonUtcZones() {
        ZonedDateTime date = ZonedDateTime.of(2026, 4, 23, 11, 0, 0, 0, ZoneOffset.ofHours(2));

        assertThat(WeaviateDateUtil.format(date)).isEqualTo("2026-04-23T11:00:00.000+02:00");
    }

    @Test
    void normalizeDateProperties_formatsAnOffsetDateTimeValue() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(DUE_DATE, OffsetDateTime.of(2026, 5, 17, 18, 24, 0, 0, ZoneOffset.UTC));

        WeaviateDateUtil.normalizeDateProperties(properties);

        assertThat(properties.get(DUE_DATE)).isEqualTo("2026-05-17T18:24:00.000Z");
    }

    @Test
    void normalizeDateProperties_formatsAZonedDateTimeValue() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(DUE_DATE, ZonedDateTime.of(2026, 5, 17, 18, 24, 0, 0, ZoneOffset.UTC));

        WeaviateDateUtil.normalizeDateProperties(properties);

        assertThat(properties.get(DUE_DATE)).isEqualTo("2026-05-17T18:24:00.000Z");
    }

    @Test
    void normalizeDateProperties_reformatsAParseableOffsetString() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(DUE_DATE, "2026-05-17T18:24:00Z");

        WeaviateDateUtil.normalizeDateProperties(properties);

        assertThat(properties.get(DUE_DATE)).isEqualTo("2026-05-17T18:24:00.000Z");
    }

    @Test
    void normalizeDateProperties_leavesAnUnparseableStringUnchanged() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(DUE_DATE, "not-a-date");

        WeaviateDateUtil.normalizeDateProperties(properties);

        assertThat(properties.get(DUE_DATE)).isEqualTo("not-a-date");
    }

    @Test
    void normalizeDateProperties_fallsBackToToStringForAnUnanticipatedType() {
        // Instant has no date/offset fields; if the Weaviate client ever returns one instead of
        // OffsetDateTime/ZonedDateTime/String, this must not throw, and still produces a value
        // (the fallback logs a warning so the gap becomes visible rather than silently wrong).
        Instant value = Instant.parse("2026-05-17T18:24:00Z");
        Map<String, Object> properties = new HashMap<>();
        properties.put(DUE_DATE, value);

        WeaviateDateUtil.normalizeDateProperties(properties);

        assertThat(properties.get(DUE_DATE)).isEqualTo(value.toString());
    }

    @Test
    void normalizeDateProperties_leavesAbsentPropertiesAlone() {
        Map<String, Object> properties = new HashMap<>();

        WeaviateDateUtil.normalizeDateProperties(properties);

        assertThat(properties).doesNotContainKey(DUE_DATE);
    }
}
