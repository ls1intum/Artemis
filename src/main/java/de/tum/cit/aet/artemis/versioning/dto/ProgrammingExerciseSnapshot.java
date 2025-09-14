package de.tum.cit.aet.artemis.versioning.dto;

import java.io.Serializable;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
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

public record ProgrammingExerciseSnapshot(String testRepositoryUri, List<AuxiliaryRepository> auxiliaryRepositories, Boolean allowOnlineEditor, Boolean allowOfflineIde,
        Boolean allowOnlineIde, Boolean staticCodeAnalysisEnabled, Integer maxStaticCodeAnalysisPenalty, ProgrammingLanguage programmingLanguage, String packageName,
        Boolean showTestNamesToStudents, ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, String projectKey,
        ProgrammingExerciseSnapshot.ParticipationData templateParticipation, ProgrammingExerciseSnapshot.ParticipationData solutionParticipation,
        Set<ProgrammingExerciseTestCaseDTO> testCases, List<ProgrammingExerciseSnapshot.ProgrammingExerciseTaskData> tasks,
        Set<ProgrammingExerciseSnapshot.StaticCodeAnalysisCategoryData> staticCodeAnalysisCategories, ProgrammingExerciseSnapshot.SubmissionPolicyData submissionPolicy,
        ProjectType projectType, Boolean releaseTestsWithExampleSolution, ProgrammingExerciseSnapshot.ProgrammingExerciseBuildConfigData buildConfig,
        // Derivative fields for versioning
        String testsCommitId, Map<String, String> auxiliaryCommitIds) implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseSnapshot.class);

    public static ProgrammingExerciseSnapshot of(ProgrammingExercise exercise, GitService gitService) {
        var templateParticipation = exercise.getTemplateParticipation() != null
                ? new ProgrammingExerciseSnapshot.ParticipationData(exercise.getTemplateParticipation().getId(), exercise.getTemplateRepositoryUri(),
                        exercise.getTemplateBuildPlanId(), getCommitHash(exercise.getVcsTemplateRepositoryUri(), gitService))
                : null;
        var solutionParticipation = exercise.getSolutionParticipation() != null
                ? new ProgrammingExerciseSnapshot.ParticipationData(exercise.getSolutionParticipation().getId(), exercise.getSolutionRepositoryUri(),
                        exercise.getSolutionBuildPlanId(), getCommitHash(exercise.getVcsSolutionRepositoryUri(), gitService))
                : null;
        var testCommitHash = getCommitHash(exercise.getVcsTestRepositoryUri(), gitService);
        var auxiliaryCommitHashes = new HashMap<String, String>();
        if (exercise.getAuxiliaryRepositories() != null) {
            for (AuxiliaryRepository auxiliaryRepository : exercise.getAuxiliaryRepositories()) {
                if (auxiliaryRepository.getVcsRepositoryUri() != null) {
                    var auxiliaryCommitHash = getCommitHash(auxiliaryRepository.getVcsRepositoryUri(), gitService);
                    if (auxiliaryCommitHash == null) {
                        continue;
                    }
                    auxiliaryCommitHashes.put(auxiliaryRepository.getId().toString(), auxiliaryCommitHash);
                }
            }
        }
        return new ProgrammingExerciseSnapshot(exercise.getTestRepositoryUri(), exercise.getAuxiliaryRepositories(), exercise.isAllowOnlineEditor(), exercise.isAllowOfflineIde(),
                exercise.isAllowOnlineIde(), exercise.isStaticCodeAnalysisEnabled(), exercise.getMaxStaticCodeAnalysisPenalty(), exercise.getProgrammingLanguage(),
                exercise.getPackageName(), exercise.getShowTestNamesToStudents(), toUtc(exercise.getBuildAndTestStudentSubmissionsAfterDueDate()), exercise.getProjectKey(),
                templateParticipation, solutionParticipation, exercise.getTestCases().stream().map(ProgrammingExerciseTestCaseDTO::of).collect(Collectors.toSet()),
                exercise.getTasks().stream().map(ProgrammingExerciseSnapshot.ProgrammingExerciseTaskData::of).collect(Collectors.toCollection(ArrayList::new)),
                exercise.getStaticCodeAnalysisCategories().stream().map(ProgrammingExerciseSnapshot.StaticCodeAnalysisCategoryData::of).collect(Collectors.toSet()),
                ProgrammingExerciseSnapshot.SubmissionPolicyData.of(exercise.getSubmissionPolicy()), exercise.getProjectType(), exercise.isReleaseTestsWithExampleSolution(),
                ProgrammingExerciseSnapshot.ProgrammingExerciseBuildConfigData.of(exercise.getBuildConfig()), testCommitHash,
                auxiliaryCommitHashes.isEmpty() ? null : auxiliaryCommitHashes

        );
    }

    public record ParticipationData(long id, String repositoryUri, String buildPlanId, String commitId) implements Serializable {

    }

    public record ProgrammingExerciseTaskData(long id, String taskName, Set<ProgrammingExerciseTestCaseDTO> testCases) implements Serializable {

        private static ProgrammingExerciseSnapshot.ProgrammingExerciseTaskData of(ProgrammingExerciseTask task) {
            return new ProgrammingExerciseSnapshot.ProgrammingExerciseTaskData(task.getId(), task.getTaskName(),
                    task.getTestCases().stream().map(ProgrammingExerciseTestCaseDTO::of).collect(Collectors.toSet()));
        }
    }

    public record StaticCodeAnalysisCategoryData(long id, String name, Double penalty, Double maxPenalty, CategoryState state) implements Serializable {

        private static ProgrammingExerciseSnapshot.StaticCodeAnalysisCategoryData of(StaticCodeAnalysisCategory category) {
            return new ProgrammingExerciseSnapshot.StaticCodeAnalysisCategoryData(category.getId(), category.getName(), category.getPenalty(), category.getMaxPenalty(),
                    category.getState());
        }
    }

    public record SubmissionPolicyData(long id, int submissionLimit, boolean active, Double exceedingPenalty, String type) implements Serializable {

        private static ProgrammingExerciseSnapshot.SubmissionPolicyData of(SubmissionPolicy policy) {
            if (policy == null) {
                return null;
            }
            return new ProgrammingExerciseSnapshot.SubmissionPolicyData(policy.getId(), policy.getSubmissionLimit(), policy.isActive(),
                    policy instanceof SubmissionPenaltyPolicy penaltyPolicy ? penaltyPolicy.getExceedingPenalty() : null, policy.getClass().getSimpleName());
        }
    }

    public record ProgrammingExerciseBuildConfigData(Boolean sequentialTestRuns, String branch, String buildPlanConfiguration, String buildScript,
            boolean checkoutSolutionRepository, String testCheckoutPath, String assignmentCheckoutPath, String solutionCheckoutPath, int timeoutSeconds, String dockerFlags,
            String theiaImage, boolean allowBranching, String branchRegex) implements Serializable {

        private static ProgrammingExerciseSnapshot.ProgrammingExerciseBuildConfigData of(ProgrammingExerciseBuildConfig buildConfig) {
            if (buildConfig == null) {
                return null;
            }
            return new ProgrammingExerciseSnapshot.ProgrammingExerciseBuildConfigData(buildConfig.hasSequentialTestRuns() ? buildConfig.hasSequentialTestRuns() : null,
                    buildConfig.getBranch(), buildConfig.getBuildPlanConfiguration(), buildConfig.getBuildScript(), buildConfig.getCheckoutSolutionRepository(),
                    buildConfig.getTestCheckoutPath(), buildConfig.getAssignmentCheckoutPath(), buildConfig.getSolutionCheckoutPath(), buildConfig.getTimeoutSeconds(),
                    buildConfig.getDockerFlags(), buildConfig.getTheiaImage(), buildConfig.isAllowBranching(), buildConfig.getBranchRegex());
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
        catch (EntityNotFoundException e) {
            log.warn("Could not retrieve the last commit hash for repoUri {} in ExerciseSnapshot", uri);
            return null;
        }
    }

    private static ZonedDateTime toUtc(ZonedDateTime zdt) {
        return zdt == null ? null : zdt.withZoneSameInstant(ZoneOffset.UTC);
    }
}
