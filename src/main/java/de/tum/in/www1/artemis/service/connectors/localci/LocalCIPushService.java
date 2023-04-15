package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

/**
 * Service for further processing authenticated and authorized pushes in the local CI system.
 */
@Service
@Profile("localci")
public class LocalCIPushService {

    private final Logger log = LoggerFactory.getLogger(LocalCIPushService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final ProgrammingTriggerService programmingTriggerService;

    private final LocalCIExecutorService localCIExecutorService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final LocalCITriggerService localCITriggerService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    public LocalCIPushService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingMessagingService programmingMessagingService, ProgrammingTriggerService programmingTriggerService, LocalCIExecutorService localCIExecutorService,
            ProgrammingExerciseGradingService programmingExerciseGradingService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            LocalCITriggerService localCITriggerService, AuthorizationCheckService authorizationCheckService, UserRepository userRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingMessagingService = programmingMessagingService;
        this.programmingTriggerService = programmingTriggerService;
        this.localCIExecutorService = localCIExecutorService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.localCITriggerService = localCITriggerService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
    }

    /**
     * Trigger the respective build and process the results.
     *
     * @param commitHash the hash of the last commit.
     * @param repository the remote repository which was pushed to.
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
            // This should never happen, as the exercise is already retrieved in the LocalVCPushFilter.
            throw new LocalCIException("Programming exercise with project key " + projectKey + " not found");
        }

        ProgrammingExerciseParticipation participation;

        try {
            if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.getName())) {
                // For pushes to the tests repository, the solution repository is built first.
                participation = programmingExerciseParticipationService.findParticipationByRepositoryTypeOrUserNameAndExerciseAndTestRunOrThrow(RepositoryType.SOLUTION.toString(),
                        exercise, false, true);
            }
            else {
                // The repository is a test run repository either if the repository URL contains "-practice-" or if the exercise is an exam exercise and the repository's owner is
                // at least an editor (exam test run repository).
                boolean isTestRunRepository = localVCRepositoryUrl.isPracticeRepository() || (exercise.isExamExercise()
                        && !repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString()) && !repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())
                        && authorizationCheckService.isAtLeastEditorForExercise(exercise, userRepository.getUserByLoginElseThrow(repositoryTypeOrUserName)));
                participation = programmingExerciseParticipationService.findParticipationByRepositoryTypeOrUserNameAndExerciseAndTestRunOrThrow(repositoryTypeOrUserName, exercise,
                        isTestRunRepository, true);
            }
        }
        catch (EntityNotFoundException e) {
            // This should never happen, as the participation is already retrieved in the LocalVCPushFilter.
            throw new LocalCIException("No participation found for the given repository");
        }

        try {
            if (commitHash == null) {
                commitHash = getLatestCommitHash(repository);
            }

            Commit commit = extractCommitInfo(commitHash, repository);

            if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.getName())) {
                processNewPushToTestsRepository(exercise, commitHash, (SolutionProgrammingExerciseParticipation) participation);
                return;
            }

            // Process push to any repository other than the tests repository.
            processNewPushToRepository(participation, commit);

        }
        catch (Exception e) {
            // In case anything goes wrong, notify the user.
            // Throwing an exception here would lead to the Git client request getting stuck.
            // Instead, the user can see in the UI that creating the submission failed.
            log.error("Exception encountered when trying to process a new push to the repository {} with the following commit: {}", repository.getDirectory().getName(), commitHash,
                    e);
            BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), participation.getId());
            // This cast to Participation is safe as the participation is either a ProgrammingExerciseStudentParticipation, a TemplateProgrammingExerciseParticipation, or a
            // SolutionProgrammingExerciseParticipation, which all extend Participation.
            programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation, error);
        }

        log.info("New push processed to repository {} in {}.", localVCRepositoryUrl.getURI(), TimeLogUtil.formatDurationFrom(timeNanoStart));
    }

    private LocalVCRepositoryUrl getLocalVCRepositoryUrl(Path repositoryFolderPath) {
        try {
            return new LocalVCRepositoryUrl(repositoryFolderPath, localVCBaseUrl);
        }
        catch (LocalVCException e) {
            // This means something is misconfigured.
            throw new LocalCIException("Could not create valid repository URL from path " + repositoryFolderPath, e);
        }
    }

    private String getLatestCommitHash(Repository repository) {
        try (Git git = new Git(repository)) {
            RevCommit latestCommit = git.log().setMaxCount(1).call().iterator().next();
            return latestCommit.getName();
        }
        catch (GitAPIException e) {
            throw new LocalCIException("Could not retrieve latest commit from repository " + repository.getDirectory().toPath(), e);
        }
    }

    /**
     * Process a new push to the tests repository.
     * Build and test the solution repository to make sure all tests are still passing.
     *
     * @param exercise   the exercise for which the push was made.
     * @param commitHash the hash of the last commit to the tests repository.
     */
    private void processNewPushToTestsRepository(ProgrammingExercise exercise, String commitHash, SolutionProgrammingExerciseParticipation participation) {
        // Create a new submission for the solution repository.
        ProgrammingSubmission submission = programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), commitHash);
        programmingMessagingService.notifyUserAboutSubmission(submission);

        // Set a flag to inform the instructor that the student results are now outdated.
        programmingTriggerService.setTestCasesChanged(exercise.getId(), true);

        // Trigger a build of the solution repository.
        CompletableFuture<LocalCIBuildResult> futureSolutionBuildResult = localCIExecutorService.addBuildJobToQueue(participation);
        futureSolutionBuildResult.whenComplete((buildResult, exception) -> {
            if (exception != null) {
                log.error("Exception encountered when trying to process a new push to the tests repository of exercise {} with the following commit: {}", exercise.getId(),
                        commitHash, exception);
                return;
            }

            // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
            // Therefore, a mock auth object has to be created.
            SecurityUtils.setAuthorizationObject();

            // Process the result.
            Optional<Result> optResult = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult);
            if (optResult.isEmpty()) {
                log.error("No result found for solution repository build of participation {}.", participation.getId());
                return;
            }

            Result result = optResult.get();
            // Notify the user about the new solution result.
            programmingMessagingService.notifyUserAboutNewResult(result, participation);

            // The solution participation received a new result, also trigger a build of the template repository.
            try {
                programmingTriggerService.triggerTemplateBuildAndNotifyUser(exercise.getId(), submission.getCommitHash(), SubmissionType.TEST);
            }
            catch (EntityNotFoundException e) {
                log.error("Could not trigger the template build for exercise with id {} because no template participation was found.", exercise.getId());
            }
        }).join(); // Wait for the completion and rethrow any exceptions.
    }

    /**
     * Process a new push to a student's repository or to the template or solution repository of the exercise.
     */
    private void processNewPushToRepository(ProgrammingExerciseParticipation participation, Commit commit) {
        // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();
        ProgrammingSubmission submission = programmingSubmissionService.processNewProgrammingSubmission(participation, commit);
        // Remove unnecessary information from the new submission.
        submission.getParticipation().setSubmissions(null);
        programmingMessagingService.notifyUserAboutSubmission(submission);

        // Trigger the build for the new submission on the local CI system.
        localCITriggerService.triggerBuild(participation);
    }

    private Commit extractCommitInfo(String commitHash, Repository repository) {
        RevCommit revCommit;
        String branch = null;

        try {
            ObjectId objectId = repository.resolve(commitHash);
            if (objectId == null) {
                throw new LocalCIException("Unable to resolve commit hash to an ObjectId");
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
        }
        catch (IOException | GitAPIException e) {
            throw new LocalCIException("Could not resolve commit hash " + commitHash + " to a commit.", e);
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
