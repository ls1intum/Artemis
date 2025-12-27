package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

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
}
