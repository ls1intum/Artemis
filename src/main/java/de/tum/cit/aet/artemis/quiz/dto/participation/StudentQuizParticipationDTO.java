package de.tum.cit.aet.artemis.quiz.dto.participation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(discriminatorProperty = "quizQuestionsType", discriminatorMapping = {
        @DiscriminatorMapping(value = StudentQuizParticipationDTO.AFTER_QUIZ_END, schema = StudentQuizParticipationWithSolutionsDTO.class),
        @DiscriminatorMapping(value = StudentQuizParticipationDTO.LIVE_QUIZ, schema = StudentQuizParticipationWithQuestionsDTO.class),
        @DiscriminatorMapping(value = StudentQuizParticipationDTO.BEFORE_QUIZ_START, schema = StudentQuizParticipationWithoutQuestionsDTO.class) }, oneOf = {
                StudentQuizParticipationWithSolutionsDTO.class, StudentQuizParticipationWithQuestionsDTO.class, StudentQuizParticipationWithoutQuestionsDTO.class })
@JsonSubTypes({ @JsonSubTypes.Type(value = StudentQuizParticipationWithSolutionsDTO.class, name = StudentQuizParticipationDTO.AFTER_QUIZ_END),
        @JsonSubTypes.Type(value = StudentQuizParticipationWithQuestionsDTO.class, name = StudentQuizParticipationDTO.LIVE_QUIZ),
        @JsonSubTypes.Type(value = StudentQuizParticipationWithoutQuestionsDTO.class, name = StudentQuizParticipationDTO.BEFORE_QUIZ_START) })
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "quizQuestionsType", visible = true)
public sealed interface StudentQuizParticipationDTO
        permits StudentQuizParticipationWithoutQuestionsDTO, StudentQuizParticipationWithQuestionsDTO, StudentQuizParticipationWithSolutionsDTO {

    String BEFORE_QUIZ_START = "before-quiz-start";

    String LIVE_QUIZ = "live-quiz";

    String AFTER_QUIZ_END = "after-quiz-end";
}
