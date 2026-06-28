package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryVCSAccessToken;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;

/**
 * Manages repository-scoped VCS access tokens for course staff (tutors, editors, instructors).
 * <p>
 * Each token is bound to exactly one base repository (template, tests, solution or one auxiliary repository) of a programming exercise. Tokens are pre-provisioned eagerly when
 * staff join a course or when an exercise is created, lazily created on demand (clone dialog), and removed when staff leave, when an exercise is deleted, or when the owning user
 * is deleted. The token only authenticates the user; the actual authorization is still enforced on every git operation in {@code LocalVCServletService}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class RepositoryVcsAccessTokenService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryVcsAccessTokenService.class);

    private final RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public RepositoryVcsAccessTokenService(RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        this.repositoryVCSAccessTokenRepository = repositoryVCSAccessTokenRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * A base repository of a programming exercise the token can be scoped to.
     */
    private record BaseRepository(RepositoryType repositoryType, AuxiliaryRepository auxiliaryRepository, String repositoryUri) {
    }

    /**
     * Returns the repository-scoped token a user owns for the given repository URI, if any.
     *
     * @param userId        the id of the owning user
     * @param repositoryUri the canonical repository URI
     * @return the token if present
     */
    public Optional<RepositoryVCSAccessToken> findByUserIdAndRepositoryUri(long userId, String repositoryUri) {
        return repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(userId, repositoryUri);
    }

    /**
     * Returns the existing token for the user and base repository, or creates a new one if none exists.
     * <p>
     * Used both as the lazy fallback (clone dialog) and by the eager provisioning paths. Authorization (at least tutor in the course) must be checked by the caller; this method
     * only manages the token itself.
     *
     * @param user                  the owning user
     * @param exercise              the programming exercise the repository belongs to
     * @param repositoryType        the type of base repository (TEMPLATE, SOLUTION, TESTS or AUXILIARY)
     * @param auxiliaryRepositoryId the id of the auxiliary repository (only used for {@link RepositoryType#AUXILIARY}, otherwise {@code null})
     * @return the existing or newly created token
     */
    public RepositoryVCSAccessToken getOrCreateToken(User user, ProgrammingExercise exercise, RepositoryType repositoryType, Long auxiliaryRepositoryId) {
        BaseRepository baseRepository = resolveBaseRepository(exercise, repositoryType, auxiliaryRepositoryId);
        Optional<RepositoryVCSAccessToken> existingToken = repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(user.getId(), baseRepository.repositoryUri());
        if (existingToken.isPresent()) {
            return existingToken.get();
        }
        try {
            return createToken(user, exercise, baseRepository);
        }
        catch (DataIntegrityViolationException e) {
            // A concurrent request (e.g. a double-clicked clone dialog or a racing eager-provisioning path) inserted the token for the same (user, repository URI) first and
            // tripped the unique constraint. Re-read and return the now-existing token instead of failing the user-facing request.
            return repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(user.getId(), baseRepository.repositoryUri()).orElseThrow(() -> e);
        }
    }

    /**
     * Returns the existing token for the user and base repository, or throws if none exists (used by the read-only REST endpoint).
     *
     * @param user                  the owning user
     * @param exercise              the programming exercise the repository belongs to
     * @param repositoryType        the type of base repository
     * @param auxiliaryRepositoryId the id of the auxiliary repository (only for {@link RepositoryType#AUXILIARY})
     * @return the existing token
     */
    public RepositoryVCSAccessToken findTokenOrElseThrow(User user, ProgrammingExercise exercise, RepositoryType repositoryType, Long auxiliaryRepositoryId) {
        BaseRepository baseRepository = resolveBaseRepository(exercise, repositoryType, auxiliaryRepositoryId);
        return repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(user.getId(), baseRepository.repositoryUri())
                .orElseThrow(() -> new EntityNotFoundException("RepositoryVCSAccessToken for repository " + baseRepository.repositoryUri()));
    }

    /**
     * Asynchronously provisions the repository tokens for a staff user that just joined a course (see {@link #ensureTokensForStaffUserInCourse}).
     * <p>
     * Used on the staff-join request path (adding a tutor/editor/instructor to a course group) so the client does not block while tokens for potentially many programming exercises
     * are created. The clone-dialog lazy fallback covers the brief window before the eager tokens exist, so a delayed (or failed) async run never blocks a staff member from
     * cloning.
     *
     * @param user   the staff user that just joined the course
     * @param course the course the user joined
     */
    @Async
    public void ensureTokensForStaffUserInCourseAsync(User user, Course course) {
        ensureTokensForStaffUserInCourse(user, course);
    }

    /**
     * Eagerly creates the missing repository tokens for a single staff user across all (non-exam) programming exercises of the given course.
     * <p>
     * Exam programming exercises and staff added through paths other than the course-management add endpoint (e.g. admin user management, CSV import) are intentionally not covered
     * here; the lazy clone-dialog fallback creates their tokens on first use.
     *
     * @param user   the staff user that just joined the course
     * @param course the course the user joined
     */
    public void ensureTokensForStaffUserInCourse(User user, Course course) {
        // Load all programming exercises of the course together with their template/solution participations and auxiliary repositories in a single batch query (instead of one
        // fetch per exercise), so adding a staff member to a course with many exercises stays cheap.
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findAllWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesByCourseId(course.getId());
        if (exercises.isEmpty()) {
            return;
        }
        Set<Long> exerciseIds = exercises.stream().map(DomainObject::getId).collect(Collectors.toSet());
        Set<String> existingUris = repositoryVCSAccessTokenRepository.findRepositoryUrisByUserIdAndExerciseIdIn(user.getId(), exerciseIds);
        List<RepositoryVCSAccessToken> toCreate = new ArrayList<>();
        for (ProgrammingExercise exercise : exercises) {
            for (BaseRepository baseRepository : baseRepositoriesOf(exercise)) {
                if (!existingUris.contains(baseRepository.repositoryUri())) {
                    toCreate.add(buildToken(user, exercise, baseRepository));
                }
            }
        }
        if (!toCreate.isEmpty()) {
            repositoryVCSAccessTokenRepository.saveAll(toCreate);
            log.debug("Created {} repository VCS access tokens for staff user {} in course {}", toCreate.size(), user.getLogin(), course.getId());
        }
    }

    /**
     * Eagerly creates the missing repository tokens for all current staff (tutors, editors, instructors) of the exercise's course for every base repository of the given exercise.
     * Intended to be called right after an exercise has been created (or updated, e.g. when an auxiliary repository was added).
     *
     * @param exercise the programming exercise whose base repositories should get tokens (must have template/solution participations and auxiliary repositories loaded)
     */
    public void ensureTokensForExercise(ProgrammingExercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        Set<User> staff = staffUsersOf(course);
        if (staff.isEmpty()) {
            return;
        }
        List<RepositoryVCSAccessToken> toCreate = new ArrayList<>();
        for (BaseRepository baseRepository : baseRepositoriesOf(exercise)) {
            Set<Long> userIdsWithToken = repositoryVCSAccessTokenRepository.findUserIdsByRepositoryUri(baseRepository.repositoryUri());
            for (User user : staff) {
                if (!userIdsWithToken.contains(user.getId())) {
                    toCreate.add(buildToken(user, exercise, baseRepository));
                }
            }
        }
        if (!toCreate.isEmpty()) {
            repositoryVCSAccessTokenRepository.saveAll(toCreate);
            log.debug("Created {} repository VCS access tokens for exercise {}", toCreate.size(), exercise.getId());
        }
    }

    /**
     * Asynchronously removes the staff member's repository tokens for the course when they are no longer staff (see {@link #deleteForUserInCourseIfNoLongerStaff}).
     * <p>
     * Used on the staff-leave request path (removing a tutor/editor/instructor from a course group) so the client does not block on token cleanup. Running this asynchronously is
     * safe even though a token then lingers for a brief moment: authorization is re-checked on every git operation, so a leftover token for a user that is no longer staff is
     * rejected anyway.
     *
     * @param user   the user that was removed from a course group
     * @param course the course the user was removed from
     */
    @Async
    public void deleteForUserInCourseIfNoLongerStaffAsync(User user, Course course) {
        deleteForUserInCourseIfNoLongerStaff(user, course);
    }

    /**
     * Removes the repository tokens a user owns for the (non-exam) programming exercises of the given course, but only if the user is no longer at least a tutor in that course.
     * This protects users that hold more than one staff role (e.g. an instructor that was only removed from the editor group keeps their tokens).
     * <p>
     * Tokens for exam programming exercises are not removed here. This is safe because a token never grants access on its own: authorization (at least tutor / editor in the
     * course)
     * is re-checked on every git operation, so a leftover token for a user that is no longer staff is rejected anyway.
     *
     * @param user   the user that was removed from a course group
     * @param course the course the user was removed from
     */
    public void deleteForUserInCourseIfNoLongerStaff(User user, Course course) {
        if (authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return;
        }
        List<Long> exerciseIds = programmingExerciseRepository.findAllWithCategoriesByCourseId(course.getId()).stream().map(DomainObject::getId).toList();
        if (!exerciseIds.isEmpty()) {
            repositoryVCSAccessTokenRepository.deleteAllByUserIdAndExerciseIdIn(user.getId(), exerciseIds);
        }
    }

    /**
     * Deletes all repository tokens that belong to the given exercise (across all repository types, including auxiliary). Must be called before the exercise itself is deleted, as
     * the {@code exercise_id} foreign key is {@code RESTRICT}.
     *
     * @param exerciseId the id of the programming exercise
     */
    public void deleteByExerciseId(long exerciseId) {
        repositoryVCSAccessTokenRepository.deleteAllByExerciseId(exerciseId);
    }

    /**
     * Deletes all repository tokens of a user (used when the user account is deleted).
     *
     * @param userId the id of the user
     */
    public void deleteAllByUserId(long userId) {
        repositoryVCSAccessTokenRepository.deleteAllByUserId(userId);
    }

    private RepositoryVCSAccessToken createToken(User user, ProgrammingExercise exercise, BaseRepository baseRepository) {
        return repositoryVCSAccessTokenRepository.save(buildToken(user, exercise, baseRepository));
    }

    private RepositoryVCSAccessToken buildToken(User user, ProgrammingExercise exercise, BaseRepository baseRepository) {
        RepositoryVCSAccessToken token = new RepositoryVCSAccessToken();
        token.setUser(user);
        token.setExercise(exercise);
        token.setRepositoryType(baseRepository.repositoryType());
        token.setAuxiliaryRepository(baseRepository.auxiliaryRepository());
        token.setRepositoryUri(baseRepository.repositoryUri());
        token.setVcsAccessToken(LocalVCPersonalAccessTokenManagementService.generateSecureVCSAccessToken());
        return token;
    }

    /**
     * Collects the existing base repositories of an exercise (template, solution, tests and each auxiliary repository) with their canonical URIs, skipping ones without a URI.
     */
    private List<BaseRepository> baseRepositoriesOf(ProgrammingExercise exercise) {
        List<BaseRepository> baseRepositories = new ArrayList<>();
        addIfPresent(baseRepositories, RepositoryType.TEMPLATE, null, exercise.getTemplateRepositoryUri());
        addIfPresent(baseRepositories, RepositoryType.SOLUTION, null, exercise.getSolutionRepositoryUri());
        addIfPresent(baseRepositories, RepositoryType.TESTS, null, exercise.getTestRepositoryUri());
        if (exercise.getAuxiliaryRepositories() != null) {
            for (AuxiliaryRepository auxiliaryRepository : exercise.getAuxiliaryRepositories()) {
                addIfPresent(baseRepositories, RepositoryType.AUXILIARY, auxiliaryRepository, auxiliaryRepository.getRepositoryUri());
            }
        }
        return baseRepositories;
    }

    private void addIfPresent(List<BaseRepository> baseRepositories, RepositoryType repositoryType, AuxiliaryRepository auxiliaryRepository, String repositoryUri) {
        if (StringUtils.hasText(repositoryUri)) {
            baseRepositories.add(new BaseRepository(repositoryType, auxiliaryRepository, repositoryUri));
        }
    }

    private BaseRepository resolveBaseRepository(ProgrammingExercise exercise, RepositoryType repositoryType, Long auxiliaryRepositoryId) {
        if (repositoryType == RepositoryType.AUXILIARY) {
            AuxiliaryRepository auxiliaryRepository = exercise.getAuxiliaryRepositories().stream().filter(repo -> Objects.equals(repo.getId(), auxiliaryRepositoryId)).findAny()
                    .orElseThrow(() -> new EntityNotFoundException("AuxiliaryRepository", auxiliaryRepositoryId != null ? auxiliaryRepositoryId : -1));
            return new BaseRepository(RepositoryType.AUXILIARY, auxiliaryRepository, auxiliaryRepository.getRepositoryUri());
        }
        String repositoryUri = switch (repositoryType) {
            case TEMPLATE -> exercise.getTemplateRepositoryUri();
            case SOLUTION -> exercise.getSolutionRepositoryUri();
            case TESTS -> exercise.getTestRepositoryUri();
            default -> throw new IllegalArgumentException("Unsupported repository type for a staff repository token: " + repositoryType);
        };
        if (!StringUtils.hasText(repositoryUri)) {
            throw new EntityNotFoundException("No " + repositoryType + " repository URI for exercise " + exercise.getId());
        }
        return new BaseRepository(repositoryType, null, repositoryUri);
    }

    private Set<User> staffUsersOf(Course course) {
        Set<String> staffGroups = new HashSet<>();
        staffGroups.add(course.getTeachingAssistantGroupName());
        staffGroups.add(course.getEditorGroupName());
        staffGroups.add(course.getInstructorGroupName());
        staffGroups.removeIf(group -> !StringUtils.hasText(group));
        if (staffGroups.isEmpty()) {
            return Set.of();
        }
        return userRepository.findAllWithGroupsAndAuthoritiesByDeletedIsFalseAndGroupsContains(staffGroups);
    }
}
