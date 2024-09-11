package de.tum.cit.aet.artemis.service.programming;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.service.connectors.GitService;
import de.tum.cit.aet.artemis.service.connectors.vcs.VersionControlService;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.repository.RepositoryActionType;

/**
 * Service for managing programming exercise repositories and participations
 */
@Profile(PROFILE_CORE)
@Service
public class RepositoryParticipationService {

    private final ParticipationRepository participationRepository;

    private final GitService gitService;

    private final UserRepository userRepository;

    private final RepositoryAccessService repositoryAccessService;

    private final Optional<VersionControlService> versionControlService;

    /**
     * Constructor for the RepositoryParticipationService.
     *
     * @param participationRepository the participation repository
     * @param gitService              the git service
     * @param userRepository          the user repository
     */
    public RepositoryParticipationService(ParticipationRepository participationRepository, GitService gitService, UserRepository userRepository,
            RepositoryAccessService repositoryAccessService, Optional<VersionControlService> versionControlService) {
        this.participationRepository = participationRepository;
        this.gitService = gitService;
        this.userRepository = userRepository;
        this.repositoryAccessService = repositoryAccessService;
        this.versionControlService = versionControlService;
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

        repositoryAccessService.checkHasAccessToPlagiarismSubmission(programmingParticipation, userRepository.getUserWithGroupsAndAuthorities(), RepositoryActionType.READ);

        return getRepositoryFromGitService(true, programmingParticipation);
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
            String branch = versionControlService.orElseThrow().getOrRetrieveBranchOfParticipation(programmingParticipation);
            return gitService.getOrCheckoutRepository(repositoryUri, pullOnGet, branch);
        }
    }
}
