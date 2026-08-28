package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localci.service.ci.SharedBuildTriggerData;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildStatistics;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildStatisticsRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseBuildConfigService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeature;

/**
 * Service for triggering builds on the local CI system.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCITriggerService implements ContinuousIntegrationTriggerService {

    public static final int PRIORITY_ALL_BUILDS = 4;

    public static final int PRIORITY_OPTIONAL_EXERCISE = 3;

    public static final int PRIORITY_PRACTICE = 3;

    public static final int PRIORITY_NORMAL = 2;

    public static final int PRIORITY_EXAM_CONDUCTION = 1;

    public static final int TESTCOURSE_PRIORITY_PENALTY = 5;

    private static final Logger log = LoggerFactory.getLogger(LocalCITriggerService.class);

    private final DistributedDataAccessService distributedDataAccessService;

    private final BuildPhasesTemplateService buildPhasesTemplateService;

    private final BuildScriptProviderService buildScriptProviderService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final LocalCIProgrammingLanguageFeatureService programmingLanguageFeatureService;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final LocalCIBuildConfigurationService localCIBuildConfigurationService;

    private final LegacyBuildPlanConverterService legacyBuildPlanConverterService;

    private final GitService gitService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final ProgrammingExerciseBuildStatisticsRepository programmingExerciseBuildStatisticsRepository;

    private final ExerciseDateService exerciseDateService;

    private final ProgrammingExerciseBuildConfigService programmingExerciseBuildConfigService;

    private final BuildJobRepository buildJobRepository;

    private final BuildPhaseEvaluationService buildPhaseEvaluationService;

    private final BuildJobCloneTokenService buildJobCloneTokenService;

    private static final int DEFAULT_BUILD_DURATION = 17;

    // Arbitrary value to ensure that the build duration is always a bit higher than the actual build duration
    private static final double BUILD_DURATION_SAFETY_FACTOR = 1.1;

    /**
     * Only report the stage breakdown of a build trigger when it took at least this long, so a healthy system stays quiet.
     */
    private static final long SLOW_TRIGGER_LOG_THRESHOLD_MILLIS = 200;

    public LocalCITriggerService(DistributedDataAccessService distributedDataAccessService, BuildPhasesTemplateService buildPhasesTemplateService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, LocalCIProgrammingLanguageFeatureService programmingLanguageFeatureService, GitService gitService,
            ExerciseDateService exerciseDateService, SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            LocalCIBuildConfigurationService localCIBuildConfigurationService, LegacyBuildPlanConverterService legacyBuildPlanConverterService,
            ProgrammingExerciseBuildStatisticsRepository programmingExerciseBuildStatisticsRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, BuildScriptProviderService buildScriptProviderService,
            ProgrammingExerciseBuildConfigService programmingExerciseBuildConfigService, BuildJobRepository buildJobRepository,
            BuildPhaseEvaluationService buildPhaseEvaluationService, BuildJobCloneTokenService buildJobCloneTokenService) {
        this.buildJobCloneTokenService = buildJobCloneTokenService;
        this.distributedDataAccessService = distributedDataAccessService;
        this.buildPhasesTemplateService = buildPhasesTemplateService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.localCIBuildConfigurationService = localCIBuildConfigurationService;
        this.legacyBuildPlanConverterService = legacyBuildPlanConverterService;
        this.gitService = gitService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.exerciseDateService = exerciseDateService;
        this.buildScriptProviderService = buildScriptProviderService;
        this.programmingExerciseBuildConfigService = programmingExerciseBuildConfigService;
        this.programmingExerciseBuildStatisticsRepository = programmingExerciseBuildStatisticsRepository;
        this.buildJobRepository = buildJobRepository;
        this.buildPhaseEvaluationService = buildPhaseEvaluationService;
    }

    /**
     * Add a new build job to the queue managed by the ExecutorService and process the returned result.
     *
     * @param participation the participation of the repository which should be built and tested.
     * @param triggerAll    true if this build was triggered as part of a trigger all request. Currently only used for Local CI.
     * @throws LocalCIException if the build job could not be added to the queue.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, boolean triggerAll) throws LocalCIException {
        triggerBuild(participation, null, null, triggerAll, 0, SharedBuildTriggerData.NONE);
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, boolean triggerAll, SharedBuildTriggerData sharedData) throws LocalCIException {
        triggerBuild(participation, null, null, triggerAll, 0, sharedData);
    }

    /**
     * Resolves the head commit of the exercise's test repository and the exercise's build statistics, which every
     * participation of this exercise would otherwise resolve for itself.
     *
     * @param exercise the exercise whose participations are about to be triggered
     * @return the inputs shared by every participation of that exercise
     */
    @Override
    public SharedBuildTriggerData prepareSharedTriggerData(ProgrammingExercise exercise) {
        return SharedBuildTriggerData.of(getCommitHashOrNull(exercise.getVcsTestRepositoryUri(), "test repository"), loadBuildStatistics(exercise));
    }

    /**
     * Add a new build job item containing all relevant information necessary for the execution to the distributed build job queue.
     *
     * @param participation     the participation of the repository which should be built and tested
     * @param commitHashToBuild the commit hash of the commit that triggers the build. If it is null, the latest commit of the default branch will be built.
     * @param triggeredByPushTo type of the repository that was pushed to and triggered the build job
     * @throws LocalCIException if the build job could not be added to the queue.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHashToBuild, RepositoryType triggeredByPushTo) throws LocalCIException {
        triggerBuild(participation, commitHashToBuild, triggeredByPushTo, false, 0, SharedBuildTriggerData.NONE);
    }

    public void retryBuildJob(BuildJob buildJob, ProgrammingExerciseParticipation participation) throws LocalCIException {
        log.info("Retrying build for missing build job with id {} (retry count: {})", buildJob.getBuildJobId(), buildJob.getRetryCount() + 1);
        triggerBuild(participation, buildJob.getCommitHash(), buildJob.getTriggeredByPushTo(), buildJob.getRetryCount() + 1);
    }

    /**
     * Add a new build job item containing all relevant information necessary for the execution to the distributed build job queue.
     *
     * @param participation     the participation of the repository which should be built and tested
     * @param commitHashToBuild the commit hash of the commit that triggers the build. If it is null, the latest commit of the default branch will be built.
     * @param triggeredByPushTo type of the repository that was pushed to and triggered the build job
     * @param retryCount        how often the build has been retried after it went missing
     * @throws LocalCIException if the build job could not be added to the queue.
     */
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHashToBuild, RepositoryType triggeredByPushTo, int retryCount) throws LocalCIException {
        triggerBuild(participation, commitHashToBuild, triggeredByPushTo, false, retryCount, SharedBuildTriggerData.NONE);
    }

    private void triggerBuild(ProgrammingExerciseParticipation participation, String commitHashToBuild, RepositoryType triggeredByPushTo, boolean triggerAll, int retryCount,
            SharedBuildTriggerData sharedData) throws LocalCIException {

        log.info("Triggering build for participation {} and commit hash {}", participation.getId(), commitHashToBuild);

        long stageStart = System.nanoTime();

        // Commit hash related to the repository that will be tested
        String assignmentCommitHash;

        // Commit hash related to the test repository
        String testCommitHash;

        if (triggeredByPushTo == null || triggeredByPushTo.equals(RepositoryType.AUXILIARY)) {
            assignmentCommitHash = getCommitHashOrNull(participation.getVcsRepositoryUri(), "assignment repository");
            testCommitHash = testCommitHashFrom(sharedData, participation);
        }
        else if (triggeredByPushTo.equals(RepositoryType.TESTS)) {
            assignmentCommitHash = getCommitHashOrNull(participation.getVcsRepositoryUri(), "assignment repository");
            if (commitHashToBuild == null) {
                commitHashToBuild = testCommitHashFrom(sharedData, participation);
            }
            testCommitHash = commitHashToBuild;
        }
        else {
            assignmentCommitHash = commitHashToBuild;
            testCommitHash = testCommitHashFrom(sharedData, participation);
        }

        // If we couldn't retrieve commit hashes, skip the build - there's nothing to build yet
        if (assignmentCommitHash == null || testCommitHash == null) {
            log.info("Skipping build for participation {} - commit hashes not available yet", participation.getId());
            return;
        }

        long commitHashNanos = System.nanoTime() - stageStart;
        stageStart = System.nanoTime();

        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();

        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        // Exam exercises have highest priority, Exercises with due date in the past have lowest priority
        int priority = determinePriority(programmingExercise, participation, triggerAll);
        priority = addPenaltyIfTestCourse(programmingExercise, priority);

        ZonedDateTime submissionDate = ZonedDateTime.now();

        String buildJobId = String.valueOf(participation.getId()) + submissionDate.toInstant().toEpochMilli();

        var programmingExerciseBuildConfig = loadBuildConfig(programmingExercise);

        var buildStatistics = sharedData.resolved() ? sharedData.buildStatistics() : loadBuildStatistics(programmingExercise);

        long estimatedDuration = (buildStatistics != null && buildStatistics.getBuildDurationSeconds() > 0) ? buildStatistics.getBuildDurationSeconds() : DEFAULT_BUILD_DURATION;
        estimatedDuration = Math.round(estimatedDuration * BUILD_DURATION_SAFETY_FACTOR);

        JobTimingInfo jobTimingInfo = new JobTimingInfo(submissionDate, null, null, null, estimatedDuration);

        RepositoryInfo repositoryInfo = getRepositoryInfo(participation, triggeredByPushTo, programmingExerciseBuildConfig);

        BuildConfig buildConfig = getBuildConfig(participation, commitHashToBuild, assignmentCommitHash, testCommitHash, programmingExerciseBuildConfig);

        BuildAgentDTO buildAgent = new BuildAgentDTO(null, null, null);

        // The credential the agent that claims this job will clone with. Scoped to this job's repositories and valid
        // only while the job is in the processing list, so it replaces the installation-wide build agent password
        // rather than adding to it.
        String cloneToken = buildJobCloneTokenService.generateCloneToken();

        BuildJobQueueItem buildJobQueueItem = new BuildJobQueueItem(buildJobId, participation.getBuildPlanId(), buildAgent, participation.getId(), courseId,
                programmingExercise.getId(), retryCount, priority, null, repositoryInfo, jobTimingInfo, buildConfig, null, cloneToken);

        long buildJobDataNanos = System.nanoTime() - stageStart;
        stageStart = System.nanoTime();

        // Save the build job before adding it to the queue to ensure it exists in the database.
        // This prevents potential race conditions where a build agent pulls the job from the queue very quickly before it is persisted,
        // leading to a failed update operation due to a missing record.
        buildJobRepository.save(new BuildJob(buildJobQueueItem, BuildStatus.QUEUED, null));

        long persistNanos = System.nanoTime() - stageStart;
        stageStart = System.nanoTime();

        distributedDataAccessService.getDistributedBuildJobQueue().add(buildJobQueueItem);

        long enqueueNanos = System.nanoTime() - stageStart;
        // Queueing a build was measured as effectively the whole latency of a git push under exam load, while each
        // individual step is a few milliseconds when uncontended. Report the breakdown when a call is slow, so a
        // regression can be attributed to a step rather than guessed at.
        long totalMillis = (commitHashNanos + buildJobDataNanos + persistNanos + enqueueNanos) / 1_000_000;
        if (totalMillis >= SLOW_TRIGGER_LOG_THRESHOLD_MILLIS) {
            log.info("Slow build trigger for participation {}: {} ms total (commit hashes {} ms, build job data {} ms, persist {} ms, enqueue {} ms)", participation.getId(),
                    totalMillis, commitHashNanos / 1_000_000, buildJobDataNanos / 1_000_000, persistNanos / 1_000_000, enqueueNanos / 1_000_000);
        }
        log.info("Added build job {} for exercise {} and participation {} with priority {} to the queue", buildJobId, programmingExercise.getShortName(), participation.getId(),
                priority);

        distributedDataAccessService.getDistributedDockerImageCleanupInfo().put(buildConfig.dockerImage(), jobTimingInfo.submissionDate());
    }

    // -------Helper methods for triggerBuild()-------

    /**
     * Collects all necessary information regarding the repositories involved in the build job processing.
     *
     * @param participation     the participation for which to get the repository information
     * @param triggeredByPushTo type of the repository that was pushed to and triggered the build job
     * @return the repository information for the given participation
     */
    private RepositoryInfo getRepositoryInfo(ProgrammingExerciseParticipation participation, RepositoryType triggeredByPushTo, ProgrammingExerciseBuildConfig buildConfig) {

        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();

        List<AuxiliaryRepository> auxiliaryRepositories;

        // If the auxiliary repositories are not initialized, we need to fetch them from the database.
        if (Hibernate.isInitialized(participation.getProgrammingExercise().getAuxiliaryRepositories())) {
            auxiliaryRepositories = programmingExercise.getAuxiliaryRepositories();
        }
        else {
            auxiliaryRepositories = auxiliaryRepositoryRepository.findByExerciseId(participation.getProgrammingExercise().getId());
        }

        String assignmentRepositoryUri = participation.getRepositoryUri();
        String testRepositoryUri = programmingExercise.getTestRepositoryUri();
        String solutionRepositoryUri = null;
        String[] auxiliaryRepositoryUris = auxiliaryRepositories.stream().map(AuxiliaryRepository::getRepositoryUri).toArray(String[]::new);
        String[] auxiliaryRepositoryCheckoutDirectories1 = auxiliaryRepositories.stream().map(AuxiliaryRepository::getCheckoutDirectory).toArray(String[]::new);

        if (buildConfig.getCheckoutSolutionRepository()) {
            ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.getProgrammingLanguageFeatures(programmingExercise.getProgrammingLanguage());
            if (programmingLanguageFeature.checkoutSolutionRepositoryAllowed()) {
                var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(participation.getProgrammingExercise().getId());
                if (solutionParticipation.isPresent()) {
                    solutionRepositoryUri = solutionParticipation.get().getRepositoryUri();
                }
            }
        }

        String repositoryTypeOrUserName = participation.getVcsRepositoryUri().repositoryNameWithoutProjectKey();

        String repositoryName = participation.getVcsRepositoryUri().repositorySlug();

        RepositoryType repositoryType;
        // Only template, solution and user repositories are build
        if (repositoryTypeOrUserName.equals("exercise")) {
            repositoryType = RepositoryType.TEMPLATE;
        }
        else if (repositoryTypeOrUserName.equals("solution")) {
            repositoryType = RepositoryType.SOLUTION;
        }
        else {
            repositoryType = RepositoryType.USER;
        }

        // if the build is not triggered by a push to the test or an auxiliary repository, it was triggered by a push to its own repository
        if (triggeredByPushTo == null) {
            triggeredByPushTo = repositoryType;
        }

        return new RepositoryInfo(repositoryName, repositoryType, triggeredByPushTo, assignmentRepositoryUri, testRepositoryUri, solutionRepositoryUri, auxiliaryRepositoryUris,
                auxiliaryRepositoryCheckoutDirectories1);

    }

    private BuildConfig getBuildConfig(ProgrammingExerciseParticipation participation, String commitHashToBuild, String assignmentCommitHash, String testCommitHash,
            ProgrammingExerciseBuildConfig buildConfig) throws LocalCIException {
        String branch = participation instanceof ProgrammingExerciseStudentParticipation studentParticipation ? studentParticipation.getBranch() : buildConfig.getBranch();
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        ProgrammingLanguage programmingLanguage = programmingExercise.getProgrammingLanguage();
        ProjectType projectType = programmingExercise.getProjectType();
        boolean staticCodeAnalysisEnabled = programmingExercise.isStaticCodeAnalysisEnabled();
        boolean sequentialTestRunsEnabled = buildConfig.hasSequentialTestRuns();

        DockerRunConfig dockerRunConfig = programmingExerciseBuildConfigService.getDockerRunConfig(buildConfig);

        programmingExercise.setBuildConfig(buildConfig);
        BuildPlanPhasesDTO buildPlanPhasesDTO;
        try {
            buildPlanPhasesDTO = BuildPlanPhasesDTO.fromBuildPlanConfiguration(buildConfig.getBuildPlanConfiguration());
        }
        catch (JsonProcessingException e) {
            throw new LocalCIException("The build plan configuration is invalid for build config " + buildConfig.getId(), e);
        }

        final List<BuildPhaseDTO> phases = buildPlanPhasesDTO.phases() == null ? buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(programmingExercise)
                : buildPlanPhasesDTO.phases();
        final String dockerImage = buildPlanPhasesDTO.dockerImage() == null ? buildPhasesTemplateService.getDefaultDockerImageFor(programmingExercise)
                : buildPlanPhasesDTO.dockerImage();

        final List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        final Set<String> resultPathsSet = BuildPhaseEvaluationService.gatherResultPaths(activePhases);
        final List<String> resultPaths = finalizeResultPaths(buildConfig, resultPathsSet.stream());

        final String buildScript = localCIBuildConfigurationService.createBuildScriptFromActivePhases(programmingExercise.getBuildConfig(), activePhases);

        return new BuildConfig(buildScript, dockerImage, commitHashToBuild, assignmentCommitHash, testCommitHash, branch, programmingLanguage, projectType,
                staticCodeAnalysisEnabled, sequentialTestRunsEnabled, resultPaths, buildConfig.getTimeoutSeconds(), buildConfig.getAssignmentCheckoutPath(),
                buildConfig.getTestCheckoutPath(), buildConfig.getSolutionCheckoutPath(), dockerRunConfig);
    }

    private List<String> finalizeResultPaths(final ProgrammingExerciseBuildConfig buildConfig, final Stream<String> resultPaths) {
        List<String> resultPathsList = resultPaths.map(path -> LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/" + path).toList();
        resultPathsList = buildScriptProviderService.replaceResultPathsPlaceholders(resultPathsList, buildConfig);
        return resultPathsList;
    }

    private ProgrammingExerciseBuildConfig loadBuildConfig(ProgrammingExercise programmingExercise) {
        return programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(programmingExercise);
    }

    /**
     * @param sharedData    the inputs the caller resolved for the whole exercise
     * @param participation the participation being triggered
     * @return the head commit of the exercise's test repository, taken from the caller when it resolved it and read
     *         from the repository otherwise
     */
    private String testCommitHashFrom(SharedBuildTriggerData sharedData, ProgrammingExerciseParticipation participation) {
        if (sharedData.resolved()) {
            return sharedData.testCommitHash();
        }
        return getCommitHashOrNull(participation.getProgrammingExercise().getVcsTestRepositoryUri(), "test repository");
    }

    private ProgrammingExerciseBuildStatistics loadBuildStatistics(ProgrammingExercise programmingExercise) {
        return programmingExerciseBuildStatisticsRepository.findByExerciseId(programmingExercise.getId()).orElse(null);
    }

    /**
     * Determines the priority of the build job.
     * Lower values indicate higher priority.
     */
    private int determinePriority(ProgrammingExercise programmingExercise, ProgrammingExerciseParticipation participation, boolean triggerAll) {
        // Use the lowest priority if the build is part of a trigger all action
        if (triggerAll) {
            return PRIORITY_ALL_BUILDS;
        }

        // Check for test exams and exam test runs
        if (programmingExercise.isExamExercise()) {
            if (programmingExercise.getExam().isTestExam()) {
                return PRIORITY_NORMAL;
            }
            if (participation instanceof StudentParticipation sp && sp.isTestRun()) {
                return PRIORITY_NORMAL;
            }
        }

        // Submissions after the due date (e.g. practice mode or finished exams) have lowest priority
        if (exerciseDateService.isAfterDueDate(participation)) {
            return PRIORITY_PRACTICE;
        }

        // If the exercise is now an exam exercise, then the exam is currently ongoing
        // Here quick feedback is important, so we give it a higher priority
        if (programmingExercise.isExamExercise()) {
            return PRIORITY_EXAM_CONDUCTION;
        }

        // Reduce priority of optional exercises
        if (programmingExercise.getIncludedInOverallScore() == IncludedInOverallScore.NOT_INCLUDED) {
            return PRIORITY_OPTIONAL_EXERCISE;
        }

        return PRIORITY_NORMAL;
    }

    private int addPenaltyIfTestCourse(ProgrammingExercise programmingExercise, int priority) {
        if (programmingExercise.getCourseViaExerciseGroupOrCourseMember().isTestCourse()) {
            return priority + TESTCOURSE_PRIORITY_PENALTY;
        }
        return priority;
    }

    /**
     * Gets the commit hash from the repository or returns null if it cannot be retrieved.
     *
     * @param repositoryUri   the URI of the repository
     * @param repositoryLabel a human-readable label for the repository (used in log messages)
     * @return the commit hash as a string, or null if not available
     */
    @Nullable
    private String getCommitHashOrNull(LocalVCRepositoryUri repositoryUri, String repositoryLabel) {
        var commitHash = gitService.getLastCommitHash(repositoryUri);
        if (commitHash == null) {
            log.warn("Could not retrieve commit hash for {} - the repository may not have any commits yet", repositoryLabel);
            return null;
        }
        return commitHash;
    }
}
