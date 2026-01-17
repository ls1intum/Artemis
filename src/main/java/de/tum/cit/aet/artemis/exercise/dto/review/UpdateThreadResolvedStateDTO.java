package de.tum.cit.aet.artemis.exercise.dto.review;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request payload to update the resolved state of a thread.")
public record UpdateThreadResolvedStateDTO(@Schema(description = "Whether the thread is resolved.") boolean resolved) {
}
