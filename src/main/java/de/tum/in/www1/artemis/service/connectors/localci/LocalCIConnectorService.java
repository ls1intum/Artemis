package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Commit;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCServletService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * This service connects the local VC system and the online editor to the local CI system.
 * It contains the {@link #processNewPush(String, Repository)} method that is called by the local VC system and the RepositoryResource and makes sure the correct build is
 * triggered.
 * TODO LOCALVC_CI: It would be preferred to have the logic for processing the submission with the local VC subsystem instead of the local CI subsystem (here).
 * Move all logic that depends on the {@link ProgrammingSubmissionService} into {@link LocalVCServletService#processNewPush(String, Repository)}
 * See <a href="https://github.com/ls1intum/Artemis/issues/6700">#6700</a> for more information.
 */
@Service
@Profile("localci")
public class LocalCIConnectorService {

    private final Logger log = LoggerFactory.getLogger(LocalCIConnectorService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final ProgrammingTriggerService programmingTriggerService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final LocalCITriggerService localCITriggerService;

    private final LocalCIProgrammingLanguageFeatureService localCIProgrammingLanguageFeatureService;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    public LocalCIConnectorService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingMessagingService programmingMessagingService, ProgrammingTriggerService programmingTriggerService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, LocalCITriggerService localCITriggerService,
            LocalCIProgrammingLanguageFeatureService localCIProgrammingLanguageFeatureService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingMessagingService = programmingMessagingService;
        this.programmingTriggerService = programmingTriggerService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.localCITriggerService = localCITriggerService;
        this.localCIProgrammingLanguageFeatureService = localCIProgrammingLanguageFeatureService;
    }

    /**
     * Create a submission, trigger the respective build, and process the results.
     *
     * @param commitHash the hash of the last commit.
     * @param repository the remote repository which was pushed to.
     * @throws LocalCIException        if something goes wrong preparing the queueing of the build job.
     * @throws VersionControlException if the commit belongs to the wrong branch (i.e. not the default branch of the participation).
     */
    public void processNewPush(String commitHash, Repository repository) {
        long timeNanoStart = System.nanoTime();

        Path repositoryFolderPath = repository.getDirectory().toPath();

        LocalVCRepositoryUrl localVCRepositoryUrl = getLocalVCRepositoryUrl(repositoryFolderPath);

        String repositoryTypeOrUserName = localVCRepositoryUrl.getRepositoryTypeOrUserName();
        String projectKey = localVCRepositoryUrl.getProjectKey();

        ProgrammingExercise exercise;

        try {
            exercise = programmingExerciseRepository.findOneByProjectKeyOrThrow(projectKey, false);
        }
        catch (EntityNotFoundException e) {
            throw new LocalCIException("Could not find programming exercise for project key " + projectKey, e);
        }

        ProgrammingLanguage programmingLanguage = exercise.getProgrammingLanguage();
        ProjectType projectType = exercise.getProjectType();

        List<ProjectType> supportedProjectTypes = localCIProgrammingLanguageFeatureService.getProgrammingLanguageFeatures(programmingLanguage).projectTypes();

        if (projectType != null && !supportedProjectTypes.contains(exercise.getProjectType())) {
            throw new LocalCIException("The project type " + exercise.getProjectType() + " is not supported by the local CI.");
        }

        ProgrammingExerciseParticipation participation;

        try {
            participation = programmingExerciseParticipationService.getParticipationForRepository(exercise, repositoryTypeOrUserName, localVCRepositoryUrl.isPracticeRepository(),
                    true);
        }
        catch (EntityNotFoundException e) {
            throw new LocalCIException("Could not find participation for repository " + repositoryTypeOrUserName + " of exercise " + exercise, e);
        }

        try {
            if (commitHash == null) {
                commitHash = getLatestCommitHash(repository);
            }

            if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.getName())) {
                processNewPushToTestRepository(exercise, commitHash, (SolutionProgrammingExerciseParticipation) participation);
                return;
            }

            Commit commit = extractCommitInfo(commitHash, repository);

            // Process push to any repository other than the test repository.
            processNewPushToRepository(participation, commit);
        }
        catch (GitAPIException | IOException e) {
            // This catch clause does not catch exceptions that happen during runBuildJob() as that method is called asynchronously.
            // For exceptions happening inside runBuildJob(), the user is notified. See the addBuildJobToQueue() method in the LocalCIBuildJobManagementService for that.
            throw new LocalCIException("Could not process new push to repository " + localVCRepositoryUrl.getURI() + " and commit " + commitHash + ". No build job was queued.", e);
        }

        log.info("New push processed to repository {} for commit {} in {}. A build job was queued.", localVCRepositoryUrl.getURI(), commitHash,
                TimeLogUtil.formatDurationFrom(timeNanoStart));
    }

    private LocalVCRepositoryUrl getLocalVCRepositoryUrl(Path repositoryFolderPath) {
        try {
            return new LocalVCRepositoryUrl(repositoryFolderPath, localVCBaseUrl);
        }
        catch (LocalVCInternalException e) {
            // This means something is misconfigured.
            throw new LocalCIException("Could not create valid repository URL from path " + repositoryFolderPath, e);
        }
    }

    private String getLatestCommitHash(Repository repository) throws GitAPIException {
        try (Git git = new Git(repository)) {
            RevCommit latestCommit = git.log().setMaxCount(1).call().iterator().next();
            return latestCommit.getName();
        }
    }

    /**
     * Process a new push to the test repository.
     * Build and test the solution repository to make sure all tests are still passing.
     *
     * @param exercise   the exercise for which the push was made.
     * @param commitHash the hash of the last commit to the test repository.
     * @throws LocalCIException if something unexpected goes wrong when creating the submission or triggering the build.
     */
    private void processNewPushToTestRepository(ProgrammingExercise exercise, String commitHash, SolutionProgrammingExerciseParticipation solutionParticipation) {
        // Create a new submission for the solution repository.
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), commitHash);
        }
        catch (EntityNotFoundException | IllegalStateException e) {
            throw new LocalCIException("Could not create submission for solution participation", e);
        }

        programmingMessagingService.notifyUserAboutSubmission(submission, exercise.getId());

        try {
            // Set a flag to inform the instructor that the student results are now outdated.
            programmingTriggerService.setTestCasesChanged(exercise.getId(), true);
        }
        catch (EntityNotFoundException e) {
            throw new LocalCIException("Could not set test cases changed flag", e);
        }

        localCITriggerService.triggerBuild(solutionParticipation, commitHash);

        try {
            programmingTriggerService.triggerTemplateBuildAndNotifyUser(exercise.getId(), submission.getCommitHash(), SubmissionType.TEST);
        }
        catch (EntityNotFoundException e) {
            // Something went wrong while retrieving the template participation.
            // At this point, programmingMessagingService.notifyUserAboutSubmissionError() does not work, because the template participation is not available.
            // The instructor will see in the UI that no build of the template repository was conducted and will receive an error message when triggering the build manually.
            log.error("Something went wrong while triggering the template build for exercise " + exercise.getId() + " after the solution build was finished.", e);
        }
    }

    /**
     * Process a new push to a student's repository or to the template or solution repository of the exercise.
     *
     * @param participation the participation for which the push was made
     * @param commit        the commit that was pushed
     * @throws LocalCIException        if something unexpected goes wrong creating the submission or triggering the build
     * @throws VersionControlException if the commit belongs to the wrong branch (i.e. not the default branch of the participation)
     */
    private void processNewPushToRepository(ProgrammingExerciseParticipation participation, Commit commit) {
        // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.processNewProgrammingSubmission(participation, commit);
        }
        catch (EntityNotFoundException | IllegalStateException | IllegalArgumentException e) {
            throw new LocalCIException("Could not process submission for participation: " + e.getMessage(), e);
        }

        // Remove unnecessary information from the new submission.
        submission.getParticipation().setSubmissions(null);
        programmingMessagingService.notifyUserAboutSubmission(submission, participation.getExercise().getId());
    }

    private Commit extractCommitInfo(String commitHash, Repository repository) throws IOException, GitAPIException {
        RevCommit revCommit;
        String branch = null;

        ObjectId objectId = repository.resolve(commitHash);

        if (objectId == null) {
            throw new LocalCIException("Could not resolve commit hash " + commitHash + " in repository");
        }

        revCommit = repository.parseCommit(objectId);

        // Get the branch name.
        Git git = new Git(repository);
        // Look in the 'refs/heads' namespace for a ref that points to the commit.
        // The returned map contains at most one entry where the key is the commit id and the value denotes the branch which points to it.
        Map<ObjectId, String> objectIdBranchNameMap = git.nameRev().addPrefix("refs/heads").add(objectId).call();
        if (!objectIdBranchNameMap.isEmpty()) {
            branch = objectIdBranchNameMap.get(objectId);
        }
        git.close();

        if (revCommit == null || branch == null) {
            throw new LocalCIException("Something went wrong retrieving the revCommit or the branch.");
        }

        Commit commit = new Commit();
        commit.setCommitHash(commitHash);
        commit.setAuthorName(revCommit.getAuthorIdent().getName());
        commit.setAuthorEmail(revCommit.getAuthorIdent().getEmailAddress());
        commit.setBranch(branch);
        commit.setMessage(revCommit.getFullMessage());

        return commit;
    }
}
