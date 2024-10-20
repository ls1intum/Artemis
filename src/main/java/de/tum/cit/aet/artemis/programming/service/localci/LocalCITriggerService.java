package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeature;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusResult;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.programming.service.aeolus.Windfile;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

/**
 * Service for triggering builds on the local CI system.
 */
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

    private final HazelcastInstance hazelcastInstance;

    private final AeolusTemplateService aeolusTemplateService;

    private final BuildScriptProviderService buildScriptProviderService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final LocalCIProgrammingLanguageFeatureService programmingLanguageFeatureService;

    private final Optional<VersionControlService> versionControlService;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final LocalCIBuildConfigurationService localCIBuildConfigurationService;

    private final GitService gitService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private IQueue<BuildJobQueueItem> queue;

    private IMap<String, ZonedDateTime> dockerImageCleanupInfo;

    private final ExerciseDateService exerciseDateService;

    public LocalCITriggerService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, AeolusTemplateService aeolusTemplateService,
            ProgrammingLanguageConfiguration programmingLanguageConfiguration, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            LocalCIProgrammingLanguageFeatureService programmingLanguageFeatureService, Optional<VersionControlService> versionControlService,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            LocalCIBuildConfigurationService localCIBuildConfigurationService, GitService gitService, ExerciseDateService exerciseDateService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, BuildScriptProviderService buildScriptProviderService) {
        this.hazelcastInstance = hazelcastInstance;
        this.aeolusTemplateService = aeolusTemplateService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.versionControlService = versionControlService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.localCIBuildConfigurationService = localCIBuildConfigurationService;
        this.gitService = gitService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.exerciseDateService = exerciseDateService;
        this.buildScriptProviderService = buildScriptProviderService;
    }

    @PostConstruct
    public void init() {
        this.queue = this.hazelcastInstance.getQueue("buildJobQueue");
        this.dockerImageCleanupInfo = this.hazelcastInstance.getMap("dockerImageCleanupInfo");
    }

    /**
     * Add a new build job to the queue managed by the ExecutorService and process the returned result.
     *
     * @param participation the participation of the repository which should be built and tested.
     * @throws LocalCIException if the build job could not be added to the queue.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, boolean triggerAll) throws LocalCIException {
        triggerBuild(participation, null, null, triggerAll);
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
        triggerBuild(participation, commitHashToBuild, triggeredByPushTo, false);
    }

    private void triggerBuild(ProgrammingExerciseParticipation participation, String commitHashToBuild, RepositoryType triggeredByPushTo, boolean triggerAll)
            throws LocalCIException {

        log.info("Triggering build for participation {} and commit hash {}", participation.getId(), commitHashToBuild);

        // Commit hash related to the repository that will be tested
        String assignmentCommitHash;

        // Commit hash related to the test repository
        String testCommitHash;

        if (triggeredByPushTo == null || triggeredByPushTo.equals(RepositoryType.AUXILIARY)) {
            assignmentCommitHash = gitService.getLastCommitHash(participation.getVcsRepositoryUri()).getName();
            testCommitHash = gitService.getLastCommitHash(participation.getProgrammingExercise().getVcsTestRepositoryUri()).getName();
        }
        else if (triggeredByPushTo.equals(RepositoryType.TESTS)) {
            assignmentCommitHash = gitService.getLastCommitHash(participation.getVcsRepositoryUri()).getName();
            commitHashToBuild = Objects.requireNonNullElseGet(commitHashToBuild,
                    () -> gitService.getLastCommitHash(participation.getProgrammingExercise().getVcsTestRepositoryUri()).getName());
            testCommitHash = commitHashToBuild;
        }
        else {
            assignmentCommitHash = commitHashToBuild;
            testCommitHash = gitService.getLastCommitHash(participation.getProgrammingExercise().getVcsTestRepositoryUri()).getName();
        }

        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();

        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        // Exam exercises have highest priority, Exercises with due date in the past have lowest priority
        int priority = determinePriority(programmingExercise, participation, triggerAll);
        priority = addPenaltyIfTestCourse(programmingExercise, priority);

        ZonedDateTime submissionDate = ZonedDateTime.now();

        String buildJobId = String.valueOf(participation.getId()) + submissionDate.toInstant().toEpochMilli();

        JobTimingInfo jobTimingInfo = new JobTimingInfo(submissionDate, null, null);

        var programmingExerciseBuildConfig = loadBuildConfig(programmingExercise);

        RepositoryInfo repositoryInfo = getRepositoryInfo(participation, triggeredByPushTo, programmingExerciseBuildConfig);

        BuildConfig buildConfig = getBuildConfig(participation, commitHashToBuild, assignmentCommitHash, testCommitHash, programmingExerciseBuildConfig);

        BuildJobQueueItem buildJobQueueItem = new BuildJobQueueItem(buildJobId, participation.getBuildPlanId(), null, participation.getId(), courseId, programmingExercise.getId(),
                0, priority, null, repositoryInfo, jobTimingInfo, buildConfig, null);

        queue.add(buildJobQueueItem);
        log.info("Added build job {} to the queue", buildJobId);

        dockerImageCleanupInfo.put(buildConfig.dockerImage(), jobTimingInfo.submissionDate());
    }

    // -------Helper methods for triggerBuild()-------

    private List<String> getTestResultPaths(Windfile windfile) throws IllegalArgumentException {
        List<String> testResultPaths = new ArrayList<>();
        for (AeolusResult testResultPath : windfile.getResults()) {
            testResultPaths.add(LOCALCI_WORKING_DIRECTORY + "/testing-dir/" + testResultPath.path());
        }
        return testResultPaths;
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

    private BuildConfig getBuildConfig(ProgrammingExerciseParticipation participation, String commitHashToBuild, String assignmentCommitHash, String testCommitHash,
            ProgrammingExerciseBuildConfig buildConfig) {
        String branch;
        try {
            branch = versionControlService.orElseThrow().getOrRetrieveBranchOfParticipation(participation);
        }
        catch (LocalVCInternalException e) {
            throw new LocalCIException("Error while getting branch of participation", e);
        }

        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        ProgrammingLanguage programmingLanguage = programmingExercise.getProgrammingLanguage();
        ProjectType projectType = programmingExercise.getProjectType();
        boolean staticCodeAnalysisEnabled = programmingExercise.isStaticCodeAnalysisEnabled();
        boolean sequentialTestRunsEnabled = buildConfig.hasSequentialTestRuns();
        boolean testwiseCoverageEnabled = buildConfig.isTestwiseCoverageEnabled();

        Windfile windfile;
        String dockerImage;
        try {
            windfile = buildConfig.getWindfile();
            dockerImage = windfile.getMetadata().docker().getFullImageName();
        }
        catch (NullPointerException e) {
            log.warn("Could not retrieve windfile for programming exercise {}. Using default windfile instead.", programmingExercise.getId());
            windfile = aeolusTemplateService.getDefaultWindfileFor(programmingExercise);
            dockerImage = programmingLanguageConfiguration.getImage(programmingExercise.getProgrammingLanguage(), Optional.ofNullable(programmingExercise.getProjectType()));
        }

        List<String> resultPaths = getTestResultPaths(windfile);
        resultPaths = buildScriptProviderService.replaceResultPathsPlaceholders(resultPaths, buildConfig);

        // Todo: If build agent does not have access to filesystem, we need to send the build script to the build agent and execute it there.
        programmingExercise.setBuildConfig(buildConfig);
        String buildScript = localCIBuildConfigurationService.createBuildScript(programmingExercise);

        return new BuildConfig(buildScript, dockerImage, commitHashToBuild, assignmentCommitHash, testCommitHash, branch, programmingLanguage, projectType,
                staticCodeAnalysisEnabled, sequentialTestRunsEnabled, testwiseCoverageEnabled, resultPaths, buildConfig.getTimeoutSeconds(),
                buildConfig.getAssignmentCheckoutPath(), buildConfig.getTestCheckoutPath(), buildConfig.getSolutionCheckoutPath());
    }

    private ProgrammingExerciseBuildConfig loadBuildConfig(ProgrammingExercise programmingExercise) {
        return programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(programmingExercise);
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
}
