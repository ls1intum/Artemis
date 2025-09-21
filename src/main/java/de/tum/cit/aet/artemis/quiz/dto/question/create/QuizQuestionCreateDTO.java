package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceQuestionCreateDTO.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropQuestionCreateDTO.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerQuestionCreateDTO.class, name = "short-answer") })
public interface QuizQuestionCreateDTO {
}
