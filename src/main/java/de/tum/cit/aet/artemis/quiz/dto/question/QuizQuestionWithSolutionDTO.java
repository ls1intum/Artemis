package de.tum.cit.aet.artemis.quiz.dto.question;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The full post-publish projection of an exam quiz question, one implementation per question type.
 * <p>
 * The discriminator is the {@code type} property {@link QuizQuestionBaseDTO} already writes, so
 * {@code As.EXISTING_PROPERTY} reuses it instead of adding a second copy, and the payload stays the flat object it has
 * always been: the base fields, {@code explanation}, and the fields of the one question type.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = MultipleChoiceQuizQuestionWithSolutionDTO.class, name = "multiple-choice"),
    @JsonSubTypes.Type(value = DragAndDropQuizQuestionWithSolutionDTO.class, name = "drag-and-drop"),
    @JsonSubTypes.Type(value = ShortAnswerQuizQuestionWithSolutionDTO.class, name = "short-answer")
})
// @formatter:on
@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceQuizQuestionWithSolutionDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropQuizQuestionWithSolutionDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerQuizQuestionWithSolutionDTO.class) }, oneOf = { MultipleChoiceQuizQuestionWithSolutionDTO.class,
                DragAndDropQuizQuestionWithSolutionDTO.class, ShortAnswerQuizQuestionWithSolutionDTO.class })
public sealed interface QuizQuestionWithSolutionDTO extends QuizQuestionForExamDTO
        permits MultipleChoiceQuizQuestionWithSolutionDTO, DragAndDropQuizQuestionWithSolutionDTO, ShortAnswerQuizQuestionWithSolutionDTO {

    /**
     * @return the fields every question type carries, including the {@code type} discriminator
     */
    QuizQuestionBaseDTO quizQuestionBaseDTO();

    /**
     * @return the explanation shown once solutions are published
     */
    String explanation();

    /**
     * Creates the projection matching the concrete question type.
     *
     * @param quizQuestion the QuizQuestion object
     * @return the created QuizQuestionWithSolutionDTO object
     */
    static QuizQuestionWithSolutionDTO of(final QuizQuestion quizQuestion) {
        QuizQuestionBaseDTO base = QuizQuestionBaseDTO.of(quizQuestion);
        String explanation = quizQuestion.getExplanation();
        return switch (quizQuestion) {
            case MultipleChoiceQuestion question -> new MultipleChoiceQuizQuestionWithSolutionDTO(base, explanation, MultipleChoiceQuestionWithSolutionDTO.of(question));
            case DragAndDropQuestion question -> new DragAndDropQuizQuestionWithSolutionDTO(base, explanation, DragAndDropQuestionWithSolutionDTO.of(question));
            case ShortAnswerQuestion question -> new ShortAnswerQuizQuestionWithSolutionDTO(base, explanation, ShortAnswerQuestionWithMappingDTO.of(question));
            default -> throw new IllegalArgumentException("Unsupported quiz question type: " + quizQuestion.getClass().getSimpleName());
        };
    }
}
