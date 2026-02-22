package de.tum.cit.aet.artemis.hyperion.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Comparison result between suggested and declared difficulty.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum DifficultyDelta {

    LOWER, MATCH, HIGHER, UNKNOWN;
}
