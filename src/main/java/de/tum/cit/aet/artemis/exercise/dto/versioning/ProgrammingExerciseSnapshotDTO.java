package de.tum.cit.aet.artemis.exercise.dto.versioning;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseSnapshotDTO(String testRepositoryUri, List<AuxiliaryRepositorySnapshotDTO> auxiliaryRepositories, Boolean allowOnlineEditor,
        Boolean allowOfflineIde, Boolean allowOnlineIde, Boolean staticCodeAnalysisEnabled, Integer maxStaticCodeAnalysisPenalty, ProgrammingLanguage programmingLanguage,
        String packageName, Boolean showTestNamesToStudents, ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, String projectKey,
        ParticipationSnapshotDTO templateParticipation, ParticipationSnapshotDTO solutionParticipation, Set<ProgrammingExerciseTestCaseDTO> testCases,
        Set<ProgrammingExerciseTaskSnapshotDTO> tasks, Set<StaticCodeAnalysisCategorySnapshotDTO> staticCodeAnalysisCategories, SubmissionPolicySnapshotDTO submissionPolicy,
        ProjectType projectType, Boolean releaseTestsWithExampleSolution, ProgrammingExerciseBuildConfigSnapshotDTO buildConfig,
        // Derivative fields for versioning
        String testsCommitId) implements Serializable {

    /**
     * Carrier for the git commit hashes of a programming exercise's repositories. These are resolved by the service
     * layer (which owns the {@code GitService}) and passed in, so this DTO stays a pure data mapper without any
     * dependency on {@code GitService}.
     *
     * @param templateCommitHash              commit hash of the template repository (may be {@code null})
     * @param solutionCommitHash              commit hash of the solution repository (may be {@code null})
     * @param testsCommitHash                 commit hash of the tests repository (may be {@code null})
     * @param auxiliaryRepositoryCommitHashes commit hash per auxiliary repository, keyed by auxiliary repository id
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CommitHashesDTO(String templateCommitHash, String solutionCommitHash, String testsCommitHash, Map<Long, String> auxiliaryRepositoryCommitHashes) {
    }

    /**
     * Creates a snapshot of the given programming exercise.
     *
     * @param exercise     {@link ProgrammingExercise}
     * @param commitHashes the pre-resolved git commit hashes of the exercise's repositories
     * @return {@link ProgrammingExerciseSnapshotDTO}
     */
    public static ProgrammingExerciseSnapshotDTO of(ProgrammingExercise exercise, CommitHashesDTO commitHashes) {
        var templateParticipation = exercise.getTemplateParticipation() != null
                ? new ParticipationSnapshotDTO(exercise.getTemplateParticipation().getId(), exercise.getTemplateRepositoryUri(), exercise.getTemplateBuildPlanId(),
                        commitHashes.templateCommitHash())
                : null;
        var solutionParticipation = exercise.getSolutionParticipation() != null
                ? new ParticipationSnapshotDTO(exercise.getSolutionParticipation().getId(), exercise.getSolutionRepositoryUri(), exercise.getSolutionBuildPlanId(),
                        commitHashes.solutionCommitHash())
                : null;
        var testCommitHash = commitHashes.testsCommitHash();

        var auxiliaryRepositories = CollectionUtil.nullIfEmpty(exercise.getAuxiliaryRepositories());

        ArrayList<AuxiliaryRepositorySnapshotDTO> auxiliaryRepositoriesDTO = null;
        if (auxiliaryRepositories != null) {
            auxiliaryRepositoriesDTO = new ArrayList<>();
            for (AuxiliaryRepository repository : exercise.getAuxiliaryRepositories()) {
                var auxiliaryCommitHash = commitHashes.auxiliaryRepositoryCommitHashes().get(repository.getId());
                auxiliaryRepositoriesDTO.add(new AuxiliaryRepositorySnapshotDTO(repository.getId(), repository.getName(), repository.getCheckoutDirectory(),
                        repository.getDescription(), repository.getRepositoryUri(), auxiliaryCommitHash));
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
    public record AuxiliaryRepositorySnapshotDTO(long id, String name, String checkoutDirectory, String description, String repositoryUri, String commitId)
            implements Serializable {

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

    private static ZonedDateTime toUtc(ZonedDateTime zdt) {
        return ExerciseSnapshotDTO.toUtc(zdt);
    }
}
