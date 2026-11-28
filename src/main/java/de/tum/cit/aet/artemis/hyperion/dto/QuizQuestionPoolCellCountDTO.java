package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;

/**
 * DTO carrying how many pool items of a course a cell holds and the highest index the cell uses.
 *
 * @param competencyId  the cell's competency
 * @param lectureUnitId the cell's lecture unit
 * @param language      the cell's language
 * @param questionType  the cell's question type
 * @param difficulty    the cell's difficulty
 * @param count         the items in the cell whose material is unchanged
 * @param highestIndex  the highest item index in the cell, including items whose material changed; indices can have gaps after deletions
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionPoolCellCountDTO(long competencyId, long lectureUnitId, QuizQuestionGenerationLanguage language, QuizQuestionGenerationType questionType,
        QuizQuestionDifficulty difficulty, long count, int highestIndex) {
}
