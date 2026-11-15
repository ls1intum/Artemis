package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Service for managing programming exercise repositories and participations
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class RepositoryParticipationService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryParticipationService.class);

    private final ParticipationRepository participationRepository;

    private final GitService gitService;

    private final UserRepository userRepository;

    private final RepositoryAccessService repositoryAccessService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final RepositoryService repositoryService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    /**
     * Constructor for the RepositoryParticipationService.
     *
     * @param participationRepository         the participation repository
     * @param gitService                      the git service
     * @param userRepository                  the user repository
     * @param repositoryAccessService         the repository access service
     * @param programmingExerciseRepository   the programming exercise repository
     * @param repositoryService               the repository service used to read file contents
     * @param programmingSubmissionRepository the programming submission repository
     */
    public RepositoryParticipationService(ParticipationRepository participationRepository, GitService gitService, UserRepository userRepository,
            RepositoryAccessService repositoryAccessService, ProgrammingExerciseRepository programmingExerciseRepository, RepositoryService repositoryService,
            ProgrammingSubmissionRepository programmingSubmissionRepository) {
        this.participationRepository = participationRepository;
        this.gitService = gitService;
        this.userRepository = userRepository;
        this.repositoryAccessService = repositoryAccessService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.repositoryService = repositoryService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
    }

    /**
     * Returns the files of the participation's repository as they are in its latest commit, read directly from the bare
     * repository.
     * <p>
     * Reading the bare repository in place is what makes this cheap. Checking the working copy out on the server
     * instead costs a clone, a pull and disk space on every single request, which is only worth paying when
     * uncommitted changes made through the online editor have to be visible.
     * <p>
     * Binary files are never included, because the content is returned as a {@link String}.
     *
     * @param participation the participation whose repository files are requested
     * @return a map of file path to file content, empty if the repository has no commit yet
     */
    public Map<String, String> getFilesContentFromLastCommit(ProgrammingExerciseParticipation participation) {
        try {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(participation.getVcsRepositoryUri());
        }
        catch (IOException e) {
            // Log the cause before it is replaced by a generic 500, otherwise the JGit or filesystem stack trace is lost.
            log.error("Could not read the repository files of participation {} from the bare repository", participation.getId(), e);
            throw new InternalServerErrorException("Could not retrieve the repository files content for participation " + participation.getId());
        }
    }

    /**
     * What the plagiarism view shows for a participation: the participation and the compared commit, or no commit if the live repository is shown.
     *
     * @param participation the participation whose repository is shown
     * @param commitHash    the hash of the compared commit, null for the live repository
     */
    public record PlagiarismViewTarget(ProgrammingExerciseParticipation participation, @Nullable String commitHash) {
    }

    /**
     * Checks the access to the plagiarism view of the given participation and determines which state of the repository is shown.
     *
     * @param participationId the id of the participation to retrieve the repository for
     * @return the participation and the compared commit, the commit is null if the live repository is shown
     * @throws AccessForbiddenException if the user does not have access to the repository
     */
    public PlagiarismViewTarget resolvePlagiarismViewTarget(Long participationId) throws AccessForbiddenException {
        Participation participation = participationRepository.findByIdElseThrow(participationId);

        if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
            throw new IllegalArgumentException("Participation is not a programming exercise participation");
        }

        User user = userRepository.getUserWithAuthorities();
        boolean liveAccess = repositoryAccessService.checkHasAccessToPlagiarismSubmission(programmingParticipation, user, RepositoryActionType.READ);
        if (liveAccess) {
            return new PlagiarismViewTarget(programmingParticipation, null);
        }
        String comparedCommitHash = findComparedCommitHash(programmingParticipation)
                .orElseThrow(() -> new AccessForbiddenException("The compared state of this repository is not available."));
        return new PlagiarismViewTarget(programmingParticipation, comparedCommitHash);
    }

    /**
     * Finds the commit that was compared for the participation. Results created before the commit was stored fall back to the latest commit that was submitted
     * not later than the creation of the result, both dates are recorded by the server.
     */
    private Optional<String> findComparedCommitHash(ProgrammingExerciseParticipation programmingParticipation) {
        Optional<String> stored = repositoryAccessService.findStoredComparedCommitHash(programmingParticipation);
        if (stored.isPresent()) {
            return stored;
        }
        return repositoryAccessService.findComparisonCreatedDate(programmingParticipation)
                .flatMap(createdDate -> programmingSubmissionRepository
                        .findCommitHashesSubmittedUntil(programmingParticipation.getId(), ZonedDateTime.ofInstant(createdDate, ZoneId.systemDefault()), Pageable.ofSize(1)).stream()
                        .findFirst());
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
        String branch = programmingParticipation instanceof ProgrammingExerciseStudentParticipation studentParticipation ? studentParticipation.getBranch()
                : programmingExerciseRepository.findBranchByExerciseId(programmingParticipation.getExercise().getId());
        return gitService.getOrCheckoutRepository(repositoryUri, pullOnGet, branch, false);
    }
}
