package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO answering a request to build a course's quiz question pool.
 *
 * @param started  whether a build was started; {@code false} when one was already running anywhere in the cluster
 * @param enqueued intended questions newly added to the pool before the build started
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Outcome of a request to build a course's quiz question pool")
public record QuizQuestionPoolBuildStartDTO(@Schema(description = "Whether a build was started") boolean started,
        @Schema(description = "Intended questions newly added to the pool") int enqueued) {
}
