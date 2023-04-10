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
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

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

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    public LocalCIPushService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingMessagingService programmingMessagingService, ProgrammingTriggerService programmingTriggerService, LocalCIExecutorService localCIExecutorService,
            ProgrammingExerciseGradingService programmingExerciseGradingService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            LocalCITriggerService localCITriggerService, SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingMessagingService = programmingMessagingService;
        this.programmingTriggerService = programmingTriggerService;
        this.localCIExecutorService = localCIExecutorService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.localCITriggerService = localCITriggerService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
    }

    /**
     * Trigger the respective build and process the results.
     *
     * @param commitHash the hash of the last commit.
     * @param repository the remote repository which was pushed to.
     */
    public void processNewPush(String commitHash, Repository repository) {
        long timeNanoStart = System.nanoTime();

        if (commitHash == null) {
            commitHash = getLatestCommitHash(repository);
        }

        Path repositoryFolderPath = repository.getDirectory().toPath();

        LocalVCRepositoryUrl localVCRepositoryUrl = getLocalVCRepositoryUrl(repositoryFolderPath);

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

        // Retrieve the user from the commit.
        Commit commit = extractCommitInfo(commitHash, repository);

        if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.getName())) {
            processNewPushToTestsRepository(exercise, commitHash, localVCRepositoryUrl);
            return;
        }

        // Process push to any repository other than the tests repository.
        processNewPushToRepository(repositoryTypeOrUserName, exercise, localVCRepositoryUrl, commit);

        log.info("New push processed to repository {} in {}.", localVCRepositoryUrl.getURI(), TimeLogUtil.formatDurationFrom(timeNanoStart));
    }

    private LocalVCRepositoryUrl getLocalVCRepositoryUrl(Path repositoryFolderPath) {
        try {
            return new LocalVCRepositoryUrl(repositoryFolderPath, localVCBaseUrl);
        }
        catch (LocalVCException e) {
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
    private void processNewPushToTestsRepository(ProgrammingExercise exercise, String commitHash, LocalVCRepositoryUrl localVCRepositoryUrl) {
        // Create a new submission for the solution repository.
        ProgrammingSubmission submission = programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), commitHash);
        programmingMessagingService.notifyUserAboutSubmission(submission);

        // Set a flag to inform the instructor that the student results are now outdated.
        programmingTriggerService.setTestCasesChanged(exercise.getId(), true);

        // Retrieve the solution participation.
        SolutionProgrammingExerciseParticipation participation = (SolutionProgrammingExerciseParticipation) getParticipation(RepositoryType.SOLUTION.toString(), exercise,
                localVCRepositoryUrl);

        // Trigger a build of the solution repository.
        CompletableFuture<LocalCIBuildResult> futureSolutionBuildResult = localCIExecutorService.addBuildJobToQueue(participation);
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

            // The solution participation received a new result, also trigger a build of the template repository.
            try {
                programmingTriggerService.triggerTemplateBuildAndNotifyUser(exercise.getId(), submission.getCommitHash(), SubmissionType.TEST);
            }
            catch (EntityNotFoundException e) {
                log.error("No template participation found for exercise with id {}.", exercise.getId());
            }
        });
    }

    /**
     * Process a new push to a student's repository or to the template or solution repository of the exercise.
     */
    private void processNewPushToRepository(String repositoryTypeOrUserName, ProgrammingExercise exercise, LocalVCRepositoryUrl localVCRepositoryUrl, Commit commit) {
        // Retrieve participation for the repository.
        ProgrammingExerciseParticipation participation = getParticipation(repositoryTypeOrUserName, exercise, localVCRepositoryUrl);

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

    private ProgrammingExerciseParticipation getParticipation(String repositoryTypeOrUserName, ProgrammingExercise exercise, LocalVCRepositoryUrl localVCRepositoryUrl) {
        try {
            if (repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())) {
                return solutionProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            else if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString())) {
                return templateProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
            }

            if (exercise.isTeamMode()) {
                // The repositoryTypeOrUserName is the team short name.
                return programmingExerciseParticipationService.findTeamParticipationByExerciseAndTeamShortNameOrThrow(exercise, repositoryTypeOrUserName, true);
            }

            return programmingExerciseParticipationService.findStudentParticipationByExerciseAndStudentLoginAndTestRunOrThrow(exercise, repositoryTypeOrUserName,
                    localVCRepositoryUrl.isPracticeRepository(), true);
        }
        catch (EntityNotFoundException e) {
            throw new LocalCIException("No participation found for the given repository: " + localVCRepositoryUrl.getURI(), e);
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
        catch (IOException | GitAPIException e) {
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
