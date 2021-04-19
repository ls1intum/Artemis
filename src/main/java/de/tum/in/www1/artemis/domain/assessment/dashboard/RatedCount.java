package de.tum.in.www1.artemis.domain.assessment.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RatedCount(boolean rated, long count) {
}
