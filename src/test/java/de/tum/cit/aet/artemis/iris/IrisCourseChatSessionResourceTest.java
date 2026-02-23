package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseChatSessionRepository;

class IrisCourseChatSessionResourceTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iriscoursechatsessiontest";

    @Autowired
    private IrisCourseChatSessionRepository irisCourseChatSessionRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);

        course = courseUtilService.createCourse();

        activateIrisGlobally();
        activateIrisFor(course);
    }

    // -------------------- getCurrentSessionOrCreateIfNotExists tests --------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_createsNewSession() throws Exception {
        // When: User requests current session for the first time
        var response = request.postWithResponseBody("/api/iris/course-chat/" + course.getId() + "/sessions/current", null, IrisCourseChatSession.class, HttpStatus.OK);

        // Then: A new session should be created
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        // Verify session was saved in database
        var sessionsInDb = irisCourseChatSessionRepository.findByCourseIdAndUserId(course.getId(), userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId());
        assertThat(sessionsInDb).hasSize(1);
        assertThat(sessionsInDb.getFirst().getId()).isEqualTo(response.getId());
        assertThat(sessionsInDb.getFirst().getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_returnsExistingSession() throws Exception {
        // Given: User already has a session
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisCourseChatSession existingSession = new IrisCourseChatSession(course, user);
        existingSession = irisCourseChatSessionRepository.save(existingSession);

        // When: User requests current session again
        var response = request.postWithResponseBody("/api/iris/course-chat/" + course.getId() + "/sessions/current", null, IrisCourseChatSession.class, HttpStatus.OK);

        // Then: The existing session should be returned
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(existingSession.getId());

        // Verify no new session was created
        var sessionsInDb = irisCourseChatSessionRepository.findByCourseIdAndUserId(course.getId(), user.getId());
        assertThat(sessionsInDb).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_forbiddenWhenNotInCourse() throws Exception {
        // Given: Create a separate course where student1 is NOT enrolled
        String otherPrefix = "other";
        userUtilService.addUsers(otherPrefix, 1, 0, 0, 1);
        Course otherCourse = courseUtilService.addEmptyCourse();
        activateIrisFor(otherCourse);

        // When/Then: Request should be forbidden (student1 is not in otherCourse)
        request.postWithResponseBody("/api/iris/course-chat/" + otherCourse.getId() + "/sessions/current", null, IrisCourseChatSession.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_forbiddenForInvalidCourseId() throws Exception {
        // When/Then: Request with invalid course ID should fail with FORBIDDEN
        // (The @EnforceAtLeastStudentInCourse annotation returns FORBIDDEN when the user cannot be verified as a course member)
        request.postWithResponseBody("/api/iris/course-chat/999999/sessions/current", null, IrisCourseChatSession.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_differentUsersGetDifferentSessions() throws Exception {
        // Given: Student1 already has a session
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisCourseChatSession student1Session = new IrisCourseChatSession(course, student1);
        student1Session = irisCourseChatSessionRepository.save(student1Session);

        // When: Student2 requests a session
        var student2Response = request.postWithResponseBody("/api/iris/course-chat/" + course.getId() + "/sessions/current", null, IrisCourseChatSession.class, HttpStatus.OK);

        // Then: Student2 should get a different session
        assertThat(student2Response).isNotNull();
        assertThat(student2Response.getId()).isNotEqualTo(student1Session.getId());

        // Verify both sessions exist
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var student1Sessions = irisCourseChatSessionRepository.findByCourseIdAndUserId(course.getId(), student1.getId());
        var student2Sessions = irisCourseChatSessionRepository.findByCourseIdAndUserId(course.getId(), student2.getId());

        assertThat(student1Sessions).hasSize(1);
        assertThat(student2Sessions).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCurrentSessionOrCreateIfNotExists_tutorCanAccessSession() throws Exception {
        // When: Tutor requests current session
        var response = request.postWithResponseBody("/api/iris/course-chat/" + course.getId() + "/sessions/current", null, IrisCourseChatSession.class, HttpStatus.OK);

        // Then: Tutor should successfully create/get a session
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        // Verify the session is in the database with correct course ID
        var sessionFromDb = irisCourseChatSessionRepository.findById(response.getId()).orElseThrow();
        assertThat(sessionFromDb.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCurrentSessionOrCreateIfNotExists_instructorCanAccessSession() throws Exception {
        // When: Instructor requests current session
        var response = request.postWithResponseBody("/api/iris/course-chat/" + course.getId() + "/sessions/current", null, IrisCourseChatSession.class, HttpStatus.OK);

        // Then: Instructor should successfully create/get a session
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        // Verify the session is in the database with correct course ID
        var sessionFromDb = irisCourseChatSessionRepository.findById(response.getId()).orElseThrow();
        assertThat(sessionFromDb.getCourseId()).isEqualTo(course.getId());
    }

    // -------------------- getAllSessions tests --------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllSessions_returnsAllUserSessions() throws Exception {
        // Given: User has multiple sessions
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisCourseChatSession session1 = new IrisCourseChatSession(course, user);
        session1 = irisCourseChatSessionRepository.save(session1);
        IrisCourseChatSession session2 = new IrisCourseChatSession(course, user);
        session2 = irisCourseChatSessionRepository.save(session2);

        // When: User requests all sessions
        var response = request.getList("/api/iris/course-chat/" + course.getId() + "/sessions", HttpStatus.OK, IrisCourseChatSession.class);

        // Then: All sessions should be returned
        assertThat(response).hasSize(2);
        assertThat(response).extracting(IrisCourseChatSession::getId).containsExactlyInAnyOrder(session1.getId(), session2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllSessions_notFoundWhenNoSessions() throws Exception {
        // When/Then: Request should fail when user has no sessions
        request.getList("/api/iris/course-chat/" + course.getId() + "/sessions", HttpStatus.NOT_FOUND, IrisCourseChatSession.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetAllSessions_onlyReturnsOwnSessions() throws Exception {
        // Given: Student1 has sessions, Student2 has one session
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        IrisCourseChatSession student1Session = new IrisCourseChatSession(course, student1);
        irisCourseChatSessionRepository.save(student1Session);
        IrisCourseChatSession student2Session = new IrisCourseChatSession(course, student2);
        student2Session = irisCourseChatSessionRepository.save(student2Session);

        // When: Student2 requests all sessions
        var response = request.getList("/api/iris/course-chat/" + course.getId() + "/sessions", HttpStatus.OK, IrisCourseChatSession.class);

        // Then: Only Student2's sessions should be returned
        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getId()).isEqualTo(student2Session.getId());
    }

    // -------------------- createSessionForCourse tests --------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateSessionForCourse_createsNewSession() throws Exception {
        // When: User creates a new session
        var response = request.postWithResponseBody("/api/iris/course-chat/" + course.getId() + "/sessions", null, IrisCourseChatSession.class, HttpStatus.CREATED);

        // Then: A new session should be created
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        // Verify session was saved in database
        var sessionsInDb = irisCourseChatSessionRepository.findByCourseIdAndUserId(course.getId(), userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId());
        assertThat(sessionsInDb).hasSize(1);
        assertThat(sessionsInDb.getFirst().getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateSessionForCourse_createsMultipleSessions() throws Exception {
        // Given: User already has a session
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisCourseChatSession existingSession = new IrisCourseChatSession(course, user);
        existingSession = irisCourseChatSessionRepository.save(existingSession);

        // When: User creates another session
        var response = request.postWithResponseBody("/api/iris/course-chat/" + course.getId() + "/sessions", null, IrisCourseChatSession.class, HttpStatus.CREATED);

        // Then: A new session should be created (old one not deleted)
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getId()).isNotEqualTo(existingSession.getId());

        // Verify both sessions exist
        var sessionsInDb = irisCourseChatSessionRepository.findByCourseIdAndUserId(course.getId(), user.getId());
        assertThat(sessionsInDb).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateSessionForCourse_forbiddenForInvalidCourseId() throws Exception {
        // When/Then: Request with invalid course ID should fail with FORBIDDEN
        // (The @EnforceAtLeastStudentInCourse annotation returns FORBIDDEN when the user cannot be verified as a course member)
        request.postWithResponseBody("/api/iris/course-chat/999999/sessions", null, IrisCourseChatSession.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateSessionForCourse_tutorCanCreateSession() throws Exception {
        // When: Tutor creates a session
        var response = request.postWithResponseBody("/api/iris/course-chat/" + course.getId() + "/sessions", null, IrisCourseChatSession.class, HttpStatus.CREATED);

        // Then: Session should be created successfully
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        var sessionFromDb = irisCourseChatSessionRepository.findById(response.getId()).orElseThrow();
        assertThat(sessionFromDb.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateSessionForCourse_instructorCanCreateSession() throws Exception {
        // When: Instructor creates a session
        var response = request.postWithResponseBody("/api/iris/course-chat/" + course.getId() + "/sessions", null, IrisCourseChatSession.class, HttpStatus.CREATED);

        // Then: Session should be created successfully
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        var sessionFromDb = irisCourseChatSessionRepository.findById(response.getId()).orElseThrow();
        assertThat(sessionFromDb.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_invokesIrisCitationService() throws Exception {
        request.postWithResponseBody("/api/iris/course-chat/" + course.getId() + "/sessions/current", null, IrisCourseChatSession.class, HttpStatus.OK);

        verify(irisCitationService).enrichSessionWithCitationInfo(any());
    }
}
