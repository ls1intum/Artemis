package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for AI quiz question generation requests.
 * Either {@code topic} (free-topic mode) or {@code competencyIds} (competency-graph mode) must be provided.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to generate quiz questions. Either topic or competencyIds must be provided.")
public record QuizQuestionGenerationRequestDTO(@Nullable @Size(min = 1, max = 500) @Schema(description = "Main topic for the generated quiz (free-topic mode)") String topic,
        @Nullable @Size(min = 1) @Schema(description = "IDs of course competencies to assess (competency-graph mode)") List<Long> competencyIds,
        @Nullable @Size(max = 2000) @Schema(description = "Optional additional instructions") String optionalPrompt,
        @NotNull @Schema(description = "Target language for the generated quiz") QuizQuestionGenerationLanguage language,
        @NotEmpty @Schema(description = "Question types to include") Set<@NotNull QuizQuestionGenerationType> questionTypes,
        @NotNull @Min(1) @Max(10) @Schema(description = "Number of questions to generate") Integer numberOfQuestions,
        @NotNull @Min(0) @Max(100) @Schema(description = "Difficulty as a value from 0 to 100") Integer difficulty) {

    @AssertTrue(message = "Either topic or competencyIds must be provided")
    @Schema(hidden = true)
    public boolean isTopicOrCompetencyIdsPresent() {
        boolean hasTopic = topic != null && !topic.isBlank();
        boolean hasCompetencies = competencyIds != null && !competencyIds.isEmpty();
        return hasTopic || hasCompetencies;
    }
}
