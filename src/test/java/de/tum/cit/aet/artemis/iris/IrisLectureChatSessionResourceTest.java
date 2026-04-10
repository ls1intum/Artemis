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
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionResponseDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;

class IrisLectureChatSessionResourceTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irislecturechatsessiontest";

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Course course;

    private Lecture lecture;

    @BeforeEach
    void initTestCase() {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        for (User user : users) {
            user.setSelectedLLMUsageTimestamp(ZonedDateTime.parse("2025-12-11T00:00:00Z"));
            user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }

        course = courseUtilService.createCourse();
        lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());

        activateIrisGlobally();
        activateIrisFor(course);
    }

    private String lectureChatCurrentUrl(long lectureId) {
        return "/api/iris/chat/" + course.getId() + "/sessions/current?mode=LECTURE_CHAT&entityId=" + lectureId;
    }

    private String lectureChatCurrentUrl() {
        return lectureChatCurrentUrl(lecture.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_createsNewSession() throws Exception {
        // When: User requests current session for the first time
        var response = request.postWithResponseBody(lectureChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: A new session should be created
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // Verify session was saved in database
        var sessionsInDb = irisChatSessionRepository.findByEntityIdAndUserIdOrderByCreationDateDesc(lecture.getId(),
                userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId(), Pageable.unpaged());
        assertThat(sessionsInDb).hasSize(1);
        assertThat(sessionsInDb.getFirst().getId()).isEqualTo(response.id());
        assertThat(sessionsInDb.getFirst().getEntityId()).isEqualTo(lecture.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_returnsExistingSession() throws Exception {
        // Given: User already has a session
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession existingSession = new IrisChatSession(lecture, user);
        existingSession = irisChatSessionRepository.save(existingSession);

        // When: User requests current session again
        var response = request.postWithResponseBody(lectureChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: The existing session should be returned
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(existingSession.getId());

        // Verify no new session was created
        var sessionsInDb = irisChatSessionRepository.findByEntityIdAndUserIdOrderByCreationDateDesc(lecture.getId(), user.getId(), Pageable.unpaged());
        assertThat(sessionsInDb).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_returnsLatestSession() throws Exception {
        // Given: User has multiple sessions
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession olderSession = new IrisChatSession(lecture, user);
        olderSession.setCreationDate(ZonedDateTime.now().minusMinutes(1));
        irisChatSessionRepository.save(olderSession);

        IrisChatSession newerSession = new IrisChatSession(lecture, user);
        newerSession = irisChatSessionRepository.save(newerSession);

        // When: User requests current session
        var response = request.postWithResponseBody(lectureChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: The latest session should be returned
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(newerSession.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_forbiddenWhenNotInCourse() throws Exception {
        // Given: Create a separate course and lecture where student1 is NOT enrolled
        String otherPrefix = "other";
        userUtilService.addUsers(otherPrefix, 1, 0, 0, 1);  // Create separate users
        Course otherCourse = courseUtilService.addEmptyCourse();
        Lecture otherLecture = lectureUtilService.createLecture(otherCourse, ZonedDateTime.now());
        activateIrisFor(otherCourse);

        // When/Then: Request should be forbidden (student1 is not in otherCourse)
        request.postWithResponseBody("/api/iris/chat/" + otherCourse.getId() + "/sessions/current?mode=LECTURE_CHAT&entityId=" + otherLecture.getId(), null,
                IrisChatSessionResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_notFoundForInvalidLectureId() throws Exception {
        // When/Then: Request with invalid lecture ID should fail
        request.postWithResponseBody("/api/iris/chat/" + course.getId() + "/sessions/current?mode=LECTURE_CHAT&entityId=999999", null, IrisChatSessionResponseDTO.class,
                HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_differentUsersGetDifferentSessions() throws Exception {
        // Given: Student1 already has a session
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession student1Session = new IrisChatSession(lecture, student1);
        student1Session = irisChatSessionRepository.save(student1Session);

        // When: Student2 requests a session
        var student2Response = request.postWithResponseBody(lectureChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: Student2 should get a different session
        assertThat(student2Response).isNotNull();
        assertThat(student2Response.id()).isNotEqualTo(student1Session.getId());

        // Verify both sessions exist
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var student1Sessions = irisChatSessionRepository.findByEntityIdAndUserIdOrderByCreationDateDesc(lecture.getId(), student1.getId(), Pageable.unpaged());
        var student2Sessions = irisChatSessionRepository.findByEntityIdAndUserIdOrderByCreationDateDesc(lecture.getId(), student2.getId(), Pageable.unpaged());

        assertThat(student1Sessions).hasSize(1);
        assertThat(student2Sessions).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCurrentSessionOrCreateIfNotExists_tutorCanAccessSession() throws Exception {
        // When: Tutor requests current session
        var response = request.postWithResponseBody(lectureChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: Tutor should successfully create/get a session
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // Verify the session is in the database with correct lecture ID
        var sessionFromDb = irisChatSessionRepository.findById(response.id()).orElseThrow();
        assertThat(sessionFromDb.getEntityId()).isEqualTo(lecture.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCurrentSessionOrCreateIfNotExists_instructorCanAccessSession() throws Exception {
        // When: Instructor requests current session
        var response = request.postWithResponseBody(lectureChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        // Then: Instructor should successfully create/get a session
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // Verify the session is in the database with correct lecture ID
        var sessionFromDb = irisChatSessionRepository.findById(response.id()).orElseThrow();
        assertThat(sessionFromDb.getEntityId()).isEqualTo(lecture.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_invokesIrisCitationService() throws Exception {
        // Given: User already has an existing session so the "get existing" path is taken
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        irisChatSessionRepository.save(new IrisChatSession(lecture, user));

        request.postWithResponseBody(lectureChatCurrentUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        verify(irisCitationService).enrichSessionWithCitationInfo(any());
    }
}
