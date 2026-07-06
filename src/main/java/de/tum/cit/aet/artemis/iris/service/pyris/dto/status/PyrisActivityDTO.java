package de.tum.cit.aet.artemis.iris.service.pyris.dto.status;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

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
public record PyrisActivityDTO(String id, PyrisActivityKind kind, String name, PyrisActivityState state, @Nullable String detail, @Nullable String result,
        @Nullable Long durationMillis) {
}
