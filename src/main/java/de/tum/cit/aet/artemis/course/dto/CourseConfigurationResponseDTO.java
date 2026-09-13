package de.tum.cit.aet.artemis.course.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;

/**
 * Course-level retention and auto-orchestration settings exposed to authorized course managers.
 *
 * @param id                            the configuration identifier
 * @param gradeRelevant                 whether the course is grade relevant
 * @param dataRetentionHold             whether automatic data cleanup is suspended
 * @param autoOrchestratorEnabled       whether Atlas auto-orchestration is enabled
 * @param debounceWindowSecondsOverride the optional course-specific debounce override
 * @param maxDailyOrchestrationOverride the optional course-specific daily limit
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseConfigurationResponseDTO(long id, boolean gradeRelevant, boolean dataRetentionHold, boolean autoOrchestratorEnabled,
        @Nullable Integer debounceWindowSecondsOverride, @Nullable Integer maxDailyOrchestrationOverride) {

    /**
     * Maps the public settings without exposing cleanup timestamps or the course back-reference.
     *
     * @param configuration the course configuration
     * @return the configuration response
     */
    public static CourseConfigurationResponseDTO of(CourseConfiguration configuration) {
        return new CourseConfigurationResponseDTO(configuration.getId(), configuration.isGradeRelevant(), configuration.isDataRetentionHold(),
                configuration.isAutoOrchestratorEnabled(), configuration.getDebounceWindowSecondsOverride(), configuration.getMaxDailyOrchestrationOverride());
    }
}
