package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

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

    /**
     * Creates a {@link QuizQuestionCreateDTO} from the given {@link QuizQuestion} domain object.
     * <p>
     * Dispatches to the appropriate subclass DTO based on the question type and invokes its {@code of} method.
     *
     * @param question the {@link QuizQuestion} domain object to convert
     * @return the corresponding {@link QuizQuestionCreateDTO} implementation with properties set from the domain object
     * @throws IllegalArgumentException if the question type is unsupported
     */
    public static QuizQuestionCreateDTO of(QuizQuestion question) {
        if (question instanceof MultipleChoiceQuestion mc) {
            return MultipleChoiceQuestionCreateDTO.of(mc);
        }
        else if (question instanceof DragAndDropQuestion dnd) {
            return DragAndDropQuestionCreateDTO.of(dnd);
        }
        else if (question instanceof ShortAnswerQuestion sa) {
            return ShortAnswerQuestionCreateDTO.of(sa);
        }
        throw new IllegalArgumentException("Unsupported question type: " + question.getClass());
    }
}
