package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing which questions to draw from a course's quiz question pool for a new quiz.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "A request to draw questions from the quiz question pool for a new quiz")
public record QuizQuestionPoolSelectionRequestDTO(@NotEmpty @Schema(description = "Ids of the competencies the quiz covers") List<Long> competencyIds,
        @Schema(description = "Ids of the lecture units to draw from; every linked unit when absent") @Nullable List<Long> lectureUnitIds,
        @Min(1) @Max(MAX_QUESTIONS) @Schema(description = "Number of questions to draw") int count,
        @Schema(description = "Question types to draw; every type when absent") @Nullable List<QuizQuestionGenerationType> questionTypes,
        @Schema(description = "Difficulties to draw; every difficulty when absent") @Nullable List<QuizQuestionDifficulty> difficulties) {

    /** The most questions one draw may request. */
    public static final int MAX_QUESTIONS = 100;
}
