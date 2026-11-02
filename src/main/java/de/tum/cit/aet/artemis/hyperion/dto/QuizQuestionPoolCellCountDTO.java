package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;

/**
 * DTO carrying how many pool items of a course are in one cell.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionPoolCellCountDTO(long competencyId, long lectureUnitId, QuizQuestionGenerationLanguage language, QuizQuestionGenerationType questionType,
        QuizQuestionDifficulty difficulty, long count) {
}
