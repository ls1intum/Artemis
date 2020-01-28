package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

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
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class QuizExerciseIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

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

        QuizExercise quizExercise = createQuiz(course);
        return request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.CREATED);
        // TODO: add some checks
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
        // TODO: also write one test that expects HttpStatus.OK
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testPerformStartNow() throws Exception {
        QuizExercise quizExercise = createQuizOnServer();
        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/start-now", quizExercise, QuizExercise.class,
                HttpStatus.OK);
        // TODO: add some additional checks for the retrieved data
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testPerformSetVisible() throws Exception {
        QuizExercise quizExercise = createQuizOnServer();
        // we expect a bad request because the quiz is already visible
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/set-visible", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        // TODO: also write one test that expects HttpStatus.OK
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testPerformOpenForPractice() throws Exception {
        QuizExercise quizExercise = createQuizOnServer();
        // we expect a bad request because the quiz has not ended yet
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/open-for-practice", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        // TODO: also write one test that expects HttpStatus.OK
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

}
