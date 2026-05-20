package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

/**
 * DTO interface for quiz questions in the editor context.
 * Supports both creating new questions (id is null) and updating existing questions (id is non-null).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceQuestionFromEditorDTO.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropQuestionFromEditorDTO.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerQuestionFromEditorDTO.class, name = "short-answer") })
public sealed interface QuizQuestionFromEditorDTO permits DragAndDropQuestionFromEditorDTO, MultipleChoiceQuestionFromEditorDTO, ShortAnswerQuestionFromEditorDTO {

    String ENTITY_NAME = "QuizExercise";

    /**
     * Gets the ID of the question (null for new questions).
     *
     * @return the question ID or null
     */
    Long id();

    /**
     * Converts this DTO to a QuizQuestion domain object.
     * This should only be used for new questions (id is null).
     * For existing questions, use applyTo() to update the existing entity.
     *
     * @return the QuizQuestion domain object
     */
    QuizQuestion toDomainObject();

    /**
     * Creates a QuizQuestionFromEditorDTO from the given QuizQuestion domain object.
     *
     * @param question the quiz question to convert
     * @return the corresponding DTO
     */
    static QuizQuestionFromEditorDTO of(QuizQuestion question) {
        return switch (question) {
            case MultipleChoiceQuestion mc -> MultipleChoiceQuestionFromEditorDTO.of(mc);
            case DragAndDropQuestion dnd -> DragAndDropQuestionFromEditorDTO.of(dnd);
            case ShortAnswerQuestion sa -> ShortAnswerQuestionFromEditorDTO.of(sa);
            default -> throw new BadRequestAlertException("The quiz question type is invalid", ENTITY_NAME, "invalidQuiz");
        };
    }
}
