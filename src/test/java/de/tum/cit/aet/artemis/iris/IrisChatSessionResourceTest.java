package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisLectureChatSessionService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class IrisChatSessionResourceTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iristextchatmessageintegration";

    @Autowired
    private IrisExerciseChatSessionService irisExerciseChatSessionService;

    @Autowired
    private IrisSessionRepository irisLectureChatSessionRepository;

    @Autowired
    private IrisLectureChatSessionService irisLectureChatSessionService;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    private ProgrammingExercise soloExercise;

    private ProgrammingExerciseStudentParticipation soloParticipation;

    private ProgrammingExercise teamExercise;

    private TextExercise textExercise;

    private ProgrammingExerciseStudentParticipation teamParticipation;

    private AtomicBoolean pipelineDone;

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

        soloExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        teamExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        teamExercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(teamExercise);

        Team team = new Team();
        team.setName("Team 1");
        team.setShortName("team1");
        team.setExercise(teamExercise);
        team.setStudents(Set.of(users.get(1), users.get(2)));
        team.setOwner(users.get(1));
        teamRepository.save(team);

        pipelineDone = new AtomicBoolean(false);

        lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(textExercise);
        activateIrisFor(teamExercise);
        activateIrisFor(soloExercise);
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
        IrisCourseChatSession courseSession = createCourseChatSessionForUser("student1");
        IrisSession irisChatSessions = request.get("/api/iris/chat-history/" + course.getId() + "/session/" + courseSession.getId(), HttpStatus.OK, IrisSession.class);
        assertThat(irisChatSessions.getId()).isEqualTo(courseSession.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllSessionsForCourseWithSessions() throws Exception {
        IrisCourseChatSession courseSession = createCourseChatSessionForUser("student1");
        IrisLectureChatSession lectureSession = createLectureSessionForUser("student1");
        IrisTextExerciseChatSession textExerciseSession = createTextExerciseChatSessionForUser("student1");
        IrisProgrammingExerciseChatSession programmingExerciseSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        List<IrisChatSessionDTO> irisChatSessions = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", HttpStatus.OK, IrisChatSessionDTO.class);
        assertThat(irisChatSessions).hasSize(4);
        assertThat(irisChatSessions.stream().filter(s -> s.id().equals(courseSession.getId())).findFirst()).isPresent();
        assertThat(irisChatSessions.stream().filter(s -> s.id().equals(lectureSession.getId())).findFirst()).isPresent();
        assertThat(irisChatSessions.stream().filter(s -> s.id().equals(textExerciseSession.getId())).findFirst()).isPresent();
        assertThat(irisChatSessions.stream().filter(s -> s.id().equals(programmingExerciseSession.getId())).findFirst()).isPresent();
    }

    private IrisLectureChatSession createLectureSessionForUser(String userLogin) {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + userLogin);
        return irisLectureChatSessionRepository.save(new IrisLectureChatSession(lecture, user));
    }

    private IrisCourseChatSession createCourseChatSessionForUser(String userLogin) {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + userLogin);
        return irisLectureChatSessionRepository.save(new IrisCourseChatSession(course, user));
    }

    private IrisTextExerciseChatSession createTextExerciseChatSessionForUser(String userLogin) {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + userLogin);
        return irisLectureChatSessionRepository.save(new IrisTextExerciseChatSession(textExercise, user));
    }
}
