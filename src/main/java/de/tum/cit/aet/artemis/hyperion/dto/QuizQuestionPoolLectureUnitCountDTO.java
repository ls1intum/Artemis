package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Item counts of one lecture unit in a course's quiz question pool, as the repository groups them.
 *
 * @param lectureUnitId   the lecture unit
 * @param total           items of the unit
 * @param approved        items an instructor approved
 * @param materialChanged items flagged as generated from material that has changed since
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionPoolLectureUnitCountDTO(Long lectureUnitId, Long total, Long approved, Long materialChanged) {
}
