package de.tum.cit.aet.artemis.quiz.dto.question;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceQuizQuestionWithoutSolutionDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropQuizQuestionWithoutSolutionDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerQuizQuestionWithoutSolutionDTO.class) }, oneOf = { MultipleChoiceQuizQuestionWithoutSolutionDTO.class,
                DragAndDropQuizQuestionWithoutSolutionDTO.class, ShortAnswerQuizQuestionWithoutSolutionDTO.class })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Note: Only one of the three questions will be non-null depending on the question type
public record QuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO,
        @Nullable @JsonUnwrapped MultipleChoiceQuestionWithoutSolutionDTO multipleChoiceQuestionWithoutSolutionDTO,
        @Nullable @JsonUnwrapped DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionWithoutSolutionDTO,
        @Nullable @JsonUnwrapped ShortAnswerQuestionWithoutMappingDTO shortAnswerQuestionWithoutMappingDTO) {

    /**
     * Creates a QuizQuestionWithoutSolutionDTO object from a QuizQuestion object.
     *
     * @param quizQuestion the QuizQuestion object
     * @return the created QuizQuestionWithoutSolutionDTO object
     */
    public static QuizQuestionWithoutSolutionDTO of(final QuizQuestion quizQuestion) {
        QuizQuestionBaseDTO quizQuestionBaseDTO = QuizQuestionBaseDTO.of(quizQuestion);
        MultipleChoiceQuestionWithoutSolutionDTO multipleChoiceQuestionDTO = null;
        DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionDTO = null;
        ShortAnswerQuestionWithoutMappingDTO shortAnswerQuestionDTO = null;
        switch (quizQuestion) {
            case MultipleChoiceQuestion multipleChoiceQuestion -> multipleChoiceQuestionDTO = MultipleChoiceQuestionWithoutSolutionDTO.of(multipleChoiceQuestion);
            case DragAndDropQuestion dragAndDropQuestion -> dragAndDropQuestionDTO = DragAndDropQuestionWithoutSolutionDTO.of(dragAndDropQuestion);
            case ShortAnswerQuestion shortAnswerQuestion -> shortAnswerQuestionDTO = ShortAnswerQuestionWithoutMappingDTO.of(shortAnswerQuestion);
            default -> {
                // TODO: Potentially figure out what to do here
            }
        }
        return new QuizQuestionWithoutSolutionDTO(quizQuestionBaseDTO, multipleChoiceQuestionDTO, dragAndDropQuestionDTO, shortAnswerQuestionDTO);
    }

}

// These definitions are used for OpenAPI generation because polymorphic types with @JsonUnwrapped do not work here
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceQuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped MultipleChoiceQuestionWithoutSolutionDTO multipleChoiceQuestionWithoutSolutionDTO) {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropQuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionWithoutSolutionDTO) {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerQuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped ShortAnswerQuestionWithoutMappingDTO shortAnswerQuestionWithoutMappingDTO) {
}

/*
 * QuizQuestionWithoutSolution:
 * type: object
 * discriminator:
 * propertyName: type
 * mapping: {multiple-choice: '#/components/schemas/MultipleChoiceQuizQuestionWithoutSolution',
 * drag-and-drop: '#/components/schemas/DragAndDropQuizQuestionWithoutSolution',
 * short-answer: '#/components/schemas/ShortAnswerQuizQuestionWithoutSolution'}
 * oneOf:
 * - {$ref: '#/components/schemas/MultipleChoiceQuizQuestionWithoutSolution'}
 * - {$ref: '#/components/schemas/DragAndDropQuizQuestionWithoutSolution'}
 * - {$ref: '#/components/schemas/ShortAnswerQuizQuestionWithoutSolution'}
 * properties:
 * id: {type: integer, format: int64}
 * title: {type: string}
 * text: {type: string}
 * hint: {type: string}
 * points: {type: number, format: double}
 * scoringType:
 * type: string
 * enum: [ALL_OR_NOTHING, PROPORTIONAL_WITH_PENALTY, PROPORTIONAL_WITHOUT_PENALTY]
 * randomizeOrder: {type: boolean}
 * invalid: {type: boolean}
 * type: {type: string}
 * answerOptions:
 * type: array
 * items: {$ref: '#/components/schemas/AnswerOptionWithoutSolution'}
 * singleChoice: {type: boolean}
 * backgroundFilePath: {type: string}
 * dropLocations:
 * type: array
 * items: {$ref: '#/components/schemas/DropLocation'}
 * dragItems:
 * type: array
 * items: {$ref: '#/components/schemas/DragItem'}
 * spots:
 * type: array
 * items: {$ref: '#/components/schemas/ShortAnswerSpot'}
 * solutions:
 * type: array
 * items: {$ref: '#/components/schemas/ShortAnswerSolution'}
 * similarityValue: {type: integer, format: int32}
 * matchLetterCase: {type: boolean}
 */
