package de.tum.cit.aet.artemis.communication.dto.exercise_review;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateThreadResolvedStateDTO(boolean resolved) {
}
