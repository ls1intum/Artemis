package de.tum.in.www1.artemis.web.rest.dto.pageablesearch;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;

public record FinishedBuildJobPageableSearchDTO(BuildStatus buildStatus, String buildAgentAddress, ZonedDateTime startDate, ZonedDateTime endDate, Integer buildDurationLower,
        Integer buildDurationUpper, SearchTermPageableSearchDTO<String> pageable) {
}
