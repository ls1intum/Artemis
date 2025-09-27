package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceQuestionCreateDTO.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropQuestionCreateDTO.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerQuestionCreateDTO.class, name = "short-answer") })
public interface QuizQuestionCreateDTO {

    /**
     * Converts this DTO to a {@link QuizQuestion} domain object.
     * <p>
     * Implementations of this interface should map their properties to the appropriate
     * {@link QuizQuestion} subclass and return the populated domain object.
     *
     * @return the {@link QuizQuestion} domain object corresponding to this DTO
     */
    QuizQuestion toDomainObject();
}
