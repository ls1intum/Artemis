package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Objects;

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
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.localvc.service.RepositoryVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * REST controller that hands out repository-scoped VCS access tokens for course staff (tutors, editors, instructors). A token can be bound to a base repository (template, tests,
 * solution, auxiliary) or to a student assignment repository ({@link RepositoryType#USER}) of a programming exercise. Access is restricted to at least tutor in the exercise's
 * course; for a student repository the caller must additionally be allowed to read that exact repository. The actual git authorization (read vs. write) is still enforced on every
 * git operation, so handing out a token never widens a user's permissions.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage("access/vcs-access-tokens")
@RestController
@RequestMapping("api/programming/")
public class RepositoryVcsAccessTokenResource {

    private static final Logger log = LoggerFactory.getLogger(RepositoryVcsAccessTokenResource.class);

    private static final String ENTITY_NAME = "repositoryVcsAccessToken";

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final RepositoryVcsAccessTokenService repositoryVcsAccessTokenService;

    private final RepositoryAccessService repositoryAccessService;

    public RepositoryVcsAccessTokenResource(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, RepositoryVcsAccessTokenService repositoryVcsAccessTokenService,
            RepositoryAccessService repositoryAccessService) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.repositoryVcsAccessTokenService = repositoryVcsAccessTokenService;
        this.repositoryAccessService = repositoryAccessService;
    }

    /**
     * A student participation together with its programming exercise, resolved and authorized for the requesting staff member.
     */
    private record AuthorizedStudentRepository(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation) {
    }

    /**
     * GET repository-vcs-access-token : Returns the repository-scoped VCS access token of the current user for a repository of a programming exercise. Returns 404 if no token
     * exists yet (the client then creates one via the PUT endpoint).
     *
     * @param exerciseId            the id of the programming exercise
     * @param repositoryType        the repository type (TEMPLATE, SOLUTION, TESTS, AUXILIARY or USER)
     * @param auxiliaryRepositoryId the id of the auxiliary repository (required for {@link RepositoryType#AUXILIARY}, otherwise ignored)
     * @param participationId       the id of the student participation (required for {@link RepositoryType#USER}, otherwise ignored)
     * @return the token string
     */
    @GetMapping("repository-vcs-access-token")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<String> getRepositoryVcsAccessToken(@RequestParam("exerciseId") long exerciseId, @RequestParam("repositoryType") RepositoryType repositoryType,
            @RequestParam(value = "auxiliaryRepositoryId", required = false) Long auxiliaryRepositoryId,
            @RequestParam(value = "participationId", required = false) Long participationId) {
        validateRepositoryTypeInput(repositoryType, auxiliaryRepositoryId, participationId);
        User user = userRepository.getUser();
        log.debug("REST request to get repository VCS access token of user {} for {} repository of exercise {}", user.getLogin(), repositoryType, exerciseId);
        if (repositoryType == RepositoryType.USER) {
            AuthorizedStudentRepository authorized = resolveAndAuthorizeStudentParticipation(exerciseId, participationId, user);
            return ResponseEntity.ok(repositoryVcsAccessTokenService.findStudentRepositoryTokenOrElseThrow(user, authorized.participation()).getVcsAccessToken());
        }
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
        return ResponseEntity.ok(repositoryVcsAccessTokenService.findTokenOrElseThrow(user, exercise, repositoryType, auxiliaryRepositoryId).getVcsAccessToken());
    }

    /**
     * PUT repository-vcs-access-token : Returns the existing repository-scoped VCS access token of the current user for a repository, creating it if none exists.
     *
     * @param exerciseId            the id of the programming exercise
     * @param repositoryType        the repository type (TEMPLATE, SOLUTION, TESTS, AUXILIARY or USER)
     * @param auxiliaryRepositoryId the id of the auxiliary repository (required for {@link RepositoryType#AUXILIARY}, otherwise ignored)
     * @param participationId       the id of the student participation (required for {@link RepositoryType#USER}, otherwise ignored)
     * @return the token string
     */
    @PutMapping("repository-vcs-access-token")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<String> createRepositoryVcsAccessToken(@RequestParam("exerciseId") long exerciseId, @RequestParam("repositoryType") RepositoryType repositoryType,
            @RequestParam(value = "auxiliaryRepositoryId", required = false) Long auxiliaryRepositoryId,
            @RequestParam(value = "participationId", required = false) Long participationId) {
        validateRepositoryTypeInput(repositoryType, auxiliaryRepositoryId, participationId);
        User user = userRepository.getUser();
        log.debug("REST request to create a repository VCS access token for user {} for {} repository of exercise {}", user.getLogin(), repositoryType, exerciseId);
        if (repositoryType == RepositoryType.USER) {
            AuthorizedStudentRepository authorized = resolveAndAuthorizeStudentParticipation(exerciseId, participationId, user);
            return ResponseEntity
                    .ok(repositoryVcsAccessTokenService.getOrCreateStudentRepositoryToken(user, authorized.exercise(), authorized.participation()).getVcsAccessToken());
        }
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
        return ResponseEntity.ok(repositoryVcsAccessTokenService.getOrCreateToken(user, exercise, repositoryType, auxiliaryRepositoryId).getVcsAccessToken());
    }

    /**
     * Loads the student participation, verifies it belongs to the exercise the caller is authorized for (at least tutor), and checks that the caller may at least read its
     * repository. This mirrors the git-time authorization so a token is only ever handed out for a repository the requester can already access; the token itself never widens
     * permissions (read vs. write is re-derived from the live course role on every git operation).
     *
     * @param exerciseId      the id of the programming exercise the caller is authorized for
     * @param participationId the id of the student participation whose repository the token should grant access to
     * @param user            the requesting staff user
     * @return the resolved and authorized exercise + student participation
     */
    private AuthorizedStudentRepository resolveAndAuthorizeStudentParticipation(long exerciseId, long participationId, User user) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        ProgrammingExercise exercise = programmingExerciseRepository.getProgrammingExerciseFromParticipationElseThrow(participation);
        if (!Objects.equals(exercise.getId(), exerciseId)) {
            throw new BadRequestAlertException("The participation does not belong to the specified exercise", ENTITY_NAME, "participationExerciseMismatch");
        }
        repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, exercise, RepositoryActionType.READ);
        return new AuthorizedStudentRepository(exercise, participation);
    }

    /**
     * Fails fast with 400 when the requested repository type cannot have a staff token, when an auxiliary repository is requested without its id, or when a student ({@code USER})
     * repository is requested without its participation id.
     *
     * @param repositoryType        the requested repository type
     * @param auxiliaryRepositoryId the auxiliary repository id (must be present for {@link RepositoryType#AUXILIARY})
     * @param participationId       the student participation id (must be present for {@link RepositoryType#USER})
     */
    private void validateRepositoryTypeInput(RepositoryType repositoryType, Long auxiliaryRepositoryId, Long participationId) {
        if (repositoryType == RepositoryType.USER) {
            if (participationId == null) {
                throw new BadRequestAlertException("participationId is required for the USER (student) repository type", ENTITY_NAME, "participationIdMissing");
            }
            return;
        }
        if (repositoryType != RepositoryType.TEMPLATE && repositoryType != RepositoryType.SOLUTION && repositoryType != RepositoryType.TESTS
                && repositoryType != RepositoryType.AUXILIARY) {
            throw new BadRequestAlertException("Only base repositories (TEMPLATE, SOLUTION, TESTS, AUXILIARY) or a student repository (USER) can have staff VCS access tokens",
                    ENTITY_NAME, "unsupportedRepositoryType");
        }
        if (repositoryType == RepositoryType.AUXILIARY && auxiliaryRepositoryId == null) {
            throw new BadRequestAlertException("auxiliaryRepositoryId is required for the AUXILIARY repository type", ENTITY_NAME, "auxiliaryRepositoryIdMissing");
        }
    }
}
