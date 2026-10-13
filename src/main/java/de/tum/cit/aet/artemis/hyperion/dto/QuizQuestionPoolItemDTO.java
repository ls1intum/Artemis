package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.ZonedDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing one item of a course's quiz question pool.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "One item of a course's quiz question pool")
public record QuizQuestionPoolItemDTO(@NotNull @Schema(description = "Id of the pool item") Long id,
        @NotNull @Schema(description = "Id of the competency the question assesses") Long competencyId,
        @Schema(description = "Title of the competency the question assesses") @Nullable String competencyTitle,
        @NotNull @Schema(description = "Language of the question") QuizQuestionGenerationLanguage language,
        @NotNull @Schema(description = "Type of the question") QuizQuestionGenerationType questionType,
        @NotNull @Schema(description = "Difficulty band of the question") QuizQuestionDifficulty difficulty,
        @NotNull @Schema(description = "Lifecycle state of the item") QuizQuestionPoolItemState state,
        @NotNull @Schema(description = "The instructor's review decision") QuizQuestionReviewState reviewState,
        @Valid @Schema(description = "The generated question, present once generated") @Nullable GeneratedQuizQuestionDTO question,
        @Valid @Schema(description = "The filter's decision, present once judged") @Nullable QuizQuestionFilterDecisionDTO decision,
        @Schema(description = "Why the last attempt failed, present after a failure") @Nullable String failure,
        @Schema(description = "Model that generated the question") @Nullable String generatorModel,
        @Schema(description = "Model that judged the question") @Nullable String judgeModel, @NotNull @Schema(description = "When the item last changed") ZonedDateTime updatedAt) {
}
