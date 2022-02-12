package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.ScoringType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.service.scheduled.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.web.websocket.QuizSubmissionWebsocketService;

public class QuizSubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private QuizExerciseService quizExerciseService;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private QuizScheduleService quizScheduleService;

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

    private final int multiplier = 10;

    @BeforeEach
    public void init() {
        quizScheduleService.stopSchedule();
        database.addUsers(10 * multiplier, 5, 0, 1);
        // do not use the schedule service based on a time interval in the tests, because this would result in flaky tests that run much slower
    }

    @AfterEach
    public void tearDown() {
        quizScheduleService.clearAllQuizData();
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizSubmit() throws Exception {
        QuizExercise quizExercise = setupQuizExerciseParameters();
        quizExercise = quizExerciseService.save(quizExercise);

        int numberOfParticipants = 10 * multiplier;
        QuizSubmission quizSubmission;

        for (int i = 1; i <= numberOfParticipants; i++) {
            quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, i, false, null);
            final var username = "student" + i;
            final Principal principal = () -> username;
            // save
            quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, principal);
        }

        // only half of the students submit manually
        for (int i = 1; i <= numberOfParticipants / 2; i++) {
            quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, i, true, null);
            final var username = "student" + i;
            final Principal principal = () -> username;
            // submit
            quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, principal);
        }

        // before the quiz submissions are processed, none of them ends up in the database
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(0);

        // process first half of the submissions
        quizScheduleService.processCachedQuizSubmissions();
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(numberOfParticipants / 2);

        // End the quiz right now so that results can be processed
        quizExercise = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExercise).isNotNull();
        quizExercise.setDuration((int) (Duration.between(quizExercise.getReleaseDate(), ZonedDateTime.now()).getSeconds() - Constants.QUIZ_GRACE_PERIOD_IN_SECONDS));
        exerciseRepository.saveAndFlush(quizExercise);

        quizScheduleService.processCachedQuizSubmissions();

        // after the quiz submissions have been processed, all submission are saved to the database
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(numberOfParticipants);

        // Test the statistics directly from the database
        QuizExercise quizExerciseWithStatistic = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExerciseWithStatistic).isNotNull();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isEqualTo(0);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isEqualTo(numberOfParticipants);
        int questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getPoints).reduce(0, Integer::sum);
        assertThat(quizExerciseWithStatistic.getMaxPoints()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters().size()).isEqualTo(questionScore + 1);
        // check general statistics
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getPoints() == 0.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(numberOfParticipants / 3.0));
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(0);
            }
            else if (pointCounter.getPoints() == 3.0 || pointCounter.getPoints() == 4.0 || pointCounter.getPoints() == 6.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(numberOfParticipants / 6.0));
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(0);
            }
            else if (pointCounter.getPoints() == 7.0 || pointCounter.getPoints() == 9.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(numberOfParticipants / 12.0));
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(0);
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(0);
            }
        }
        // check statistic for each question
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(Math.round(numberOfParticipants / 2.0));
            }
            else if (question instanceof DragAndDropQuestion) {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(Math.round(numberOfParticipants / 3.0));
            }
            else {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(Math.round(numberOfParticipants / 4.0));
            }
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(0);
            assertThat(question.getQuizQuestionStatistic().getParticipantsRated()).isEqualTo(numberOfParticipants);
            assertThat(question.getQuizQuestionStatistic().getParticipantsUnrated()).isEqualTo(0);
        }

        // execute the scheduler again, this should remove the quiz exercise from the cache
        quizScheduleService.processCachedQuizSubmissions();
        // but of course keep all submissions
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(numberOfParticipants);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizSubmit_partial_points() throws Exception {
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
            var studentId = i + 1;
            var username = "student" + studentId;
            final Principal principal = () -> username;
            quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), submissions.get(i), principal);
        }

        quizScheduleService.processCachedQuizSubmissions();

        QuizExercise quizExerciseWithStatistic = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        var quizPointStatistic = quizExerciseWithStatistic.getQuizPointStatistic();
        assertThat(quizExerciseWithStatistic).isNotNull();

        for (var pointCounter : quizPointStatistic.getPointCounters()) {
            assertThat(pointCounter.getUnRatedCounter()).as("Unrated counter is always 0").isEqualTo(0);
            if (pointCounter.getPoints() == 0.0) {
                assertThat(pointCounter.getRatedCounter()).as("Bucket 0.0 contains 1 rated submission -> 0.33 points").isEqualTo(1);
            }
            else if (pointCounter.getPoints() == 1.0) {
                assertThat(pointCounter.getRatedCounter()).as("Bucket 1.0 contains 1 rated submission -> 0.83 points").isEqualTo(1);
            }
            else if (pointCounter.getPoints() == 6.0) {
                assertThat(pointCounter.getRatedCounter()).as("Bucket 6.0 contains 1 rated submission -> 6 points").isEqualTo(1);
            }
            else {
                assertThat(pointCounter.getRatedCounter()).as("All other buckets contain 0 rated submissions").isEqualTo(0);
            }

        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizSubmitLiveMode() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(false);
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusSeconds(10), null);
        quizExercise = quizExerciseService.save(quizExercise);

        // at the beginning there are no submissions and no participants
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(0);
        assertThat(participationRepository.findAll().size()).isEqualTo(0);

        int numberOfParticipants = 10;

        for (int i = 1; i <= numberOfParticipants; i++) {
            database.changeUser("student" + i);
            QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, i, false, null);
            assertThat(quizSubmission.getSubmittedAnswers().size()).isEqualTo(3);
            assertThat(quizSubmission.isSubmitted()).isFalse();
            assertThat(quizSubmission.getSubmissionDate()).isNull();
            QuizSubmission updatedSubmission = request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, QuizSubmission.class,
                    HttpStatus.OK);
            // check whether submission flag was updated
            assertThat(updatedSubmission.isSubmitted()).isTrue();
            // check whether all answers were submitted properly
            assertThat(updatedSubmission.getSubmittedAnswers().size()).isEqualTo(quizSubmission.getSubmittedAnswers().size());
            // check whether submission date was set
            assertThat(updatedSubmission.getSubmissionDate()).isNotNull();
        }

        // process cached submissions
        quizScheduleService.processCachedQuizSubmissions();

        // check whether all submissions were saved to the database
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(numberOfParticipants);
        assertThat(participationRepository.findAll().size()).isEqualTo(numberOfParticipants);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizSubmitLiveMode_badRequest_notActive() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(false);
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().plusSeconds(20), ZonedDateTime.now().plusSeconds(30));
        quizExercise.setDuration(10);
        quizExercise = quizExerciseService.save(quizExercise);
        quizExercise.setIsPlannedToStart(true);
        QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, 1, false, null);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizSubmitLiveMode_badRequest_alreadySubmitted() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(false);
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now(), ZonedDateTime.now().plusSeconds(10));
        quizExercise.setDuration(10);
        quizExercise = quizExerciseService.save(quizExercise);
        QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, 1, false, ZonedDateTime.now());
        // submit quiz for the first time, expected status = OK
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, Result.class, HttpStatus.OK);
        // submit quiz for the second time, expected status = BAD_REQUEST
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizSubmitPractice() throws Exception {

        List<Course> courses = database.createCoursesWithExercisesAndLectures(false);
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusSeconds(10), null);
        quizExercise.setDueDate(ZonedDateTime.now().minusSeconds(8));
        quizExercise.setDuration(2);
        quizExercise.setIsPlannedToStart(true);
        quizExercise.setIsVisibleBeforeStart(true);
        quizExercise.setIsOpenForPractice(true);
        quizExerciseService.save(quizExercise);

        // at the beginning there are no submissions and participants
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(0);
        assertThat(participationRepository.findAll().size()).isEqualTo(0);

        var numberOfParticipants = 10;

        // submit 10 times for 10 different students
        for (int i = 1; i <= numberOfParticipants; i++) {
            QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, i, true, null);
            database.changeUser("student" + i);
            Result receivedResult = request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/practice", quizSubmission, Result.class, HttpStatus.OK);
            assertThat(((QuizSubmission) receivedResult.getSubmission()).getSubmittedAnswers().size()).isEqualTo(quizSubmission.getSubmittedAnswers().size());
        }

        // after the quiz has ended, all submission are saved to the database
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(numberOfParticipants);
        assertThat(participationRepository.findAll().size()).isEqualTo(numberOfParticipants);

        // processing the quiz submissions will update the statistics
        quizScheduleService.processCachedQuizSubmissions();

        // Test the statistics directly from the database
        QuizExercise quizExerciseWithStatistic = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExerciseWithStatistic).isNotNull();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isEqualTo(0);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isEqualTo(numberOfParticipants);
        int questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getPoints).reduce(0, Integer::sum);
        assertThat(quizExerciseWithStatistic.getMaxPoints()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters().size()).isEqualTo(questionScore + 1);
        // check general statistics
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getPoints() == 0.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(3);
            }
            else if (pointCounter.getPoints() == 3.0 || pointCounter.getPoints() == 4.0 || pointCounter.getPoints() == 6.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(2);
            }
            else if (pointCounter.getPoints() == 7.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(1);
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(0);
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
            assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(0);
            assertThat(question.getQuizQuestionStatistic().getParticipantsUnrated()).isEqualTo(numberOfParticipants);
            assertThat(question.getQuizQuestionStatistic().getParticipantsRated()).isEqualTo(0);
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizSubmitPractice_badRequest() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);
        QuizExercise quizExerciseServer = database.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null);
        quizExerciseServer.setDueDate(ZonedDateTime.now().minusSeconds(2));
        quizExerciseServer.setDuration(2);
        quizExerciseServer.setIsPlannedToStart(true);
        quizExerciseServer.setIsVisibleBeforeStart(true);
        quizExerciseServer.setIsOpenForPractice(false);
        quizExerciseService.save(quizExerciseServer);

        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(0);

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExerciseServer.getQuizQuestions()) {
            for (int i = 1; i <= 10; i++) {
                var answer = database.generateSubmittedAnswerFor(question, i % 2 == 0);
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
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizSubmitPractice_badRequest_exam() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        QuizExercise quizExerciseServer = database.createQuizForExam(exerciseGroup);
        quizExerciseService.save(quizExerciseServer);

        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(0);

        QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExerciseServer, 1, true, null);
        // exam quiz not open for practice --> bad request expected
        Result result = request.postWithResponseBody("/api/exercises/" + quizExerciseServer.getId() + "/submissions/practice", quizSubmission, Result.class,
                HttpStatus.BAD_REQUEST);
        assertThat(result).isNull();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizSubmitPreview_forbidden() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null);
        quizExerciseService.save(quizExercise);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/preview", new QuizSubmission(), Result.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizSubmitPractice_forbidden() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);
        course.setStudentGroupName("abc");
        courseRepository.save(course);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null);
        quizExerciseService.save(quizExercise);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/practice", new QuizSubmission(), Result.class, HttpStatus.FORBIDDEN);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testQuizSubmitPreview_forbidden_otherTa() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);
        course.setTeachingAssistantGroupName("tutor2");
        courseRepository.save(course);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null);
        quizExerciseService.save(quizExercise);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/preview", new QuizSubmission(), Result.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testQuizSubmitPreview_badRequest_noQuiz() throws Exception {
        request.postWithResponseBody("/api/exercises/" + 11223344 + "/submissions/preview", new QuizSubmission(), Result.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testQuizSubmitPractice_badRequest_noQuiz() throws Exception {
        request.postWithResponseBody("/api/exercises/" + 11223344 + "/submissions/practice", new QuizSubmission(), Result.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testQuizSubmitPreview_badRequest_submissionId() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null);
        quizExerciseService.save(quizExercise);
        var quizSubmission = new QuizSubmission();
        quizSubmission.setId(1L);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/preview", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testQuizSubmitPractice_badRequest_submissionId() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null);
        quizExerciseService.save(quizExercise);
        var quizSubmission = new QuizSubmission();
        quizSubmission.setId(1L);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/practice", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testQuizSubmitPreview() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null);
        quizExerciseService.save(quizExercise);

        int numberOfParticipants = 10;

        for (int i = 1; i <= numberOfParticipants; i++) {
            QuizSubmission quizSubmission = new QuizSubmission();
            for (var question : quizExercise.getQuizQuestions()) {
                quizSubmission.addSubmittedAnswers(database.generateSubmittedAnswerFor(question, i % 2 == 0));
            }
            Result receivedResult = request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/preview", quizSubmission, Result.class, HttpStatus.OK);
            assertThat(((QuizSubmission) receivedResult.getSubmission()).getSubmittedAnswers().size()).isEqualTo(quizSubmission.getSubmittedAnswers().size());
        }

        // in the preview the submission will not be saved to the database
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(0);

        quizScheduleService.processCachedQuizSubmissions();

        // all stats must be 0 because we have a preview here
        // Test the statistics directly from the database
        QuizExercise quizExerciseWithStatistic = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExerciseWithStatistic).isNotNull();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isEqualTo(0);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isEqualTo(0);
        int questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getPoints).reduce(0, Integer::sum);
        assertThat(quizExerciseWithStatistic.getMaxPoints()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters().size()).isEqualTo(questionScore + 1);
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
            assertThat(pointCounter.getUnRatedCounter()).isEqualTo(0);
        }
        // check statistic for each question
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(0);
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(0);
            assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(0);
            assertThat(question.getQuizQuestionStatistic().getParticipantsUnrated()).isEqualTo(0);
            assertThat(question.getQuizQuestionStatistic().getParticipantsRated()).isEqualTo(0);
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizSubmitScheduledAndDeleted() throws Exception {
        log.debug("// Start testQuizSubmitScheduledAndDeleted");
        /*
         * The time we wait in between needs to be relatively high to make sure the concurrent tasks are finished in time, especially sending out the exercise can easily take up to
         * 100 ms, so we should leave about 200 ms for that, similar for the completion of all saving/updating/scheduling operations.
         */
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);
        String publishQuizPath = "/topic/courses/" + course.getId() + "/quizExercises";
        log.debug("// Creating the quiz exercise 2s in the future");
        var releaseDate = ZonedDateTime.now().plus(2, ChronoUnit.SECONDS);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().plus(2000, ChronoUnit.MILLIS), null);
        quizExercise.duration(60);
        quizExercise.setIsPlannedToStart(true);
        quizExercise.setIsVisibleBeforeStart(true);

        // also schedules the quiz
        log.debug("// Saving the quiz initially");
        quizExercise = quizExerciseService.save(quizExercise);

        checkQuizNotStarted(publishQuizPath);

        // wait a bit
        sleep(1000);
        checkQuizNotStarted(publishQuizPath);

        // reschedule
        log.debug("// Rescheduling the quiz for another 2s into the future");
        releaseDate = releaseDate.plus(2000, ChronoUnit.MILLIS);
        quizExercise.setReleaseDate(releaseDate);
        quizExercise = quizExerciseService.save(quizExercise);

        // wait for the old release date to pass
        sleep(1500);

        // check that quiz has still not started now
        checkQuizNotStarted(publishQuizPath);

        // check that submission fails
        QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, 1, true, null);
        quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, () -> "student1");

        quizScheduleService.processCachedQuizSubmissions();
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isZero();

        // wait for the new release date to pass
        sleep(2500);

        // check that quiz has started
        log.debug("// Check that the quiz has started");
        verify(messagingTemplate, times(1)).send(eq(publishQuizPath), any());

        // process cached submissions
        quizScheduleService.processCachedQuizSubmissions();

        // save submissions
        int numberOfParticipants = 10;
        for (int i = 1; i <= numberOfParticipants; i++) {
            quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, i, false, null);
            final var username = "student" + i;
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

        sleep(1000);

        // the deleted quiz should get removed, no submissions should be saved
        quizScheduleService.processCachedQuizSubmissions();

        // quiz is not cached anymore
        assertThat(quizScheduleService.getQuizExercise(quizExercise.getId())).isNull();
        // no submissions were marked as submitted and saved
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isZero();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizScoringTypes() {
        Course course = database.createCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusMinutes(1), null);
        quizExercise.duration(60);
        quizExercise.setIsPlannedToStart(true);
        quizExercise.setIsVisibleBeforeStart(true);
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExercise.getQuizQuestions()) {
            quizSubmission.addSubmittedAnswers(database.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
        }
        quizSubmission.submitted(true);
        quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, () -> "student1");

        quizScheduleService.processCachedQuizSubmissions();

        // End the quiz right now so that results can be processed
        quizExercise = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExercise).isNotNull();
        quizExercise.setDuration((int) Duration.between(quizExercise.getReleaseDate(), ZonedDateTime.now()).getSeconds() - Constants.QUIZ_GRACE_PERIOD_IN_SECONDS);
        exerciseRepository.saveAndFlush(quizExercise);

        quizScheduleService.processCachedQuizSubmissions();

        assertThat(quizSubmissionRepository.count()).isEqualTo(1);

        List<Result> results = resultRepository.findByParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
        assertThat(results.size()).isEqualTo(1);
        var result = results.get(0);

        assertThat(result.getResultString()).isEqualTo("1 of 9 points");

        var submittedAnswers = ((QuizSubmission) result.getSubmission()).getSubmittedAnswers();
        for (SubmittedAnswer submittedAnswer : submittedAnswers) {
            // MC submitted answers 0 points as one correct and one false -> ALL_OR_NOTHING
            if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isEqualTo(0D);
            } // DND submitted answers 0 points as one correct and two false -> PROPORTIONAL_WITH_PENALTY
            else if (submittedAnswer instanceof DragAndDropSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isEqualTo(0D);
            } // SA submitted answers 1 points as one correct and one false -> PROPORTIONAL_WITHOUT_PENALTY
            else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isEqualTo(1D);
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ScoringType.class)
    @WithMockUser(username = "student1", roles = "USER")
    public void testQuizScoringType(ScoringType scoringType) {
        Course course = database.createCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusMinutes(1), null);
        quizExercise.duration(60);
        quizExercise.setIsPlannedToStart(true);
        quizExercise.setIsVisibleBeforeStart(true);
        quizExercise.setQuizQuestions(quizExercise.getQuizQuestions().stream().peek(quizQuestion -> quizQuestion.setScoringType(scoringType)).collect(Collectors.toList()));
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExercise.getQuizQuestions()) {
            quizSubmission.addSubmittedAnswers(database.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
        }
        quizSubmission.submitted(true);
        quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, () -> "student1");

        quizScheduleService.processCachedQuizSubmissions();

        // End the quiz right now so that results can be processed
        quizExercise = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assert quizExercise != null;
        quizExercise.setDuration((int) Duration.between(quizExercise.getReleaseDate(), ZonedDateTime.now()).getSeconds() - Constants.QUIZ_GRACE_PERIOD_IN_SECONDS);
        exerciseRepository.saveAndFlush(quizExercise);

        quizScheduleService.processCachedQuizSubmissions();

        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(1);

        List<Result> results = resultRepository.findByParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
        assertThat(results.size()).isEqualTo(1);
        var result = results.get(0);

        var resultString = switch (scoringType) {
            case ALL_OR_NOTHING, PROPORTIONAL_WITH_PENALTY -> "0 of 9 points";
            case PROPORTIONAL_WITHOUT_PENALTY -> "4 of 9 points";
        };
        assertThat(result.getResultString()).isEqualTo(resultString);
    }

    private void checkQuizNotStarted(String path) {
        // check that quiz has not started now
        log.debug("// Check that the quiz has not started and submissions are not allowed");
        verify(messagingTemplate, never()).send(eq(path), any());
    }

    private void sleep(long millis) throws InterruptedException {
        log.debug("zzzzzzzzzzzzz Sleep {}ms", millis);
        TimeUnit.MILLISECONDS.sleep(millis);
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

    private QuizExercise setupQuizExerciseParameters() throws Exception {
        // change config to make test faster
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now(), null);
        quizExercise.duration(240);
        quizExercise.setIsPlannedToStart(true);
        quizExercise.setIsVisibleBeforeStart(true);

        return quizExercise;
    }
}
