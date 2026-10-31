package de.tum.cit.aet.artemis.programming.dto;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.BuildTimeWindow;
import de.tum.cit.aet.artemis.programming.domain.OnlineIdeExperienceLevel;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;

/**
 * DTO for updating ProgrammingExerciseBuildConfig.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateProgrammingExerciseBuildConfigDTO(Long id, Boolean sequentialTestRuns, String branch, String buildPlanConfiguration, String buildScript,
        boolean checkoutSolutionRepository, String testCheckoutPath, String assignmentCheckoutPath, String solutionCheckoutPath, int timeoutSeconds, String dockerFlags,
        @Nullable String theiaImage, @Nullable OnlineIdeExperienceLevel onlineIdeExperienceLevel, boolean allowBranching, @Nullable String branchRegex,
        @Nullable BuildConfigAdvancedSettingsDTO advancedSettings) {

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
                buildConfig.getTheiaImage(), buildConfig.getOnlineIdeExperienceLevel(), buildConfig.isAllowBranching(), buildConfig.getBranchRegex(),
                BuildConfigAdvancedSettingsDTO.of(buildConfig.getBuildTimeWindow()));
    }

    /**
     * Creates the part of the build configuration that students may read: only the build time window, so the notice about
     * builds that are not run can be shown. Everything else, such as the build plan or the Docker flags, stays with the
     * instructors; the other components carry their defaults and are meaningless.
     *
     * @param buildTimeWindow the window of the exercise, may be null
     * @return a build configuration carrying only the window, or null if there is no window to tell students about
     */
    @Nullable
    public static UpdateProgrammingExerciseBuildConfigDTO ofBuildTimeWindow(@Nullable BuildTimeWindow buildTimeWindow) {
        BuildConfigAdvancedSettingsDTO advancedSettings = BuildConfigAdvancedSettingsDTO.of(buildTimeWindow);
        if (advancedSettings == null) {
            return null;
        }
        return new UpdateProgrammingExerciseBuildConfigDTO(null, null, null, null, null, false, null, null, null, 0, null, null, null, false, null, advancedSettings);
    }

    /**
     * Returns the same build configuration without the row id, for payloads that are written to a file and read back
     * by another instance, whose import copies the id onto the configuration it creates.
     *
     * @return a copy of this DTO with a {@code null} id
     */
    public UpdateProgrammingExerciseBuildConfigDTO withoutId() {
        return new UpdateProgrammingExerciseBuildConfigDTO(null, sequentialTestRuns, branch, buildPlanConfiguration, buildScript, checkoutSolutionRepository, testCheckoutPath,
                assignmentCheckoutPath, solutionCheckoutPath, timeoutSeconds, dockerFlags, theiaImage, onlineIdeExperienceLevel, allowBranching, branchRegex, advancedSettings);
    }

    /**
     * Builds a transient {@link ProgrammingExerciseBuildConfig} from this DTO, copying the id through so an existing
     * configuration keeps its identity. The back-reference to the programming exercise is never set.
     *
     * @return the build configuration entity described by this DTO
     */
    public ProgrammingExerciseBuildConfig toEntity() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setId(id);
        buildConfig.setSequentialTestRuns(sequentialTestRuns);
        buildConfig.setBranch(branch);
        buildConfig.setBuildPlanConfiguration(buildPlanConfiguration);
        buildConfig.setBuildScript(buildScript);
        buildConfig.setCheckoutSolutionRepository(checkoutSolutionRepository);
        buildConfig.setTestCheckoutPath(testCheckoutPath);
        buildConfig.setAssignmentCheckoutPath(assignmentCheckoutPath);
        buildConfig.setSolutionCheckoutPath(solutionCheckoutPath);
        buildConfig.setTimeoutSeconds(timeoutSeconds);
        buildConfig.setDockerFlags(dockerFlags);
        buildConfig.setTheiaImage(theiaImage);
        buildConfig.setOnlineIdeExperienceLevel(onlineIdeExperienceLevel);
        buildConfig.setAllowBranching(allowBranching);
        buildConfig.setBranchRegex(branchRegex);
        buildConfig.setBuildTimeWindow(advancedSettings != null ? advancedSettings.buildTimeWindow() : null);
        return buildConfig;
    }
}
