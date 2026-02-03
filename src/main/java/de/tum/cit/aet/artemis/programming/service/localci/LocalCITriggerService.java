package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.DockerContainerConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildStatistics;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildStatisticsRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseBuildConfigService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeature;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

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

    private final AeolusTemplateService aeolusTemplateService;

    private final BuildScriptProviderService buildScriptProviderService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final LocalCIProgrammingLanguageFeatureService programmingLanguageFeatureService;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final LocalCIBuildConfigurationService localCIBuildConfigurationService;

    private final GitService gitService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final ProgrammingExerciseBuildStatisticsRepository programmingExerciseBuildStatisticsRepository;

    private final ExerciseDateService exerciseDateService;

    private final ProgrammingExerciseBuildConfigService programmingExerciseBuildConfigService;

    private final BuildJobRepository buildJobRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ResultRepository resultRepository;

    private static final int DEFAULT_BUILD_DURATION = 17;

    // Arbitrary value to ensure that the build duration is always a bit higher than the actual build duration
    private static final double BUILD_DURATION_SAFETY_FACTOR = 1.1;

    public LocalCITriggerService(DistributedDataAccessService distributedDataAccessService, AeolusTemplateService aeolusTemplateService,
            ProgrammingLanguageConfiguration programmingLanguageConfiguration, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            LocalCIProgrammingLanguageFeatureService programmingLanguageFeatureService, GitService gitService, ExerciseDateService exerciseDateService,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            LocalCIBuildConfigurationService localCIBuildConfigurationService, ProgrammingExerciseBuildStatisticsRepository programmingExerciseBuildStatisticsRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, BuildScriptProviderService buildScriptProviderService,
            ProgrammingExerciseBuildConfigService programmingExerciseBuildConfigService, BuildJobRepository buildJobRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            ResultRepository resultRepository) {
        this.distributedDataAccessService = distributedDataAccessService;
        this.aeolusTemplateService = aeolusTemplateService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.localCIBuildConfigurationService = localCIBuildConfigurationService;
        this.gitService = gitService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.exerciseDateService = exerciseDateService;
        this.buildScriptProviderService = buildScriptProviderService;
        this.programmingExerciseBuildConfigService = programmingExerciseBuildConfigService;
        this.programmingExerciseBuildStatisticsRepository = programmingExerciseBuildStatisticsRepository;
        this.buildJobRepository = buildJobRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.resultRepository = resultRepository;
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
        triggerBuild(participation, null, null, triggerAll, 0);
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
        triggerBuild(participation, commitHashToBuild, triggeredByPushTo, false, 0);
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
        triggerBuild(participation, commitHashToBuild, triggeredByPushTo, false, retryCount);
    }

    private void triggerBuild(ProgrammingExerciseParticipation participation, String commitHashToBuild, RepositoryType triggeredByPushTo, boolean triggerAll, int retryCount)
            throws LocalCIException {

        log.info("Triggering build for participation {} and commit hash {}", participation.getId(), commitHashToBuild);

        // Commit hash related to the repository that will be tested
        String assignmentCommitHash;

        // Commit hash related to the test repository
        String testCommitHash;

        if (triggeredByPushTo == null || triggeredByPushTo.equals(RepositoryType.AUXILIARY)) {
            assignmentCommitHash = getCommitHashOrNull(participation.getVcsRepositoryUri(), "assignment repository");
            testCommitHash = getCommitHashOrNull(participation.getProgrammingExercise().getVcsTestRepositoryUri(), "test repository");
        }
        else if (triggeredByPushTo.equals(RepositoryType.TESTS)) {
            assignmentCommitHash = getCommitHashOrNull(participation.getVcsRepositoryUri(), "assignment repository");
            if (commitHashToBuild == null) {
                commitHashToBuild = getCommitHashOrNull(participation.getProgrammingExercise().getVcsTestRepositoryUri(), "test repository");
            }
            testCommitHash = commitHashToBuild;
        }
        else {
            assignmentCommitHash = commitHashToBuild;
            testCommitHash = getCommitHashOrNull(participation.getProgrammingExercise().getVcsTestRepositoryUri(), "test repository");
        }

        // If we couldn't retrieve commit hashes, skip the build - there's nothing to build yet
        if (assignmentCommitHash == null || testCommitHash == null) {
            log.info("Skipping build for participation {} - commit hashes not available yet", participation.getId());
            return;
        }

        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();

        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        // Exam exercises have highest priority, Exercises with due date in the past have lowest priority
        int priority = determinePriority(programmingExercise, participation, triggerAll);
        priority = addPenaltyIfTestCourse(programmingExercise, priority);

        ZonedDateTime submissionDate = ZonedDateTime.now();

        long time = submissionDate.toInstant().toEpochMilli();

        var programmingExerciseBuildConfig = loadBuildConfig(programmingExercise);

        var buildStatistics = loadBuildStatistics(programmingExercise);

        long estimatedDuration = (buildStatistics != null && buildStatistics.getBuildDurationSeconds() > 0) ? buildStatistics.getBuildDurationSeconds() : DEFAULT_BUILD_DURATION;
        estimatedDuration = Math.round(estimatedDuration * BUILD_DURATION_SAFETY_FACTOR);

        JobTimingInfo jobTimingInfo = new JobTimingInfo(submissionDate, null, null, null, estimatedDuration);

        RepositoryInfo repositoryInfo = getRepositoryInfo(participation, triggeredByPushTo, programmingExerciseBuildConfig);

        List<BuildConfig> buildConfigs = getBuildConfigs(participation, commitHashToBuild, assignmentCommitHash, testCommitHash, programmingExerciseBuildConfig);

        BuildAgentDTO buildAgent = new BuildAgentDTO(null, null, null);

        // TODO: Sorting here is obviously very ugly.
        var sortedContainerConfigs = programmingExerciseBuildConfig.getContainerConfigs().values().stream().sorted(Comparator.comparing(DomainObject::getId)).toList();

        int expectedContainerCount = Math.max(buildConfigs.size(), 1);

        // Find or create the submission that all containers will process
        // This ensures all BuildJobQueueItems for the same commit share the same submissionId
        Long submissionId = findOrCreateSubmission(participation, assignmentCommitHash, submissionDate, triggeredByPushTo, expectedContainerCount);

        var buildJobs = distributedDataAccessService.getDistributedBuildJobQueue();

        for (int i = 0; i < buildConfigs.size(); i++) { // TODO: You cannot do this as the windfile might be default? Fix later.
            final BuildConfig buildConfig = buildConfigs.get(i);
            final DockerContainerConfig containerConfig = sortedContainerConfigs.get(i);

            String buildJobId = String.format("%d-%d-%d", participation.getId(), containerConfig.getId(), time);
            BuildJobQueueItem buildJobQueueItem = new BuildJobQueueItem(buildJobId, participation.getBuildPlanId(), buildAgent, participation.getId(), containerConfig.getId(),
                    courseId, programmingExercise.getId(), retryCount, priority, null, repositoryInfo, jobTimingInfo, buildConfig, null, submissionId);

            // Save the build job before adding it to the queue to ensure it exists in the database.
            // This prevents potential race conditions where a build agent pulls the job from the queue very quickly before it is persisted,
            // leading to a failed update operation due to a missing record.
            buildJobRepository.save(new BuildJob(buildJobQueueItem, BuildStatus.QUEUED, null));
            buildJobs.add(buildJobQueueItem);
            log.info("Added build job {} for exercise {} and participation {} and container {} with priority {} to the queue", buildJobId, programmingExercise.getShortName(),
                    participation.getId(), containerConfig.getId(), priority);

            distributedDataAccessService.getDistributedDockerImageCleanupInfo().put(buildConfig.dockerImage(), jobTimingInfo.submissionDate());
        }
    }

    // -------Helper methods for triggerBuild()-------

    /**
     * Finds or creates a ProgrammingSubmission for the given participation and commit hash.
     * This ensures all containers processing the same commit share the same submission.
     * If a submission already exists (e.g., created by the push processing flow), it will be reused.
     *
     * @param participation          the participation
     * @param commitHash             the commit hash
     * @param submissionDate         the submission date
     * @param triggeredByPushTo      the repository type that triggered the push
     * @param expectedContainerCount expected number of containers for this submission
     * @return the submission ID
     */
    private Long findOrCreateSubmission(ProgrammingExerciseParticipation participation, String commitHash, ZonedDateTime submissionDate, RepositoryType triggeredByPushTo,
            int expectedContainerCount) {
        // Try to find an existing submission for this commit
        Optional<ProgrammingSubmission> existingSubmission = programmingSubmissionRepository.findFirstByParticipationIdAndCommitHashOrderByIdDesc(participation.getId(),
                commitHash);

        if (existingSubmission.isPresent()) {
            ProgrammingSubmission submission = existingSubmission.get();
            log.debug("Found existing submission {} for commit {}", submission.getId(), commitHash);
            if (submission.getExpectedContainerCount() == null || submission.getExpectedContainerCount() <= 0) {
                submission.setExpectedContainerCount(expectedContainerCount);
                programmingSubmissionRepository.save(submission);
            }
            return submission.getId();
        }

        // Create a new submission
        // Note: This might fail if another process (e.g., LocalVCServletService) creates the submission concurrently
        // In that case, we catch the error and fetch the existing submission
        try {
            ProgrammingSubmission newSubmission = new ProgrammingSubmission();
            newSubmission.setParticipation((Participation) participation);
            newSubmission.setCommitHash(commitHash);
            newSubmission.setSubmissionDate(submissionDate);
            newSubmission.setSubmitted(true);

            // Set submission type based on repository type
            if (triggeredByPushTo == RepositoryType.TESTS) {
                newSubmission.setType(SubmissionType.TEST);
            }
            else {
                newSubmission.setType(SubmissionType.MANUAL);
            }

            newSubmission.setExpectedContainerCount(expectedContainerCount);
            ProgrammingSubmission savedSubmission = programmingSubmissionRepository.save(newSubmission);
            createPlaceholderResultIfNeeded(savedSubmission, participation, expectedContainerCount);
            log.info("Created new submission {} for commit {} in participation {}", savedSubmission.getId(), commitHash, participation.getId());

            return savedSubmission.getId();
        }
        catch (Exception e) {
            // If save failed (likely due to duplicate submission), fetch the existing one
            log.debug("Failed to create submission (probably already exists), fetching existing submission: {}", e.getMessage());
            existingSubmission = programmingSubmissionRepository.findFirstByParticipationIdAndCommitHashOrderByIdDesc(participation.getId(), commitHash);

            if (existingSubmission.isPresent()) {
                ProgrammingSubmission submission = existingSubmission.get();
                log.info("Found existing submission {} for commit {} after failed creation attempt", submission.getId(), commitHash);
                if (submission.getExpectedContainerCount() == null || submission.getExpectedContainerCount() <= 0) {
                    submission.setExpectedContainerCount(expectedContainerCount);
                    programmingSubmissionRepository.save(submission);
                }
                return submission.getId();
            }

            // If we still can't find it, something is wrong
            log.error("Could not create or find submission for commit {} in participation {}", commitHash, participation.getId(), e);
            throw new RuntimeException("Could not create or find submission for commit " + commitHash, e);
        }
    }

    private List<String> getTestResultPaths(Windfile windfile) {
        return windfile.results().stream().map(result -> LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/" + result.path()).toList();
    }

    private void createPlaceholderResultIfNeeded(ProgrammingSubmission submission, ProgrammingExerciseParticipation participation, int expectedContainerCount) {
        if (expectedContainerCount <= 1) {
            return;
        }
        if (submission.getLatestResult() != null) {
            return;
        }

        ProgrammingExercise exercise = participation.getProgrammingExercise();
        if (exercise == null) {
            return;
        }

        Result result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setScore(0D, exercise.getCourseViaExerciseGroupOrCourseMember());
        result.setCompletionDate(null);
        result.setSuccessful(null);
        result.setSubmission(submission);
        result.setExerciseId(exercise.getId());
        result.setRatedIfNotAfterDueDate();

        int testCaseCount = programmingExerciseTestCaseRepository.findByExerciseIdAndActive(exercise.getId(), true).size();
        result.setTestCaseCount(testCaseCount);
        result.setPassedTestCaseCount(0);
        result.setCodeIssueCount(0);

        submission.addResult(result);
        resultRepository.save(result);
    }

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

    private List<BuildConfig> getBuildConfigs(ProgrammingExerciseParticipation participation, String commitHashToBuild, String assignmentCommitHash, String testCommitHash,
            ProgrammingExerciseBuildConfig buildConfig) {
        String branch = participation instanceof ProgrammingExerciseStudentParticipation studentParticipation ? studentParticipation.getBranch() : buildConfig.getBranch();
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        ProgrammingLanguage programmingLanguage = programmingExercise.getProgrammingLanguage();
        ProjectType projectType = programmingExercise.getProjectType();
        boolean staticCodeAnalysisEnabled = programmingExercise.isStaticCodeAnalysisEnabled();
        boolean sequentialTestRunsEnabled = buildConfig.hasSequentialTestRuns();

        List<Windfile> windfiles;
        List<String> dockerImages;
        try {
            windfiles = buildConfig.getWindfiles();
            dockerImages = windfiles.stream().map(windfile -> windfile.metadata().docker().getFullImageName()).toList();
        }
        catch (NullPointerException e) {
            log.warn("Could not retrieve windfile for programming exercise {}. Using default windfile instead.", programmingExercise.getId());
            programmingExercise.setBuildConfig(buildConfig); // windfile could fail to lazy load build config
            windfiles = List.of(aeolusTemplateService.getDefaultWindfileFor(programmingExercise)); // TODO: Does it make sense to just assume one? This might be a horrible idea, as
                                                                                                   // it is out of sync with the db.
            dockerImages = List
                    .of(programmingLanguageConfiguration.getImage(programmingExercise.getProgrammingLanguage(), Optional.ofNullable(programmingExercise.getProjectType()))); // TODO:
                                                                                                                                                                             // dito.
        }

        DockerRunConfig dockerRunConfig = programmingExerciseBuildConfigService.getDockerRunConfig(buildConfig);

        List<BuildConfig> buildConfigs = new ArrayList<>();
        for (int i = 0; i < windfiles.size(); i++) {
            List<String> resultPaths = getTestResultPaths(windfiles.get(i));
            resultPaths = buildScriptProviderService.replaceResultPathsPlaceholders(resultPaths, buildConfig);

            // Todo: If build agent does not have access to filesystem, we need to send the build script to the build agent and execute it there.
            programmingExercise.setBuildConfig(buildConfig);
            String buildScript = localCIBuildConfigurationService.createBuildScript(programmingExercise, i);

            buildConfigs.add(new BuildConfig(buildScript, dockerImages.get(i), commitHashToBuild, assignmentCommitHash, testCommitHash, branch, programmingLanguage, projectType,
                    staticCodeAnalysisEnabled, sequentialTestRunsEnabled, resultPaths, buildConfig.getTimeoutSeconds(), buildConfig.getAssignmentCheckoutPath(),
                    buildConfig.getTestCheckoutPath(), buildConfig.getSolutionCheckoutPath(), dockerRunConfig));
        }

        return buildConfigs;
    }

    private ProgrammingExerciseBuildConfig loadBuildConfig(ProgrammingExercise programmingExercise) {
        return programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(programmingExercise);
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
