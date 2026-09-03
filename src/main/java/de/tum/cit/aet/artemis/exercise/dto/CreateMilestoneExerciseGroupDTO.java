package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseBuildConfigDTO;

/**
 * Payload for creating a {@link MilestoneExerciseGroup}. A milestone group is never created empty: these are the
 * settings of the {@link MilestoneExercise} that will be provisioned as its anchor (repositories, build plan, the works),
 * so this is a programming-exercise configuration minus everything a milestone never has.
 * <p>
 * Notably absent: points and assessment settings (a milestone's points are always the sum of its user stories' points,
 * kept in sync by {@code MilestoneExerciseService}), the owning course (taken from the request path), and the group's
 * own title (taken from the exercise's).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateMilestoneExerciseGroupDTO(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String shortName, @Nullable String problemStatement,
        @Nullable String channelName, ProgrammingLanguage programmingLanguage, @Nullable ProjectType projectType, @Nullable String packageName, @Nullable Boolean allowOnlineEditor,
        @Nullable Boolean allowOfflineIde, boolean allowOnlineIde, @Nullable Boolean staticCodeAnalysisEnabled, @Nullable Integer maxStaticCodeAnalysisPenalty,
        @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate,
        @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable UpdateProgrammingExerciseBuildConfigDTO buildConfig) {

    /**
     * Builds the (still transient) milestone exercise this payload describes. The course, project key and everything the
     * creation pipeline derives are set by the caller; the fields fixed for every milestone are applied here.
     *
     * @return the milestone exercise to hand to the programming-exercise creation pipeline
     */
    public MilestoneExercise toMilestoneExercise() {
        MilestoneExercise exercise = new MilestoneExercise();
        exercise.setTitle(title);
        exercise.setShortName(shortName);
        exercise.setProblemStatement(problemStatement);
        exercise.setChannelName(channelName);
        exercise.setProgrammingLanguage(programmingLanguage);
        exercise.setProjectType(projectType);
        exercise.setPackageName(packageName);
        exercise.setAllowOnlineEditor(allowOnlineEditor);
        exercise.setAllowOfflineIde(allowOfflineIde);
        exercise.setAllowOnlineIde(allowOnlineIde);
        exercise.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);
        exercise.setMaxStaticCodeAnalysisPenalty(maxStaticCodeAnalysisPenalty);
        exercise.setReleaseDate(releaseDate);
        exercise.setStartDate(startDate);
        exercise.setDueDate(dueDate);
        exercise.setAssessmentDueDate(assessmentDueDate);
        exercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(0.0);
        exercise.setBonusPoints(0.0);
        exercise.setBuildConfig(toBuildConfig());
        return exercise;
    }

    /**
     * The build config to provision alongside the exercise. Always non-null: the creation pipeline validates against it
     * unconditionally, so an omitted one becomes a default config rather than a {@code NullPointerException}.
     */
    private ProgrammingExerciseBuildConfig toBuildConfig() {
        ProgrammingExerciseBuildConfig config = new ProgrammingExerciseBuildConfig();
        if (buildConfig == null) {
            return config;
        }
        config.setSequentialTestRuns(buildConfig.sequentialTestRuns());
        config.setBranch(buildConfig.branch());
        config.setBuildPlanConfiguration(buildConfig.buildPlanConfiguration());
        config.setBuildScript(buildConfig.buildScript());
        config.setCheckoutSolutionRepository(buildConfig.checkoutSolutionRepository());
        config.setTestCheckoutPath(buildConfig.testCheckoutPath());
        config.setAssignmentCheckoutPath(buildConfig.assignmentCheckoutPath());
        config.setSolutionCheckoutPath(buildConfig.solutionCheckoutPath());
        config.setTimeoutSeconds(buildConfig.timeoutSeconds());
        config.setDockerFlags(buildConfig.dockerFlags());
        config.setTheiaImage(buildConfig.theiaImage());
        config.setAllowBranching(buildConfig.allowBranching());
        config.setBranchRegex(buildConfig.branchRegex());
        return config;
    }
}
