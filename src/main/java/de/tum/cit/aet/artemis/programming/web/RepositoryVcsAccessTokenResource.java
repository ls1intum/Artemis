package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.localvc.service.RepositoryVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller that hands out repository-scoped VCS access tokens for course staff (tutors, editors, instructors) for the base repositories (template, tests, solution,
 * auxiliary) of a programming exercise. Access is restricted to at least tutor in the exercise's course; the actual git authorization (read vs. write) is still enforced on every
 * git operation, so handing out a token never widens a user's permissions.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class RepositoryVcsAccessTokenResource {

    private static final Logger log = LoggerFactory.getLogger(RepositoryVcsAccessTokenResource.class);

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final RepositoryVcsAccessTokenService repositoryVcsAccessTokenService;

    public RepositoryVcsAccessTokenResource(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            RepositoryVcsAccessTokenService repositoryVcsAccessTokenService) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.repositoryVcsAccessTokenService = repositoryVcsAccessTokenService;
    }

    /**
     * GET repository-vcs-access-token : Returns the repository-scoped VCS access token of the current user for a base repository of a programming exercise. Returns 404 if no token
     * exists yet (the client then creates one via the PUT endpoint).
     *
     * @param exerciseId            the id of the programming exercise
     * @param repositoryType        the base repository type (TEMPLATE, SOLUTION, TESTS or AUXILIARY)
     * @param auxiliaryRepositoryId the id of the auxiliary repository (required for {@link RepositoryType#AUXILIARY}, otherwise ignored)
     * @return the token string
     */
    @GetMapping("repository-vcs-access-token")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<String> getRepositoryVcsAccessToken(@RequestParam("exerciseId") long exerciseId, @RequestParam("repositoryType") RepositoryType repositoryType,
            @RequestParam(value = "auxiliaryRepositoryId", required = false) Long auxiliaryRepositoryId) {
        User user = userRepository.getUser();
        log.debug("REST request to get repository VCS access token of user {} for {} repository of exercise {}", user.getLogin(), repositoryType, exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
        return ResponseEntity.ok(repositoryVcsAccessTokenService.findTokenOrElseThrow(user, exercise, repositoryType, auxiliaryRepositoryId).getVcsAccessToken());
    }

    /**
     * PUT repository-vcs-access-token : Returns the existing repository-scoped VCS access token of the current user for a base repository, creating it if none exists.
     *
     * @param exerciseId            the id of the programming exercise
     * @param repositoryType        the base repository type (TEMPLATE, SOLUTION, TESTS or AUXILIARY)
     * @param auxiliaryRepositoryId the id of the auxiliary repository (required for {@link RepositoryType#AUXILIARY}, otherwise ignored)
     * @return the token string
     */
    @PutMapping("repository-vcs-access-token")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<String> createRepositoryVcsAccessToken(@RequestParam("exerciseId") long exerciseId, @RequestParam("repositoryType") RepositoryType repositoryType,
            @RequestParam(value = "auxiliaryRepositoryId", required = false) Long auxiliaryRepositoryId) {
        User user = userRepository.getUser();
        log.debug("REST request to create a repository VCS access token for user {} for {} repository of exercise {}", user.getLogin(), repositoryType, exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
        return ResponseEntity.ok(repositoryVcsAccessTokenService.getOrCreateToken(user, exercise, repositoryType, auxiliaryRepositoryId).getVcsAccessToken());
    }
}
