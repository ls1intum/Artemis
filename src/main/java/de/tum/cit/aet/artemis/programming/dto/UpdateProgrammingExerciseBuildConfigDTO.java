package de.tum.cit.aet.artemis.programming.dto;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;

/**
 * DTO for updating ProgrammingExerciseBuildConfig.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateProgrammingExerciseBuildConfigDTO(Long id, Boolean sequentialTestRuns, String branch, String buildPlanConfiguration, String buildScript,
        boolean checkoutSolutionRepository, String testCheckoutPath, String assignmentCheckoutPath, String solutionCheckoutPath, int timeoutSeconds, String dockerFlags,
        @Nullable String theiaImage, boolean allowBranching, @Nullable String branchRegex) {

    /**
     * Creates a DTO from a ProgrammingExerciseBuildConfig entity.
     *
     * @param buildConfig the ProgrammingExerciseBuildConfig entity to convert
     * @return a new UpdateProgrammingExerciseBuildConfigDTO with data from the entity, or null if entity is null
     */
    @Nullable
    public static UpdateProgrammingExerciseBuildConfigDTO of(@Nullable ProgrammingExerciseBuildConfig buildConfig) {
        if (buildConfig == null || !Hibernate.isInitialized(buildConfig)) {
            return null;
        }
        return new UpdateProgrammingExerciseBuildConfigDTO(buildConfig.getId(), buildConfig.hasSequentialTestRuns(), buildConfig.getBranch(),
                buildConfig.getBuildPlanConfiguration(), buildConfig.getBuildScript(), buildConfig.getCheckoutSolutionRepository(), buildConfig.getTestCheckoutPath(),
                buildConfig.getAssignmentCheckoutPath(), buildConfig.getSolutionCheckoutPath(), buildConfig.getTimeoutSeconds(), buildConfig.getDockerFlags(),
                buildConfig.getTheiaImage(), buildConfig.isAllowBranching(), buildConfig.getBranchRegex());
    }
}
