package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

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

    private final RepositoryAccessService repositoryAccessService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final RepositoryService repositoryService;

    /**
     * Constructor for the RepositoryParticipationService.
     *
     * @param participationRepository       the participation repository
     * @param gitService                    the git service
     * @param userRepository                the user repository
     * @param repositoryAccessService       the repository access service
     * @param programmingExerciseRepository the programming exercise repository
     * @param repositoryService             the repository service used to read file contents
     */
    public RepositoryParticipationService(ParticipationRepository participationRepository, GitService gitService, UserRepository userRepository,
            RepositoryAccessService repositoryAccessService, ProgrammingExerciseRepository programmingExerciseRepository, RepositoryService repositoryService) {
        this.participationRepository = participationRepository;
        this.gitService = gitService;
        this.userRepository = userRepository;
        this.repositoryAccessService = repositoryAccessService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.repositoryService = repositoryService;
    }

    /**
     * Checks out the repository for the given participation and returns its files together with their content.
     * <p>
     * The owning {@link ProgrammingExercise} is taken as an explicit parameter and attached to the participation here, so
     * resolving the branch (which needs the exercise id) never relies on a lazily-loaded back-reference being hydrated
     * by the caller. This avoids a hidden precondition (and a {@code LazyInitializationException} when the participation
     * was loaded in a different persistence context).
     * <p>
     * This method performs <strong>no</strong> authorization check — the caller is responsible for ensuring the current
     * user may read the participation's repository.
     *
     * @param participation the (solution/template/student) participation whose repository files are requested
     * @param exercise      the owning programming exercise (used to resolve the repository branch)
     * @param omitBinaries  whether to omit binary files to reduce the payload size
     * @return a map of file path to file content
     */
    public Map<String, String> getFilesContentFromWorkingCopy(ProgrammingExerciseParticipation participation, ProgrammingExercise exercise, boolean omitBinaries) {
        participation.setProgrammingExercise(exercise);
        try {
            Repository repository = getRepositoryFromGitService(true, participation);
            return repositoryService.getFilesContentFromWorkingCopy(repository, omitBinaries);
        }
        catch (GitAPIException e) {
            throw new InternalServerErrorException("Could not retrieve the repository files content for participation " + participation.getId());
        }
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

        repositoryAccessService.checkHasAccessToPlagiarismSubmission(programmingParticipation, userRepository.getUserWithCourseRolesAndAuthorities(), RepositoryActionType.READ);

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
        String branch = programmingParticipation instanceof ProgrammingExerciseStudentParticipation studentParticipation ? studentParticipation.getBranch()
                : programmingExerciseRepository.findBranchByExerciseId(programmingParticipation.getExercise().getId());
        return gitService.getOrCheckoutRepository(repositoryUri, pullOnGet, branch, false);
    }
}
