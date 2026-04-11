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
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionResponseDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;

class IrisCourseChatSessionResourceTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iriscoursechatsessiontest";

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    private Course course;

    @BeforeEach
    void initTestCase() {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        for (User user : users) {
            user.setSelectedLLMUsageTimestamp(ZonedDateTime.parse("2025-12-11T00:00:00Z"));
            user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }

        course = courseUtilService.createCourse();

        activateIrisGlobally();
        activateIrisFor(course);
    }

    // -------------------- getCurrentSessionOrCreateIfNotExists tests --------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_createsNewSession() throws Exception {
        // When: User requests current session for the first time
        var response = request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions/current?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class,
                HttpStatus.OK);

        // Then: A new session should be created
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // Verify session was saved in database
        var sessionsInDb = irisChatSessionRepository.findByCourseIdAndChatModeAndUserIdOrderByCreationDateDesc(course.getId(), IrisChatMode.COURSE_CHAT,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId(), Pageable.unpaged());
        assertThat(sessionsInDb).hasSize(1);
        assertThat(sessionsInDb.getFirst().getId()).isEqualTo(response.id());
        assertThat(sessionsInDb.getFirst().getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_returnsExistingSession() throws Exception {
        // Given: User already has a session
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession existingSession = new IrisChatSession(course, user);
        existingSession = irisChatSessionRepository.save(existingSession);

        // When: User requests current session again
        var response = request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions/current?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class,
                HttpStatus.OK);

        // Then: The existing session should be returned
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(existingSession.getId());

        // Verify no new session was created
        var sessionsInDb = irisChatSessionRepository.findByCourseIdAndChatModeAndUserIdOrderByCreationDateDesc(course.getId(), IrisChatMode.COURSE_CHAT, user.getId(),
                Pageable.unpaged());
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
        request.postWithResponseBody("/api/iris/chat/" + otherCourse.getId() + "/sessions/current?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_forbiddenForInvalidCourseId() throws Exception {
        // When/Then: Request with invalid course ID should fail with FORBIDDEN
        // (The @EnforceAtLeastStudentInCourse annotation returns FORBIDDEN when the user cannot be verified as a course member)
        request.postWithResponseBody("/api/iris/chat/999999/sessions/current?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_differentUsersGetDifferentSessions() throws Exception {
        // Given: Student1 already has a session
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession student1Session = new IrisChatSession(course, student1);
        student1Session = irisChatSessionRepository.save(student1Session);

        // When: Student2 requests a session
        var student2Response = request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions/current?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class,
                HttpStatus.OK);

        // Then: Student2 should get a different session
        assertThat(student2Response).isNotNull();
        assertThat(student2Response.id()).isNotEqualTo(student1Session.getId());

        // Verify both sessions exist
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var student1Sessions = irisChatSessionRepository.findByCourseIdAndChatModeAndUserIdOrderByCreationDateDesc(course.getId(), IrisChatMode.COURSE_CHAT, student1.getId(),
                Pageable.unpaged());
        var student2Sessions = irisChatSessionRepository.findByCourseIdAndChatModeAndUserIdOrderByCreationDateDesc(course.getId(), IrisChatMode.COURSE_CHAT, student2.getId(),
                Pageable.unpaged());

        assertThat(student1Sessions).hasSize(1);
        assertThat(student2Sessions).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCurrentSessionOrCreateIfNotExists_tutorCanAccessSession() throws Exception {
        // When: Tutor requests current session
        var response = request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions/current?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class,
                HttpStatus.OK);

        // Then: Tutor should successfully create/get a session
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // Verify the session is in the database with correct course ID
        var sessionFromDb = irisChatSessionRepository.findById(response.id()).orElseThrow();
        assertThat(sessionFromDb.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCurrentSessionOrCreateIfNotExists_instructorCanAccessSession() throws Exception {
        // When: Instructor requests current session
        var response = request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions/current?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class,
                HttpStatus.OK);

        // Then: Instructor should successfully create/get a session
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // Verify the session is in the database with correct course ID
        var sessionFromDb = irisChatSessionRepository.findById(response.id()).orElseThrow();
        assertThat(sessionFromDb.getCourseId()).isEqualTo(course.getId());
    }

    // -------------------- createSessionForCourse tests --------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateSessionForCourse_createsNewSession() throws Exception {
        // When: User creates a new session
        var response = request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);

        // Then: A new session should be created
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // Verify session was saved in database
        var sessionsInDb = irisChatSessionRepository.findByCourseIdAndChatModeAndUserIdOrderByCreationDateDesc(course.getId(), IrisChatMode.COURSE_CHAT,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId(), Pageable.unpaged());
        assertThat(sessionsInDb).hasSize(1);
        assertThat(sessionsInDb.getFirst().getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateSessionForCourse_createsMultipleSessions() throws Exception {
        // Given: User already has a session
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession existingSession = new IrisChatSession(course, user);
        existingSession = irisChatSessionRepository.save(existingSession);

        // When: User creates another session
        var response = request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);

        // Then: A new session should be created (old one not deleted)
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.id()).isNotEqualTo(existingSession.getId());

        // Verify both sessions exist
        var sessionsInDb = irisChatSessionRepository.findByCourseIdAndChatModeAndUserIdOrderByCreationDateDesc(course.getId(), IrisChatMode.COURSE_CHAT, user.getId(),
                Pageable.unpaged());
        assertThat(sessionsInDb).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateSessionForCourse_forbiddenForInvalidCourseId() throws Exception {
        // When/Then: Request with invalid course ID should fail with FORBIDDEN
        // (The @EnforceAtLeastStudentInCourse annotation returns FORBIDDEN when the user cannot be verified as a course member)
        request.postWithResponseBody("/api/iris/chat/999999/sessions?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateSessionForCourse_tutorCanCreateSession() throws Exception {
        // When: Tutor creates a session
        var response = request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);

        // Then: Session should be created successfully
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        var sessionFromDb = irisChatSessionRepository.findById(response.id()).orElseThrow();
        assertThat(sessionFromDb.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateSessionForCourse_instructorCanCreateSession() throws Exception {
        // When: Instructor creates a session
        var response = request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);

        // Then: Session should be created successfully
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        var sessionFromDb = irisChatSessionRepository.findById(response.id()).orElseThrow();
        assertThat(sessionFromDb.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_invokesIrisCitationService() throws Exception {
        request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions/current?mode=COURSE_CHAT", null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        verify(irisCitationService).enrichSessionWithCitationInfo(any());
    }
}
