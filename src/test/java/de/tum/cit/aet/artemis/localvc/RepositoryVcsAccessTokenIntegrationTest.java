package de.tum.cit.aet.artemis.localvc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localvc.service.RepositoryVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryVCSAccessToken;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;

class RepositoryVcsAccessTokenIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "repovcsat";

    @Autowired
    private RepositoryVcsAccessTokenService repositoryVcsAccessTokenService;

    @Autowired
    private RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

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
    void ensureTokensForExercise_createsForStaffOnly() {
        repositoryVcsAccessTokenService.ensureTokensForExercise(exercise);

        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isPresent();
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(instructor.getId(), templateUri)).isPresent();
        // A student of the course must not receive a staff repository token.
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(student.getId(), templateUri)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void staffMembershipLifecycle_createsAndRemovesTokens() {
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");

        // Joining the course as staff pre-provisions the tokens for the course's base repositories.
        repositoryVcsAccessTokenService.ensureTokensForStaffUserInCourse(tutor, course);
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isPresent();

        // While the user is still at least a tutor in the course, their tokens must be kept.
        repositoryVcsAccessTokenService.deleteForUserInCourseIfNoLongerStaff(tutor, course);
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isPresent();

        // Once the user is no longer staff in the course, their tokens for that course's exercises are removed.
        tutor.getGroups().remove(course.getTeachingAssistantGroupName());
        userRepository.save(tutor);
        repositoryVcsAccessTokenService.deleteForUserInCourseIfNoLongerStaff(tutor, course);
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), templateUri)).isEmpty();

        // Deleting the user's account removes any remaining repository tokens.
        repositoryVcsAccessTokenService.getOrCreateToken(tutor, exercise, RepositoryType.SOLUTION, null);
        repositoryVcsAccessTokenService.deleteAllByUserId(tutor.getId());
        assertThat(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(tutor.getId(), exercise.getSolutionRepositoryUri())).isEmpty();
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void repositoryVcsAccessTokenEndpoints_forbiddenForStudent() throws Exception {
        String url = "/api/programming/repository-vcs-access-token?exerciseId=" + exercise.getId() + "&repositoryType=TEMPLATE";
        request.get(url, HttpStatus.FORBIDDEN, String.class);
        request.put(url, null, HttpStatus.FORBIDDEN);
    }
}
