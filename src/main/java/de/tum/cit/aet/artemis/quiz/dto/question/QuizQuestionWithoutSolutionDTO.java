package de.tum.cit.aet.artemis.quiz.dto.question;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The solution-hidden projection of an exam quiz question, one implementation per question type.
 * <p>
 * The discriminator is the {@code type} property {@link QuizQuestionBaseDTO} already writes, so
 * {@code As.EXISTING_PROPERTY} reuses it instead of adding a second copy, and the payload stays the flat object it has
 * always been: the base fields plus the fields of the one question type.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = MultipleChoiceQuizQuestionWithoutSolutionDTO.class, name = "multiple-choice"),
    @JsonSubTypes.Type(value = DragAndDropQuizQuestionWithoutSolutionDTO.class, name = "drag-and-drop"),
    @JsonSubTypes.Type(value = ShortAnswerQuizQuestionWithoutSolutionDTO.class, name = "short-answer")
})
// @formatter:on
@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceQuizQuestionWithoutSolutionDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropQuizQuestionWithoutSolutionDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerQuizQuestionWithoutSolutionDTO.class) }, oneOf = { MultipleChoiceQuizQuestionWithoutSolutionDTO.class,
                DragAndDropQuizQuestionWithoutSolutionDTO.class, ShortAnswerQuizQuestionWithoutSolutionDTO.class })
public sealed interface QuizQuestionWithoutSolutionDTO extends QuizQuestionForExamDTO
        permits MultipleChoiceQuizQuestionWithoutSolutionDTO, DragAndDropQuizQuestionWithoutSolutionDTO, ShortAnswerQuizQuestionWithoutSolutionDTO {

    /**
     * @return the fields every question type carries, including the {@code type} discriminator
     */
    QuizQuestionBaseDTO quizQuestionBaseDTO();

    /**
     * Creates the projection matching the concrete question type.
     *
     * @param quizQuestion the QuizQuestion object
     * @return the created QuizQuestionWithoutSolutionDTO object
     */
    static QuizQuestionWithoutSolutionDTO of(final QuizQuestion quizQuestion) {
        QuizQuestionBaseDTO base = QuizQuestionBaseDTO.of(quizQuestion);
        return switch (quizQuestion) {
            case MultipleChoiceQuestion question -> new MultipleChoiceQuizQuestionWithoutSolutionDTO(base, MultipleChoiceQuestionWithoutSolutionDTO.of(question));
            case DragAndDropQuestion question -> new DragAndDropQuizQuestionWithoutSolutionDTO(base, DragAndDropQuestionWithoutSolutionDTO.of(question));
            case ShortAnswerQuestion question -> new ShortAnswerQuizQuestionWithoutSolutionDTO(base, ShortAnswerQuestionWithoutMappingDTO.of(question));
            default -> throw new IllegalArgumentException("Unsupported quiz question type: " + quizQuestion.getClass().getSimpleName());
        };
    }
}

/**
 * The solution-hidden projection of a multiple-choice question.
 *
 * @param quizQuestionBaseDTO                      the fields shared by every question type
 * @param multipleChoiceQuestionWithoutSolutionDTO the multiple-choice fields, with the correctness flags removed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceQuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO,
        @JsonUnwrapped MultipleChoiceQuestionWithoutSolutionDTO multipleChoiceQuestionWithoutSolutionDTO) implements QuizQuestionWithoutSolutionDTO {
}

/**
 * The solution-hidden projection of a drag-and-drop question.
 *
 * @param quizQuestionBaseDTO                   the fields shared by every question type
 * @param dragAndDropQuestionWithoutSolutionDTO the drag-and-drop fields, with the correct mappings removed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropQuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO,
        @JsonUnwrapped DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionWithoutSolutionDTO) implements QuizQuestionWithoutSolutionDTO {
}

/**
 * The solution-hidden projection of a short-answer question.
 *
 * @param quizQuestionBaseDTO                  the fields shared by every question type
 * @param shortAnswerQuestionWithoutMappingDTO the short-answer fields, with the correct mappings removed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerQuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO,
        @JsonUnwrapped ShortAnswerQuestionWithoutMappingDTO shortAnswerQuestionWithoutMappingDTO) implements QuizQuestionWithoutSolutionDTO {
}
