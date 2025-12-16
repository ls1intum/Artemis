package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceSubmittedAnswerFromStudentDTO.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropSubmittedAnswerFromStudentDTO.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerSubmittedAnswerFromStudentDTO.class, name = "short-answer") })
public sealed interface SubmittedAnswerFromStudentDTO
        permits DragAndDropSubmittedAnswerFromStudentDTO, MultipleChoiceSubmittedAnswerFromStudentDTO, ShortAnswerSubmittedAnswerFromStudentDTO {

    /**
     * Returns the identifier of the quiz question associated with the given submitted answer DTO.
     * <p>
     * The concrete question id is extracted based on the runtime type of the provided
     * SubmittedAnswerFromStudentDTO implementation.
     *
     * @param submittedAnswer the submitted answer DTO whose question id should be retrieved;
     *                            must not be null
     * @return the id of the associated question
     * @throws IllegalArgumentException if submittedAnswer is null
     */
    static Long getQuestionId(SubmittedAnswerFromStudentDTO submittedAnswer) {
        return switch (submittedAnswer) {
            case MultipleChoiceSubmittedAnswerFromStudentDTO mcAnswer -> mcAnswer.questionId();
            case DragAndDropSubmittedAnswerFromStudentDTO dndAnswer -> dndAnswer.questionId();
            case ShortAnswerSubmittedAnswerFromStudentDTO saAnswer -> saAnswer.questionId();
            case null -> throw new IllegalArgumentException("Submitted answer cannot be null");
        };
    }

    /**
     * Creates a SubmittedAnswerFromStudentDTO from the given SubmittedAnswer domain entity.
     * <p>
     * The concrete DTO subtype is selected based on the runtime type of the provided
     * SubmittedAnswer (multiple choice, drag and drop, or short answer).
     *
     * @param submittedAnswer the submitted answer entity to convert; must not be null
     * @return the corresponding DTO representation for the given submitted answer
     * @throws IllegalArgumentException if the submitted answer type is unsupported or null
     */
    static SubmittedAnswerFromStudentDTO of(SubmittedAnswer submittedAnswer) {
        switch (submittedAnswer) {
            case MultipleChoiceSubmittedAnswer mcAnswer -> {
                return MultipleChoiceSubmittedAnswerFromStudentDTO.of(mcAnswer);
            }
            case DragAndDropSubmittedAnswer dndAnswer -> {
                return DragAndDropSubmittedAnswerFromStudentDTO.of(dndAnswer);
            }
            case ShortAnswerSubmittedAnswer saAnswer -> {
                return ShortAnswerSubmittedAnswerFromStudentDTO.of(saAnswer);
            }
            default -> throw new IllegalArgumentException("Unknown submitted answer type: " + submittedAnswer.getClass().getName());
        }
    }
}
