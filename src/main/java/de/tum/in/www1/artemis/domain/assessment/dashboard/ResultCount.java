package de.tum.in.www1.artemis.domain.assessment.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
// we have to use upper case Boolean here, because in Result.rated can also take the value null
public record ResultCount(Boolean rated, long count) {
}
