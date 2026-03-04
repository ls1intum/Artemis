package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the condition under which a build phase should be executed.
 */
public enum BuildPhaseConditionDTO {

    @JsonProperty("ALWAYS")
    ALWAYS,

    @JsonProperty("AFTER_DUE_DATE")
    AFTER_DUE_DATE
}
