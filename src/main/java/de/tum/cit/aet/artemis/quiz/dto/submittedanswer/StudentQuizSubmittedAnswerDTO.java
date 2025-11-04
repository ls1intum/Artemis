package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = StudentQuizMultipleChoiceSubmittedAnswerDTO.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = StudentQuizDragAndDropSubmittedAnswerDTO.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = StudentQuizShortAnswerSubmittedAnswerDTO.class, name = "short-answer") })
public sealed interface StudentQuizSubmittedAnswerDTO
        permits StudentQuizDragAndDropSubmittedAnswerDTO, StudentQuizMultipleChoiceSubmittedAnswerDTO, StudentQuizShortAnswerSubmittedAnswerDTO {
}
