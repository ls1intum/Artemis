package de.tum.cit.aet.artemis.globalsearch.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

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
}
