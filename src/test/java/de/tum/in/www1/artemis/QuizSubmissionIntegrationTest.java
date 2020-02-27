package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.websocket.QuizSubmissionWebsocketService;

public class QuizSubmissionIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    QuizExerciseService quizExerciseService;

    @Autowired
    QuizScheduleService scheduleService;

    @Autowired
    QuizSubmissionWebsocketService quizSubmissionWebsocketService;

    @Autowired
    QuizSubmissionRepository quizSubmissionRepository;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ResultRepository resultRepository;

    @BeforeEach
    public void init() {
        database.addUsers(10, 5, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testQuizSubmit() throws Exception {
        // change config to make test faster
        scheduleService.stopSchedule();
        scheduleService.startSchedule(2 * 1000); // every 1 second
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now(), null);
        quizExercise.setDueDate(ZonedDateTime.now().plusSeconds(2));
        quizExercise.setDuration(2);
        quizExercise.setIsPlannedToStart(true);
        quizExercise.setIsVisibleBeforeStart(true);
        quizExerciseService.save(quizExercise);

        int numberOfParticipants = 10;

        for (int i = 1; i <= numberOfParticipants; i++) {
            var quizSubmission = wrongQuizSubmissionFor(quizExercise);
            // TODO: add more submitted answers
            final var username = "student" + i;
            final Principal principal = () -> username;
            // save
            quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, principal);
            verify(messagingTemplate, times(1)).convertAndSendToUser(username, "/topic/quizExercise/" + quizExercise.getId() + "/submission", quizSubmission);
        }

        for (int i = 1; i <= numberOfParticipants; i++) {
            var quizSubmission = wrongQuizSubmissionFor(quizExercise);
            // TODO: add more submitted answers
            quizSubmission.setSubmitted(true);
            final var username = "student" + i;
            final Principal principal = () -> username;
            // submit
            quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, principal);
            verify(messagingTemplate, times(1)).convertAndSendToUser(username, "/topic/quizExercise/" + quizExercise.getId() + "/submission", quizSubmission);
        }

        // before the quiz has ended, no submission is saved to the database
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(0);

        // wait until the quiz has finished
        Thread.sleep(4000);

        // after the quiz has ended, all submission are saved to the database
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(numberOfParticipants);

        QuizExercise quizExerciseWithStatistic = quizExerciseService.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isEqualTo(0);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isEqualTo(numberOfParticipants);
        int questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getScore).reduce(0, Integer::sum);
        assertThat(quizExerciseWithStatistic.getMaxScore()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters().size()).isEqualTo(questionScore + 1);
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getPoints() == 0.0f) {
                // all participants have 0 points (and are rated)
                assertThat(pointCounter.getRatedCounter()).isEqualTo(numberOfParticipants);
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(0);
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(0);
            }
        }
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(0);
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(0);
        }
        // TODO: check more statistics (e.g. for each question)
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testQuizSubmitPractice() throws Exception {
        // change config to make test faster
        scheduleService.stopSchedule();
        scheduleService.startSchedule(2 * 1000); // every 2 seconds
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null);
        quizExercise.setDueDate(ZonedDateTime.now().minusSeconds(2));
        quizExercise.setDuration(2);
        quizExercise.setIsPlannedToStart(true);
        quizExercise.setIsVisibleBeforeStart(true);
        quizExercise.setIsOpenForPractice(true);
        quizExerciseService.save(quizExercise);

        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(0);

        var numberOfParticipants = 1;
        var quizSubmission = wrongQuizSubmissionFor(quizExercise);
        // TODO: add more submitted answers
        quizSubmission.setSubmitted(true);
        Result result = request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/practice", quizSubmission, Result.class, HttpStatus.OK);
        // TODO: check the result
        // TODO: can we simulate more students submitting here?

        // after the quiz has ended, all submission are saved to the database
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(numberOfParticipants);

        // wait until statistics have been updated
        Thread.sleep(4000);

        QuizExercise quizExerciseWithStatistic = quizExerciseService.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isEqualTo(0);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isEqualTo(numberOfParticipants);
        int questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getScore).reduce(0, Integer::sum);
        assertThat(quizExerciseWithStatistic.getMaxScore()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters().size()).isEqualTo(questionScore + 1);
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getPoints() == 0.0f) {
                // all participants have 0 points (and are unrated)
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(numberOfParticipants);
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(0);
            }
        }
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(0);
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(0);
        }
        // TODO: check more statistics (e.g. for each question)
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testQuizSubmitPractice_badRequest() throws Exception {
        // change config to make test faster
        scheduleService.stopSchedule();
        scheduleService.startSchedule(2 * 1000); // every 2 seconds
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        Course course = courses.get(0);
        QuizExercise quizExerciseServer = database.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null);
        quizExerciseServer.setDueDate(ZonedDateTime.now().minusSeconds(2));
        quizExerciseServer.setDuration(2);
        quizExerciseServer.setIsPlannedToStart(true);
        quizExerciseServer.setIsVisibleBeforeStart(true);
        quizExerciseServer.setIsOpenForPractice(false);
        quizExerciseService.save(quizExerciseServer);

        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(0);

        var quizSubmission = wrongQuizSubmissionFor(quizExerciseServer);
        // TODO: add more submitted answers
        quizSubmission.setSubmitted(true);
        // quiz not open for practice --> bad request expected
        Result result = request.postWithResponseBody("/api/exercises/" + quizExerciseServer.getId() + "/submissions/practice", quizSubmission, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testQuizSubmitPreview() throws Exception {
        // change config to make test faster
        scheduleService.stopSchedule();
        scheduleService.startSchedule(2 * 1000); // every 1 second
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null);
        quizExerciseService.save(quizExercise);

        var quizSubmission = wrongQuizSubmissionFor(quizExercise);
        // TODO: add more submitted answers
        Result result = request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/preview", quizSubmission, Result.class, HttpStatus.OK);
        // TODO: check the result

        // after the quiz has ended, all submission are saved to the database
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(0);

        // wait until statistics might have been updated
        Thread.sleep(4000);

        // all stats must be 0 because we have a preview here
        QuizExercise quizExerciseWithStatistic = quizExerciseService.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isEqualTo(0);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isEqualTo(0);
        int questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getScore).reduce(0, Integer::sum);
        assertThat(quizExerciseWithStatistic.getMaxScore()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters().size()).isEqualTo(questionScore + 1);
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getPoints() == 0.0f) {
                // all participants have 0 points (and are unrated)
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(0);
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(0);
            }
        }
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(0);
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(0);
        }
        // TODO: check more statistics (e.g. for each question)
    }

    private QuizExercise createQuizOnServer() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        Course course = courses.get(0);

        QuizExercise quizExercise = createQuiz(course);
        return request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.CREATED);
        // TODO: add some checks
    }

    @NotNull
    private QuizExercise createQuiz(Course course) {
        QuizExercise quizExercise = ModelFactory.generateQuizExercise(ZonedDateTime.now().plusHours(5), null, course);
        quizExercise.addQuestions(createMultipleChoiceQuestion());
        quizExercise.addQuestions(createDragAndDropQuestion());
        quizExercise.addQuestions(createShortAnswerQuestion());
        return quizExercise;
    }

    @NotNull
    private ShortAnswerQuestion createShortAnswerQuestion() {
        ShortAnswerQuestion sa = (ShortAnswerQuestion) new ShortAnswerQuestion().title("SA").score(2).text("This is a long answer text");
        var shortAnswerSpot1 = new ShortAnswerSpot().spotNr(0).width(1);
        shortAnswerSpot1.setTempID(generateTempId());
        var shortAnswerSpot2 = new ShortAnswerSpot().spotNr(2).width(2);
        shortAnswerSpot2.setTempID(generateTempId());
        sa.getSpots().add(shortAnswerSpot1);
        sa.getSpots().add(shortAnswerSpot2);
        var shortAnswerSolution1 = new ShortAnswerSolution().text("is");
        shortAnswerSolution1.setTempID(generateTempId());
        var shortAnswerSolution2 = new ShortAnswerSolution().text("long");
        shortAnswerSolution2.setTempID(generateTempId());
        sa.getSolutions().add(shortAnswerSolution1);
        sa.getSolutions().add(shortAnswerSolution2);
        sa.getCorrectMappings().add(new ShortAnswerMapping().spot(sa.getSpots().get(0)).solution(sa.getSolutions().get(0)));
        sa.getCorrectMappings().add(new ShortAnswerMapping().spot(sa.getSpots().get(1)).solution(sa.getSolutions().get(1)));
        return sa;
    }

    @NotNull
    private DragAndDropQuestion createDragAndDropQuestion() {
        DragAndDropQuestion dnd = (DragAndDropQuestion) new DragAndDropQuestion().title("DnD").score(1).text("Q2");
        var dropLocation1 = new DropLocation().posX(10).posY(10).height(10).width(10);
        dropLocation1.setTempID(generateTempId());
        var dropLocation2 = new DropLocation().posX(20).posY(20).height(10).width(10);
        dropLocation2.setTempID(generateTempId());
        dnd.getDropLocations().add(dropLocation1);
        dnd.getDropLocations().add(dropLocation2);
        var dragItem1 = new DragItem().text("D1");
        dragItem1.setTempID(generateTempId());
        var dragItem2 = new DragItem().text("D2");
        dragItem2.setTempID(generateTempId());
        dnd.getDragItems().add(dragItem1);
        dnd.getDragItems().add(dragItem2);
        dnd.getCorrectMappings().add(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1));
        dnd.getCorrectMappings().add(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation2));
        return dnd;
    }

    private Long generateTempId() {
        return ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    }

    @NotNull
    private MultipleChoiceQuestion createMultipleChoiceQuestion() {
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(1).text("Q1");
        mc.getAnswerOptions().add(new AnswerOption().text("A").hint("H1").explanation("E1").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("B").hint("H2").explanation("E2").isCorrect(false));
        return mc;
    }

    private QuizSubmission wrongQuizSubmissionFor(QuizExercise quizExercise) {
        var quizSubmission = new QuizSubmission();
        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                var submittedAnswer = new MultipleChoiceSubmittedAnswer();
                submittedAnswer.setQuizQuestion(question);
                for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                    if (!answerOption.isIsCorrect()) {
                        submittedAnswer.addSelectedOptions(answerOption);
                    }
                }
                quizSubmission.addSubmittedAnswers(submittedAnswer);
            }
            else if (question instanceof DragAndDropQuestion) {
                var submittedAnswer = new DragAndDropSubmittedAnswer();
                submittedAnswer.setQuizQuestion(question);
                quizSubmission.addSubmittedAnswers(submittedAnswer);
            }
            else if (question instanceof ShortAnswerQuestion) {
                var submittedAnswer = new ShortAnswerSubmittedAnswer();
                submittedAnswer.setQuizQuestion(question);
                quizSubmission.addSubmittedAnswers(submittedAnswer);
            }
        }
        return quizSubmission;
    }

}
