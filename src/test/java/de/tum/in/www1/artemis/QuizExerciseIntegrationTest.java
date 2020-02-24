package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.websocket.QuizSubmissionWebsocketService;

public class QuizExerciseIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    QuizScheduleService scheduleService;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    QuizExerciseService quizExerciseService;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    QuizSubmissionWebsocketService quizSubmissionWebsocketService;

    @Autowired
    QuizSubmissionRepository quizSubmissionRepository;

    @BeforeEach
    public void init() {
        database.addUsers(10, 5, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCreateQuizExercise() throws Exception {
        QuizExercise quizExerciseServer = createQuizOnServer();

        // General assertions
        assertThat(quizExerciseServer.getQuizQuestions().size()).as("Quiz questions were saved").isEqualTo(3);
        assertThat(quizExerciseServer.getDuration()).as("Quiz duration was correctly set").isEqualTo(10);
        assertThat(quizExerciseServer.getDifficulty()).as("Quiz difficulty was correctly set").isEqualTo(DifficultyLevel.MEDIUM);

        // Quiz type specific assertions
        for (QuizQuestion question : quizExerciseServer.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                MultipleChoiceQuestion multipleChoiceQuestion = (MultipleChoiceQuestion) question;
                assertThat(multipleChoiceQuestion.getAnswerOptions().size()).as("Multiple choice question answer options were saved").isEqualTo(2);
                assertThat(multipleChoiceQuestion.getTitle()).as("Multiple choice question title is correct").isEqualTo("MC");
                assertThat(multipleChoiceQuestion.getText()).as("Multiple choice question text is correct").isEqualTo("Q1");
                assertThat(multipleChoiceQuestion.getScore()).as("Multiple choice question score is correct").isEqualTo(1);

                List<AnswerOption> answerOptions = multipleChoiceQuestion.getAnswerOptions();
                assertThat(answerOptions.get(0).getText()).as("Text for answer option is correct").isEqualTo("A");
                assertThat(answerOptions.get(0).getHint()).as("Hint for answer option is correct").isEqualTo("H1");
                assertThat(answerOptions.get(0).getExplanation()).as("Explanation for answer option is correct").isEqualTo("E1");
                assertThat(answerOptions.get(0).isIsCorrect()).as("Is correct for answer option is correct").isTrue();
                assertThat(answerOptions.get(1).getText()).as("Text for answer option is correct").isEqualTo("B");
                assertThat(answerOptions.get(1).getHint()).as("Hint for answer option is correct").isEqualTo("H2");
                assertThat(answerOptions.get(1).getExplanation()).as("Explanation for answer option is correct").isEqualTo("E2");
                assertThat(answerOptions.get(1).isIsCorrect()).as("Is correct for answer option is correct").isFalse();
            }
            if (question instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                assertThat(dragAndDropQuestion.getDropLocations().size()).as("Drag and drop question drop locations were saved").isEqualTo(2);
                assertThat(dragAndDropQuestion.getDragItems().size()).as("Drag and drop question drag items were saved").isEqualTo(2);
                assertThat(dragAndDropQuestion.getTitle()).as("Drag and drop question title is correct").isEqualTo("DnD");
                assertThat(dragAndDropQuestion.getText()).as("Drag and drop question text is correct").isEqualTo("Q2");
                assertThat(dragAndDropQuestion.getScore()).as("Drag and drop question score is correct").isEqualTo(1);

                List<DropLocation> dropLocations = dragAndDropQuestion.getDropLocations();
                assertThat(dropLocations.get(0).getPosX()).as("Pos X for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(0).getPosY()).as("Pos Y for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(0).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(0).getHeight()).as("Height for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(1).getPosX()).as("Pos X for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(1).getPosY()).as("Pos Y for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(1).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(1).getHeight()).as("Height for drop location is correct").isEqualTo(10);

                List<DragItem> dragItems = dragAndDropQuestion.getDragItems();
                assertThat(dragItems.get(0).getText()).as("Text for drag item is correct").isEqualTo("D1");
                assertThat(dragItems.get(1).getText()).as("Text for drag item is correct").isEqualTo("D2");
            }
            if (question instanceof ShortAnswerQuestion) {
                ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) question;
                assertThat(shortAnswerQuestion.getSpots().size()).as("Short answer question spots were saved").isEqualTo(2);
                assertThat(shortAnswerQuestion.getSolutions().size()).as("Short answer question solutions were saved").isEqualTo(2);
                assertThat(shortAnswerQuestion.getTitle()).as("Short answer question title is correct").isEqualTo("SA");
                assertThat(shortAnswerQuestion.getText()).as("Short answer question text is correct").isEqualTo("This is a long answer text");
                assertThat(shortAnswerQuestion.getScore()).as("Short answer question score is correct").isEqualTo(2);

                List<ShortAnswerSpot> spots = shortAnswerQuestion.getSpots();
                assertThat(spots.get(0).getSpotNr()).as("Spot nr for spot is correct").isEqualTo(0);
                assertThat(spots.get(0).getWidth()).as("Width for spot is correct").isEqualTo(1);
                assertThat(spots.get(1).getSpotNr()).as("Spot nr for spot is correct").isEqualTo(2);
                assertThat(spots.get(1).getWidth()).as("Width for spot is correct").isEqualTo(2);

                List<ShortAnswerSolution> solutions = shortAnswerQuestion.getSolutions();
                assertThat(solutions.get(0).getText()).as("Text for solution is correct").isEqualTo("is");
                assertThat(solutions.get(1).getText()).as("Text for solution is correct").isEqualTo("long");
            }
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testEditQuizExercise() throws Exception {
        QuizExercise quizExerciseServer = createQuizOnServer();
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExerciseServer.getQuizQuestions().get(0);
        mc.getAnswerOptions().remove(0);
        mc.getAnswerOptions().add(new AnswerOption().text("C").hint("H3").explanation("E3").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("D").hint("H4").explanation("E4").isCorrect(true));

        DragAndDropQuestion dnd = (DragAndDropQuestion) quizExerciseServer.getQuizQuestions().get(1);
        dnd.getDropLocations().remove(0);
        dnd.getCorrectMappings().remove(0);
        dnd.getDragItems().remove(0);

        ShortAnswerQuestion sa = (ShortAnswerQuestion) quizExerciseServer.getQuizQuestions().get(2);
        sa.getSpots().remove(0);
        sa.getSolutions().remove(0);
        sa.getCorrectMappings().remove(0);

        quizExerciseServer = request.putWithResponseBody("/api/quiz-exercises", quizExerciseServer, QuizExercise.class, HttpStatus.OK);

        // Quiz type specific assertions
        for (QuizQuestion question : quizExerciseServer.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                MultipleChoiceQuestion multipleChoiceQuestion = (MultipleChoiceQuestion) question;
                assertThat(multipleChoiceQuestion.getAnswerOptions().size()).as("Multiple choice question answer options were saved").isEqualTo(3);
                assertThat(multipleChoiceQuestion.getTitle()).as("Multiple choice question title is correct").isEqualTo("MC");
                assertThat(multipleChoiceQuestion.getText()).as("Multiple choice question text is correct").isEqualTo("Q1");
                assertThat(multipleChoiceQuestion.getScore()).as("Multiple choice question score is correct").isEqualTo(1);

                List<AnswerOption> answerOptions = multipleChoiceQuestion.getAnswerOptions();
                assertThat(answerOptions.get(0).getText()).as("Text for answer option is correct").isEqualTo("B");
                assertThat(answerOptions.get(0).getHint()).as("Hint for answer option is correct").isEqualTo("H2");
                assertThat(answerOptions.get(0).getExplanation()).as("Explanation for answer option is correct").isEqualTo("E2");
                assertThat(answerOptions.get(0).isIsCorrect()).as("Is correct for answer option is correct").isFalse();
                assertThat(answerOptions.get(1).getText()).as("Text for answer option is correct").isEqualTo("C");
                assertThat(answerOptions.get(1).getHint()).as("Hint for answer option is correct").isEqualTo("H3");
                assertThat(answerOptions.get(1).getExplanation()).as("Explanation for answer option is correct").isEqualTo("E3");
                assertThat(answerOptions.get(1).isIsCorrect()).as("Is correct for answer option is correct").isTrue();
                assertThat(answerOptions.get(2).getText()).as("Text for answer option is correct").isEqualTo("D");
                assertThat(answerOptions.get(2).getHint()).as("Hint for answer option is correct").isEqualTo("H4");
                assertThat(answerOptions.get(2).getExplanation()).as("Explanation for answer option is correct").isEqualTo("E4");
                assertThat(answerOptions.get(2).isIsCorrect()).as("Is correct for answer option is correct").isTrue();
            }
            if (question instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                assertThat(dragAndDropQuestion.getDropLocations().size()).as("Drag and drop question drop locations were saved").isEqualTo(1);
                assertThat(dragAndDropQuestion.getDragItems().size()).as("Drag and drop question drag items were saved").isEqualTo(1);
                assertThat(dragAndDropQuestion.getTitle()).as("Drag and drop question title is correct").isEqualTo("DnD");
                assertThat(dragAndDropQuestion.getText()).as("Drag and drop question text is correct").isEqualTo("Q2");
                assertThat(dragAndDropQuestion.getScore()).as("Drag and drop question score is correct").isEqualTo(1);

                List<DropLocation> dropLocations = dragAndDropQuestion.getDropLocations();
                assertThat(dropLocations.get(0).getPosX()).as("Pos X for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(0).getPosY()).as("Pos Y for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(0).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(0).getHeight()).as("Height for drop location is correct").isEqualTo(10);

                List<DragItem> dragItems = dragAndDropQuestion.getDragItems();
                assertThat(dragItems.get(0).getText()).as("Text for drag item is correct").isEqualTo("D2");
            }
            if (question instanceof ShortAnswerQuestion) {
                ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) question;
                assertThat(shortAnswerQuestion.getSpots().size()).as("Short answer question spots were saved").isEqualTo(1);
                assertThat(shortAnswerQuestion.getSolutions().size()).as("Short answer question solutions were saved").isEqualTo(1);
                assertThat(shortAnswerQuestion.getTitle()).as("Short answer question title is correct").isEqualTo("SA");
                assertThat(shortAnswerQuestion.getText()).as("Short answer question text is correct").isEqualTo("This is a long answer text");
                assertThat(shortAnswerQuestion.getScore()).as("Short answer question score is correct").isEqualTo(2);

                List<ShortAnswerSpot> spots = shortAnswerQuestion.getSpots();
                assertThat(spots.get(0).getSpotNr()).as("Spot nr for spot is correct").isEqualTo(2);
                assertThat(spots.get(0).getWidth()).as("Width for spot is correct").isEqualTo(2);

                List<ShortAnswerSolution> solutions = shortAnswerQuestion.getSolutions();
                assertThat(solutions.get(0).getText()).as("Text for solution is correct").isEqualTo("long");
            }
        }
    }

    private QuizExercise createQuizOnServer() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        Course course = courses.get(0);

        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().plusHours(5), null);
        QuizExercise quizExerciseServer = request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.CREATED);
        QuizExercise quizExerciseDatabase = quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseServer.getId());
        assertThat(quizExercise.getQuizQuestions()).hasSize(quizExerciseServer.getQuizQuestions().size());
        assertThat(quizExercise.getQuizQuestions()).hasSize(quizExerciseDatabase.getQuizQuestions().size());
        for (int i = 0; i < quizExercise.getQuizQuestions().size(); i++) {
            var question = quizExercise.getQuizQuestions().get(i);
            var questionServer = quizExerciseServer.getQuizQuestions().get(i);
            var questionDatabase = quizExerciseDatabase.getQuizQuestions().get(i);
            assertThat(questionDatabase).isEqualTo(questionServer);
            // TODO check additional values
        }
        return quizExerciseServer;
        // TODO: add some additional checks
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteQuizExercise() throws Exception {
        QuizExercise quizExerciseServer = createQuizOnServer();
        request.delete("/api/quiz-exercises/" + quizExerciseServer.getId(), HttpStatus.OK);
        // TODO: check that the quiz was actually deleted from the database
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetQuizExercise() throws Exception {
        QuizExercise quizExercise = createQuizOnServer();
        QuizExercise quizExerciseGet = request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK, QuizExercise.class);
        QuizExercise quizExerciseForStudent = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        List<QuizExercise> allQuizExercisesForCourse = request.getList("/api/courses/" + quizExercise.getCourse().getId() + "/quiz-exercises", HttpStatus.OK, QuizExercise.class);
        // TODO: add some additional checks for the retrieved data
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testRecalculateStatistics() throws Exception {
        QuizExercise quizExercise = createQuizOnServer();
        QuizExercise quizExerciseWithRecalculatedStatistics = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", HttpStatus.OK,
                QuizExercise.class);
        // TODO: add some additional checks for the retrieved data
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testReevaluateStatistics() throws Exception {
        QuizExercise quizExercise = createQuizOnServer();
        // we expect a bad request because the quiz has not ended yet
        request.putWithResponseBody("/api/quiz-exercises-re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusSeconds(60));
        quizExercise.setDueDate(ZonedDateTime.now().minusSeconds(20));
        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.OK);

        var now = ZonedDateTime.now();

        for (int i = 1; i <= 10; i++) {
            QuizSubmission quizSubmission = new QuizSubmission();

            // TODO: add some values to the quiz submission

            quizSubmission.submitted(true);
            quizSubmission.submissionDate(now.minusMinutes(3));
            database.addSubmission(quizExercise, quizSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, 10L, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, 20L, true);
            }
        }

        request.putWithResponseBody("/api/quiz-exercises-re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.OK);
        // TODO: actually set some question elements invalid, remove them, etc. and check that afert the reevaluation everything is ok

    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testPerformStartNow() throws Exception {
        QuizExercise quizExercise = createQuizOnServer();
        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/start-now", quizExercise, QuizExercise.class,
                HttpStatus.OK);
        long millis = ChronoUnit.MILLIS.between(updatedQuizExercise.getReleaseDate(), ZonedDateTime.now());
        // actually the two dates should be "exactly" the same, but for the sake of slow CI testing machines and to prevent flaky tests, we live with the following rule
        assertThat(millis).isCloseTo(0, byLessThan(2000L));
        assertThat(updatedQuizExercise.isIsPlannedToStart()).isTrue();
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

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testQuizSubmit() throws Exception {
        // change config to make test faster
        scheduleService.stopSchedule();
        scheduleService.startSchedule(2 * 1000); // every 2 seconds
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
        Thread.sleep(6000);

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
        scheduleService.startSchedule(1000); // every second
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
        Thread.sleep(5000);

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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testQuizSubmitPreview() throws Exception {
        // change config to make test faster
        scheduleService.stopSchedule();
        scheduleService.startSchedule(1000); // every second
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        Course course = courses.get(0);
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null);
        quizExerciseService.save(quizExercise);

        var quizSubmission = wrongQuizSubmissionFor(quizExercise);
        // TODO: add more submitted answers
        Result result = request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/preview", quizSubmission, Result.class, HttpStatus.OK);
        // TODO: check the result

        // in the preview nothing should be stored in thee database
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(0);

        // wait until statistics might have been updated
        Thread.sleep(5000);

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

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testPerformSetVisible() throws Exception {
        QuizExercise quizExercise = createQuizOnServer();
        // we expect a bad request because the quiz is already visible
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/set-visible", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        quizExercise.setIsVisibleBeforeStart(false);
        quizExerciseService.save(quizExercise);
        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/set-visible", quizExercise, QuizExercise.class,
                HttpStatus.OK);
        assertThat(updatedQuizExercise.isVisibleToStudents()).isTrue();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testPerformOpenForPractice() throws Exception {
        QuizExercise quizExercise = createQuizOnServer();
        // we expect a bad request because the quiz has not ended yet
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/open-for-practice", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusMinutes(5));
        quizExercise.setDueDate(ZonedDateTime.now().minusMinutes(2));
        quizExercise.setDuration(180);
        quizExerciseService.save(quizExercise);
        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/open-for-practice", quizExercise, QuizExercise.class,
                HttpStatus.OK);
        assertThat(updatedQuizExercise.isIsOpenForPractice()).isTrue();
    }

}
