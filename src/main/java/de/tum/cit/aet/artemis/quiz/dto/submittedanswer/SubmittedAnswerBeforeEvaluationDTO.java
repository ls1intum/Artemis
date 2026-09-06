package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A submitted answer as it looks before the quiz is evaluated, one implementation per question type.
 * <p>
 * The discriminator is the {@code type} property each answer projection already writes, so
 * {@code As.EXISTING_PROPERTY} reuses it rather than adding a second copy, and the payload stays the flat object it has
 * always been: {@code id}, the question, and the fields of the one answer type.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = MultipleChoiceSubmittedAnswerBeforeEvaluationDTO.class, name = "multiple-choice"),
    @JsonSubTypes.Type(value = DragAndDropSubmittedAnswerBeforeEvaluationDTO.class, name = "drag-and-drop"),
    @JsonSubTypes.Type(value = ShortAnswerSubmittedAnswerBeforeEvaluationDTO.class, name = "short-answer")
})
// @formatter:on
@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceSubmittedAnswerBeforeEvaluationDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropSubmittedAnswerBeforeEvaluationDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerSubmittedAnswerBeforeEvaluationDTO.class) }, oneOf = {
                MultipleChoiceSubmittedAnswerBeforeEvaluationDTO.class, DragAndDropSubmittedAnswerBeforeEvaluationDTO.class, ShortAnswerSubmittedAnswerBeforeEvaluationDTO.class })
public sealed interface SubmittedAnswerBeforeEvaluationDTO
        permits MultipleChoiceSubmittedAnswerBeforeEvaluationDTO, DragAndDropSubmittedAnswerBeforeEvaluationDTO, ShortAnswerSubmittedAnswerBeforeEvaluationDTO {

    /**
     * @return the id of the submitted answer
     */
    Long id();

    /**
     * @return the question this answer belongs to, with its solutions removed
     */
    QuizQuestionWithoutSolutionDTO quizQuestion();

    /**
     * Creates the projection matching the concrete answer type.
     *
     * @param submittedAnswer the SubmittedAnswer object
     * @return the created SubmittedAnswerBeforeEvaluationDTO object
     */
    static SubmittedAnswerBeforeEvaluationDTO of(final SubmittedAnswer submittedAnswer) {
        Long id = submittedAnswer.getId();
        QuizQuestionWithoutSolutionDTO question = QuizQuestionWithoutSolutionDTO.of(submittedAnswer.getQuizQuestion());
        return switch (submittedAnswer) {
            case MultipleChoiceSubmittedAnswer answer ->
                new MultipleChoiceSubmittedAnswerBeforeEvaluationDTO(id, question, MultipleChoiceSubmittedAnswerWithoutSolutionDTO.of(answer));
            case DragAndDropSubmittedAnswer answer -> new DragAndDropSubmittedAnswerBeforeEvaluationDTO(id, question, DragAndDropSubmittedAnswerDTO.of(answer));
            case ShortAnswerSubmittedAnswer answer -> new ShortAnswerSubmittedAnswerBeforeEvaluationDTO(id, question, ShortAnswerSubmittedAnswerDTO.of(answer));
            default -> throw new IllegalArgumentException("Unsupported submitted answer type: " + submittedAnswer.getClass().getSimpleName());
        };
    }
}

/**
 * A submitted multiple-choice answer before evaluation.
 *
 * @param id                            the id of the submitted answer
 * @param quizQuestion                  the question this answer belongs to
 * @param multipleChoiceSubmittedAnswer the selected options, without the correctness flags
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceSubmittedAnswerBeforeEvaluationDTO(Long id, QuizQuestionWithoutSolutionDTO quizQuestion,
        @JsonUnwrapped MultipleChoiceSubmittedAnswerWithoutSolutionDTO multipleChoiceSubmittedAnswer) implements SubmittedAnswerBeforeEvaluationDTO {
}

/**
 * A submitted drag-and-drop answer before evaluation.
 *
 * @param id                         the id of the submitted answer
 * @param quizQuestion               the question this answer belongs to
 * @param dragAndDropSubmittedAnswer the submitted mappings
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropSubmittedAnswerBeforeEvaluationDTO(Long id, QuizQuestionWithoutSolutionDTO quizQuestion, @JsonUnwrapped DragAndDropSubmittedAnswerDTO dragAndDropSubmittedAnswer)
        implements SubmittedAnswerBeforeEvaluationDTO {
}

/**
 * A submitted short-answer answer before evaluation.
 *
 * @param id                         the id of the submitted answer
 * @param quizQuestion               the question this answer belongs to
 * @param shortAnswerSubmittedAnswer the submitted texts
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerSubmittedAnswerBeforeEvaluationDTO(Long id, QuizQuestionWithoutSolutionDTO quizQuestion, @JsonUnwrapped ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer)
        implements SubmittedAnswerBeforeEvaluationDTO {
}
