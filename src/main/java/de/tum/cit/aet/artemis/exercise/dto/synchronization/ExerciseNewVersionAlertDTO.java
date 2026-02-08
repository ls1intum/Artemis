package de.tum.cit.aet.artemis.exercise.dto.synchronization;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;
import de.tum.cit.aet.artemis.exercise.domain.synchronization.ExerciseEditorSyncTarget;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Websocket payload notifying clients about a new exercise metadata version.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Alert/notification that a new exercise version was pushed.")
public record ExerciseNewVersionAlertDTO(@Schema(description = "Event type discriminator.", requiredMode = Schema.RequiredMode.REQUIRED) ExerciseEditorSyncEventType eventType,
        @Schema(description = "Synchronization target.", requiredMode = Schema.RequiredMode.REQUIRED) ExerciseEditorSyncTarget target,
        @Schema(description = "The id of the new exercise version.", requiredMode = Schema.RequiredMode.REQUIRED) Long exerciseVersionId,
        @Schema(description = "The author of the new version.", requiredMode = Schema.RequiredMode.REQUIRED) UserPublicInfoDTO author,
        @Schema(description = "Changed exercise fields compared to the previous version.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) Set<String> changedFields,
        @Schema(description = "The client session id of the sender.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String sessionId) {

}
