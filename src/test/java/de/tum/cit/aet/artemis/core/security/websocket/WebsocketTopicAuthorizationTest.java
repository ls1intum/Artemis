package de.tum.cit.aet.artemis.core.security.websocket;

import static de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry.Decision.ALLOWED;
import static de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry.Decision.DENIED;
import static de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry.Decision.INVALID;
import static de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry.Decision.UNDECLARED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry.Decision;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Checks the declared websocket topics against the rules they declare, through the same entry point the subscription interceptor uses.
 */
class WebsocketTopicAuthorizationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "wstopicauthorization";

    @Autowired
    private WebsocketTopicRegistry registry;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    private Course course;

    private TextExercise courseExercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        userUtilService.addAdmin(TEST_PREFIX);
        // not enrolled in any course: the enrollment helpers only pick up the student, tutor, editor and instructor logins of the prefix
        userUtilService.createAndSaveUser(TEST_PREFIX + "outsider1");
        course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        courseExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
    }

    @Test
    void testInvalidAndUndeclaredDestinationsAreRejected() {
        var student = user("student1");
        assertThat(registry.authorizeSubscription(student, null)).isEqualTo(INVALID);
        assertThat(registry.authorizeSubscription(null, "/topic/management/feature-toggles")).isEqualTo(INVALID);
        var anonymous = new AnonymousAuthenticationToken("test", "anonymous", List.of(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority())));
        assertThat(registry.authorizeSubscription(anonymous, "/topic/management/feature-toggles")).isEqualTo(INVALID);

        for (String pattern : List.of("/topic/**", "/topic/*/1/notifications/conversations", "/topic/user/{userId}/notifications/conversations", "/topic/#", "/topic/>",
                "/topic/management/feature-toggle?", "/user/topic/**")) {
            assertThat(registry.authorizeSubscription(student, pattern)).as("pattern %s", pattern).isEqualTo(INVALID);
        }

        // broker-internal topics, the session-specific destinations user topics resolve to, and destinations nobody declares
        for (String destination : List.of("/topic/unresolved-user", "/topic/user-registry", "/topic/newResults-user1a2b3c4d", "/topic/newResults", "/topic", "/topic/",
                "/queue/anything", "/app/iris/command-ack", "/topic/communication/courses", "/topic/communication/courses/" + course.getId() + "/extra",
                "/topic//communication/courses/" + course.getId(), "/user/topic/unknown", "/user/" + TEST_PREFIX + "student2/topic/newResults")) {
            assertThat(registry.authorizeSubscription(student, destination)).as("undeclared destination %s", destination).isEqualTo(UNDECLARED);
        }
    }

    @Test
    void testTopicsForAnyAuthenticatedUser() {
        for (String destination : List.of("/topic/management/feature-toggles", "/topic/notification/system-notification")) {
            assertThat(decide("outsider1", Role.STUDENT, destination)).as(destination).isEqualTo(ALLOWED);
        }
    }

    @Test
    void testCourseRoles() {
        String courseWidePosts = "/topic/communication/courses/" + course.getId();
        assertThat(decide("student1", Role.STUDENT, courseWidePosts)).isEqualTo(ALLOWED);
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, courseWidePosts)).isEqualTo(ALLOWED);
        assertThat(decide("outsider1", Role.STUDENT, courseWidePosts)).as("user outside the course").isEqualTo(DENIED);
        assertThat(decide("admin", Role.ADMIN, courseWidePosts)).as("elevated administrator").isEqualTo(ALLOWED);
        assertThat(decide("admin", Role.STUDENT, courseWidePosts)).as("administrator without elevation").isEqualTo(DENIED);
        assertThat(decide("student1", Role.STUDENT, "/topic/communication/courses/0" + course.getId())).as("id with leading zero").isEqualTo(DENIED);
        assertThat(decide("student1", Role.STUDENT, "/topic/communication/courses/99999999999999999999")).as("id beyond the long range").isEqualTo(DENIED);
        assertThat(decide("student1", Role.STUDENT, "/topic/communication/courses/abc")).as("id that is no number").isEqualTo(DENIED);

        String quizExercises = "/topic/courses/" + course.getId() + "/quizExercises";
        assertThat(decide("student1", Role.STUDENT, quizExercises)).isEqualTo(ALLOWED);
        assertThat(decide("outsider1", Role.STUDENT, quizExercises)).isEqualTo(DENIED);

        String operationProgress = "/topic/courses/" + course.getId() + "/operation-progress";
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, operationProgress)).isEqualTo(ALLOWED);
        assertThat(decide("student1", Role.STUDENT, operationProgress)).isEqualTo(DENIED);

        String courseArchive = "/topic/courses/" + course.getId() + "/export-course";
        assertThat(decide("instructor1", Role.INSTRUCTOR, courseArchive)).isEqualTo(ALLOWED);
        assertThat(decide("editor1", Role.EDITOR, courseArchive)).isEqualTo(DENIED);
    }

    @Test
    void testOwnUserTopic() {
        long student1Id = userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();
        String topic = "/topic/user/" + student1Id + "/notifications/conversations";
        assertThat(decide("student1", Role.STUDENT, topic)).isEqualTo(ALLOWED);
        assertThat(decide("student2", Role.STUDENT, topic)).as("another user").isEqualTo(DENIED);
        assertThat(decide("admin", Role.ADMIN, topic)).as("administrator on another user's topic").isEqualTo(DENIED);
        assertThat(decide("student1", Role.STUDENT, "/topic/user/0" + student1Id + "/notifications/conversations")).isEqualTo(DENIED);
    }

    @Test
    void testExerciseRoles() {
        String results = "/topic/exercise/" + courseExercise.getId() + "/newResults";
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, results)).isEqualTo(ALLOWED);
        assertThat(decide("student1", Role.STUDENT, results)).isEqualTo(DENIED);
        assertThat(decide("outsider1", Role.STUDENT, results)).isEqualTo(DENIED);
        assertThat(decide("admin", Role.ADMIN, results)).isEqualTo(ALLOWED);

        Exam exam = examUtilService.addExerciseGroupsAndExercisesToExam(examUtilService.addExam(course), false);
        Exercise examExercise = exam.getExerciseGroups().getFirst().getExercises().iterator().next();
        String examResults = "/topic/exercise/" + examExercise.getId() + "/newResults";
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, examResults)).as("tutor on the results of an exam exercise").isEqualTo(DENIED);
        assertThat(decide("instructor1", Role.INSTRUCTOR, examResults)).isEqualTo(ALLOWED);

        String examSubmissions = "/topic/exercise/" + examExercise.getId() + "/newSubmissions";
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, examSubmissions)).as("tutor on the template and solution builds of an exam exercise").isEqualTo(DENIED);
        assertThat(decide("editor1", Role.EDITOR, examSubmissions)).as("editor on the template and solution builds of an exam exercise").isEqualTo(ALLOWED);
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, "/topic/exercise/" + courseExercise.getId() + "/submissionProcessing")).isEqualTo(ALLOWED);

        String testCases = "/topic/programming-exercises/" + courseExercise.getId() + "/test-cases";
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, testCases)).isEqualTo(ALLOWED);
        assertThat(decide("student1", Role.STUDENT, testCases)).isEqualTo(DENIED);

        String synchronization = "/topic/exercises/" + courseExercise.getId() + "/synchronization";
        assertThat(decide("editor1", Role.EDITOR, synchronization)).isEqualTo(ALLOWED);
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, synchronization)).isEqualTo(DENIED);

        String plagiarismCheck = "/topic/text-exercises/" + courseExercise.getId() + "/plagiarism-check";
        assertThat(decide("editor1", Role.EDITOR, plagiarismCheck)).isEqualTo(ALLOWED);
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, plagiarismCheck)).isEqualTo(DENIED);
    }

    @Test
    void testLectureRoles() {
        Lecture lecture = lectureUtilService.createLecture(course);
        String processingState = "/topic/lectures/" + lecture.getId() + "/unit-processing-state";
        assertThat(decide("editor1", Role.EDITOR, processingState)).isEqualTo(ALLOWED);
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, processingState)).isEqualTo(DENIED);
        assertThat(decide("outsider1", Role.STUDENT, processingState)).isEqualTo(DENIED);
    }

    @Test
    void testExamTopics() {
        Exam exam = examUtilService.addExam(course);
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student1");

        String studentExamEvents = "/topic/exam-participation/studentExam/" + studentExam.getId() + "/events";
        assertThat(decide("student1", Role.STUDENT, studentExamEvents)).isEqualTo(ALLOWED);
        assertThat(decide("student2", Role.STUDENT, studentExamEvents)).as("another student").isEqualTo(DENIED);
        assertThat(decide("instructor1", Role.INSTRUCTOR, studentExamEvents)).as("the topic belongs to one student").isEqualTo(DENIED);

        String examEvents = "/topic/exam-participation/exam/" + exam.getId() + "/events";
        assertThat(decide("student1", Role.STUDENT, examEvents)).isEqualTo(ALLOWED);
        assertThat(decide("student2", Role.STUDENT, examEvents)).as("student without a student exam").isEqualTo(DENIED);
        assertThat(decide("instructor1", Role.INSTRUCTOR, examEvents)).isEqualTo(ALLOWED);

        for (String instructorTopic : List.of("/topic/exam/" + exam.getId() + "/started", "/topic/exam/" + exam.getId() + "/submitted",
                "/topic/exams/" + exam.getId() + "/exercise-start-status", "/topic/exams/" + exam.getId() + "/export")) {
            assertThat(decide("instructor1", Role.INSTRUCTOR, instructorTopic)).as(instructorTopic).isEqualTo(ALLOWED);
            assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, instructorTopic)).as(instructorTopic).isEqualTo(DENIED);
            assertThat(decide("admin", Role.ADMIN, instructorTopic)).as(instructorTopic).isEqualTo(ALLOWED);
        }
    }

    @Test
    void testQuizBatchTopic() {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveEnrolledQuiz(TEST_PREFIX, ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1),
                QuizMode.BATCHED);
        QuizBatch batch = new QuizBatch();
        quizExerciseUtilService.setQuizBatchExerciseAndSave(batch, quizExercise);
        QuizSubmission submission = new QuizSubmission();
        submission.setQuizBatch(batch.getId());
        quizExerciseUtilService.saveQuizSubmission(quizExercise, submission, TEST_PREFIX + "student1");

        long quizCourseId = quizExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        String batchTopic = "/topic/courses/" + quizCourseId + "/quizExercises/" + batch.getId();
        assertThat(decide("student1", Role.STUDENT, batchTopic)).as("student who joined the batch").isEqualTo(ALLOWED);
        assertThat(decide("student2", Role.STUDENT, batchTopic)).as("student who did not join the batch").isEqualTo(DENIED);
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, batchTopic)).isEqualTo(ALLOWED);
        assertThat(decide("outsider1", Role.STUDENT, batchTopic)).isEqualTo(DENIED);

        String statistics = "/topic/statistic/" + quizExercise.getId();
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, statistics)).isEqualTo(ALLOWED);
        assertThat(decide("student1", Role.STUDENT, statistics)).isEqualTo(DENIED);
    }

    @Test
    void testPlagiarismCaseTopic() {
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(courseExercise);
        plagiarismCase.setStudent(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);

        String topic = "/topic/communication/plagiarismCase/" + plagiarismCase.getId();
        assertThat(decide("student1", Role.STUDENT, topic)).as("the student the case is about").isEqualTo(ALLOWED);
        assertThat(decide("student2", Role.STUDENT, topic)).as("another student").isEqualTo(DENIED);
        assertThat(decide("tutor1", Role.TEACHING_ASSISTANT, topic)).isEqualTo(DENIED);
        assertThat(decide("instructor1", Role.INSTRUCTOR, topic)).isEqualTo(ALLOWED);

        // a case about a team belongs to all members of the team
        courseExercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(courseExercise);
        Team team = teamUtilService.createTeam(Set.of(userUtilService.getUserByLogin(TEST_PREFIX + "student2")), userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"),
                courseExercise, TEST_PREFIX + "plagiarismteam");
        PlagiarismCase teamCase = new PlagiarismCase();
        teamCase.setExercise(courseExercise);
        teamCase.setTeam(team);
        teamCase = plagiarismCaseRepository.save(teamCase);
        String teamTopic = "/topic/communication/plagiarismCase/" + teamCase.getId();
        assertThat(decide("student2", Role.STUDENT, teamTopic)).as("a member of the team the case is about").isEqualTo(ALLOWED);
        assertThat(decide("student1", Role.STUDENT, teamTopic)).as("a student outside the team").isEqualTo(DENIED);
    }

    @Test
    void testTeamParticipationTopics() {
        courseExercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(courseExercise);
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Team team = teamUtilService.createTeam(Set.of(student1), userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), courseExercise, TEST_PREFIX + "team");
        StudentParticipation participation = participationUtilService.addTeamParticipationForExercise(courseExercise, team.getId());

        for (String suffix : List.of("", "/text-submissions", "/modeling-submissions")) {
            String topic = "/topic/participations/" + participation.getId() + "/team" + suffix;
            assertThat(decide("student1", Role.STUDENT, topic)).as("team member on %s", topic).isEqualTo(ALLOWED);
            assertThat(decide("student2", Role.STUDENT, topic)).as("student outside the team on %s", topic).isEqualTo(DENIED);
            assertThat(decide("admin", Role.ADMIN, topic)).as("administrator on %s", topic).isEqualTo(DENIED);
        }
        assertThat(decide("student1", Role.STUDENT, "/topic/participations/" + participation.getId() + "/team/typing")).isEqualTo(UNDECLARED);
    }

    @Test
    void testUserTopicsNeedADeclaration() {
        for (String destination : List.of("/user/topic/newResults", "/user/topic/newSubmissions", "/user/topic/team-assignments", "/user/topic/notification/" + course.getId(),
                "/user/topic/notification/all", "/user/topic/exercise/" + courseExercise.getId() + "/participation")) {
            assertThat(decide("outsider1", Role.STUDENT, destination)).as(destination).isEqualTo(ALLOWED);
        }
    }

    @Test
    void testInterceptorDropsRejectedSubscriptionsOnly() {
        var interceptor = new WebsocketSubscriptionInterceptor(() -> registry);
        var channel = mock(MessageChannel.class);
        var student = user("student1");

        var allowed = frame(StompCommand.SUBSCRIBE, "/topic/communication/courses/" + course.getId(), student);
        assertThat(interceptor.preSend(allowed, channel)).isSameAs(allowed);

        var denied = frame(StompCommand.SUBSCRIBE, "/topic/communication/courses/" + course.getId(), user("outsider1"));
        assertThat(interceptor.preSend(denied, channel)).isNull();

        var undeclared = frame(StompCommand.SUBSCRIBE, "/topic/unresolved-user", student);
        assertThat(interceptor.preSend(undeclared, channel)).isNull();

        // other frames are left to the frame-level rules of Spring Security
        var unsubscribe = frame(StompCommand.UNSUBSCRIBE, null, student);
        assertThat(interceptor.preSend(unsubscribe, channel)).isSameAs(unsubscribe);
    }

    private Decision decide(String loginWithoutPrefix, Role role, String destination) {
        return registry.authorizeSubscription(authentication(TEST_PREFIX + loginWithoutPrefix, role), destination);
    }

    private Authentication user(String loginWithoutPrefix) {
        return authentication(TEST_PREFIX + loginWithoutPrefix, Role.STUDENT);
    }

    private static Authentication authentication(String login, Role role) {
        return new UsernamePasswordAuthenticationToken(login, "irrelevant", List.of(new SimpleGrantedAuthority(role.getAuthority())));
    }

    private static org.springframework.messaging.Message<byte[]> frame(StompCommand command, String destination, Principal user) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        headers.setUser(user);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
