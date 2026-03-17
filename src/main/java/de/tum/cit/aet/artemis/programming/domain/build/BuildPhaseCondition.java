package de.tum.cit.aet.artemis.programming.domain.build;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the condition under which a build phase should be executed.
 */
public enum BuildPhaseCondition {

    @JsonProperty("ALWAYS")
    ALWAYS,

    @JsonProperty("AFTER_DUE_DATE")
    AFTER_DUE_DATE,

    @JsonProperty("AFTER_DUE_DATE")
    FORCE_RUN
}
