package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * When the most recent quiz question pool build of a course that ran to its end finished, as the repository groups it.
 *
 * @param courseId   the course
 * @param finishedAt when that build finished
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionPoolLastCompletedBuildDTO(Long courseId, ZonedDateTime finishedAt) {
}
