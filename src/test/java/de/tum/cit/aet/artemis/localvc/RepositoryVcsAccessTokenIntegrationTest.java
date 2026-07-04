package de.tum.cit.aet.artemis.localvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseAccessService;
import de.tum.cit.aet.artemis.localvc.service.RepositoryVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryVCSAccessToken;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService;

class RepositoryVcsAccessTokenIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "repovcsat";

    @Autowired
    private RepositoryVcsAccessTokenService repositoryVcsAccessTokenService;

    @Autowired
    private RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    @Autowired
    private ProgrammingExerciseDeletionService programmingExerciseDeletionService;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseAccessService courseAccessService;

    private ProgrammingExercise exercise;

    private Course course;

    private String templateUri;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        long exerciseId = programmingExerciseRepository.findAllWithCategoriesByCourseId(course.getId()).getFirst().getId();
        exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(exerciseId).orElseThrow();
        // Keep the in-memory course (with its group names) attached so the service can resolve staff without a lazy load in the test.
        exercise.setCourse(course);

        // The util-created exercise does not set base repository URIs, so we set deterministic ones here (the token is scoped by exactly this URI).
        templateUri = "http://localhost/git/TESTREPOVCSAT/testrepovcsat-exercise.git";
        exercise.getTemplateParticipation().setRepositoryUri(templateUri);
        exercise.getSolutionParticipation().setRepositoryUri("http://localhost/git/TESTREPOVCSAT/testrepovcsat-solution.git");
        exercise.setTestRepositoryUri("http://localhost/git/TESTREPOVCSAT/testrepovcsat-tests.git");
        participationRepository.save(exercise.getTemplateParticipation());
        participationRepository.save(exercise.getSolutionParticipation());
        programmingExerciseRepository.save(exercise);
    }

    @AfterEach
    void tearDown() {
        // The base repository URIs are reused across test methods, but tokens are unique per (user, repository URI). Remove this test's tokens (scoped to its exercise) so they do
        // not leak into the next method and make an otherwise-empty lookup return a stale token.
        repositoryVcsAccessTokenService.deleteByExerciseId(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOrCreateToken_isScopedIdempotentAndDeletable() {
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");

        RepositoryVCSAccessToken token = repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.TEMPLATE, null);
        assertThat(token.getVcsAccessToken()).startsWith("vcpat-").hasSize(50);
        assertThat(token.getRepositoryUri()).isEqualTo(templateUri);

        // Calling again returns the same token (idempotent).
        RepositoryVCSAccessToken again = repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.TEMPLATE, null);
        assertThat(again.getId()).isEqualTo(token.getId());

        // The solution repository gets a different, separate token (scope is one repository).
        RepositoryVCSAccessToken solutionToken = repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.SOLUTION, null);
        assertThat(solutionToken.getId()).isNotEqualTo(token.getId());
        assertThat(solutionToken.getVcsAccessToken()).isNotEqualTo(token.getVcsAccessToken());

        repositoryVcsAccessTokenService.deleteByExerciseId(exercise.getId());
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void ensureTokensForExerciseAsync_provisionsTokensForStaffOnly() {
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isEmpty();

        // The exercise create/update path provisions the staff tokens ASYNCHRONOUSLY by exercise id (re-fetching the exercise off the request thread), so the request returns
        // without waiting for token generation for all staff members.
        repositoryVcsAccessTokenService.ensureTokensForExerciseAsync(exercise.getId());

        // Poll until the asynchronously created tokens for the course's staff exist for all base repositories.
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isPresent();
            assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(instructor.getId(), templateUri)).isPresent();
            assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(instructor.getId(), exercise.getSolutionRepositoryUri())).isPresent();
        });
        // A student of the course must still not receive a staff repository token.
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(student.getId(), templateUri)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void staffMembershipLifecycle_createsAndRemovesTokens() {
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");

        // Joining the course as staff pre-provisions the tokens for the course's base repositories — asynchronously, exactly as the staff-join request path does.
        repositoryVcsAccessTokenService.ensureTokensForStaffUserInCourseAsync(tutor, course);
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isPresent());

        // While the user is still at least a tutor in the course, their tokens must be kept. The keep/delete decision lives in the synchronous core method that the async wrapper
        // merely delegates to; assert it directly so "keep" is deterministic instead of racing an asynchronous no-op (a "stays present" condition cannot be awaited reliably).
        repositoryVcsAccessTokenService.deleteForUserInCourseIfNoLongerStaff(tutor, course);
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isPresent();

        // Once the user is no longer staff in the course, their tokens for that course's exercises are removed — asynchronously, as the staff-leave request path does.
        tutor.getGroups().remove(course.getTeachingAssistantGroupName());
        userRepository.save(tutor);
        repositoryVcsAccessTokenService.deleteForUserInCourseIfNoLongerStaffAsync(tutor, course);
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isEmpty());

        // Deleting the user's account removes any remaining repository tokens (softDeleteUser does this synchronously).
        repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.SOLUTION, null);
        repositoryVcsAccessTokenService.deleteAllByUserId(tutor.getId());
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), exercise.getSolutionRepositoryUri())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addStaffToCourseGroup_asynchronouslyProvisionsTokens_andRemovalDeletesThem() {
        // A dedicated user that is not yet staff in the course (so soft state of the shared per-prefix users is untouched).
        User joiningStaff = userUtilService.createAndSaveUser(TEST_PREFIX + "joiningstaff");
        String instructorGroup = course.getInstructorGroupName();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(joiningStaff.getId(), templateUri)).isEmpty();

        // Adding the user to a staff group triggers ASYNCHRONOUS provisioning of repository tokens for every base repository of the course's exercises (the request itself returns
        // without waiting for token creation).
        courseAccessService.addUserToGroup(joiningStaff, instructorGroup, course);

        // The provisioning runs off the request thread, so poll until the tokens for all base repositories of the exercise exist.
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(joiningStaff.getId(), templateUri)).isPresent();
            assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(joiningStaff.getId(), exercise.getSolutionRepositoryUri())).isPresent();
            assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(joiningStaff.getId(), exercise.getTestRepositoryUri())).isPresent();
        });

        // Removing the user from the staff group (they are no longer staff in the course) deletes their repository tokens for the course — also asynchronously, so poll again.
        courseAccessService.removeUserFromGroup(joiningStaff, instructorGroup, course);
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(joiningStaff.getId(), templateUri)).isEmpty();
            assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(joiningStaff.getId(), exercise.getSolutionRepositoryUri())).isEmpty();
            assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(joiningStaff.getId(), exercise.getTestRepositoryUri())).isEmpty();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAndCreateRepositoryVcsAccessToken_asTutor() throws Exception {
        String url = "/api/programming/repository-vcs-access-token?exerciseId=" + exercise.getId() + "&repositoryType=TEMPLATE";
        // No token exists yet.
        request.get(url, HttpStatus.NOT_FOUND, String.class);
        // Create it via PUT.
        String token = request.putWithResponseBody(url, null, String.class, HttpStatus.OK);
        assertThat(token).startsWith("vcpat-");
        // GET now returns the same token.
        String fetched = request.get(url, HttpStatus.OK, String.class);
        assertThat(fetched).isEqualTo(token);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void repositoryVcsAccessTokenEndpoints_auxiliaryWithoutIdIsBadRequest() throws Exception {
        String url = "/api/programming/repository-vcs-access-token?exerciseId=" + exercise.getId() + "&repositoryType=AUXILIARY";
        request.get(url, HttpStatus.BAD_REQUEST, String.class);
        request.put(url, null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void repositoryVcsAccessTokenEndpoints_nonBaseRepositoryTypeIsBadRequest() throws Exception {
        // USER is a student-participation repository, not a staff base repository. The endpoint must reject it with 400 instead of letting it reach the service's repository-type
        // switch and surface as a 500.
        String url = "/api/programming/repository-vcs-access-token?exerciseId=" + exercise.getId() + "&repositoryType=USER";
        request.get(url, HttpStatus.BAD_REQUEST, String.class);
        request.put(url, null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void repositoryVcsAccessTokenEndpoints_forbiddenForStudent() throws Exception {
        String url = "/api/programming/repository-vcs-access-token?exerciseId=" + exercise.getId() + "&repositoryType=TEMPLATE";
        request.get(url, HttpStatus.FORBIDDEN, String.class);
        request.put(url, null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void repositoryVcsAccessTokenEndpoints_auxiliaryIdNotBelongingToExerciseIsNotFound() throws Exception {
        // An auxiliaryRepositoryId that is not one of THIS exercise's auxiliary repositories (a non-existent id, or one belonging to a different exercise) must not resolve to a
        // token. The service only ever looks at the requested exercise's own auxiliary repositories, so a foreign id cannot be used to mint a token for a repository the caller
        // should not reach.
        String url = "/api/programming/repository-vcs-access-token?exerciseId=" + exercise.getId() + "&repositoryType=AUXILIARY&auxiliaryRepositoryId=999999";
        request.get(url, HttpStatus.NOT_FOUND, String.class);
        request.put(url, null, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOrCreateToken_forAuxiliaryRepository_isScopedToTheAuxiliaryRepository() {
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        AuxiliaryRepository auxiliaryRepository = persistAuxiliaryRepository("http://localhost/git/TESTREPOVCSAT/testrepovcsat-aux.git");

        RepositoryVCSAccessToken token = repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.AUXILIARY, auxiliaryRepository.getId());
        assertThat(token.getVcsAccessToken()).startsWith("vcpat-").hasSize(50);
        assertThat(token.getRepositoryType()).isEqualTo(RepositoryType.AUXILIARY);
        assertThat(token.getRepositoryUri()).isEqualTo(auxiliaryRepository.getRepositoryUri());
        assertThat(token.getAuxiliaryRepository()).isNotNull();
        assertThat(token.getAuxiliaryRepository().getId()).isEqualTo(auxiliaryRepository.getId());

        // The auxiliary token is separate from the template token (scope is exactly one repository), and idempotent on a second call.
        RepositoryVCSAccessToken templateToken = repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.TEMPLATE, null);
        assertThat(templateToken.getId()).isNotEqualTo(token.getId());
        RepositoryVCSAccessToken again = repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.AUXILIARY, auxiliaryRepository.getId());
        assertThat(again.getId()).isEqualTo(token.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOrCreateToken_forUnknownAuxiliaryRepository_throws() {
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        // No auxiliary repository with this id is attached to the exercise.
        assertThatThrownBy(() -> repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.AUXILIARY, 999999L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAndCreateRepositoryVcsAccessToken_forAuxiliaryRepository_asTutor() throws Exception {
        AuxiliaryRepository auxiliaryRepository = persistAuxiliaryRepository("http://localhost/git/TESTREPOVCSAT/testrepovcsat-aux2.git");
        String url = "/api/programming/repository-vcs-access-token?exerciseId=" + exercise.getId() + "&repositoryType=AUXILIARY&auxiliaryRepositoryId="
                + auxiliaryRepository.getId();
        // No token exists yet.
        request.get(url, HttpStatus.NOT_FOUND, String.class);
        // Create it via PUT.
        String token = request.putWithResponseBody(url, null, String.class, HttpStatus.OK);
        assertThat(token).startsWith("vcpat-");
        // GET now returns the same token.
        assertThat(request.get(url, HttpStatus.OK, String.class)).isEqualTo(token);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExercise_withRepositoryTokens_doesNotThrowAndRemovesTokens() {
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        long exerciseId = exercise.getId();
        // Provision several tokens (multiple staff users, multiple base repositories) that all reference the exercise via the RESTRICT exercise_id foreign key.
        repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.TEMPLATE, null);
        repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.SOLUTION, null);
        repositoryVcsAccessTokenService.getOrCreateToken(instructor, exercise, RepositoryType.TEMPLATE, null);
        assertThat(repositoryVCSAccessTokenRepository.findRepositoryUrisByUserIdAndExerciseIdIn(tutor.getId(), Set.of(exerciseId))).isNotEmpty();

        // Deleting the exercise must NOT throw despite the RESTRICT exercise_id foreign key: the service removes the tokens before deleting the exercise.
        assertThatCode(() -> programmingExerciseDeletionService.delete(exerciseId, false)).doesNotThrowAnyException();

        // The exercise and every token referencing it are gone.
        assertThat(programmingExerciseRepository.findById(exerciseId)).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), exercise.getSolutionRepositoryUri())).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(instructor.getId(), templateUri)).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findRepositoryUrisByUserIdAndExerciseIdIn(tutor.getId(), Set.of(exerciseId))).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteUser_withRepositoryTokens_doesNotThrowAndRemovesTokens() {
        // A dedicated user so soft-deleting it does not affect the shared per-prefix users used by the other test methods.
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "todelete");
        repositoryVcsAccessTokenService.getOrCreateToken(user, exercise, RepositoryType.TEMPLATE, null);
        repositoryVcsAccessTokenService.getOrCreateToken(user, exercise, RepositoryType.SOLUTION, null);
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(user.getId(), templateUri)).isPresent();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(user.getId(), exercise.getSolutionRepositoryUri())).isPresent();

        // Deleting the user must NOT throw despite the RESTRICT user_id foreign key: softDeleteUser removes the user's tokens before deleting the account.
        assertThatCode(() -> userService.softDeleteUser(user.getLogin())).doesNotThrowAnyException();

        // All tokens owned by the user are gone.
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(user.getId(), templateUri)).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(user.getId(), exercise.getSolutionRepositoryUri())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAuxiliaryRepository_withRepositoryToken_cascadesAndDoesNotThrow() {
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        AuxiliaryRepository auxiliaryRepository = persistAuxiliaryRepository("http://localhost/git/TESTREPOVCSAT/testrepovcsat-aux-delete.git");
        RepositoryVCSAccessToken token = repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.AUXILIARY, auxiliaryRepository.getId());
        assertThat(repositoryVCSAccessTokenRepository.findById(token.getId())).isPresent();

        // Deleting the auxiliary repository must NOT throw and must cascade-delete the token (the auxiliary_repository_id foreign key uses ON DELETE CASCADE).
        assertThatCode(() -> auxiliaryRepositoryRepository.delete(auxiliaryRepository)).doesNotThrowAnyException();

        // The token that referenced the removed auxiliary repository is gone via the database cascade.
        assertThat(repositoryVCSAccessTokenRepository.findById(token.getId())).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), auxiliaryRepository.getRepositoryUri())).isEmpty();
    }

    /**
     * Deleting a programming exercise that still has repository tokens (template, solution, tests and an auxiliary repository) referencing it must succeed. The
     * {@code exercise_id} foreign key is {@code ON DELETE RESTRICT}, so the deletion service has to remove the tokens up front; otherwise the delete would throw a
     * {@code DataIntegrityViolationException}. This is the regression guard for that ordering.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deletingExerciseWithReferencingTokensSucceeds() {
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        AuxiliaryRepository auxiliaryRepository = persistAuxiliaryRepository("http://localhost/git/TESTREPOVCSAT/testrepovcsat-aux-exercise-delete.git");

        // Tokens of every base-repository type (including an auxiliary-scoped one) reference the exercise via the RESTRICT foreign key.
        repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.TEMPLATE, null);
        repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.SOLUTION, null);
        repositoryVcsAccessTokenService.getOrCreateToken(instructor, exercise, RepositoryType.TESTS, null);
        repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.AUXILIARY, auxiliaryRepository.getId());
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isPresent();

        long exerciseId = exercise.getId();
        assertThatCode(() -> programmingExerciseDeletionService.delete(exerciseId, false)).doesNotThrowAnyException();

        // The exercise and all of its referencing tokens are gone, and no foreign-key exception was thrown.
        assertThat(programmingExerciseRepository.findById(exerciseId)).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), exercise.getSolutionRepositoryUri())).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(instructor.getId(), exercise.getTestRepositoryUri())).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), auxiliaryRepository.getRepositoryUri())).isEmpty();
    }

    /**
     * Soft-deleting a user account that still owns repository tokens must succeed. The {@code user_id} foreign key is {@code ON DELETE RESTRICT}, so {@code softDeleteUser} has to
     * remove the user's tokens before anonymizing the account. This guards against a foreign-key exception during user deletion.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deletingUserWithReferencingTokensSucceeds() {
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        String solutionUri = exercise.getSolutionRepositoryUri();
        repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.TEMPLATE, null);
        repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.SOLUTION, null);
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isPresent();

        assertThatCode(() -> userService.softDeleteUser(tutor.getLogin())).doesNotThrowAnyException();

        // The user's tokens were removed during the deletion, and no foreign-key exception was thrown.
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isEmpty();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), solutionUri)).isEmpty();
    }

    /**
     * Deleting only an auxiliary repository (e.g. when it is removed during an exercise update, while the exercise itself remains) must remove its token automatically. The
     * {@code auxiliary_repository_id} foreign key is {@code ON DELETE CASCADE}, so no explicit token cleanup is needed and the delete must not throw.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deletingAuxiliaryRepositoryWithReferencingTokenCascades() {
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        AuxiliaryRepository auxiliaryRepository = persistAuxiliaryRepository("http://localhost/git/TESTREPOVCSAT/testrepovcsat-aux-cascade.git");

        RepositoryVCSAccessToken token = repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.AUXILIARY, auxiliaryRepository.getId());
        assertThat(repositoryVCSAccessTokenRepository.findById(token.getId())).isPresent();

        // This mirrors the production path (AuxiliaryRepositoryService#handleAuxiliaryRepositoriesWhenUpdatingExercises deletes removed auxiliary repositories directly).
        assertThatCode(() -> {
            auxiliaryRepositoryRepository.delete(auxiliaryRepository);
            auxiliaryRepositoryRepository.flush();
        }).doesNotThrowAnyException();

        // The ON DELETE CASCADE removed the token along with the auxiliary repository, while the exercise and unrelated tokens remain untouched.
        assertThat(repositoryVCSAccessTokenRepository.findById(token.getId())).isEmpty();
        assertThat(programmingExerciseRepository.findById(exercise.getId())).isPresent();
    }

    /**
     * Attaches an auxiliary repository with the given canonical URI to the exercise. Goes through the util helper (which writes the {@code @OrderColumn} index by saving the
     * exercise) and keeps the in-memory exercise object consistent so the service can resolve the auxiliary repository by id.
     */
    private AuxiliaryRepository persistAuxiliaryRepository(String repositoryUri) {
        AuxiliaryRepository auxiliaryRepository = programmingExerciseUtilService.addAuxiliaryRepositoryToExercise(exercise);
        auxiliaryRepository.setRepositoryUri(repositoryUri);
        return auxiliaryRepositoryRepository.save(auxiliaryRepository);
    }
}
