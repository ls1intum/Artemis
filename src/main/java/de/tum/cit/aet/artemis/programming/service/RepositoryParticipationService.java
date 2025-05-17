package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCGitBranchService;

/**
 * Service for managing programming exercise repositories and participations
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class RepositoryParticipationService {

    private final ParticipationRepository participationRepository;

    private final GitService gitService;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final Optional<LocalVCGitBranchService> localVCGitBranchService;

    private final Optional<PlagiarismApi> plagiarismApi;

    /**
     * Constructor for the RepositoryParticipationService.
     *
     * @param participationRepository the participation repository
     * @param gitService              the git service
     * @param userRepository          the user repository
     */
    public RepositoryParticipationService(ParticipationRepository participationRepository, GitService gitService, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService, Optional<LocalVCGitBranchService> localVCGitBranchService, Optional<PlagiarismApi> plagiarismApi) {
        this.participationRepository = participationRepository;
        this.gitService = gitService;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.localVCGitBranchService = localVCGitBranchService;
        this.plagiarismApi = plagiarismApi;
    }

    /**
     * Get the repository for the plagiarism view of the given participation.
     *
     * @param participationId the id of the participation to retrieve the repository for
     * @return the repository for the plagiarism view of the given participation
     * @throws GitAPIException          if the repository could not be accessed
     * @throws AccessForbiddenException if the user does not have access to the repository
     */
    public Repository getRepositoryForPlagiarismView(Long participationId) throws GitAPIException, AccessForbiddenException {
        Participation participation = participationRepository.findByIdElseThrow(participationId);

        if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
            throw new IllegalArgumentException("Participation is not a programming exercise participation");
        }

        checkHasAccessToPlagiarismSubmission(programmingParticipation, userRepository.getUserWithGroupsAndAuthorities());

        return getRepositoryFromGitService(true, programmingParticipation);
    }

    /**
     * Checks if the user has access to the plagiarism submission of the given programming participation.
     *
     * @param programmingParticipation The participation for which the plagiarism submission should be accessed.
     * @param user                     The user who wants to access the plagiarism submission.
     * @throws AccessForbiddenException If the user is not allowed to access the plagiarism submission.
     */
    private void checkHasAccessToPlagiarismSubmission(ProgrammingExerciseParticipation programmingParticipation, User user) throws AccessForbiddenException {
        boolean isAtLeastTeachingAssistant = authorizationCheckService
                .isAtLeastTeachingAssistantInCourse(programmingParticipation.getProgrammingExercise().getCourseViaExerciseGroupOrCourseMember(), user);
        if (isAtLeastTeachingAssistant) {
            return;
        }
        if (plagiarismApi.isEmpty() || plagiarismApi.get().hasAccessToSubmission(programmingParticipation.getId(), user.getLogin(), (Participation) programmingParticipation)) {
            return;
        }
        throw new AccessForbiddenException("You are not allowed to access the plagiarism result of this programming exercise.");
    }

    /**
     * Get the repository for the given participation from the git service.
     *
     * @param pullOnGet                whether to pull the repository before returning it
     * @param programmingParticipation the participation to retrieve the repository for
     * @return the repository for the given participation
     * @throws GitAPIException if the repository could not be accessed
     */
    public Repository getRepositoryFromGitService(boolean pullOnGet, ProgrammingExerciseParticipation programmingParticipation) throws GitAPIException {
        var repositoryUri = programmingParticipation.getVcsRepositoryUri();

        // This check reduces the amount of REST-calls that retrieve the default branch of a repository.
        // Retrieving the default branch is not necessary if the repository is already cached.
        if (gitService.isRepositoryCached(repositoryUri)) {
            return gitService.getOrCheckoutRepository(repositoryUri, pullOnGet);
        }
        else {
            String branch = localVCGitBranchService.orElseThrow().getOrRetrieveBranchOfParticipation(programmingParticipation);
            return gitService.getOrCheckoutRepository(repositoryUri, pullOnGet, branch);
        }
    }
}
