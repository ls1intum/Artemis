package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
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
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.localvc.LocalVCException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationPushService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.programming.*;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Profile("localci")
public class LocalCIPushService implements ContinuousIntegrationPushService {

    private final Logger log = LoggerFactory.getLogger(LocalCIPushService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final ProgrammingTriggerService programmingTriggerService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final LocalCIExecutorService localCIExecutorService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final LocalCITriggerService localCITriggerService;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    public LocalCIPushService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingMessagingService programmingMessagingService, ProgrammingTriggerService programmingTriggerService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, LocalCIExecutorService localCIExecutorService,
            ProgrammingExerciseGradingService programmingExerciseGradingService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            LocalCITriggerService localCITriggerService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingMessagingService = programmingMessagingService;
        this.programmingTriggerService = programmingTriggerService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.localCIExecutorService = localCIExecutorService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.localCITriggerService = localCITriggerService;
    }

    /**
     * Trigger the respective build and process the results.
     *
     * @param commitHash the hash of the last commit.
     * @param repository the remote repository which was pushed to.
     */
    @Override
    public void processNewPush(String commitHash, Repository repository) {
        long timeNanoStart = System.nanoTime();

        if (commitHash == null) {
            try (Git git = new Git(repository)) {
                RevCommit latestCommit = git.log().setMaxCount(1).call().iterator().next();
                commitHash = latestCommit.getName();
            }
            catch (GitAPIException e) {
                log.error("Could not retrieve latest commit from repository {}.", repository.getDirectory().toPath());
                return;
            }
        }

        Path repositoryFolderPath = repository.getDirectory().toPath();

        LocalVCRepositoryUrl localVCRepositoryUrl;

        try {
            localVCRepositoryUrl = new LocalVCRepositoryUrl(repositoryFolderPath, localVCBaseUrl);
        }
        catch (LocalVCException e) {
            log.error("Could not create valid repository URL from path {}.", repositoryFolderPath);
            return;
        }

        String repositoryTypeOrUserName = localVCRepositoryUrl.getRepositoryTypeOrUserName();
        String projectKey = localVCRepositoryUrl.getProjectKey();

        ProgrammingExercise exercise;

        try {
            List<ProgrammingExercise> exercises;
            exercises = programmingExerciseRepository.findByProjectKey(projectKey);

            if (exercises.size() != 1) {
                throw new EntityNotFoundException("No exercise or multiple exercises found for the given project key: " + projectKey);
            }

            exercise = exercises.get(0);
        }
        catch (EntityNotFoundException e) {
            // This should never happen, as the unambiguous exercise is already retrieved in the LocalVCPushFilter.
            log.error("No exercise or multiple exercises found for the given project key: {}", projectKey);
            return;
        }

        if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.getName())) {
            processNewPushToTestsRepository(exercise.getId(), commitHash);
            return;
        }

        // Process push to any repository other than the tests repository.
        processNewPushToRepository(repositoryTypeOrUserName, exercise, localVCRepositoryUrl, commitHash, repository);

        log.info("New push processed to repository {} in {}.", localVCRepositoryUrl.getURI(), TimeLogUtil.formatDurationFrom(timeNanoStart));
    }

    /**
     * Process a new push to the tests repository.
     * Build and test the solution repository to make sure all tests are still passing.
     *
     * @param exerciseId the id of the exercise.
     * @param commitHash the hash of the last commit to the tests repository.
     */
    private void processNewPushToTestsRepository(Long exerciseId, String commitHash) {
        // Create a new submission for the solution repository.
        ProgrammingSubmission submission = programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exerciseId, commitHash);
        programmingMessagingService.notifyUserAboutSubmission(submission);

        // Set a flag to inform the instructor that the student results are now outdated.
        programmingTriggerService.setTestCasesChanged(exerciseId, true);

        // Retrieve the solution participation.
        SolutionProgrammingExerciseParticipation participation;
        try {
            participation = solutionProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exerciseId);
        }
        catch (EntityNotFoundException e) {
            log.error("No solution participation found for exercise with id {}.", exerciseId);
            return;
        }

        // Trigger a build of the solution repository.
        CompletableFuture<LocalCIBuildResultNotificationDTO> futureSolutionBuildResult = localCIExecutorService.addBuildJobToQueue(participation);
        futureSolutionBuildResult.thenAccept(buildResult -> {
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

            // If the solution participation was updated, also trigger the template participation build.
            ProgrammingSubmission solutionSubmission = (ProgrammingSubmission) result.getSubmission();
            if (!solutionSubmission.belongsToTestRepository() || (submission.belongsToTestRepository() && submission.getResults() != null && !submission.getResults().isEmpty())) {
                // Sanity check. This should not happen, just copied from triggerTemplateBuildIfTestCasesChanged in the ResultResource.
                log.error("The submission of the solution repository build of participation {} does not belong to the test repository.", participation.getId());
                return;
            }

            try {
                programmingTriggerService.triggerTemplateBuildAndNotifyUser(exerciseId, submission.getCommitHash(), SubmissionType.TEST);
            }
            catch (EntityNotFoundException e) {
                log.error("No template participation found for exercise with id {}.", exerciseId);
            }
        });
    }

    /**
     * Process a new push to a student's repository or to the template or solution repository of the exercise.
     */
    private void processNewPushToRepository(String repositoryTypeOrUserName, ProgrammingExercise exercise, LocalVCRepositoryUrl localVCRepositoryUrl, String commitHash,
            Repository repository) {
        // Retrieve participation for the repository.
        ProgrammingExerciseParticipation participation;
        try {
            if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.getName())) {
                participation = templateProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            else if (repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.getName())) {
                participation = solutionProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            else {
                // TODO: Figure out what to do in case of a test run.
                boolean isPracticeRepository = localVCRepositoryUrl.isPracticeRepository();
                participation = programmingExerciseParticipationService.findStudentParticipationByExerciseAndStudentLoginAndTestRun(exercise, repositoryTypeOrUserName,
                        isPracticeRepository, true);
            }
        }
        catch (EntityNotFoundException e) {
            // This should never happen, as the participation is already retrieved in the LocalVCPushFilter.
            log.error("No participation found for the given repository: {}", localVCRepositoryUrl.getURI());
            return;
        }

        Commit commit = extractCommitInfo(commitHash, repository);

        try {
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
        catch (Exception ex) {
            log.error("Exception encountered when trying to create a new submission for participation {} with the following commit: {}", participation.getId(), commit, ex);
            // Throwing an exception here would lead to the Git client request getting stuck.
            // Instead, the user can see in the UI that creating the submission failed.
        }
    }

    private Commit extractCommitInfo(String commitHash, Repository repository) {
        RevCommit revCommit = null;
        String branch = null;

        try {
            ObjectId objectId = repository.resolve(commitHash);
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
        }
        catch (IOException | NullPointerException | GitAPIException e) {
            log.error("Could not resolve commit hash {} to a commit.", commitHash, e);
        }

        Commit commit = new Commit();
        commit.setCommitHash(commitHash);
        commit.setAuthorName(revCommit != null ? revCommit.getAuthorIdent().getName() : null);
        commit.setAuthorEmail(revCommit != null ? revCommit.getAuthorIdent().getEmailAddress() : null);
        commit.setBranch(branch);
        commit.setMessage(revCommit != null ? revCommit.getFullMessage() : null);

        return commit;
    }
}
