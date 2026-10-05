package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.ResultDTO;

class ResultDTOSerializationTest {

    private static final ZonedDateTime COMPLETION_DATE = ZonedDateTime.parse("2026-01-15T10:00:00Z");

    private final JsonMapper objectMapper = new JsonMapper();

    /**
     * The client matches a result to a correction round by this field, so it has to survive serialization. Round 0 is
     * the interesting case: the DTOs carry {@code @JsonInclude(NON_EMPTY)}, and a zero being treated as empty would
     * silently drop the first correction round.
     */
    @Test
    void shouldSerializeCorrectionRoundZero() throws Exception {
        Result result = new Result();
        result.setId(1L);
        result.setAssessmentType(AssessmentType.MANUAL);
        result.setCompletionDate(COMPLETION_DATE);
        result.setCorrectionRound(0);

        String json = objectMapper.writeValueAsString(ResultDTO.of(result));

        assertThat(json).contains("\"correctionRound\":0");
    }

    @Test
    void shouldOmitCorrectionRoundWhenAbsent() throws Exception {
        Result result = new Result();
        result.setId(1L);
        result.setAssessmentType(AssessmentType.AUTOMATIC);

        String json = objectMapper.writeValueAsString(ResultDTO.of(result));

        assertThat(json).doesNotContain("correctionRound");
    }
}
