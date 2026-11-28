package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentDraftRules;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseRepositoriesDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseRepositoryLocationDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Tells a coding agent where the git repositories of a programming exercise are and whether it may push to them, so that it can clone them with the
 * personal access token that it already holds.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseRepositoryLocationService {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseDraftGuardService programmingExerciseDraftGuardService;

    public ProgrammingExerciseRepositoryLocationService(ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseDraftGuardService programmingExerciseDraftGuardService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseDraftGuardService = programmingExerciseDraftGuardService;
    }

    /**
     * Locates the repositories of an exercise.
     *
     * @param exerciseId the id of the exercise
     * @param username   the login of the user, which is the user name of the git credentials
     * @return where the repositories are, which branch to use and whether a push is allowed now
     * @throws AccessForbiddenException if the exercise belongs to an exam, which a token cannot reach
     */
    public ProgrammingExerciseRepositoriesDTO locate(long exerciseId, String username) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
        AgentDraftRules.rejectExamExercise(exercise.isExamExercise());

        // An exercise whose setup failed may lack an address, which is then left out rather than failing the whole answer.
        List<ProgrammingExerciseRepositoryLocationDTO> repositories = new ArrayList<>();
        for (RepositoryType type : List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS)) {
            LocalVCRepositoryUri uri = exercise.getRepositoryURI(type);
            if (uri != null) {
                repositories.add(location(type, null, uri.toString()));
            }
        }
        for (AuxiliaryRepository auxiliaryRepository : exercise.getAuxiliaryRepositories()) {
            if (auxiliaryRepository.getVcsRepositoryUri() != null) {
                repositories.add(location(RepositoryType.AUXILIARY, auxiliaryRepository.getName(), auxiliaryRepository.getVcsRepositoryUri().toString()));
            }
        }

        Optional<String> blocked = programmingExerciseDraftGuardService.repositoryPushBlockedReason(exerciseId, exercise.getReleaseDate(), exercise.getStartDate());
        String branch = programmingExerciseRepository.findBranchByExerciseId(exerciseId);
        return new ProgrammingExerciseRepositoriesDTO(username, branch, blocked.isEmpty(), blocked.orElse(null), repositories);
    }

    private static ProgrammingExerciseRepositoryLocationDTO location(RepositoryType type, String name, String cloneUrl) {
        return new ProgrammingExerciseRepositoryLocationDTO(type.name().toLowerCase(Locale.ROOT), name, cloneUrl);
    }
}
