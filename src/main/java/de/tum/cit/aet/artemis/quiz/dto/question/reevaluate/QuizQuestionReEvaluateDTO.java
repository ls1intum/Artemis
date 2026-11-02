package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceQuestionReEvaluateDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropQuestionReEvaluateDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerQuestionReEvaluateDTO.class) }, oneOf = { MultipleChoiceQuestionReEvaluateDTO.class,
                DragAndDropQuestionReEvaluateDTO.class, ShortAnswerQuestionReEvaluateDTO.class })
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceQuestionReEvaluateDTO.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropQuestionReEvaluateDTO.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerQuestionReEvaluateDTO.class, name = "short-answer") })
public sealed interface QuizQuestionReEvaluateDTO permits DragAndDropQuestionReEvaluateDTO, MultipleChoiceQuestionReEvaluateDTO, ShortAnswerQuestionReEvaluateDTO {

    /**
     * Creates a QuizQuestionReEvaluateDTO from the given QuizQuestion.
     *
     * @param quizQuestion the quiz question to convert
     * @return the corresponding DTO
     */
    static QuizQuestionReEvaluateDTO of(QuizQuestion quizQuestion) {
        return switch (quizQuestion) {
            case MultipleChoiceQuestion multipleChoiceQuestion -> MultipleChoiceQuestionReEvaluateDTO.of(multipleChoiceQuestion);
            case DragAndDropQuestion dragAndDropQuestion -> DragAndDropQuestionReEvaluateDTO.of(dragAndDropQuestion);
            case ShortAnswerQuestion shortAnswerQuestion -> ShortAnswerQuestionReEvaluateDTO.of(shortAnswerQuestion);
            default -> throw new IllegalArgumentException("Unknown quiz question type: " + quizQuestion.getClass());
        };
    }
}
