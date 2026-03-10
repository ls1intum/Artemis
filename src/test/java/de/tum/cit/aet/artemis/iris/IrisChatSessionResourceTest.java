package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionCountDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionFactory;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class IrisChatSessionResourceTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iristextchatmessageintegration";

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    private ProgrammingExercise programmingExercise;

    private TextExercise textExercise;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Lecture lecture;

    private Course course;

    @BeforeEach
    void initTestCase() throws Exception {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        for (User user : users) {
            user.setSelectedLLMUsageTimestamp(ZonedDateTime.parse("2025-12-11T00:00:00Z"));
            user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);
        course = courses.getFirst();
        textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now(), ZonedDateTime.of(2040, 9, 9, 12, 12, 0, 0, ZoneId.of("Europe/Berlin")),
                ZonedDateTime.of(2040, 9, 9, 12, 12, 0, 0, ZoneId.of("Europe/Berlin")));
        StudentParticipation studentParticipation = new StudentParticipation().exercise(textExercise);
        studentParticipation.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        studentParticipationRepository.save(studentParticipation);

        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        ProgrammingExercise teamExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        teamExercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(teamExercise);

        Team team = new Team();
        team.setName("Team 1");
        team.setShortName("team1");
        team.setExercise(teamExercise);
        team.setStudents(Set.of(users.get(1), users.get(2)));
        team.setOwner(users.get(1));
        teamRepository.save(team);

        lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(textExercise);
        activateIrisFor(teamExercise);
        activateIrisFor(programmingExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllSessionsForCourseWithoutSessions() throws Exception {
        List<IrisChatSession> irisChatSessions = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", HttpStatus.OK, IrisChatSession.class);
        assertThat(irisChatSessions).hasSize(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getSessionForSessionId() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisCourseChatSession courseSession = IrisChatSessionFactory.createCourseChatSessionForUser(course, user);
        this.irisSessionRepository.save(courseSession);

        IrisSession irisChatSessions = request.get("/api/iris/chat-history/" + course.getId() + "/session/" + courseSession.getId(), HttpStatus.OK, IrisSession.class);
        assertThat(irisChatSessions.getId()).isEqualTo(courseSession.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllSessionsForCourseWithSessions_filteringOutSessionsWithoutMessages() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        irisSessionRepository.save(IrisChatSessionFactory.createCourseChatSessionForUser(course, user));
        irisSessionRepository.save(IrisChatSessionFactory.createLectureSessionForUser(lecture, user));
        irisSessionRepository.save(IrisChatSessionFactory.createTextExerciseChatSessionForUser(textExercise, user));
        irisSessionRepository.save(IrisChatSessionFactory.createProgrammingExerciseChatSessionForUser(programmingExercise, user));

        List<IrisChatSessionDTO> irisChatSessions = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", HttpStatus.OK, IrisChatSessionDTO.class);
        assertThat(irisChatSessions).hasSize(0);
    }

    private void saveChatSessionWithMessages(IrisChatSession session) {
        irisSessionRepository.save(session);
        irisMessageRepository.saveAll(session.getMessages());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllSessionsForCourseWithSessions_shouldReturnSessionsWithMessages() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        saveChatSessionWithMessages(IrisChatSessionFactory.createLectureSessionForUserWithMessages(lecture, user));
        saveChatSessionWithMessages(IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user));
        saveChatSessionWithMessages(IrisChatSessionFactory.createTextExerciseSessionForUserWithMessages(textExercise, user));
        saveChatSessionWithMessages(IrisChatSessionFactory.createProgrammingExerciseChatSessionForUserWithMessages(programmingExercise, user));

        List<IrisChatSessionDTO> irisChatSessions = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", HttpStatus.OK, IrisChatSessionDTO.class);

        assertThat(irisChatSessions).hasSize(4);

        IrisChatSessionDTO lectureSession = irisChatSessions.stream().filter(session -> session.entityId() == lecture.getId()).findFirst().orElse(null);
        assertThat(lectureSession).isNotNull();

        IrisChatSessionDTO courseSession = irisChatSessions.stream().filter(session -> session.entityId() == course.getId()).findFirst().orElse(null);
        assertThat(courseSession).isNotNull();

        IrisChatSessionDTO textExerciseSession = irisChatSessions.stream().filter(session -> session.entityId() == textExercise.getId()).findFirst().orElse(null);
        assertThat(textExerciseSession).isNotNull();

        IrisChatSessionDTO programmingExerciseSession = irisChatSessions.stream().filter(session -> session.entityId() == programmingExercise.getId()).findFirst().orElse(null);
        assertThat(programmingExerciseSession).isNotNull();

        assertThat(lectureSession.entityName()).isEqualTo(lecture.getTitle());
        assertThat(textExerciseSession.entityName()).isEqualTo(textExercise.getShortName());
        assertThat(programmingExerciseSession.entityName()).isEqualTo(programmingExercise.getShortName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllSessionsForCourseWithSessions_shouldReturnTitlesWhenPresent() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var titledCourseSession = IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user);
        titledCourseSession.setTitle("New chat");
        saveChatSessionWithMessages(titledCourseSession);

        var untitledLectureSession = IrisChatSessionFactory.createLectureSessionForUserWithMessages(lecture, user);
        saveChatSessionWithMessages(untitledLectureSession);

        List<IrisChatSessionDTO> irisChatSessions = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", HttpStatus.OK, IrisChatSessionDTO.class);

        assertThat(irisChatSessions).hasSize(2);

        IrisChatSessionDTO titledDto = irisChatSessions.stream().filter(session -> session.entityId() == course.getId()).findFirst().orElse(null);
        assertThat(titledDto.title()).isEqualTo("New chat");

        IrisChatSessionDTO untitledDto = irisChatSessions.stream().filter(session -> session.entityId() == lecture.getId()).findFirst().orElse(null);
        assertThat(untitledDto.title()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllSessionsForCourse_returnsEmptyWhenIrisDisabledForCourse() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // Create sessions with messages (which would normally be returned)
        saveChatSessionWithMessages(IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user));
        saveChatSessionWithMessages(IrisChatSessionFactory.createLectureSessionForUserWithMessages(lecture, user));

        // Disable Iris for the course
        disableIrisFor(course);

        // Should return empty list when Iris is disabled at course level
        List<IrisChatSessionDTO> irisChatSessions = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", HttpStatus.OK, IrisChatSessionDTO.class);
        assertThat(irisChatSessions).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteAllSessionsForCurrentUser() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        saveChatSessionWithMessages(IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user));
        saveChatSessionWithMessages(IrisChatSessionFactory.createLectureSessionForUserWithMessages(lecture, user));

        // Verify sessions exist
        List<IrisChatSessionDTO> sessionsBefore = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", HttpStatus.OK, IrisChatSessionDTO.class);
        assertThat(sessionsBefore).hasSize(2);

        // Delete all sessions
        request.delete("/api/iris/chat-history/sessions", HttpStatus.NO_CONTENT);

        // Verify sessions are deleted
        List<IrisChatSessionDTO> sessionsAfter = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", HttpStatus.OK, IrisChatSessionDTO.class);
        assertThat(sessionsAfter).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteAllSessionsForCurrentUser_noSessions() throws Exception {
        // Should succeed even with no sessions
        request.delete("/api/iris/chat-history/sessions", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getSessionAndMessageCount_returnsCountsAndIncreasesAfterCreatingSessions() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // Get initial counts (may be non-zero due to other tests)
        var initialCounts = request.get("/api/iris/chat-history/sessions/count", HttpStatus.OK, IrisChatSessionCountDTO.class);
        assertThat(initialCounts.sessions()).isGreaterThanOrEqualTo(0);
        assertThat(initialCounts.messages()).isGreaterThanOrEqualTo(0);

        // Create 2 sessions with 2 messages each
        saveChatSessionWithMessages(IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user));
        saveChatSessionWithMessages(IrisChatSessionFactory.createLectureSessionForUserWithMessages(lecture, user));

        var updatedCounts = request.get("/api/iris/chat-history/sessions/count", HttpStatus.OK, IrisChatSessionCountDTO.class);
        assertThat(updatedCounts.sessions()).isEqualTo(initialCounts.sessions() + 2);
        // Each session created by the factory has 2 messages (1 LLM + 1 USER)
        assertThat(updatedCounts.messages()).isEqualTo(initialCounts.messages() + 4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteIndividualSession() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var session1 = IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user);
        var session2 = IrisChatSessionFactory.createLectureSessionForUserWithMessages(lecture, user);
        saveChatSessionWithMessages(session1);
        saveChatSessionWithMessages(session2);

        // Verify both sessions exist
        List<IrisChatSessionDTO> sessionsBefore = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", HttpStatus.OK, IrisChatSessionDTO.class);
        assertThat(sessionsBefore).hasSize(2);

        // Delete individual session
        request.delete("/api/iris/chat-history/sessions/" + session1.getId(), HttpStatus.NO_CONTENT);

        // Verify only one session remains
        List<IrisChatSessionDTO> sessionsAfter = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", HttpStatus.OK, IrisChatSessionDTO.class);
        assertThat(sessionsAfter).hasSize(1);
        assertThat(sessionsAfter.getFirst().id()).isEqualTo(session2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void deleteIndividualSession_forbiddenForOtherUser() throws Exception {
        User user1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var session = IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user1);
        saveChatSessionWithMessages(session);

        // Student2 tries to delete student1's session
        request.delete("/api/iris/chat-history/sessions/" + session.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteIndividualSession_notFound() throws Exception {
        request.delete("/api/iris/chat-history/sessions/999999", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getSessionForSessionId_returns403WhenIrisDisabledForCourse() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // Create and save a valid session
        IrisCourseChatSession courseSession = IrisChatSessionFactory.createCourseChatSessionForUser(course, user);
        irisSessionRepository.save(courseSession);

        // Disable Iris for the course
        disableIrisFor(course);

        // Should return 403 Forbidden when Iris is disabled at course level
        request.get("/api/iris/chat-history/" + course.getId() + "/session/" + courseSession.getId(), HttpStatus.FORBIDDEN, IrisSession.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getSessionForSessionId_invokesIrisCitationService() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisCourseChatSession courseSession = IrisChatSessionFactory.createCourseChatSessionForUser(course, user);
        irisSessionRepository.save(courseSession);

        request.get("/api/iris/chat-history/" + course.getId() + "/session/" + courseSession.getId(), HttpStatus.OK, IrisSession.class);

        verify(irisCitationService).resolveCitationInfoFromMessages(any());
    }
}
