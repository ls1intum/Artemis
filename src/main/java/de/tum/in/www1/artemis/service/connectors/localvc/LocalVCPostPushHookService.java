package de.tum.in.www1.artemis.service.connectors.localvc;

import java.io.File;
import java.util.List;

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
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.LocalVCException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;

@Service
@Profile("localvc")
public class LocalVCPostPushHookService {

    private final Logger log = LoggerFactory.getLogger(LocalVCPostPushHookService.class);

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCPath;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingMessagingService programmingMessagingService;

    public LocalVCPostPushHookService(ProgrammingExerciseRepository programmingExerciseRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingMessagingService programmingMessagingService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingMessagingService = programmingMessagingService;
    }

    /**
     * @param commitHash the hash of the commit that leads to a new submission.
     * @param repository the JGit repository this submission belongs to.
     */
    public void createNewSubmission(String commitHash, Repository repository) {

        File repositoryFolderPath = repository.getDirectory();

        LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(localVCPath, repositoryFolderPath);

        // For pushes to the "tests" repository, no submission is created.
        if (localVCRepositoryUrl.getRepositoryTypeOrUserName().equals(RepositoryType.TESTS.getName())) {
            return;
        }

        List<ProgrammingExercise> exercises = programmingExerciseRepository.findByProjectKey(localVCRepositoryUrl.getProjectKey());
        if (exercises.size() != 1) {
            throw new LocalVCException("No exercise or multiple exercises found for the given project key: " + localVCRepositoryUrl.getProjectKey());
        }

        ProgrammingExercise exercise = exercises.get(0);

        // Retrieve participation for the repository.
        ProgrammingExerciseParticipation participation;

        if (localVCRepositoryUrl.getRepositoryTypeOrUserName().equals(RepositoryType.TEMPLATE.getName())) {
            participation = templateProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exercise.getId()).orElse(null);
        }
        else if (localVCRepositoryUrl.getRepositoryTypeOrUserName().equals(RepositoryType.SOLUTION.getName())) {
            participation = solutionProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exercise.getId()).orElse(null);
        }
        else {
            List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseStudentParticipationRepository
                    .findWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), localVCRepositoryUrl.getRepositoryTypeOrUserName());
            if (participations.size() != 1) {
                participation = null;
            }
            else {
                participation = participations.get(0);
            }
        }

        if (participation == null) {
            throw new LocalVCInternalException("No participation found for repository " + repository.getDirectory().getPath());
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
        }
        catch (Exception ex) {
            log.error("Exception encountered when trying to create a new submission for participation {} with the following commit: {}", participation.getId(), commit, ex);
            throw new LocalVCInternalException();
        }
    }

    private Commit extractCommitInfo(String commitHash, Repository repository) {
        RevCommit revCommit;
        String branch;

        try {
            ObjectId objectId = repository.resolve(commitHash);
            revCommit = repository.parseCommit(objectId);
            branch = repository.getBranch();
        }
        catch (Exception e) {
            throw new LocalVCInternalException(e.getMessage());
        }

        if (revCommit == null || branch == null) {
            throw new LocalVCInternalException();
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
