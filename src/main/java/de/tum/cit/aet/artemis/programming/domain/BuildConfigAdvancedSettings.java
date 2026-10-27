package de.tum.cit.aet.artemis.programming.domain;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Rarely used settings of a {@link ProgrammingExerciseBuildConfig}, stored together as one JSON document in the {@code advanced_settings} column.
 * <p>
 * Every component is optional and a missing one means "default behaviour". New settings are added as further nullable components, which needs no schema change.
 * Unknown properties are ignored so that rows written by a newer version stay readable.
 *
 * @param branching       whether and which non-default branches are allowed, or null for the default (not allowed)
 * @param buildTimeWindow the period in which pushes are built, or null if pushes are always built
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildConfigAdvancedSettings(@Nullable BranchingSettings branching, @Nullable BuildTimeWindow buildTimeWindow) {

    /**
     * Whether no component deviates from the default, i.e. the settings carry no information.
     *
     * @return true if there is nothing to store
     */
    @JsonIgnore
    public boolean isEmpty() {
        return branching == null && buildTimeWindow == null;
    }

    /**
     * Brings the settings into their canonical form, so that there is exactly one representation of the default: components that carry the default are dropped
     * and settings without any remaining component become {@code null}, which is stored as SQL NULL.
     *
     * @param settings the settings to normalise, may be null
     * @return the normalised settings, or null if they do not deviate from the default
     */
    @Nullable
    public static BuildConfigAdvancedSettings normalized(@Nullable BuildConfigAdvancedSettings settings) {
        if (settings == null) {
            return null;
        }
        BranchingSettings branching = settings.branching() == null || settings.branching().isDefault() ? null : settings.branching();
        BuildConfigAdvancedSettings normalized = new BuildConfigAdvancedSettings(branching, BuildTimeWindow.normalized(settings.buildTimeWindow()));
        return normalized.isEmpty() ? null : normalized;
    }
}
