package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceQuestionReEvaluateDTO.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropQuestionReEvaluateDTO.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerQuestionReEvaluateDTO.class, name = "short-answer") })
public sealed interface QuizQuestionReEvaluateDTO permits AnswerOptionReEvaluateDTO, DragAndDropQuestionReEvaluateDTO, ShortAnswerQuestionReEvaluateDTO {
}
