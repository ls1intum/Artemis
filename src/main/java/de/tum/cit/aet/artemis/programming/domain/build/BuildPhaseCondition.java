package de.tum.cit.aet.artemis.programming.domain.build;

/**
 * Represents the condition under which a build phase should be executed.
 * Note: Serialized to JSON, be careful with renaming values.
 */
public enum BuildPhaseCondition {
    ALWAYS,
    AFTER_DUE_DATE
}
