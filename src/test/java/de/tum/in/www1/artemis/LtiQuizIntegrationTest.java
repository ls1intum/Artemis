package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.quiz.MultipleChoiceQuestion;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.websocket.QuizSubmissionWebsocketService;

class LtiQuizIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "ltiquizsubmissiontest";

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private QuizExerciseService quizExerciseService;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private QuizSubmissionWebsocketService quizSubmissionWebsocketService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @BeforeEach
    void init() {
        // do not use the schedule service based on a time interval in the tests, because this would result in flaky tests that run much slower
        quizScheduleService.stopSchedule();
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        arrangeLtiServiceMocks();
    }

    @AfterEach
    protected void resetSpyBeans() {
        super.resetSpyBeans();
    }

    // @RepeatedTest(200)
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testLtiServicesAreCalledUponQuizSubmission(boolean isSubmitted) {

        QuizExercise quizExercise = createQuizExercise(ZonedDateTime.now().minusMinutes(1), 240);
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExercise.getQuizQuestions()) {
            quizSubmission.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
        }

        quizSubmission.submitted(isSubmitted);
        quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, () -> TEST_PREFIX + "student1");

        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isZero();
        quizScheduleService.processCachedQuizSubmissions();

        verifyNoInteractions(lti10Service);
        verifyNoInteractions(lti13Service);

        // End the quiz right now
        quizExercise = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExercise).isNotNull();
        quizExercise.setDueDate(ZonedDateTime.now());
        exerciseRepository.saveAndFlush(quizExercise);

        quizScheduleService.processCachedQuizSubmissions();

        verify(lti10Service).onNewResult(any());
        verify(lti13Service).onNewResult(any());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLtiReevaluateStatistics() throws Exception {

        QuizExercise quizExercise = createQuizExercise(ZonedDateTime.now().plusSeconds(5), 3600);
        quizExercise = request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.CREATED);

        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExerciseService.endQuiz(quizExercise, ZonedDateTime.now().minusMinutes(1));
        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.OK);

        // submission with everything selected
        QuizSubmission quizSubmission = QuizExerciseFactory.generateSpecialSubmissionWithResult(quizExercise, true, ZonedDateTime.now().minusHours(1), true);
        participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);

        // calculate statistics, expected to a call to onNewResult here
        quizExercise = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", HttpStatus.OK, QuizExercise.class);

        // remove wrong answer option and reevaluate
        var multipleChoiceQuestion = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        multipleChoiceQuestion.getAnswerOptions().remove(1);

        // expected to a call to onNewResult here
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.OK);

        verify(lti10Service, times(2)).onNewResult(any());
        verify(lti13Service, times(2)).onNewResult(any());

    }

    private QuizExercise createQuizExercise(ZonedDateTime releaseDate, int duration) {
        Course course = courseUtilService.createCourse();
        course.setOnlineCourse(true);
        courseRepository.save(course);

        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, releaseDate, null, QuizMode.SYNCHRONIZED);
        quizExercise.duration(duration);
        return quizExercise;
    }

    private void arrangeLtiServiceMocks() {
        doNothing().when(lti10Service).onNewResult(any());
        doNothing().when(lti13Service).onNewResult(any());
    }
}
