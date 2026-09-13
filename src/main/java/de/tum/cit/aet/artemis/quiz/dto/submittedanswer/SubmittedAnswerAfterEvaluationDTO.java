package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A submitted answer as it looks once the quiz has been evaluated, one implementation per question type.
 * <p>
 * The discriminator is the {@code type} property each answer projection already writes, so
 * {@code As.EXISTING_PROPERTY} reuses it rather than adding a second copy, and the payload stays the flat object it has
 * always been: {@code id}, {@code scoreInPoints}, the question, and the fields of the one answer type.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = MultipleChoiceSubmittedAnswerAfterEvaluationDTO.class, name = "multiple-choice"),
    @JsonSubTypes.Type(value = DragAndDropSubmittedAnswerAfterEvaluationDTO.class, name = "drag-and-drop"),
    @JsonSubTypes.Type(value = ShortAnswerSubmittedAnswerAfterEvaluationDTO.class, name = "short-answer")
})
// @formatter:on
@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceSubmittedAnswerAfterEvaluationDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropSubmittedAnswerAfterEvaluationDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerSubmittedAnswerAfterEvaluationDTO.class) }, oneOf = {
                MultipleChoiceSubmittedAnswerAfterEvaluationDTO.class, DragAndDropSubmittedAnswerAfterEvaluationDTO.class, ShortAnswerSubmittedAnswerAfterEvaluationDTO.class })
public sealed interface SubmittedAnswerAfterEvaluationDTO
        permits MultipleChoiceSubmittedAnswerAfterEvaluationDTO, DragAndDropSubmittedAnswerAfterEvaluationDTO, ShortAnswerSubmittedAnswerAfterEvaluationDTO {

    /**
     * @return the id of the submitted answer
     */
    Long id();

    /**
     * @return the points this answer scored
     */
    Double scoreInPoints();

    /**
     * @return the question this answer belongs to, including its solution
     */
    QuizQuestionWithSolutionDTO quizQuestion();

    /**
     * Creates the projection matching the concrete answer type.
     *
     * @param submittedAnswer the SubmittedAnswer object
     * @return the created SubmittedAnswerAfterEvaluationDTO object
     */
    static SubmittedAnswerAfterEvaluationDTO of(final SubmittedAnswer submittedAnswer) {
        Long id = submittedAnswer.getId();
        Double score = submittedAnswer.getScoreInPoints();
        QuizQuestionWithSolutionDTO question = QuizQuestionWithSolutionDTO.of(submittedAnswer.getQuizQuestion());
        return switch (submittedAnswer) {
            case MultipleChoiceSubmittedAnswer answer ->
                new MultipleChoiceSubmittedAnswerAfterEvaluationDTO(id, score, question, MultipleChoiceSubmittedAnswerWithSolutionDTO.of(answer));
            case DragAndDropSubmittedAnswer answer -> new DragAndDropSubmittedAnswerAfterEvaluationDTO(id, score, question, DragAndDropSubmittedAnswerDTO.of(answer));
            case ShortAnswerSubmittedAnswer answer -> new ShortAnswerSubmittedAnswerAfterEvaluationDTO(id, score, question, ShortAnswerSubmittedAnswerDTO.of(answer));
            default -> throw new IllegalArgumentException("Unsupported submitted answer type: " + submittedAnswer.getClass().getSimpleName());
        };
    }
}
