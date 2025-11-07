package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisLectureChatSessionRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;

class IrisLectureChatSessionResourceTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irislecturechatsessiontest";

    @Autowired
    private IrisLectureChatSessionRepository irisLectureChatSessionRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Lecture lecture;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);

        Course course = courseUtilService.createCourse();
        lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());

        activateIrisGlobally();
        activateIrisFor(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_createsNewSession() throws Exception {
        // When: User requests current session for the first time
        var response = request.postWithResponseBody("/api/iris/lecture-chat/" + lecture.getId() + "/sessions/current", null, IrisLectureChatSession.class, HttpStatus.CREATED);

        // Then: A new session should be created
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        // Verify session was saved in database
        var sessionsInDb = irisLectureChatSessionRepository.findByLectureIdAndUserIdOrderByCreationDateDesc(lecture.getId(),
                userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId());
        assertThat(sessionsInDb).hasSize(1);
        assertThat(sessionsInDb.getFirst().getId()).isEqualTo(response.getId());
        assertThat(sessionsInDb.getFirst().getLectureId()).isEqualTo(lecture.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_returnsExistingSession() throws Exception {
        // Given: User already has a session
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisLectureChatSession existingSession = new IrisLectureChatSession(lecture, user);
        existingSession = irisLectureChatSessionRepository.save(existingSession);

        // When: User requests current session again
        var response = request.postWithResponseBody("/api/iris/lecture-chat/" + lecture.getId() + "/sessions/current", null, IrisLectureChatSession.class, HttpStatus.OK);

        // Then: The existing session should be returned
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(existingSession.getId());

        // Verify no new session was created
        var sessionsInDb = irisLectureChatSessionRepository.findByLectureIdAndUserIdOrderByCreationDateDesc(lecture.getId(), user.getId());
        assertThat(sessionsInDb).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_returnsLatestSession() throws Exception {
        // Given: User has multiple sessions
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisLectureChatSession olderSession = new IrisLectureChatSession(lecture, user);
        olderSession.setCreationDate(ZonedDateTime.now().minusMinutes(1));
        irisLectureChatSessionRepository.save(olderSession);

        IrisLectureChatSession newerSession = new IrisLectureChatSession(lecture, user);
        newerSession = irisLectureChatSessionRepository.save(newerSession);

        // When: User requests current session
        var response = request.postWithResponseBody("/api/iris/lecture-chat/" + lecture.getId() + "/sessions/current", null, IrisLectureChatSession.class, HttpStatus.OK);

        // Then: The latest session should be returned
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(newerSession.getId());
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
        request.postWithResponseBody("/api/iris/lecture-chat/" + otherLecture.getId() + "/sessions/current", null, IrisLectureChatSession.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_notFoundForInvalidLectureId() throws Exception {
        // When/Then: Request with invalid lecture ID should fail
        request.postWithResponseBody("/api/iris/lecture-chat/999999/sessions/current", null, IrisLectureChatSession.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetCurrentSessionOrCreateIfNotExists_differentUsersGetDifferentSessions() throws Exception {
        // Given: Student1 already has a session
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisLectureChatSession student1Session = new IrisLectureChatSession(lecture, student1);
        student1Session = irisLectureChatSessionRepository.save(student1Session);

        // When: Student2 requests a session
        var student2Response = request.postWithResponseBody("/api/iris/lecture-chat/" + lecture.getId() + "/sessions/current", null, IrisLectureChatSession.class,
                HttpStatus.CREATED);

        // Then: Student2 should get a different session
        assertThat(student2Response).isNotNull();
        assertThat(student2Response.getId()).isNotEqualTo(student1Session.getId());

        // Verify both sessions exist
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var student1Sessions = irisLectureChatSessionRepository.findByLectureIdAndUserIdOrderByCreationDateDesc(lecture.getId(), student1.getId());
        var student2Sessions = irisLectureChatSessionRepository.findByLectureIdAndUserIdOrderByCreationDateDesc(lecture.getId(), student2.getId());

        assertThat(student1Sessions).hasSize(1);
        assertThat(student2Sessions).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCurrentSessionOrCreateIfNotExists_tutorCanAccessSession() throws Exception {
        // When: Tutor requests current session
        var response = request.postWithResponseBody("/api/iris/lecture-chat/" + lecture.getId() + "/sessions/current", null, IrisLectureChatSession.class, HttpStatus.CREATED);

        // Then: Tutor should successfully create/get a session
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        // Verify the session is in the database with correct lecture ID
        var sessionFromDb = irisLectureChatSessionRepository.findById(response.getId()).orElseThrow();
        assertThat(sessionFromDb.getLectureId()).isEqualTo(lecture.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCurrentSessionOrCreateIfNotExists_instructorCanAccessSession() throws Exception {
        // When: Instructor requests current session
        var response = request.postWithResponseBody("/api/iris/lecture-chat/" + lecture.getId() + "/sessions/current", null, IrisLectureChatSession.class, HttpStatus.CREATED);

        // Then: Instructor should successfully create/get a session
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        // Verify the session is in the database with correct lecture ID
        var sessionFromDb = irisLectureChatSessionRepository.findById(response.getId()).orElseThrow();
        assertThat(sessionFromDb.getLectureId()).isEqualTo(lecture.getId());
    }
}
