package de.tum.in.www1.artemis.exercise.quizexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.enumeration.ScoringType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.QuizBatchService;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.websocket.QuizSubmissionWebsocketService;

class QuizSubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "quizsubmissiontest";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final int NUMBER_OF_STUDENTS = 10;

    private static final int NUMBER_OF_TUTORS = 1;

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
    private QuizSubmissionRepository quizSubmissionRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private QuizBatchService quizBatchService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @BeforeEach
    void init() {
        // do not use the schedule service based on a time interval in the tests, because this would result in flaky tests that run much slower
        quizScheduleService.stopSchedule();
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testQuizSubmit() {
        QuizExercise quizExercise = setupQuizExerciseParameters();
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission;

        for (int i = 1; i <= NUMBER_OF_STUDENTS; i++) {
            quizSubmission = quizExerciseUtilService.generateSubmissionForThreeQuestions(quizExercise, i, false, null);
            final var username = TEST_PREFIX + "student" + i;
            final Principal principal = () -> username;
            // save
            quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, principal);
        }

        // only half of the students submit manually
        for (int i = 1; i <= NUMBER_OF_STUDENTS / 2; i++) {
            quizSubmission = quizExerciseUtilService.generateSubmissionForThreeQuestions(quizExercise, i, true, null);
            final var username = TEST_PREFIX + "student" + i;
            final Principal principal = () -> username;
            // submit
            quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, principal);
        }

        // before the quiz submissions are processed, none of them ends up in the database
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isZero();

        // process first half of the submissions
        quizScheduleService.processCachedQuizSubmissions();
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(NUMBER_OF_STUDENTS / 2);

        // End the quiz right now so that results can be processed
        quizExercise = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        final var exercise = quizExercise;
        assertThat(quizExercise).isNotNull();
        quizExercise.setDueDate(ZonedDateTime.now());
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(quizBatchService.quizBatchStartDate(exercise, batch.getStartTime())));
        exerciseRepository.saveAndFlush(quizExercise);

        quizScheduleService.processCachedQuizSubmissions();

        // after the quiz submissions have been processed, all submission are saved to the database
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(NUMBER_OF_STUDENTS);

        // Test the statistics directly from the database
        QuizExercise quizExerciseWithStatistic = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExerciseWithStatistic).isNotNull();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isZero();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isEqualTo(NUMBER_OF_STUDENTS);
        int questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getPoints).reduce(0, Integer::sum);
        assertThat(quizExerciseWithStatistic.getMaxPoints()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()).hasSize(questionScore + 1);
        // check general statistics
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            log.debug(pointCounter.toString());
            if (pointCounter.getPoints() == 0.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 3.0));
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            else if (pointCounter.getPoints() == 3.0 || pointCounter.getPoints() == 4.0 || pointCounter.getPoints() == 6.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 6.0));
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            else if (pointCounter.getPoints() == 7.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 12.0));
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
        }
        // check statistic for each question
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            log.debug(question.getQuizQuestionStatistic().toString());
            if (question instanceof MultipleChoiceQuestion) {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 2.0));
            }
            else if (question instanceof DragAndDropQuestion) {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 3.0));
            }
            else {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(NUMBER_OF_STUDENTS / 4);
            }
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getParticipantsRated()).isEqualTo(NUMBER_OF_STUDENTS);
            assertThat(question.getQuizQuestionStatistic().getParticipantsUnrated()).isZero();
        }

        // execute the scheduler again, this should remove the quiz exercise from the cache
        quizScheduleService.processCachedQuizSubmissions();
        // but of course keep all submissions
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(NUMBER_OF_STUDENTS);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testQuizSubmit_partial_points() {
        QuizExercise quizExercise = setupQuizExerciseParameters();
        // force getting partial points
        quizExercise.getQuizQuestions().get(0).setScoringType(ScoringType.PROPORTIONAL_WITHOUT_PENALTY);
        quizExercise.getQuizQuestions().get(1).score(1);
        quizExercise.getQuizQuestions().get(2).score(1);
        quizExercise = quizExerciseService.save(quizExercise);

        MultipleChoiceQuestion mcQuestion = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        DragAndDropQuestion dndQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);

        List<QuizSubmission> submissions = new ArrayList<>();

        QuizSubmission student1Submission = new QuizSubmission();

        // student 1 achieves 1/3 points for the DnD question
        setupDragAndDropSubmission(dndQuestion, student1Submission, 2);
        // and 0.5 points for the SA question
        setupShortAnswerSubmission(saQuestion, student1Submission, 1);

        // in total, student 1 achieves 0.83 points -> rounded to 1 point
        student1Submission.submitted(true);
        student1Submission.submissionDate(null);
        submissions.add(student1Submission);

        QuizSubmission student2Submission = new QuizSubmission();

        // student 2 achieves 1/3 points for the DnD question
        setupDragAndDropSubmission(dndQuestion, student2Submission, 2);

        // in total, student 2 achieves 0.33 points -> rounded to 0 points
        student2Submission.submitted(true);
        student2Submission.submissionDate(null);
        submissions.add(student2Submission);

        QuizSubmission student3Submission = new QuizSubmission();
        var correctAnswerOption = mcQuestion.getAnswerOptions().stream().filter(AnswerOption::isIsCorrect).findFirst().get();

        MultipleChoiceSubmittedAnswer student3mcAnswer = new MultipleChoiceSubmittedAnswer();
        student3mcAnswer.setQuizQuestion(mcQuestion);
        student3mcAnswer.addSelectedOptions(correctAnswerOption);

        // student 3 achieves 4 points for the MC question
        student3Submission.addSubmittedAnswers(student3mcAnswer);

        // and 1 point for the DnD question
        setupDragAndDropSubmission(dndQuestion, student3Submission, 3);
        // and 1 point for the SA question
        setupShortAnswerSubmission(saQuestion, student3Submission, 2);

        // in total, student 3 achieves 6 points
        student3Submission.submitted(true);
        student3Submission.submissionDate(null);
        submissions.add(student3Submission);

        for (int i = 0; i < 3; i++) {
            var username = TEST_PREFIX + "student" + (i + 1);
            final Principal principal = () -> username;
            quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), submissions.get(i), principal);
        }

        quizScheduleService.processCachedQuizSubmissions();

        QuizExercise quizExerciseWithStatistic = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        var quizPointStatistic = quizExerciseWithStatistic.getQuizPointStatistic();
        assertThat(quizExerciseWithStatistic).isNotNull();

        for (var pointCounter : quizPointStatistic.getPointCounters()) {
            assertThat(pointCounter.getUnRatedCounter()).as("Unrated counter is always 0").isZero();
            if (pointCounter.getPoints() == 0.0) {
                assertThat(pointCounter.getRatedCounter()).as("Bucket 0.0 contains 0 rated submission -> 0.33 points").isEqualTo(1);
            }
            else if (pointCounter.getPoints() == 1.0) {
                assertThat(pointCounter.getRatedCounter()).as("Bucket 1.0 contains 0 rated submission -> 0.83 points").isEqualTo(1);
            }
            else if (pointCounter.getPoints() == 6.0) {
                assertThat(pointCounter.getRatedCounter()).as("Bucket 6.0 contains 1 rated submission -> 6 points").isEqualTo(1);
            }
            else {
                assertThat(pointCounter.getRatedCounter()).as("All other buckets contain 0 rated submissions").isZero();
            }

        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    @EnumSource(QuizMode.class)
    void testQuizSubmitLiveMode(QuizMode quizMode) throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusSeconds(10), null, quizMode);
        quizExercise.setDuration(600);
        quizExercise = quizExerciseService.save(quizExercise);

        // at the beginning there are no submissions and no participants
        assertThat(quizSubmissionRepository.findByParticipation_Exercise_Id(quizExercise.getId())).isEmpty();
        assertThat(participationRepository.findByExerciseId(quizExercise.getId())).isEmpty();

        int numberOfParticipants = 10;

        if (quizMode != QuizMode.SYNCHRONIZED) {
            var batch = quizBatchService.save(QuizExerciseFactory.generateQuizBatch(quizExercise, ZonedDateTime.now().minusSeconds(10)));
            for (int i = 1; i <= numberOfParticipants; i++) {
                joinQuizBatch(quizExercise, batch, TEST_PREFIX + "student" + i);
            }
        }

        for (int i = 1; i <= numberOfParticipants; i++) {
            userUtilService.changeUser(TEST_PREFIX + "student" + i);
            QuizSubmission quizSubmission = quizExerciseUtilService.generateSubmissionForThreeQuestions(quizExercise, i, false, null);
            assertThat(quizSubmission.getSubmittedAnswers()).hasSize(3);
            assertThat(quizSubmission.isSubmitted()).isFalse();
            assertThat(quizSubmission.getSubmissionDate()).isNull();
            QuizSubmission updatedSubmission = request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, QuizSubmission.class,
                    HttpStatus.OK);
            // check whether submission flag was updated
            assertThat(updatedSubmission.isSubmitted()).isTrue();
            // check whether all answers were submitted properly
            assertThat(updatedSubmission.getSubmittedAnswers()).hasSameSizeAs(quizSubmission.getSubmittedAnswers());
            // check whether submission date was set
            assertThat(updatedSubmission.getSubmissionDate()).isNotNull();
        }

        // process cached submissions
        quizScheduleService.processCachedQuizSubmissions();

        // check whether all submissions were saved to the database
        assertThat(quizSubmissionRepository.findByParticipation_Exercise_Id(quizExercise.getId())).hasSize(numberOfParticipants);
        assertThat(participationRepository.findByExerciseId(quizExercise.getId())).hasSize(numberOfParticipants);
    }

    private void joinQuizBatch(QuizExercise quizExercise, QuizBatch batch, String username) {
        var user = new User();
        user.setLogin(username);
        quizScheduleService.joinQuizBatch(quizExercise, batch, user);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    @EnumSource(QuizMode.class)
    void testQuizSubmitLiveMode_badRequest_notActive(QuizMode quizMode) throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().plusSeconds(20), ZonedDateTime.now().plusSeconds(30), quizMode);
        quizExercise.setDuration(10);
        quizExercise = quizExerciseService.save(quizExercise);

        if (quizMode != QuizMode.SYNCHRONIZED) {
            var batch = quizBatchService.save(QuizExerciseFactory.generateQuizBatch(quizExercise, ZonedDateTime.now().plusSeconds(10)));
            joinQuizBatch(quizExercise, batch, TEST_PREFIX + "student4");
        }

        QuizSubmission quizSubmission = quizExerciseUtilService.generateSubmissionForThreeQuestions(quizExercise, 1, false, null);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student5", roles = "USER")
    @EnumSource(QuizMode.class)
    void testQuizSubmitLiveMode_badRequest_alreadySubmitted(QuizMode quizMode) throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, 5);
        Course course = courses.get(0);
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusSeconds(5), ZonedDateTime.now().plusSeconds(10), quizMode);
        quizExercise.setDuration(10);
        quizExercise = quizExerciseService.save(quizExercise);

        if (quizMode != QuizMode.SYNCHRONIZED) {
            var batch = quizBatchService.save(QuizExerciseFactory.generateQuizBatch(quizExercise, ZonedDateTime.now().minusSeconds(5)));
            joinQuizBatch(quizExercise, batch, TEST_PREFIX + "student5");
        }

        QuizSubmission quizSubmission = quizExerciseUtilService.generateSubmissionForThreeQuestions(quizExercise, 1, false, ZonedDateTime.now());
        // submit quiz for the first time, expected status = OK
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, Result.class, HttpStatus.OK);
        // submit quiz for the second time, expected status = BAD_REQUEST
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student5", roles = "USER")
    void testQuizSubmitEmptyQuizInLiveMode() throws Exception {
        int invalidExerciseId = -1;

        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setDuration(350);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now().minusMinutes(5)));
        quizExerciseService.save(quizExercise);

        quizScheduleService.clearAllQuizData();
        quizScheduleService.clearQuizData(quizExercise.getId());

        QuizSubmission quizSubmission = quizExerciseUtilService.generateSubmissionForThreeQuestions(quizExercise, 1, false, ZonedDateTime.now());

        // submit quiz more times than the allowed number of attempts, expected status = BAD_REQUEST
        request.postWithResponseBody("/api/exercises/" + invalidExerciseId + "/submissions/live", quizSubmission, Result.class, HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student7", roles = "USER")
    @EnumSource(QuizMode.class)
    void testQuizSubmitNoDatabaseRequests(QuizMode quizMode) throws Exception {
        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusHours(5), null, quizMode);
        quizExercise.setDuration(360);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now().minusMinutes(5)));
        quizExerciseService.save(quizExercise);

        if (quizMode != QuizMode.SYNCHRONIZED) {
            var batch = quizBatchService.save(QuizExerciseFactory.generateQuizBatch(quizExercise, ZonedDateTime.now().minusSeconds(5)));
            joinQuizBatch(quizExercise, batch, TEST_PREFIX + "student7");
        }

        QuizSubmission quizSubmission = quizExerciseUtilService.generateSubmissionForThreeQuestions(quizExercise, 1, false, ZonedDateTime.now());

        assertThatDb(() -> request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, Result.class, HttpStatus.OK))
                .hasBeenCalledTimes(quizMode == QuizMode.SYNCHRONIZED ? 0 : 1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student8", roles = "USER")
    @EnumSource(QuizMode.class)
    void testQuizSubmitPractice(QuizMode quizMode) throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusSeconds(10), null, quizMode);
        quizExercise.setDueDate(ZonedDateTime.now().minusSeconds(8));
        quizExercise.setDuration(2);
        quizExercise.setIsOpenForPractice(true);
        quizExerciseService.save(quizExercise);

        // at the beginning there are no submissions and participants
        assertThat(quizSubmissionRepository.findByParticipation_Exercise_Id(quizExercise.getId())).isEmpty();
        assertThat(participationRepository.findByExerciseId(quizExercise.getId())).isEmpty();

        // submit 10 times for 10 different students
        for (int i = 1; i <= NUMBER_OF_STUDENTS; i++) {
            QuizSubmission quizSubmission = quizExerciseUtilService.generateSubmissionForThreeQuestions(quizExercise, i, true, null);
            userUtilService.changeUser(TEST_PREFIX + "student" + i);
            Result receivedResult = request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/practice", quizSubmission, Result.class, HttpStatus.OK);
            assertThat(((QuizSubmission) receivedResult.getSubmission()).getSubmittedAnswers()).hasSameSizeAs(quizSubmission.getSubmittedAnswers());
        }

        // after the quiz has ended, all submission are saved to the database
        assertThat(quizSubmissionRepository.findByParticipation_Exercise_Id(quizExercise.getId())).hasSize(NUMBER_OF_STUDENTS);
        assertThat(participationRepository.findByExerciseId(quizExercise.getId())).hasSize(NUMBER_OF_STUDENTS);

        // processing the quiz submissions will update the statistics
        quizScheduleService.processCachedQuizSubmissions();

        // Test the statistics directly from the database
        QuizExercise quizExerciseWithStatistic = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExerciseWithStatistic).isNotNull();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isZero();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isEqualTo(NUMBER_OF_STUDENTS);
        int questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getPoints).reduce(0, Integer::sum);
        assertThat(quizExerciseWithStatistic.getMaxPoints()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()).hasSize(questionScore + 1);
        // check general statistics
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getPoints() == 0.0) {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(3);
            }
            else if (pointCounter.getPoints() == 3.0 || pointCounter.getPoints() == 4.0 || pointCounter.getPoints() == 6.0) {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(2);
            }
            else if (pointCounter.getPoints() == 7.0) {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(1);
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
        }
        // check statistic for each question
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(5);
            }
            else if (question instanceof DragAndDropQuestion) {
                assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(3);
            }
            else {
                assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(2);
            }
            assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getParticipantsUnrated()).isEqualTo(NUMBER_OF_STUDENTS);
            assertThat(question.getQuizQuestionStatistic().getParticipantsRated()).isZero();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testQuizSubmitPractice_badRequest() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        QuizExercise quizExerciseServer = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        quizExerciseServer.setDueDate(ZonedDateTime.now().minusSeconds(2));
        quizExerciseServer.setDuration(2);
        quizExerciseServer.setIsOpenForPractice(false);
        quizExerciseService.save(quizExerciseServer);

        assertThat(quizSubmissionRepository.findByParticipation_Exercise_Id(quizExerciseServer.getId())).isEmpty();

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExerciseServer.getQuizQuestions()) {
            for (int i = 1; i <= 10; i++) {
                var answer = quizExerciseUtilService.generateSubmittedAnswerFor(question, i % 2 == 0);
                quizSubmission.addSubmittedAnswers(answer);
                // also remove once
                quizSubmission.removeSubmittedAnswers(answer);
                quizSubmission.addSubmittedAnswers(answer);
            }
        }
        quizSubmission.setSubmitted(true);
        // quiz not open for practice --> bad request expected
        Result result = request.postWithResponseBody("/api/exercises/" + quizExerciseServer.getId() + "/submissions/practice", quizSubmission, Result.class,
                HttpStatus.BAD_REQUEST);
        assertThat(result).isNull();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testQuizSubmitPractice_badRequest_exam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        QuizExercise quizExerciseServer = quizExerciseUtilService.createQuizForExam(exerciseGroup);
        quizExerciseService.save(quizExerciseServer);

        assertThat(quizSubmissionRepository.findByParticipation_Exercise_Id(quizExerciseServer.getId())).isEmpty();

        QuizSubmission quizSubmission = quizExerciseUtilService.generateSubmissionForThreeQuestions(quizExerciseServer, 1, true, null);
        // exam quiz not open for practice --> bad request expected
        Result result = request.postWithResponseBody("/api/exercises/" + quizExerciseServer.getId() + "/submissions/practice", quizSubmission, Result.class,
                HttpStatus.BAD_REQUEST);
        assertThat(result).isNull();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testQuizSubmitPreview_forbidden() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/preview", new QuizSubmission(), Result.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testQuizSubmitPractice_forbidden() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        course.setStudentGroupName("abc");
        courseRepository.save(course);
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/practice", new QuizSubmission(), Result.class, HttpStatus.FORBIDDEN);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testQuizSubmitPreview_forbidden_otherTa() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        course.setTeachingAssistantGroupName("tutor2");
        courseRepository.save(course);
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/preview", new QuizSubmission(), Result.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizSubmitPreview_badRequest_noQuiz() throws Exception {
        request.postWithResponseBody("/api/exercises/" + 11223344 + "/submissions/preview", new QuizSubmission(), Result.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizSubmitPractice_badRequest_noQuiz() throws Exception {
        request.postWithResponseBody("/api/exercises/" + 11223344 + "/submissions/practice", new QuizSubmission(), Result.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizSubmitPreview_badRequest_submissionId() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        var quizSubmission = new QuizSubmission();
        quizSubmission.setId(1L);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/preview", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizSubmitPractice_badRequest_submissionId() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        var quizSubmission = new QuizSubmission();
        quizSubmission.setId(1L);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/practice", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testQuizSubmitPreview(QuizMode quizMode) throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null, quizMode);
        quizExerciseService.save(quizExercise);

        int numberOfParticipants = 10;

        for (int i = 1; i <= numberOfParticipants; i++) {
            QuizSubmission quizSubmission = new QuizSubmission();
            for (var question : quizExercise.getQuizQuestions()) {
                quizSubmission.addSubmittedAnswers(quizExerciseUtilService.generateSubmittedAnswerFor(question, i % 2 == 0));
            }
            Result receivedResult = request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/preview", quizSubmission, Result.class, HttpStatus.OK);
            assertThat(((QuizSubmission) receivedResult.getSubmission()).getSubmittedAnswers()).hasSameSizeAs(quizSubmission.getSubmittedAnswers());
        }

        // in the preview the submission will not be saved to the database
        assertThat(quizSubmissionRepository.findByParticipation_Exercise_Id(quizExercise.getId())).isEmpty();

        quizScheduleService.processCachedQuizSubmissions();

        // all stats must be 0 because we have a preview here
        // Test the statistics directly from the database
        QuizExercise quizExerciseWithStatistic = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExerciseWithStatistic).isNotNull();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isZero();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isZero();
        int questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getPoints).reduce(0, Integer::sum);
        assertThat(quizExerciseWithStatistic.getMaxPoints()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()).hasSize(questionScore + 1);
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            assertThat(pointCounter.getRatedCounter()).isZero();
            assertThat(pointCounter.getUnRatedCounter()).isZero();
        }
        // check statistic for each question
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getParticipantsUnrated()).isZero();
            assertThat(question.getQuizQuestionStatistic().getParticipantsRated()).isZero();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testQuizSubmitScheduledAndDeleted() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        String publishQuizPath = "/topic/courses/" + course.getId() + "/quizExercises";
        log.debug("// Creating the quiz exercise 2s in the future");
        var initialReleaseDate = ZonedDateTime.now().plus(2, ChronoUnit.SECONDS);
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now(), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(initialReleaseDate));
        quizExercise.duration(60);

        // also schedules the quiz
        log.debug("// Saving the quiz initially");
        quizExercise = quizExerciseService.save(quizExercise);
        checkQuizNotStarted(publishQuizPath);

        // check that submission fails
        QuizSubmission quizSubmission = quizExerciseUtilService.generateSubmissionForThreeQuestions(quizExercise, 1, true, null);
        quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, () -> TEST_PREFIX + "student3");

        quizScheduleService.processCachedQuizSubmissions();
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isZero();

        // reschedule
        log.debug("// Rescheduling the quiz to now");
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now()));
        quizExercise = quizExerciseService.save(quizExercise);

        // check that quiz and quiz batches have started
        log.debug("// Check that the quiz has started");
        assertThat(quizExercise.isQuizStarted()).isTrue();
        assertThat(quizExercise.getQuizBatches()).allMatch(QuizBatch::isStarted);

        // process cached submissions
        quizScheduleService.processCachedQuizSubmissions();

        // save submissions
        for (int i = 1; i <= 2; i++) {
            quizSubmission = quizExerciseUtilService.generateSubmissionForThreeQuestions(quizExercise, i, false, null);
            final var username = TEST_PREFIX + "student" + i;
            final Principal principal = () -> username;
            // save
            quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, principal);
        }

        // process the saved but not submitted quiz submissions
        quizScheduleService.processCachedQuizSubmissions();

        // before the quiz submissions are submitted, none of them ends up in the database
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isZero();

        // set the quiz end to now and ...
        log.debug("// End the quiz and delete it");
        quizExercise = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExercise).isNotNull();
        quizExercise.setDuration((int) Duration.between(quizExercise.getReleaseDate(), ZonedDateTime.now()).getSeconds() - Constants.QUIZ_GRACE_PERIOD_IN_SECONDS);
        quizExercise = exerciseRepository.saveAndFlush(quizExercise);
        quizScheduleService.updateQuizExercise(quizExercise);
        // ... directly delete the quiz
        exerciseRepository.delete(quizExercise);

        QuizExercise finalQuizExercise = quizExercise;
        await().until(() -> exerciseRepository.findById(finalQuizExercise.getId()).isEmpty());
        // the deleted quiz should get removed, no submissions should be saved
        quizScheduleService.processCachedQuizSubmissions();

        // quiz is not cached anymore
        assertThat(quizScheduleService.getQuizExercise(quizExercise.getId())).isNull();
        // no submissions were marked as submitted and saved
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void testQuizScoringTypes() {
        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusMinutes(1), null, QuizMode.SYNCHRONIZED);
        quizExercise.duration(60);
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExercise.getQuizQuestions()) {
            quizSubmission.addSubmittedAnswers(quizExerciseUtilService.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
        }
        quizSubmission.submitted(true);
        quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, () -> TEST_PREFIX + "student4");

        quizScheduleService.processCachedQuizSubmissions();

        // End the quiz right now so that results can be processed
        quizExercise = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExercise).isNotNull();
        quizExercise.setDuration((int) Duration.between(quizExercise.getReleaseDate(), ZonedDateTime.now()).getSeconds() - Constants.QUIZ_GRACE_PERIOD_IN_SECONDS);
        exerciseRepository.saveAndFlush(quizExercise);

        quizScheduleService.processCachedQuizSubmissions();

        assertThat(quizSubmissionRepository.findByQuizExerciseId(quizExercise.getId())).isPresent();

        List<Result> results = resultRepository.findByParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
        assertThat(results).hasSize(1);
        var result = results.get(0);

        assertThat(result.getScore()).isEqualTo(11.1);

        quizSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(result.getSubmission().getId());
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            // MC submitted answers 0 points as one correct and one false -> ALL_OR_NOTHING
            if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isZero();
            } // DND submitted answers 0 points as one correct and two false -> PROPORTIONAL_WITH_PENALTY
            else if (submittedAnswer instanceof DragAndDropSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isZero();
            } // SA submitted answers 1 points as one correct and one false -> PROPORTIONAL_WITHOUT_PENALTY
            else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isEqualTo(1D);
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ScoringType.class)
    @WithMockUser(username = TEST_PREFIX + "student5", roles = "USER")
    void testQuizScoringType(ScoringType scoringType) {
        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now().minusMinutes(1), null, QuizMode.SYNCHRONIZED);
        quizExercise.duration(60);
        quizExercise.setQuizQuestions(quizExercise.getQuizQuestions().stream().peek(quizQuestion -> quizQuestion.setScoringType(scoringType)).toList());
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExercise.getQuizQuestions()) {
            quizSubmission.addSubmittedAnswers(quizExerciseUtilService.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
        }
        quizSubmission.submitted(true);
        quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, () -> TEST_PREFIX + "student5");

        quizScheduleService.processCachedQuizSubmissions();

        // End the quiz right now so that results can be processed
        quizExercise = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExercise).isNotNull();
        quizExercise.setDuration((int) Duration.between(quizExercise.getReleaseDate(), ZonedDateTime.now()).getSeconds() - Constants.QUIZ_GRACE_PERIOD_IN_SECONDS);
        exerciseRepository.saveAndFlush(quizExercise);

        quizScheduleService.processCachedQuizSubmissions();

        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(1);

        List<Result> results = resultRepository.findByParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
        assertThat(results).hasSize(1);
        var result = results.get(0);

        double expectedScore = switch (scoringType) {
            case ALL_OR_NOTHING, PROPORTIONAL_WITH_PENALTY -> 0;
            case PROPORTIONAL_WITHOUT_PENALTY -> 44.4;
        };
        assertThat(result.getScore()).isEqualTo(expectedScore);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @ValueSource(booleans = { true, false })
    void submitExercise_shortAnswer_tooLarge(boolean tooLarge) throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, NUMBER_OF_TUTORS);
        Course course = courses.get(0);
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusSeconds(5), ZonedDateTime.now().plusSeconds(10), QuizMode.SYNCHRONIZED,
                course);
        quizExercise.addQuestions(quizExerciseUtilService.createShortAnswerQuestion());
        quizExercise.setDuration(10);
        quizExercise = quizExerciseService.save(quizExercise);

        ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(0);
        ShortAnswerSubmittedAnswer submittedAnswer = new ShortAnswerSubmittedAnswer();
        submittedAnswer.setQuizQuestion(saQuestion);
        List<ShortAnswerSpot> spots = saQuestion.getSpots();

        ShortAnswerSubmittedText text = new ShortAnswerSubmittedText();
        text.setSpot(spots.get(0));
        char[] chars = new char[(int) (Constants.MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH + (tooLarge ? 1 : 0))];
        Arrays.fill(chars, 'a');
        text.setText(new String(chars));
        submittedAnswer.addSubmittedTexts(text);

        QuizSubmission quizSubmission = new QuizSubmission();
        quizSubmission.addSubmittedAnswers(submittedAnswer);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, Result.class,
                tooLarge ? HttpStatus.BAD_REQUEST : HttpStatus.OK);
    }

    private void checkQuizNotStarted(String path) {
        // check that quiz has not started now
        log.debug("// Check that the quiz has not started and submissions are not allowed");
        verify(messagingTemplate, never()).send(eq(path), any());
    }

    private void setupShortAnswerSubmission(ShortAnswerQuestion saQuestion, QuizSubmission submission, int amountOfCorrectAnswers) {
        ShortAnswerSubmittedAnswer submittedAnswer = new ShortAnswerSubmittedAnswer();
        submittedAnswer.setQuizQuestion(saQuestion);
        List<ShortAnswerSpot> spots = saQuestion.getSpots();

        for (int i = 0; i < amountOfCorrectAnswers; i++) {
            ShortAnswerSubmittedText text = new ShortAnswerSubmittedText();
            text.setSpot(spots.get(i));
            var correctSolution = saQuestion.getCorrectSolutionForSpot(spots.get(i)).iterator().next().getText();
            text.setText(correctSolution);
            submittedAnswer.addSubmittedTexts(text);
        }

        submission.addSubmittedAnswers(submittedAnswer);
    }

    private void setupDragAndDropSubmission(DragAndDropQuestion dndQuestion, QuizSubmission submission, int amountOfCorrectAnswers) {
        List<DragItem> dragItems = dndQuestion.getDragItems();
        List<DropLocation> dropLocations = dndQuestion.getDropLocations();

        DragAndDropSubmittedAnswer submittedDndAnswer = new DragAndDropSubmittedAnswer();
        submittedDndAnswer.setQuizQuestion(dndQuestion);

        for (int i = 0; i < amountOfCorrectAnswers; i++) {
            submittedDndAnswer.addMappings(new DragAndDropMapping().dragItem(dragItems.get(i)).dropLocation(dropLocations.get(i)));
        }

        submission.addSubmittedAnswers(submittedDndAnswer);
    }

    private QuizExercise setupQuizExerciseParameters() {
        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(course, ZonedDateTime.now(), null, QuizMode.SYNCHRONIZED);
        quizExercise.duration(240);
        return quizExercise;
    }
}
