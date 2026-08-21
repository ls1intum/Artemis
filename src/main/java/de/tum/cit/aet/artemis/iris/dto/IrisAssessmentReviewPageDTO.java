package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Page payload for the Iris assessment review overview.
 *
 * @param participations          the participation results on the current page
 * @param participationsPerFilter counts for the current text search, not narrowed by the selected verdict filters
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisAssessmentReviewPageDTO(@Valid List<IrisAssessmentProgrammingStudentParticipationDTO> participations, Map<String, Long> participationsPerFilter) {
}
