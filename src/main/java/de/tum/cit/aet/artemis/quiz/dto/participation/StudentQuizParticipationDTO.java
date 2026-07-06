package de.tum.cit.aet.artemis.quiz.dto.participation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "without-questions", schema = StudentQuizParticipationWithoutQuestionsDTO.class),
        @DiscriminatorMapping(value = "with-questions", schema = StudentQuizParticipationWithQuestionsDTO.class),
        @DiscriminatorMapping(value = "with-solutions", schema = StudentQuizParticipationWithSolutionsDTO.class) }, oneOf = { StudentQuizParticipationWithoutQuestionsDTO.class,
                StudentQuizParticipationWithQuestionsDTO.class, StudentQuizParticipationWithSolutionsDTO.class })
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = StudentQuizParticipationWithoutQuestionsDTO.class, name = "without-questions"),
        @JsonSubTypes.Type(value = StudentQuizParticipationWithQuestionsDTO.class, name = "with-questions"),
        @JsonSubTypes.Type(value = StudentQuizParticipationWithSolutionsDTO.class, name = "with-solutions") })
public sealed interface StudentQuizParticipationDTO
        permits StudentQuizParticipationWithoutQuestionsDTO, StudentQuizParticipationWithQuestionsDTO, StudentQuizParticipationWithSolutionsDTO {

}
