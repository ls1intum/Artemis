package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceSubmittedAnswerFromStudentDTO.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropSubmittedAnswerFromStudentDTO.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerSubmittedAnswerFromStudentDTO.class, name = "short-answer") })
public interface SubmittedAnswerFromStudentDTO {

    static Long getQuestionId(SubmittedAnswerFromStudentDTO submittedAnswer) {
        return switch (submittedAnswer) {
            case MultipleChoiceSubmittedAnswerFromStudentDTO mcAnswer -> mcAnswer.questionId();
            case DragAndDropSubmittedAnswerFromStudentDTO dndAnswer -> dndAnswer.questionId();
            case ShortAnswerSubmittedAnswerFromStudentDTO saAnswer -> saAnswer.questionId();
            case null, default -> throw new IllegalArgumentException("Unknown SubmittedAnswerFromStudentDTO type");
        };
    }
}
