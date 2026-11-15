package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;
import io.swagger.v3.oas.annotations.media.Schema;

/** Durable provenance for one authorized owner's run; never includes prompts, repository files, or version snapshots. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AuthoringRunDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String jobId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long exerciseId,
        Long sourceExerciseId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long courseId, String exerciseTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AuthoringRun.Kind kind, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AuthoringRun.Status status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean running, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startedAt, Instant finishedAt,
        Long beforeVersionId, Long afterVersionId, Instant revertedAt) {
}
