package de.tum.cit.aet.artemis.quiz.dto.participation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(discriminatorProperty = "quizQuestionsType", discriminatorMapping = {
        @DiscriminatorMapping(value = "after-quiz-end", schema = StudentQuizParticipationWithSolutionsDTO.class),
        @DiscriminatorMapping(value = "live-quiz", schema = StudentQuizParticipationWithQuestionsDTO.class),
        @DiscriminatorMapping(value = "before-quiz-start", schema = StudentQuizParticipationWithoutQuestionsDTO.class) }, oneOf = { StudentQuizParticipationWithSolutionsDTO.class,
                StudentQuizParticipationWithQuestionsDTO.class, StudentQuizParticipationWithoutQuestionsDTO.class })
@JsonSubTypes({ @JsonSubTypes.Type(value = StudentQuizParticipationWithSolutionsDTO.class, name = "after-quiz-end"),
        @JsonSubTypes.Type(value = StudentQuizParticipationWithQuestionsDTO.class, name = "live-quiz"),
        @JsonSubTypes.Type(value = StudentQuizParticipationWithoutQuestionsDTO.class, name = "before-quiz-start") })
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "quizQuestionsType")
public sealed interface StudentQuizParticipationDTO
        permits StudentQuizParticipationWithoutQuestionsDTO, StudentQuizParticipationWithQuestionsDTO, StudentQuizParticipationWithSolutionsDTO {

}
