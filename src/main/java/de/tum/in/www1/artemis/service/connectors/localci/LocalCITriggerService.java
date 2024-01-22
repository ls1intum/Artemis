package de.tum.in.www1.artemis.service.connectors.localci;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;

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
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusResult;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;

/**
 * Service for triggering builds on the local CI system.
 */
@Service
@Profile("localci")
public class LocalCITriggerService implements ContinuousIntegrationTriggerService {

    private final LocalCISharedBuildJobQueueService localCISharedBuildJobQueueService;

    private final HazelcastInstance hazelcastInstance;

    private final AeolusTemplateService aeolusTemplateService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final LocalCIProgrammingLanguageFeatureService programmingLanguageFeatureService;

    private final Optional<VersionControlService> versionControlService;

    private final IQueue<LocalCIBuildJobQueueItem> queue;

    /**
     * Lock to prevent multiple nodes from processing the same build job.
     */
    private final FencedLock sharedLock;

    private static final Logger log = LoggerFactory.getLogger(LocalCITriggerService.class);

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    public LocalCITriggerService(HazelcastInstance hazelcastInstance, LocalCISharedBuildJobQueueService localCISharedBuildJobQueueService,
            AeolusTemplateService aeolusTemplateService, ProgrammingLanguageConfiguration programmingLanguageConfiguration,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, LocalCIProgrammingLanguageFeatureService programmingLanguageFeatureService,
            Optional<VersionControlService> versionControlService) {
        this.hazelcastInstance = hazelcastInstance;
        this.localCISharedBuildJobQueueService = localCISharedBuildJobQueueService;
        this.aeolusTemplateService = aeolusTemplateService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.versionControlService = versionControlService;
        this.queue = this.hazelcastInstance.getQueue("buildJobQueue");
        this.sharedLock = this.hazelcastInstance.getCPSubsystem().getLock("buildJobQueueLock");
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
     * Add a new build job for a specific commit to the queue managed by the ExecutorService and process the returned result.
     *
     * @param participation     the participation of the repository which should be built and tested
     * @param commitHash        the commit hash of the commit that triggers the build. If it is null, the latest commit of the default branch will be built.
     * @param triggeredByPushTo type of the repository that was pushed to and triggered the build job
     * @throws LocalCIException if the build job could not be added to the queue.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType triggeredByPushTo) throws LocalCIException {

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
        Boolean staticCodeAnalysisEnabled = programmingExercise.isStaticCodeAnalysisEnabled();
        Boolean sequentialTestRuns = programmingExercise.hasSequentialTestRuns();
        Boolean testwiseCoverageEnabled = programmingExercise.isTestwiseCoverageEnabled();

        Windfile windfile;
        String dockerImage;
        try {
            windfile = programmingExercise.getWindfile();
            dockerImage = windfile.getMetadata().getDocker().getImage();
        }
        catch (NullPointerException e) {
            log.warn("Could not retrieve windfile for programming exercise {}. Using default windfile instead.", programmingExercise.getId());
            windfile = aeolusTemplateService.getDefaultWindfileFor(programmingExercise);
            dockerImage = programmingLanguageConfiguration.getImage(programmingExercise.getProgrammingLanguage(), Optional.ofNullable(programmingExercise.getProjectType()));
        }

        List<String> resultPaths = getTestResultPaths(windfile);

        List<AuxiliaryRepository> auxiliaryRepositories;

        // If the auxiliary repositories are not initialized, we need to fetch them from the database.
        if (Hibernate.isInitialized(participation.getProgrammingExercise().getAuxiliaryRepositories())) {
            auxiliaryRepositories = participation.getProgrammingExercise().getAuxiliaryRepositories();
        }
        else {
            auxiliaryRepositories = auxiliaryRepositoryRepository.findByExerciseId(participation.getProgrammingExercise().getId());
        }

        LocalVCRepositoryUri assignmentRepositoryUri;
        LocalVCRepositoryUri testRepositoryUri;
        LocalVCRepositoryUri solutionRepositoryUri;
        LocalVCRepositoryUri[] auxiliaryRepositoryUris;
        String[] auxiliaryRepositoryCheckoutDirectories;

        try {
            assignmentRepositoryUri = new LocalVCRepositoryUri(participation.getRepositoryUri(), localVCBaseUrl);
            testRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTestRepositoryUri(), localVCBaseUrl);
            if (programmingExercise.getCheckoutSolutionRepository()) {
                ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.getProgrammingLanguageFeatures(programmingLanguage);
                if (programmingLanguageFeature.checkoutSolutionRepositoryAllowed()) {
                    solutionRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getSolutionRepositoryUri(), localVCBaseUrl);
                }
            }

            if (!auxiliaryRepositories.isEmpty()) {
                auxiliaryRepositoryUris = new LocalVCRepositoryUri[auxiliaryRepositories.size()];
                auxiliaryRepositoryCheckoutDirectories = new String[auxiliaryRepositories.size()];

                for (int i = 0; i < auxiliaryRepositories.size(); i++) {
                    auxiliaryRepositoryUris[i] = new LocalVCRepositoryUri(auxiliaryRepositories.get(i).getRepositoryUri(), localVCBaseUrl);
                    auxiliaryRepositoryCheckoutDirectories[i] = auxiliaryRepositories.get(i).getCheckoutDirectory();
                }
            }
        }
        catch (LocalVCInternalException e) {
            throw new LocalCIException("Error while creating LocalVCRepositoryUri", e);
        }

        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

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

        // Exam exercises have a higher priority than normal exercises
        int priority = programmingExercise.isExamExercise() ? 1 : 2;

        // localCISharedBuildJobQueueService.addBuildJob(participation.getBuildPlanId(), participation.getId(), repositoryName, repositoryType, commitHash, ZonedDateTime.now(),
        // priority, courseId, triggeredByPushTo);
    }

    private List<String> getTestResultPaths(Windfile windfile) throws IllegalArgumentException {
        List<String> testResultPaths = new ArrayList<>();
        for (AeolusResult testResultPath : windfile.getResults()) {
            testResultPaths.add(LocalCIContainerService.WORKING_DIRECTORY + "/testing-dir/" + testResultPath.getPath());
        }
        return testResultPaths;
    }
}
