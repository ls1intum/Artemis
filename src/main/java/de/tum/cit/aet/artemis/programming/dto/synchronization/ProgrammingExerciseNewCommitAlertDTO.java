package de.tum.cit.aet.artemis.programming.dto.synchronization;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.synchronization.ProgrammingExerciseEditorSyncTarget;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Notification for newly pushed commits.")
public record ProgrammingExerciseNewCommitAlertDTO(
        @Schema(description = "Event type discriminator.", requiredMode = Schema.RequiredMode.REQUIRED) ProgrammingExerciseEditorSyncEventType eventType,
        @Schema(description = "Synchronization target.", requiredMode = Schema.RequiredMode.REQUIRED) ProgrammingExerciseEditorSyncTarget target,
        @Schema(description = "Auxiliary repository id when target is auxiliary.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable Long auxiliaryRepositoryId,
        @Schema(description = "Client instance id.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String clientInstanceId,
        @Schema(description = "Event timestamp in milliseconds since epoch.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable Long timestamp) {
}
