package de.tum.cit.aet.artemis.iris.service.pyris.dto.status;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Snapshot item describing one activity performed during a Pyris chat run.
 *
 * @param id             stable id within the run
 * @param kind           kind of activity
 * @param name           canonical activity name
 * @param state          current activity state
 * @param detail         optional curated input detail
 * @param result         optional curated result summary
 * @param durationMillis optional duration in milliseconds once the activity is terminal
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisActivityDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PyrisActivityKind kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PyrisActivityState state, @Nullable String detail,
        @Nullable String result, @Nullable Long durationMillis) {
}
