package de.tum.cit.aet.artemis.exercise.dto.synchronization;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.synchronization.ExerciseEditorSyncTarget;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Notification for newly pushed commits.")
public record ExerciseNewCommitAlertDTO(@Schema(description = "Event type discriminator.", requiredMode = Schema.RequiredMode.REQUIRED) ExerciseEditorSyncEventType eventType,
        @Schema(description = "Synchronization target.", requiredMode = Schema.RequiredMode.REQUIRED) ExerciseEditorSyncTarget target,
        @Schema(description = "Auxiliary repository id when target is auxiliary.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable Long auxiliaryRepositoryId,
        @Schema(description = "Client session id.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String sessionId,
        @Schema(description = "Event timestamp in milliseconds since epoch.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable Long timestamp) {
}
