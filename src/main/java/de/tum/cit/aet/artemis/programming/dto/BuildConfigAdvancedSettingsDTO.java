package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.BuildTimeWindow;

/**
 * Rarely used build settings of a programming exercise, nested in {@link UpdateProgrammingExerciseBuildConfigDTO}. New settings are added as further nullable
 * components, so the shape of the build configuration does not change for every new option.
 * <p>
 * Branching is deliberately not part of it: {@code allowBranching} and {@code branchRegex} stay flat on {@link UpdateProgrammingExerciseBuildConfigDTO}, so
 * exercises exported by older versions can still be imported.
 *
 * @param buildTimeWindow the period in which pushes to student repositories are built, null if they are always built
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildConfigAdvancedSettingsDTO(@Nullable BuildTimeWindow buildTimeWindow) implements Serializable {

    /**
     * Creates the DTO for the given window.
     *
     * @param buildTimeWindow the window of the build configuration, may be null
     * @return the DTO, or null if there is no window and therefore nothing to transfer
     */
    @Nullable
    public static BuildConfigAdvancedSettingsDTO of(@Nullable BuildTimeWindow buildTimeWindow) {
        BuildTimeWindow window = BuildTimeWindow.normalized(buildTimeWindow);
        return window == null ? null : new BuildConfigAdvancedSettingsDTO(window);
    }
}
