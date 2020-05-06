package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.*;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.websocket.QuizSubmissionWebsocketService;

public class QuizExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    QuizScheduleService quizScheduleService;

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
    QuizSubmissionRepository quizSubmissionRepository;

    @BeforeEach
    public void init() {
        database.addUsers(10, 5, 1);
        quizScheduleService.startSchedule(3000);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        quizScheduleService.stopSchedule();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCreateQuizExercise() throws Exception {
        QuizExercise quizExerciseServer = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);

        // General assertions
        assertThat(quizExerciseServer.getQuizQuestions().size()).as("Quiz questions were saved").isEqualTo(3);
        assertThat(quizExerciseServer.getDuration()).as("Quiz duration was correctly set").isEqualTo(3600);
        assertThat(quizExerciseServer.getDifficulty()).as("Quiz difficulty was correctly set").isEqualTo(DifficultyLevel.MEDIUM);

        // Quiz type specific assertions
        for (QuizQuestion question : quizExerciseServer.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                MultipleChoiceQuestion multipleChoiceQuestion = (MultipleChoiceQuestion) question;
                assertThat(multipleChoiceQuestion.getAnswerOptions().size()).as("Multiple choice question answer options were saved").isEqualTo(2);
                assertThat(multipleChoiceQuestion.getTitle()).as("Multiple choice question title is correct").isEqualTo("MC");
                assertThat(multipleChoiceQuestion.getText()).as("Multiple choice question text is correct").isEqualTo("Q1");
                assertThat(multipleChoiceQuestion.getScore()).as("Multiple choice question score is correct").isEqualTo(4);

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
                assertThat(shortAnswerQuestion.getScore()).as("Short answer question score is correct").isEqualTo(3);

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
        QuizExercise quizExerciseServer = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
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
                assertThat(multipleChoiceQuestion.getScore()).as("Multiple choice question score is correct").isEqualTo(4);

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
                assertThat(shortAnswerQuestion.getScore()).as("Short answer question score is correct").isEqualTo(3);

                List<ShortAnswerSpot> spots = shortAnswerQuestion.getSpots();
                assertThat(spots.get(0).getSpotNr()).as("Spot nr for spot is correct").isEqualTo(2);
                assertThat(spots.get(0).getWidth()).as("Width for spot is correct").isEqualTo(2);

                List<ShortAnswerSolution> solutions = shortAnswerQuestion.getSolutions();
                assertThat(solutions.get(0).getText()).as("Text for solution is correct").isEqualTo("long");
            }
        }
    }

    private QuizExercise createQuizOnServer(ZonedDateTime releaseDate, ZonedDateTime dueDate) throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);

        QuizExercise quizExercise = database.createQuiz(course, releaseDate, dueDate);
        quizExercise.setDuration(3600);
        QuizExercise quizExerciseServer = request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.CREATED);
        QuizExercise quizExerciseDatabase = quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseServer.getId());

        checkQuizExercises(quizExercise, quizExerciseServer);
        checkQuizExercises(quizExercise, quizExerciseDatabase);

        for (int i = 0; i < quizExercise.getQuizQuestions().size(); i++) {
            var question = quizExercise.getQuizQuestions().get(i);
            var questionServer = quizExerciseServer.getQuizQuestions().get(i);
            var questionDatabase = quizExerciseDatabase.getQuizQuestions().get(i);

            assertThat(question.getId()).as("Question IDs are correct").isNull();
            assertThat(questionDatabase.getId()).as("Question IDs are correct").isEqualTo(questionServer.getId());

            assertThat(question.getExercise().getId()).as("Exercise IDs are correct").isNull();
            assertThat(questionDatabase.getExercise().getId()).as("Exercise IDs are correct").isEqualTo(quizExerciseDatabase.getId());

            assertThat(question.getTitle()).as("Question titles are correct").isEqualTo(questionDatabase.getTitle());
            assertThat(question.getTitle()).as("Question titles are correct").isEqualTo(questionServer.getTitle());

            assertThat(question.getScore()).as("Question scores are correct").isEqualTo(questionDatabase.getScore());
            assertThat(question.getScore()).as("Question scores are correct").isEqualTo(questionServer.getScore());
        }
        return quizExerciseServer;
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteQuizExercise() throws Exception {
        QuizExercise quizExerciseServer = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        assertThat(quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseServer.getId())).as("Exercise is created correctly").isNotNull();
        request.delete("/api/quiz-exercises/" + quizExerciseServer.getId(), HttpStatus.OK);
        assertThat(quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseServer.getId())).as("Exercise is deleted correctly").isNull();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateNotExistingQuizExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);

        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().plusHours(5), null);
        QuizExercise quizExerciseServer = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.CREATED);
        assertThat(quizExercise.equals(quizExerciseServer));
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateRunningQuizExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);
        // create QuizExercise that already started
        QuizExercise startedQuizExercise = database.createQuiz(course, ZonedDateTime.now().minusHours(1), null);
        QuizExercise startedQuizExerciseServer = request.postWithResponseBody("/api/quiz-exercises", startedQuizExercise, QuizExercise.class, HttpStatus.CREATED);

        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) startedQuizExerciseServer.getQuizQuestions().get(0);
        mc.getAnswerOptions().remove(0);
        mc.getAnswerOptions().add(new AnswerOption().text("C").hint("H3").explanation("E3").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("D").hint("H4").explanation("E4").isCorrect(true));

        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz-exercises", startedQuizExerciseServer, QuizExercise.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedQuizExercise).isNull();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCreateExistingQuizExercise() throws Exception {
        QuizExercise quizExerciseServer = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        QuizExercise newQuizExerciseServer = request.postWithResponseBody("/api/quiz-exercises", quizExerciseServer, QuizExercise.class, HttpStatus.BAD_REQUEST);
        assertThat(newQuizExerciseServer).isNull();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetQuizExercise() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        QuizExercise quizExerciseGet = request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK, QuizExercise.class);
        checkQuizExercises(quizExercise, quizExerciseGet);

        QuizExercise quizExerciseForStudent_notStarted = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        assertThat(quizExerciseForStudent_notStarted.getQuizPointStatistic().getPointCounters().size()).isEqualTo(0);
        assertThat(quizExerciseForStudent_notStarted.getQuizQuestions()).hasSize(0);
        assertThat(quizExerciseForStudent_notStarted.getGradingInstructions()).isNull();
        assertThat(quizExerciseForStudent_notStarted.getGradingCriteria()).isEmpty();

        QuizExercise quizExercise_Started = createQuizOnServer(ZonedDateTime.now().minusMinutes(10), ZonedDateTime.now().plusHours(5));

        QuizExercise quizExerciseForStudent_Started = request.get("/api/quiz-exercises/" + quizExercise_Started.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        assertThat(quizExerciseForStudent_Started.getQuizPointStatistic().getPointCounters().size()).isEqualTo(0);
        assertThat(quizExerciseForStudent_Started.getGradingInstructions()).isNull();
        assertThat(quizExerciseForStudent_Started.getGradingCriteria()).isEmpty();
        for (QuizQuestion question : quizExerciseForStudent_Started.getQuizQuestions()){
            assertThat(question.getExplanation()).isNull();
        }

        QuizExercise quizExercise_Finished = createQuizOnServer(ZonedDateTime.now().minusHours(2), ZonedDateTime.now().minusHours(1));

        QuizExercise quizExerciseForStudent_Finished = request.get("/api/quiz-exercises/" + quizExercise_Finished.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        assertThat(quizExerciseForStudent_Finished.getQuizPointStatistic().getPointCounters().size()).isGreaterThan(0);
        assertThat(quizExerciseForStudent_Finished.getGradingInstructions()).isNull();
        assertThat(quizExerciseForStudent_Finished.getGradingCriteria()).isEmpty();
        for (QuizQuestion question : quizExerciseForStudent_Finished.getQuizQuestions()){
            assertThat(question.getExplanation()).isNotNull();
        }

        List<QuizExercise> allQuizExercisesForCourse = request.getList("/api/courses/" + quizExercise.getCourse().getId() + "/quiz-exercises", HttpStatus.OK, QuizExercise.class);
        assertThat(allQuizExercisesForCourse.size()).isEqualTo(2);
        assertThat(allQuizExercisesForCourse.get(1)).isEqualTo(quizExercise);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testRecalculateStatistics() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        QuizExercise quizExerciseWithRecalculatedStatistics = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", HttpStatus.OK,
                QuizExercise.class);
        compareStatistics(quizExercise, quizExerciseWithRecalculatedStatistics);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testReevaluateStatistics() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        // we expect a bad request because the quiz has not ended yet
        request.putWithResponseBody("/api/quiz-exercises-re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.OK);

        var now = ZonedDateTime.now();

        for (int i = 1; i <= 10; i++) {
            QuizSubmission quizSubmission = new QuizSubmission();

            MultipleChoiceSubmittedAnswer submittedMC = new MultipleChoiceSubmittedAnswer();
            submittedMC.setScoreInPoints(10.0);
            quizSubmission.addSubmittedAnswers(submittedMC);

            ShortAnswerSubmittedAnswer submittedSA = new ShortAnswerSubmittedAnswer();
            submittedSA.setScoreInPoints(10.0);
            quizSubmission.addSubmittedAnswers(submittedSA);

            DragAndDropSubmittedAnswer submittedDD = new DragAndDropSubmittedAnswer();
            submittedDD.setScoreInPoints(10.0);
            quizSubmission.addSubmittedAnswers(submittedDD);

            quizSubmission.submitted(true);
            quizSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(quizExercise, quizSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, 10L, true);
            }
            else if (i % 2 == 0) {
                database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, 20L, true);
            }
        }
        //calculate statistics
        quizExercise = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", HttpStatus.OK, QuizExercise.class);

        //reevaluate without changing anything and check if statistics are still correct
        QuizExercise quizExerciseWithReevaluatedStatistics = request.putWithResponseBody("/api/quiz-exercises-re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.OK);
        compareStatistics(quizExercise, quizExerciseWithReevaluatedStatistics);

        //remove an answer option and reevaluate
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        mc.getAnswerOptions().remove(0);
        quizExerciseWithReevaluatedStatistics = request.putWithResponseBody("/api/quiz-exercises-re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.OK);
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters().size()).isEqualTo(quizExercise.getQuizPointStatistic().getPointCounters().size());

        ShortAnswerQuestion sq = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);
        sq.setInvalid(true);

        quizExercise.getQuizQuestions().remove(1);

        quizExerciseWithReevaluatedStatistics = request.putWithResponseBody("/api/quiz-exercises-re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.OK);
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters().size()).isEqualTo(quizExercise.getQuizPointStatistic().getPointCounters().size()-1);
        // TODO: actually set some question elements invalid, remove them, etc. and check that afert the reevaluation everything is ok

    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testPerformStartNow() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/start-now", quizExercise, QuizExercise.class,
                HttpStatus.OK);
        long millis = ChronoUnit.MILLIS.between(updatedQuizExercise.getReleaseDate(), ZonedDateTime.now());
        // actually the two dates should be "exactly" the same, but for the sake of slow CI testing machines and to prevent flaky tests, we live with the following rule
        assertThat(millis).isCloseTo(0, byLessThan(2000L));
        assertThat(updatedQuizExercise.isIsPlannedToStart()).isTrue();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testPerformSetVisible() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
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
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
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

    private void checkQuizExercises(QuizExercise quizExercise, QuizExercise quizExerciseServer){
        assertThat(quizExercise.getQuizQuestions()).as("Same amount of questions saved").hasSize(quizExerciseServer.getQuizQuestions().size());
        assertThat(quizExercise.getTitle()).as("Title saved correctly").isEqualTo(quizExerciseServer.getTitle());
        assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Number of attempts saved correctly").isEqualTo(quizExerciseServer.getAllowedNumberOfAttempts());
        assertThat(quizExercise.getMaxScore()).as("Max score saved correctly").isEqualTo(quizExerciseServer.getMaxScore());
        assertThat(quizExercise.getDuration()).as("Duration saved correctly").isEqualTo(quizExerciseServer.getDuration());
        assertThat(quizExercise.getType()).as("Type saved correctly").isEqualTo(quizExerciseServer.getType());
    }

    private void compareStatistics(QuizExercise quizExercise, QuizExercise quizExercise2) {
        assertThat(quizExercise.getQuizPointStatistic().getPointCounters().size()).isEqualTo(quizExercise2.getQuizPointStatistic().getPointCounters().size());
        assertThat(quizExercise.getQuizPointStatistic().getParticipantsRated()).isEqualTo(quizExercise2.getQuizPointStatistic().getParticipantsRated());
        assertThat(quizExercise.getQuizPointStatistic().getParticipantsUnrated()).isEqualTo(quizExercise2.getQuizPointStatistic().getParticipantsUnrated());

        for (int i = 0; i < quizExercise.getQuizPointStatistic().getPointCounters().size(); i++) {
            PointCounter pointCounterBefore = quizExercise.getQuizPointStatistic().getPointCounters().iterator().next();
            PointCounter pointCounterAfter = quizExercise2.getQuizPointStatistic().getPointCounters().iterator().next();

            assertThat(pointCounterAfter.getPoints()).isEqualTo(pointCounterBefore.getPoints());
            assertThat(pointCounterAfter.getRatedCounter()).isEqualTo(pointCounterBefore.getRatedCounter());
            assertThat(pointCounterAfter.getUnRatedCounter()).isEqualTo(pointCounterBefore.getUnRatedCounter());
        }
    }

}
