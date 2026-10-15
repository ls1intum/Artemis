package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO carrying an instructor's decision whether a lecture unit is excluded from quiz question generation.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Whether a lecture unit is excluded from quiz question generation")
public record QuizQuestionPoolLectureUnitRelevanceDTO(@Schema(description = "Whether the unit is excluded from generation") boolean excluded) {
}
