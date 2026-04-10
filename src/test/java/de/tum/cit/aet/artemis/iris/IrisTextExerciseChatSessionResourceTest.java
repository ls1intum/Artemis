package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionResponseDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class IrisTextExerciseChatSessionResourceTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iristextexercisechatsession";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    private Course course;

    private TextExercise textExercise;

    @BeforeEach
    void initTestCase() {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        for (User user : users) {
            user.setSelectedLLMUsageTimestamp(ZonedDateTime.parse("2025-12-11T00:00:00Z"));
            user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }

        course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = (TextExercise) course.getExercises().iterator().next();

        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(textExercise);
    }

    private String textExerciseChatCurrentUrl(long exerciseId) {
        return "/api/iris/chat/" + course.getId() + "/sessions/current?mode=TEXT_EXERCISE_CHAT&entityId=" + exerciseId;
    }

    private String textExerciseChatCurrentUrl() {
        return textExerciseChatCurrentUrl(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_createsNewSession() throws Exception {
        // When: User requests current session for the first time
        var response = request.postWithResponseBody(textExerciseChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: A new session should be created
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // Verify session was saved in database
        var sessionsInDb = irisChatSessionRepository.findByEntityIdAndUserIdOrderByCreationDateDesc(textExercise.getId(),
                userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId(), Pageable.unpaged());
        assertThat(sessionsInDb).hasSize(1);
        assertThat(sessionsInDb.getFirst().getId()).isEqualTo(response.id());
        assertThat(sessionsInDb.getFirst().getEntityId()).isEqualTo(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_returnsExistingSession() throws Exception {
        // Given: User already has a session
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession existingSession = new IrisChatSession(textExercise, user, IrisChatMode.TEXT_EXERCISE_CHAT);
        existingSession = irisChatSessionRepository.save(existingSession);

        // When: User requests current session
        var response = request.postWithResponseBody(textExerciseChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: The existing session should be returned
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(existingSession.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_returnsLatestSession() throws Exception {
        // Given: User has multiple sessions
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession olderSession = new IrisChatSession(textExercise, user, IrisChatMode.TEXT_EXERCISE_CHAT);
        olderSession.setCreationDate(ZonedDateTime.now().minusMinutes(1));
        irisChatSessionRepository.save(olderSession);

        IrisChatSession newerSession = new IrisChatSession(textExercise, user, IrisChatMode.TEXT_EXERCISE_CHAT);
        newerSession = irisChatSessionRepository.save(newerSession);

        // When: User requests current session
        var response = request.postWithResponseBody(textExerciseChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: The latest session should be returned
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(newerSession.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_differentUsersGetDifferentSessions() throws Exception {
        // Given: Two different users
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");

        // When: Both users request current session
        var response1 = request.postWithResponseBody(textExerciseChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        userUtilService.changeUser(TEST_PREFIX + "student2");
        var response2 = request.postWithResponseBody(textExerciseChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: Each user should have their own session
        assertThat(response1.id()).isNotEqualTo(response2.id());

        // Verify in database
        var student1Sessions = irisChatSessionRepository.findByEntityIdAndUserIdOrderByCreationDateDesc(textExercise.getId(), student1.getId(), Pageable.unpaged());
        var student2Sessions = irisChatSessionRepository.findByEntityIdAndUserIdOrderByCreationDateDesc(textExercise.getId(), student2.getId(), Pageable.unpaged());

        assertThat(student1Sessions).hasSize(1);
        assertThat(student2Sessions).hasSize(1);
        assertThat(student1Sessions.getFirst().getId()).isEqualTo(response1.id());
        assertThat(student2Sessions.getFirst().getId()).isEqualTo(response2.id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_forbiddenWhenNotInCourse() throws Exception {
        // Given: Create a separate course with unique student groups where TEST_PREFIX students are NOT enrolled
        userUtilService.addUsers("otherprefix", 2, 0, 0, 1);
        Course otherCourse = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise otherExercise = (TextExercise) otherCourse.getExercises().iterator().next();
        activateIrisFor(otherCourse);
        activateIrisFor(otherExercise);

        // When/Then: Request should be forbidden (student1 with TEST_PREFIX is not in otherCourse)
        request.postWithResponseBody("/api/iris/chat/" + otherCourse.getId() + "/sessions/current?mode=TEXT_EXERCISE_CHAT&entityId=" + otherExercise.getId(), null,
                IrisChatSessionResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_failsWhenExerciseIsExamExercise() throws Exception {
        // Given: Create an exam exercise
        var exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false);
        // Get the first text exercise from the exam
        TextExercise examExercise = (TextExercise) exam.getExerciseGroups().getFirst().getExercises().iterator().next();
        activateIrisFor(examExercise);

        // When/Then: Request should fail with conflict
        request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions/current?mode=TEXT_EXERCISE_CHAT&entityId=" + examExercise.getId(), null,
                IrisChatSessionResponseDTO.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "nonExistentUser", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_notFoundForInvalidExerciseId() throws Exception {
        // When/Then: Request with invalid exercise ID should return 403 (authorization check happens before existence check)
        request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions/current?mode=TEXT_EXERCISE_CHAT&entityId=999999", null, IrisChatSessionResponseDTO.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCurrentSessionOrCreateIfNotExists_tutorCanAccessSession() throws Exception {
        // When: Tutor requests current session
        var response = request.postWithResponseBody(textExerciseChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: Tutor should successfully create/get a session
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // Verify the session is in the database with correct exercise ID
        var sessionFromDb = irisChatSessionRepository.findById(response.id()).orElseThrow();
        assertThat(sessionFromDb.getEntityId()).isEqualTo(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCurrentSessionOrCreateIfNotExists_instructorCanAccessSession() throws Exception {
        // When: Instructor requests current session
        var response = request.postWithResponseBody(textExerciseChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: Instructor should successfully create/get a session
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // Verify the session is in the database with correct exercise ID
        var sessionFromDb = irisChatSessionRepository.findById(response.id()).orElseThrow();
        assertThat(sessionFromDb.getEntityId()).isEqualTo(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_invokesIrisCitationService() throws Exception {
        // Given: User already has an existing session so the "get existing" path is taken
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        irisChatSessionRepository.save(new IrisChatSession(textExercise, user, IrisChatMode.TEXT_EXERCISE_CHAT));

        request.postWithResponseBody(textExerciseChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        verify(irisCitationService).enrichSessionWithCitationInfo(any());
    }
}
