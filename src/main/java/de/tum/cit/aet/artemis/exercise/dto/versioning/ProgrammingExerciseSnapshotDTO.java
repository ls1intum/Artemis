package de.tum.cit.aet.artemis.exercise.dto.versioning;

import java.io.Serializable;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.core.util.CollectionUtil;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseSnapshotDTO(String testRepositoryUri, List<AuxiliaryRepositorySnapshotDTO> auxiliaryRepositories, Boolean allowOnlineEditor,
        Boolean allowOfflineIde, Boolean allowOnlineIde, Boolean staticCodeAnalysisEnabled, Integer maxStaticCodeAnalysisPenalty, ProgrammingLanguage programmingLanguage,
        String packageName, Boolean showTestNamesToStudents, ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, String projectKey,
        ParticipationSnapshotDTO templateParticipation, ParticipationSnapshotDTO solutionParticipation, Set<ProgrammingExerciseTestCaseDTO> testCases,
        Set<ProgrammingExerciseTaskSnapshotDTO> tasks, Set<StaticCodeAnalysisCategorySnapshotDTO> staticCodeAnalysisCategories, SubmissionPolicySnapshotDTO submissionPolicy,
        ProjectType projectType, Boolean releaseTestsWithExampleSolution, ProgrammingExerciseBuildConfigSnapshotDTO buildConfig,
        // Derivative fields for versioning
        String testsCommitId) implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseSnapshotDTO.class);

    /**
     * Creates a snapshot of the given programming exercise.
     *
     * @param exercise   {@link ProgrammingExercise}
     * @param gitService {@link GitService}
     * @return {@link ProgrammingExerciseSnapshotDTO}
     */
    public static ProgrammingExerciseSnapshotDTO of(ProgrammingExercise exercise, GitService gitService) {
        var templateParticipation = exercise.getTemplateParticipation() != null ? new ParticipationSnapshotDTO(exercise.getTemplateParticipation().getId(),
                exercise.getTemplateRepositoryUri(), exercise.getTemplateBuildPlanId(), getCommitHash(exercise.getVcsTemplateRepositoryUri(), gitService)) : null;
        var solutionParticipation = exercise.getSolutionParticipation() != null ? new ParticipationSnapshotDTO(exercise.getSolutionParticipation().getId(),
                exercise.getSolutionRepositoryUri(), exercise.getSolutionBuildPlanId(), getCommitHash(exercise.getVcsSolutionRepositoryUri(), gitService)) : null;
        var testCommitHash = getCommitHash(exercise.getVcsTestRepositoryUri(), gitService);

        var auxiliaryRepositories = CollectionUtil.nullIfEmpty(exercise.getAuxiliaryRepositories());

        ArrayList<AuxiliaryRepositorySnapshotDTO> auxiliaryRepositoriesDTO = null;
        if (auxiliaryRepositories != null) {
            auxiliaryRepositoriesDTO = new ArrayList<>();
            for (AuxiliaryRepository repository : exercise.getAuxiliaryRepositories()) {
                if (repository.getVcsRepositoryUri() != null) {
                    var auxiliaryCommitHash = getCommitHash(repository.getVcsRepositoryUri(), gitService);
                    auxiliaryRepositoriesDTO.add(new AuxiliaryRepositorySnapshotDTO(repository.getId(), repository.getRepositoryUri(), auxiliaryCommitHash));
                }
            }
        }

        var analysisCategories = CollectionUtil
                .nullIfEmpty(exercise.getStaticCodeAnalysisCategories().stream().map(StaticCodeAnalysisCategorySnapshotDTO::of).collect(Collectors.toSet()));
        var tasks = CollectionUtil.nullIfEmpty(exercise.getTasks().stream().map(ProgrammingExerciseTaskSnapshotDTO::of).collect(Collectors.toSet()));
        var testCases = CollectionUtil.nullIfEmpty(exercise.getTestCases().stream().map(ProgrammingExerciseTestCaseDTO::of).collect(Collectors.toSet()));

        return new ProgrammingExerciseSnapshotDTO(exercise.getTestRepositoryUri(), auxiliaryRepositoriesDTO, exercise.isAllowOnlineEditor(), exercise.isAllowOfflineIde(),
                exercise.isAllowOnlineIde(), exercise.isStaticCodeAnalysisEnabled(), exercise.getMaxStaticCodeAnalysisPenalty(), exercise.getProgrammingLanguage(),
                exercise.getPackageName(), exercise.getShowTestNamesToStudents(), toUtc(exercise.getBuildAndTestStudentSubmissionsAfterDueDate()), exercise.getProjectKey(),
                templateParticipation, solutionParticipation, testCases, tasks, analysisCategories, SubmissionPolicySnapshotDTO.of(exercise.getSubmissionPolicy()),
                exercise.getProjectType(), exercise.isReleaseTestsWithExampleSolution(), ProgrammingExerciseBuildConfigSnapshotDTO.of(exercise.getBuildConfig()), testCommitHash);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AuxiliaryRepositorySnapshotDTO(long id, String repositoryUri, String commitId) implements Serializable {

    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationSnapshotDTO(long id, String repositoryUri, String buildPlanId, String commitId) implements Serializable {

    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProgrammingExerciseTaskSnapshotDTO(long id, String taskName, Set<ProgrammingExerciseTestCaseDTO> testCases) implements Serializable {

        private static ProgrammingExerciseTaskSnapshotDTO of(ProgrammingExerciseTask task) {
            var testCases = CollectionUtil.nullIfEmpty(task.getTestCases().stream().map(ProgrammingExerciseTestCaseDTO::of).collect(Collectors.toSet()));
            return new ProgrammingExerciseTaskSnapshotDTO(task.getId(), task.getTaskName(), testCases);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StaticCodeAnalysisCategorySnapshotDTO(long id, String name, Double penalty, Double maxPenalty, CategoryState state) implements Serializable {

        private static StaticCodeAnalysisCategorySnapshotDTO of(StaticCodeAnalysisCategory category) {
            return new StaticCodeAnalysisCategorySnapshotDTO(category.getId(), category.getName(), category.getPenalty(), category.getMaxPenalty(), category.getState());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record SubmissionPolicySnapshotDTO(long id, int submissionLimit, boolean active, Double exceedingPenalty, String type) implements Serializable {

        private static SubmissionPolicySnapshotDTO of(SubmissionPolicy policy) {
            if (policy == null) {
                return null;
            }
            return new SubmissionPolicySnapshotDTO(policy.getId(), policy.getSubmissionLimit(), policy.isActive(),
                    policy instanceof SubmissionPenaltyPolicy penaltyPolicy ? penaltyPolicy.getExceedingPenalty() : null, policy.getClass().getSimpleName());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProgrammingExerciseBuildConfigSnapshotDTO(Boolean sequentialTestRuns, String branch, String buildPlanConfiguration, String buildScript,
            boolean checkoutSolutionRepository, String testCheckoutPath, String assignmentCheckoutPath, String solutionCheckoutPath, int timeoutSeconds, String dockerFlags,
            String theiaImage, boolean allowBranching, String branchRegex) implements Serializable {

        private static ProgrammingExerciseBuildConfigSnapshotDTO of(ProgrammingExerciseBuildConfig buildConfig) {
            if (buildConfig == null) {
                return null;
            }
            return new ProgrammingExerciseBuildConfigSnapshotDTO(buildConfig.hasSequentialTestRuns() ? buildConfig.hasSequentialTestRuns() : null, buildConfig.getBranch(),
                    buildConfig.getBuildPlanConfiguration(), buildConfig.getBuildScript(), buildConfig.getCheckoutSolutionRepository(), buildConfig.getTestCheckoutPath(),
                    buildConfig.getAssignmentCheckoutPath(), buildConfig.getSolutionCheckoutPath(), buildConfig.getTimeoutSeconds(), buildConfig.getDockerFlags(),
                    buildConfig.getTheiaImage(), buildConfig.isAllowBranching(), buildConfig.getBranchRegex());
        }
    }

    private static String getCommitHash(LocalVCRepositoryUri uri, GitService gitService) {
        if (uri == null) {
            return null;
        }
        try {
            var commitHash = gitService.getLastCommitHash(uri);
            return commitHash == null ? null : commitHash.getName();
        }
        catch (Exception e) {
            log.warn("Could not retrieve the last commit hash for repoUri {} in ExerciseSnapshot", uri);
            return null;
        }
    }

    private static ZonedDateTime toUtc(ZonedDateTime zdt) {
        return zdt == null ? null : zdt.withZoneSameInstant(ZoneOffset.UTC);
    }
}
