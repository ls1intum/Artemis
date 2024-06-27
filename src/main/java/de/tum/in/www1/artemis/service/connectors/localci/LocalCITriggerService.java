package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.config.ProgrammingLanguageConfiguration;
import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusResult;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.BuildConfig;
import de.tum.in.www1.artemis.service.connectors.localci.dto.BuildJobItem;
import de.tum.in.www1.artemis.service.connectors.localci.dto.BuildJobItemReferenceDTO;
import de.tum.in.www1.artemis.service.connectors.localci.dto.JobTimingInfo;
import de.tum.in.www1.artemis.service.connectors.localci.dto.RepositoryInfo;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;

/**
 * Service for triggering builds on the local CI system.
 */
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCITriggerService implements ContinuousIntegrationTriggerService {

    private static final Logger log = LoggerFactory.getLogger(LocalCITriggerService.class);

    private final HazelcastInstance hazelcastInstance;

    private final AeolusTemplateService aeolusTemplateService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final LocalCIProgrammingLanguageFeatureService programmingLanguageFeatureService;

    private final Optional<VersionControlService> versionControlService;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final LocalCIBuildConfigurationService localCIBuildConfigurationService;

    private final GitService gitService;

    private IQueue<BuildJobItemReferenceDTO> queue;

    private IMap<String, ZonedDateTime> dockerImageCleanupInfo;

    private IMap<Long, CircularFifoQueue<BuildJobItem>> buildJobItemMap;

    @Value("${artemis.continuous-integration.parallel-jobs-per-participation:2}")
    private int PARALLEL_JOBS_PER_PARTICIPATION;

    public LocalCITriggerService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, AeolusTemplateService aeolusTemplateService,
            ProgrammingLanguageConfiguration programmingLanguageConfiguration, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            LocalCIProgrammingLanguageFeatureService programmingLanguageFeatureService, Optional<VersionControlService> versionControlService,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            LocalCIBuildConfigurationService localCIBuildConfigurationService, GitService gitService) {
        this.hazelcastInstance = hazelcastInstance;
        this.aeolusTemplateService = aeolusTemplateService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.versionControlService = versionControlService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.localCIBuildConfigurationService = localCIBuildConfigurationService;
        this.gitService = gitService;
    }

    @PostConstruct
    public void init() {
        this.queue = this.hazelcastInstance.getQueue("buildJobQueue");
        this.buildJobItemMap = this.hazelcastInstance.getMap("buildJobItemMap");
        this.dockerImageCleanupInfo = this.hazelcastInstance.getMap("dockerImageCleanupInfo");
    }

    /**
     * Add a new build job to the queue managed by the ExecutorService and process the returned result.
     *
     * @param participation the participation of the repository which should be built and tested.
     * @throws LocalCIException if the build job could not be added to the queue.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws LocalCIException {
        triggerBuild(participation, null, null);
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
            testCommitHash = commitHashToBuild;
        }
        else {
            assignmentCommitHash = commitHashToBuild;
            testCommitHash = gitService.getLastCommitHash(participation.getProgrammingExercise().getVcsTestRepositoryUri()).getName();
        }

        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();

        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        // Exam exercises have a higher priority than normal exercises
        int priority = programmingExercise.isExamExercise() ? 1 : 2;

        ZonedDateTime submissionDate = ZonedDateTime.now();

        String buildJobId = String.valueOf(participation.getId()) + submissionDate.toInstant().toEpochMilli();

        JobTimingInfo jobTimingInfo = new JobTimingInfo(submissionDate, null, null);

        RepositoryInfo repositoryInfo = getRepositoryInfo(participation, triggeredByPushTo);

        BuildConfig buildConfig = getBuildConfig(participation, commitHashToBuild, assignmentCommitHash, testCommitHash);

        BuildJobItem buildJobItem = new BuildJobItem(buildJobId, participation.getBuildPlanId(), null, participation.getId(), courseId, programmingExercise.getId(), 0, priority,
                null, repositoryInfo, jobTimingInfo, buildConfig, null);

        BuildJobItemReferenceDTO buildJobItemReference = new BuildJobItemReferenceDTO(buildJobItem);

        var timeStart = System.currentTimeMillis();
        buildJobItemMap.lock(participation.getId());
        try {
            CircularFifoQueue<BuildJobItem> buildJobItems = buildJobItemMap.get(participation.getId());
            if (buildJobItems == null) {
                buildJobItems = new CircularFifoQueue<>(PARALLEL_JOBS_PER_PARTICIPATION);
            }
            buildJobItems.add(buildJobItem);
            buildJobItemMap.put(participation.getId(), buildJobItems);
        }
        catch (Exception e) {
            log.error("Error while adding build job item to map", e);
            throw new LocalCIException("Error while adding build job item to map", e);
        }
        finally {
            buildJobItemMap.unlock(participation.getId());
        }
        queue.add(buildJobItemReference);
        log.info("Added build job {} to the queue", buildJobId);
        log.debug("Time to add build job to queue: {}ms", System.currentTimeMillis() - timeStart);

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
    private RepositoryInfo getRepositoryInfo(ProgrammingExerciseParticipation participation, RepositoryType triggeredByPushTo) {

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

        if (programmingExercise.getCheckoutSolutionRepository()) {
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

    private BuildConfig getBuildConfig(ProgrammingExerciseParticipation participation, String commitHashToBuild, String assignmentCommitHash, String testCommitHash) {
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
        boolean sequentialTestRunsEnabled = programmingExercise.hasSequentialTestRuns();
        boolean testwiseCoverageEnabled = programmingExercise.isTestwiseCoverageEnabled();

        Windfile windfile;
        String dockerImage;
        try {
            windfile = programmingExercise.getWindfile();
            dockerImage = windfile.getMetadata().docker().getFullImageName();
        }
        catch (NullPointerException e) {
            log.warn("Could not retrieve windfile for programming exercise {}. Using default windfile instead.", programmingExercise.getId());
            windfile = aeolusTemplateService.getDefaultWindfileFor(programmingExercise);
            dockerImage = programmingLanguageConfiguration.getImage(programmingExercise.getProgrammingLanguage(), Optional.ofNullable(programmingExercise.getProjectType()));
        }

        List<String> resultPaths = getTestResultPaths(windfile);

        // Todo: If build agent does not have access to filesystem, we need to send the build script to the build agent and execute it there.
        String buildScript = localCIBuildConfigurationService.createBuildScript(participation);

        return new BuildConfig(buildScript, dockerImage, commitHashToBuild, assignmentCommitHash, testCommitHash, branch, programmingLanguage, projectType,
                staticCodeAnalysisEnabled, sequentialTestRunsEnabled, testwiseCoverageEnabled, resultPaths);
    }
}
