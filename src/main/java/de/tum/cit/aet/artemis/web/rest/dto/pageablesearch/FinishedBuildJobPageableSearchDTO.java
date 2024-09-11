package de.tum.cit.aet.artemis.web.rest.dto.pageablesearch;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.BuildStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FinishedBuildJobPageableSearchDTO(BuildStatus buildStatus, String buildAgentAddress, ZonedDateTime startDate, ZonedDateTime endDate, Integer buildDurationLower,
        Integer buildDurationUpper, SearchTermPageableSearchDTO<String> pageable) {
}
