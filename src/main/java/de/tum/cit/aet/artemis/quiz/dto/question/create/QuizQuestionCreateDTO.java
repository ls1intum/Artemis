package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceQuestionCreateDTO.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropQuestionCreateDTO.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerQuestionCreateDTO.class, name = "short-answer") })
public sealed interface QuizQuestionCreateDTO permits DragAndDropQuestionCreateDTO, MultipleChoiceQuestionCreateDTO, ShortAnswerQuestionCreateDTO {

    String ENTITY_NAME = "QuizExercise";

    /**
     * Converts this DTO to a {@link QuizQuestion} domain object.
     * <p>
     * Implementations of this interface should map their properties to the appropriate
     * {@link QuizQuestion} subclass and return the populated domain object.
     *
     * @return the {@link QuizQuestion} domain object corresponding to this DTO
     */
    QuizQuestion toDomainObject();

    /**
     * Creates a {@link QuizQuestionCreateDTO} from the given {@link QuizQuestion} domain object.
     * <p>
     * Dispatches to the appropriate subclass DTO based on the question type and invokes its {@code of} method.
     *
     * @param question the {@link QuizQuestion} domain object to convert
     * @return the corresponding {@link QuizQuestionCreateDTO} implementation with properties set from the domain object
     * @throws IllegalArgumentException if the question type is unsupported
     */
    static QuizQuestionCreateDTO of(QuizQuestion question) {
        return switch (question) {
            case MultipleChoiceQuestion mc -> MultipleChoiceQuestionCreateDTO.of(mc);
            case DragAndDropQuestion dnd -> DragAndDropQuestionCreateDTO.of(dnd);
            case ShortAnswerQuestion sa -> ShortAnswerQuestionCreateDTO.of(sa);
            default -> throw new BadRequestAlertException("The quiz question type is invalid", ENTITY_NAME, "invalidQuiz");
        };
    }
}
