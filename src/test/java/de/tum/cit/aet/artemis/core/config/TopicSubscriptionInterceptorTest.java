package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.config.websocket.WebsocketConfiguration;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

@SuppressWarnings("unchecked")
class TopicSubscriptionInterceptorTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "topicsubscriptioninterceptor";

    @Autowired
    private WebsocketConfiguration websocketConfiguration;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseTestRepository;

    private static final String STUDENT1 = TEST_PREFIX + "student1";

    private static final String STUDENT2 = TEST_PREFIX + "student2";

    private static final String TUTOR = TEST_PREFIX + "tutor1";

    private static final String EDITOR = TEST_PREFIX + "editor1";

    private static final String INSTRUCTOR = TEST_PREFIX + "instructor1";

    /**
     * Not a member of any course.
     */
    private static final String OUTSIDER = TEST_PREFIX + "outsider";

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        userUtilService.createAndSaveUser(OUTSIDER);
    }

    @Test
    void testAllowSubscription() {
        var course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
        var exercise = course.getExercises().stream().findFirst().orElseThrow();
        var participation = exercise.getStudentParticipations().stream().findFirst().orElseThrow();

        var exam = examUtilService.addExam(course);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false);
        var examExercise = exam.getExerciseGroups().getFirst().getExercises().stream().findFirst().orElseThrow();

        var interceptor = websocketConfiguration.new TopicSubscriptionInterceptor();
        var msgMock = (Message<String>) mock(Message.class);
        try (var ignored = mockStatic(StompHeaderAccessor.class)) {
            var headerAccessorMock = mock(StompHeaderAccessor.class);
            when(StompHeaderAccessor.wrap(msgMock)).thenReturn(headerAccessorMock);
            when(headerAccessorMock.getCommand()).thenReturn(StompCommand.SUBSCRIBE);
            var principalMock = mock(Principal.class);
            when(headerAccessorMock.getUser()).thenReturn(principalMock);

            var channel = mock(MessageChannel.class);

            // Team Destination
            when(headerAccessorMock.getDestination()).thenReturn("/topic/participations/" + participation.getId() + "/team");

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            var returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student2");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            // Non Personal Exercise Result Destination
            when(headerAccessorMock.getDestination()).thenReturn("/topic/exercise/" + exercise.getId() + "/newResults");

            // Normal course exercise
            when(principalMock.getName()).thenReturn(TEST_PREFIX + "instructor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "editor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            // Exam exercise
            when(headerAccessorMock.getDestination()).thenReturn("/topic/exercise/" + examExercise.getId() + "/newResults");

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "instructor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "editor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            // Exam destination
            when(headerAccessorMock.getDestination()).thenReturn("/topic/exams/" + exam.getId() + "/test");

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "instructor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "editor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            // Exercise synchronization destination
            when(headerAccessorMock.getDestination()).thenReturn("/topic/exercises/" + exercise.getId() + "/synchronization");

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "instructor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "editor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "tutor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();
        }
    }

    @Test
    void testUserTopicOnlyForOwnUser() {
        long student1Id = userUtilService.getUserByLogin(STUDENT1).getId();
        String ownTopic = "/topic/user/" + student1Id + "/notifications/conversations";

        assertThat(canSubscribe(STUDENT1, ownTopic)).isTrue();
        assertThat(canSubscribe(STUDENT2, ownTopic)).isFalse();
        assertThat(canSubscribe(INSTRUCTOR, ownTopic)).isFalse();
        assertThat(canSubscribe(null, ownTopic)).isFalse();
        // the id has to be written exactly as the server writes it
        assertThat(canSubscribe(STUDENT1, "/topic/user/0" + student1Id + "/notifications/conversations")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/topic/user/99999999999999999999/notifications/conversations")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/topic/user/" + student1Id)).isFalse();
    }

    @Test
    void testInvalidIdsAreRejected() {
        // ids that do not fit into a long, and ids of entities that do not exist, reject the frame without closing the connection
        assertThat(canSubscribe(STUDENT1, "/topic/communication/courses/99999999999999999999")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/topic/participations/" + Long.MAX_VALUE + "/team")).isFalse();
        assertThat(canSend(STUDENT1, "/topic/participations/99999999999999999999/team/trigger")).isFalse();
    }

    @Test
    void testPatternsAndBrokerDestinationsAreRejected() {
        assertThat(canSubscribe(STUDENT1, "/topic/user/*/notifications/conversations")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/topic/**")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/topic/communication/courses/#")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/topic/communication/courses/{courseId}")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/topic/unresolved-user")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/topic/user-registry")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/topic/newResults-user1a2b3c4d")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/topic/communication/courses/1::queue")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/topic/communication/courses/1 ")).isFalse();
        assertThat(canSubscribe(STUDENT1, "/queue/errors")).isFalse();
        assertThat(canSubscribe(STUDENT1, null)).isFalse();
        assertThat(canSubscribe(INSTRUCTOR, "/topic/admin/running-jobs")).isFalse();

        // topics without course data stay open to every user
        assertThat(canSubscribe(STUDENT1, "/topic/management/feature-toggles")).isTrue();
        assertThat(canSubscribe(STUDENT1, "/user/topic/newResults")).isTrue();
    }

    @Test
    void testCourseTopics() {
        var course = textExerciseUtilService.addEnrolledCourseWithOneFinishedTextExercise(TEST_PREFIX);
        long courseId = course.getId();

        for (String prefix : Set.of("communication", "metis")) {
            String courseWidePosts = "/topic/" + prefix + "/courses/" + courseId;
            assertThat(canSubscribe(STUDENT1, courseWidePosts)).isTrue();
            assertThat(canSubscribe(OUTSIDER, courseWidePosts)).isFalse();
            // the queue syntax of an external broker would reach the checked topic under another name
            assertThat(canSubscribe(OUTSIDER, courseWidePosts + "::queue")).isFalse();
        }

        String quizExercises = "/topic/courses/" + courseId + "/quizExercises";
        assertThat(canSubscribe(STUDENT1, quizExercises)).isTrue();
        assertThat(canSubscribe(OUTSIDER, quizExercises)).isFalse();

        String operationProgress = "/topic/courses/" + courseId + "/operation-progress";
        assertThat(canSubscribe(TUTOR, operationProgress)).isTrue();
        assertThat(canSubscribe(STUDENT1, operationProgress)).isFalse();

        String orchestrationSummary = "/topic/atlas/orchestrator/" + courseId;
        assertThat(canSubscribe(TUTOR, orchestrationSummary)).isTrue();
        assertThat(canSubscribe(STUDENT1, orchestrationSummary)).isFalse();

        String courseArchive = "/topic/courses/" + courseId + "/export-course";
        assertThat(canSubscribe(INSTRUCTOR, courseArchive)).isTrue();
        assertThat(canSubscribe(EDITOR, courseArchive)).isFalse();

        var lecture = lectureUtilService.createLecture(course);
        String processingState = "/topic/lectures/" + lecture.getId() + "/unit-processing-state";
        assertThat(canSubscribe(EDITOR, processingState)).isTrue();
        assertThat(canSubscribe(TUTOR, processingState)).isFalse();
    }

    @Test
    void testExerciseTopics() {
        var course = textExerciseUtilService.addEnrolledCourseWithOneFinishedTextExercise(TEST_PREFIX);
        long exerciseId = course.getExercises().iterator().next().getId();
        var exam = examUtilService.addExerciseGroupsAndExercisesToExam(examUtilService.addExam(course), false);
        long examExerciseId = exam.getExerciseGroups().getFirst().getExercises().iterator().next().getId();

        for (String topic : Set.of("/topic/statistic/" + exerciseId, "/topic/programming-exercises/" + exerciseId + "/test-cases",
                "/topic/programming-exercises/" + exerciseId + "/test-cases-changed", "/topic/programming-exercises/" + exerciseId + "/all-builds-triggered",
                "/topic/exercise/" + exerciseId + "/newSubmissions", "/topic/exercise/" + exerciseId + "/submissionProcessing")) {
            assertThat(canSubscribe(TUTOR, topic)).as(topic).isTrue();
            assertThat(canSubscribe(STUDENT1, topic)).as(topic).isFalse();
        }

        // the template and solution builds of an exam exercise are for editors
        String examSubmissionProcessing = "/topic/exercise/" + examExerciseId + "/submissionProcessing";
        assertThat(canSubscribe(EDITOR, examSubmissionProcessing)).isTrue();
        assertThat(canSubscribe(TUTOR, examSubmissionProcessing)).isFalse();

        String plagiarismCheck = "/topic/text-exercises/" + exerciseId + "/plagiarism-check";
        assertThat(canSubscribe(EDITOR, plagiarismCheck)).isTrue();
        assertThat(canSubscribe(TUTOR, plagiarismCheck)).isFalse();
    }

    @Test
    void testExamTopics() {
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var exam = examUtilService.addExam(course);
        var studentExam = examUtilService.addStudentExamWithUser(exam, STUDENT1);

        String studentExamEvents = "/topic/exam-participation/studentExam/" + studentExam.getId() + "/events";
        assertThat(canSubscribe(STUDENT1, studentExamEvents)).isTrue();
        assertThat(canSubscribe(STUDENT2, studentExamEvents)).isFalse();
        assertThat(canSubscribe(INSTRUCTOR, studentExamEvents)).isFalse();

        String examEvents = "/topic/exam-participation/exam/" + exam.getId() + "/events";
        assertThat(canSubscribe(STUDENT1, examEvents)).isTrue();
        assertThat(canSubscribe(INSTRUCTOR, examEvents)).isTrue();
        assertThat(canSubscribe(STUDENT2, examEvents)).isFalse();

        // the exam overview of the course staff counts started and submitted exams
        for (String topic : Set.of("/topic/exam/" + exam.getId() + "/started", "/topic/exam/" + exam.getId() + "/submitted")) {
            assertThat(canSubscribe(TUTOR, topic)).as(topic).isTrue();
            assertThat(canSubscribe(STUDENT1, topic)).as(topic).isFalse();
        }

        String exerciseStartStatus = "/topic/exams/" + exam.getId() + "/exercise-start-status";
        assertThat(canSubscribe(INSTRUCTOR, exerciseStartStatus)).isTrue();
        assertThat(canSubscribe(TUTOR, exerciseStartStatus)).isFalse();
    }

    @Test
    void testQuizBatchTopic() {
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var batchedQuiz = quizExerciseTestRepository
                .save(QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now().plusMinutes(5), QuizMode.BATCHED, course));
        var batch = new QuizBatch();
        quizExerciseUtilService.setQuizBatchExerciseAndSave(batch, batchedQuiz);
        var submission = new QuizSubmission();
        submission.setQuizBatch(batch.getId());
        quizExerciseUtilService.saveQuizSubmission(batchedQuiz, submission, STUDENT1);

        // only the students who joined a batch of a batched quiz receive its questions
        String quizBatch = "/topic/courses/" + course.getId() + "/quizExercises/" + batch.getId();
        assertThat(canSubscribe(STUDENT1, quizBatch)).isTrue();
        assertThat(canSubscribe(TUTOR, quizBatch)).isTrue();
        assertThat(canSubscribe(STUDENT2, quizBatch)).isFalse();

        // all students of the course wait for the single batch of a synchronized quiz
        var synchronizedQuiz = quizExerciseUtilService.createAndSaveSynchronizedQuiz(course, ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now().plusMinutes(5), 60);
        var synchronizedBatch = new QuizBatch();
        quizExerciseUtilService.setQuizBatchExerciseAndSave(synchronizedBatch, synchronizedQuiz);
        String synchronizedQuizBatch = "/topic/courses/" + course.getId() + "/quizExercises/" + synchronizedBatch.getId();
        assertThat(canSubscribe(STUDENT2, synchronizedQuizBatch)).isTrue();
        assertThat(canSubscribe(OUTSIDER, synchronizedQuizBatch)).isFalse();
    }

    @Test
    void testPlagiarismCaseTopic() {
        var course = textExerciseUtilService.addEnrolledCourseWithOneFinishedTextExercise(TEST_PREFIX);
        var plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(course.getExercises().iterator().next());
        plagiarismCase.setStudent(userUtilService.getUserByLogin(STUDENT1));
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);

        for (String prefix : Set.of("communication", "metis")) {
            String plagiarismCasePosts = "/topic/" + prefix + "/plagiarismCase/" + plagiarismCase.getId();
            assertThat(canSubscribe(STUDENT1, plagiarismCasePosts)).isTrue();
            assertThat(canSubscribe(INSTRUCTOR, plagiarismCasePosts)).isTrue();
            assertThat(canSubscribe(STUDENT2, plagiarismCasePosts)).isFalse();
            assertThat(canSubscribe(TUTOR, plagiarismCasePosts)).isFalse();
        }
    }

    @Test
    void testSendOnlyToHandledDestinations() {
        var course = textExerciseUtilService.addEnrolledCourseWithOneFinishedTextExercise(TEST_PREFIX);
        Exercise exercise = course.getExercises().iterator().next();
        var team = teamUtilService.createTeam(Set.of(userUtilService.getUserByLogin(STUDENT1)), userUtilService.getUserByLogin(TUTOR), exercise, "team1");
        var participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        String teamTopic = "/topic/participations/" + participation.getId() + "/team";

        assertThat(canSend(STUDENT1, teamTopic + "/trigger")).isTrue();
        assertThat(canSend(STUDENT1, teamTopic + "/typing")).isTrue();
        assertThat(canSend(STUDENT2, teamTopic + "/trigger")).isFalse();
        assertThat(canSend(STUDENT2, teamTopic + "/typing")).isFalse();
        assertThat(canSend(null, teamTopic + "/trigger")).isFalse();
        // the handlers of submission updates check the team membership themselves
        assertThat(canSend(STUDENT2, teamTopic + "/text-submissions/update")).isTrue();

        String synchronization = "/topic/exercises/" + exercise.getId() + "/synchronization";
        assertThat(canSend(EDITOR, synchronization)).isTrue();
        assertThat(canSend(TUTOR, synchronization)).isFalse();

        // everything else would be forwarded by the broker to the subscribers of the topic
        long student1Id = userUtilService.getUserByLogin(STUDENT1).getId();
        assertThat(canSend(STUDENT1, teamTopic)).isFalse();
        assertThat(canSend(STUDENT1, teamTopic + "/text-submissions")).isFalse();
        assertThat(canSend(INSTRUCTOR, "/topic/user/" + student1Id + "/notifications/conversations")).isFalse();
        assertThat(canSend(INSTRUCTOR, "/topic/communication/courses/" + course.getId())).isFalse();
        assertThat(canSend(INSTRUCTOR, "/user/topic/newResults")).isFalse();

        // a client may not send the MESSAGE frames of the server, which the broker would forward as well
        assertThat(passesInterceptor(StompCommand.MESSAGE, STUDENT1, teamTopic + "/typing")).isFalse();
        assertThat(passesInterceptor(StompCommand.MESSAGE, INSTRUCTOR, "/topic/communication/courses/" + course.getId())).isFalse();
    }

    @Test
    void testQuizSubmissionsOfTheAndroidAppReachNobody() {
        String quizSubmission = "/topic/quizExercise/42/submission";
        assertThat(canSend(STUDENT1, quizSubmission)).isTrue();
        assertThat(canSubscribe(STUDENT2, quizSubmission)).isFalse();
        assertThat(canSubscribe(INSTRUCTOR, quizSubmission)).isFalse();
        assertThat(canSend(STUDENT1, "/topic/quizExercise/42/other")).isFalse();
    }

    private boolean canSubscribe(@Nullable String login, @Nullable String destination) {
        return passesInterceptor(StompCommand.SUBSCRIBE, login, destination);
    }

    private boolean canSend(@Nullable String login, String destination) {
        return passesInterceptor(StompCommand.SEND, login, destination);
    }

    private boolean passesInterceptor(StompCommand command, @Nullable String login, @Nullable String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setSessionId(TEST_PREFIX + "session");
        if (login != null) {
            Principal principal = () -> login;
            accessor.setUser(principal);
        }
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return websocketConfiguration.new TopicSubscriptionInterceptor().preSend(message, mock(MessageChannel.class)) != null;
    }
}
