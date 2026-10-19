package de.tum.cit.aet.artemis.globalsearch.dto.searchableentity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Unit tests for {@link ExamSearchableEntityDTO}.
 */
class ExamSearchableEntityDTOTest {

    @Test
    void toPropertyMap_keepsSecondsInDatesForExactMinuteTimes() {
        // Weaviate requires full RFC3339 with seconds and rejects dates like "2026-04-23T11:00Z" with HTTP 422
        ZonedDateTime startDate = ZonedDateTime.of(2026, 4, 23, 11, 0, 0, 0, ZoneOffset.UTC);
        var dto = new ExamSearchableEntityDTO(1L, 2L, "Final Exam", null, null, startDate, null, false);

        Map<String, Object> properties = dto.toPropertyMap();

        assertThat(properties.get(SearchableEntitySchema.Properties.START_DATE)).isEqualTo("2026-04-23T11:00:00.000Z");
    }
}
