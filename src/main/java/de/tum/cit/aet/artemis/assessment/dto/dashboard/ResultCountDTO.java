package de.tum.cit.aet.artemis.assessment.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultCountDTO(boolean rated, long count) {

    public ResultCountDTO(Boolean rated, Long count) {
        this(rated != null ? rated : false, count != null ? count : 0);
    }
}
