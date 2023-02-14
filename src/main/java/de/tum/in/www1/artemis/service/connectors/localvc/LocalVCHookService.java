package de.tum.in.www1.artemis.service.connectors.localvc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

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
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Profile("localvc")
public class LocalVCHookService {

    private final Logger log = LoggerFactory.getLogger(LocalVCHookService.class);

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCPath;

    private final ProgrammingExerciseService programmingExerciseService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    public LocalVCHookService(ProgrammingExerciseService programmingExerciseService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingMessagingService programmingMessagingService, Optional<ContinuousIntegrationService> continuousIntegrationService) {
        this.programmingExerciseService = programmingExerciseService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingMessagingService = programmingMessagingService;
        this.continuousIntegrationService = continuousIntegrationService;
    }

    /**
     * @param commitHash the hash of the commit that leads to a new submission.
     * @param repository the JGit repository this submission belongs to.
     */
    public void createNewSubmission(String commitHash, Repository repository) {

        Path repositoryFolderPath = repository.getDirectory().toPath();

        LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(localVCPath, repositoryFolderPath);

        // For pushes to the "tests" repository, no submission is created.
        if (localVCRepositoryUrl.getRepositoryTypeOrUserName().equals(RepositoryType.TESTS.getName())) {
            return;
        }

        ProgrammingExercise exercise;

        try {
            exercise = programmingExerciseService.findOneByProjectKey(localVCRepositoryUrl.getProjectKey(), false);
        }
        catch (EntityNotFoundException e) {
            // This should never happen, as the unambiguous exercise is already retrieved in the LocalVCPushFilter.
            log.error("No exercise or multiple exercises found for the given project key: {}", localVCRepositoryUrl.getProjectKey());
            return;
        }

        // Retrieve participation for the repository.
        ProgrammingExerciseParticipation participation;

        try {
            if (localVCRepositoryUrl.getRepositoryTypeOrUserName().equals(RepositoryType.TEMPLATE.getName())) {
                participation = templateProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            else if (localVCRepositoryUrl.getRepositoryTypeOrUserName().equals(RepositoryType.SOLUTION.getName())) {
                participation = solutionProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            else {
                participation = programmingExerciseParticipationService.findStudentParticipationByExerciseAndStudentLoginAndTestRun(exercise,
                        localVCRepositoryUrl.getRepositoryTypeOrUserName(), localVCRepositoryUrl.isTestRunRepository(), true);
            }
        }
        catch (EntityNotFoundException e) {
            // This should never happen, as the participation is already retrieved in the LocalVCPushFilter.
            log.error("No participation found for the given repository: {}", repository.getDirectory().getPath());
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
            continuousIntegrationService.get().triggerBuild(participation);
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
