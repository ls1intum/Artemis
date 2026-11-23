package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;

/**
 * DTO carrying one question of a course's quiz question pool that quizzes may use, with the competency, lecture unit, language, question type and
 * difficulty it was generated for. The question includes its answer key and the explanations of the question and of every option.
 *
 * @param id            the id of the pool item
 * @param competencyId  the competency the question was generated for
 * @param lectureUnitId the lecture unit whose material grounds the question
 * @param language      the language of the question
 * @param questionType  the question type
 * @param difficulty    the difficulty
 * @param question      the question with its options, answer key and explanations
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionPoolQuestionDTO(long id, long competencyId, long lectureUnitId, QuizQuestionGenerationLanguage language, QuizQuestionGenerationType questionType,
        QuizQuestionDifficulty difficulty, GeneratedQuizQuestionDTO question) {

    /**
     * Converts a pool item that holds a question to its DTO.
     *
     * @param item a pool item whose question is set
     * @return the DTO
     */
    public static QuizQuestionPoolQuestionDTO of(QuizQuestionPoolItem item) {
        return new QuizQuestionPoolQuestionDTO(item.getId(), item.getCompetencyId(), item.getLectureUnitId(), item.getLanguage(), item.getQuestionType(), item.getDifficulty(),
                item.getQuestion());
    }
}
