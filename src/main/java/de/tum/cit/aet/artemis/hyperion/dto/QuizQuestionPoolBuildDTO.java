package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuild;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuildTrigger;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing one build of a course's quiz question pool.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "One build of a course's quiz question pool")
public record QuizQuestionPoolBuildDTO(@NotNull @Schema(description = "Id of the build") Long id,
        @NotNull @Schema(description = "What started the build") QuizQuestionPoolBuildTrigger trigger,
        @NotNull @Schema(description = "When the build started") ZonedDateTime startedAt,
        @Schema(description = "When the build ended, absent while it runs") @Nullable ZonedDateTime finishedAt,
        @Schema(description = "Items the build added to the pool") int enqueued, @Schema(description = "Items the build generated") int generated,
        @Schema(description = "Items the build judged") int judged, @Schema(description = "Judged items the filter accepted") int accepted,
        @Schema(description = "Judged items the filter rejected") int rejected, @Schema(description = "Items that failed permanently in this build") int failed,
        @Schema(description = "Why the build stopped early, absent when it ran through") @Nullable String failure) {

    /**
     * Converts a build to its DTO.
     *
     * @param build the build
     * @return the DTO
     */
    public static QuizQuestionPoolBuildDTO of(QuizQuestionPoolBuild build) {
        return new QuizQuestionPoolBuildDTO(build.getId(), build.getTrigger(), build.getStartedAt(), build.getFinishedAt(), build.getEnqueued(), build.getGenerated(),
                build.getJudged(), build.getAccepted(), build.getRejected(), build.getFailed(), build.getFailure());
    }
}
