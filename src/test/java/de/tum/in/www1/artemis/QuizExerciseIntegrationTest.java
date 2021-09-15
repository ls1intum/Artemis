package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.*;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.service.scheduled.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.websocket.QuizSubmissionWebsocketService;

public class QuizExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private QuizScheduleService quizScheduleService;

    @Autowired
    private QuizExerciseService quizExerciseService;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private QuizSubmissionWebsocketService quizSubmissionWebsocketService;

    @Autowired
    private QuizSubmissionRepository quizSubmissionRepository;

    @Autowired
    private SubmittedAnswerRepository submittedAnswerRepository;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CourseRepository courseRepo;

    private QuizExercise quizExercise;

    // helper attributes for shorter code in assert statements
    private final PointCounter pc01 = pc(0, 1);

    private final PointCounter pc02 = pc(0, 2);

    private final PointCounter pc03 = pc(0, 3);

    private final PointCounter pc04 = pc(0, 4);

    private final PointCounter pc05 = pc(0, 5);

    private final PointCounter pc06 = pc(0, 6);

    private final PointCounter pc10 = pc(1, 0);

    private final PointCounter pc20 = pc(2, 0);

    private final PointCounter pc30 = pc(3, 0);

    private final PointCounter pc40 = pc(4, 0);

    private final PointCounter pc50 = pc(5, 0);

    private final PointCounter pc60 = pc(6, 0);

    @BeforeEach
    public void init() {
        database.addUsers(15, 5, 0, 1);
        quizScheduleService.startSchedule(5 * 1000);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        quizScheduleService.stopSchedule();
        quizScheduleService.clearAllQuizData();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCreateQuizExercise() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);

        // General assertions
        assertThat(quizExercise.getQuizQuestions().size()).as("Quiz questions were saved").isEqualTo(3);
        assertThat(quizExercise.getDuration()).as("Quiz duration was correctly set").isEqualTo(3600);
        assertThat(quizExercise.getDifficulty()).as("Quiz difficulty was correctly set").isEqualTo(DifficultyLevel.MEDIUM);

        // Quiz type specific assertions
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                assertThat(multipleChoiceQuestion.getAnswerOptions().size()).as("Multiple choice question answer options were saved").isEqualTo(2);
                assertThat(multipleChoiceQuestion.getTitle()).as("Multiple choice question title is correct").isEqualTo("MC");
                assertThat(multipleChoiceQuestion.getText()).as("Multiple choice question text is correct").isEqualTo("Q1");
                assertThat(multipleChoiceQuestion.getPoints()).as("Multiple choice question score is correct").isEqualTo(4);

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
            else if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                assertThat(dragAndDropQuestion.getDropLocations().size()).as("Drag and drop question drop locations were saved").isEqualTo(3);
                assertThat(dragAndDropQuestion.getDragItems().size()).as("Drag and drop question drag items were saved").isEqualTo(3);
                assertThat(dragAndDropQuestion.getTitle()).as("Drag and drop question title is correct").isEqualTo("DnD");
                assertThat(dragAndDropQuestion.getText()).as("Drag and drop question text is correct").isEqualTo("Q2");
                assertThat(dragAndDropQuestion.getPoints()).as("Drag and drop question score is correct").isEqualTo(3);

                List<DropLocation> dropLocations = dragAndDropQuestion.getDropLocations();
                assertThat(dropLocations.get(0).getPosX()).as("Pos X for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(0).getPosY()).as("Pos Y for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(0).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(0).getHeight()).as("Height for drop location is correct").isEqualTo(10);
                dropLocations.get(0).getQuestion();
                dropLocations.get(0).getMappings();
                assertThat(dropLocations.get(1).getPosX()).as("Pos X for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(1).getPosY()).as("Pos Y for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(1).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(1).getHeight()).as("Height for drop location is correct").isEqualTo(10);

                List<DragItem> dragItems = dragAndDropQuestion.getDragItems();
                assertThat(dragItems.get(0).getText()).as("Text for drag item is correct").isEqualTo("D1");
                assertThat(dragItems.get(1).getText()).as("Text for drag item is correct").isEqualTo("D2");
            }
            else if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
                assertThat(shortAnswerQuestion.getSpots().size()).as("Short answer question spots were saved").isEqualTo(2);
                assertThat(shortAnswerQuestion.getSolutions().size()).as("Short answer question solutions were saved").isEqualTo(2);
                assertThat(shortAnswerQuestion.getTitle()).as("Short answer question title is correct").isEqualTo("SA");
                assertThat(shortAnswerQuestion.getText()).as("Short answer question text is correct").isEqualTo("This is a long answer text");
                assertThat(shortAnswerQuestion.getPoints()).as("Short answer question score is correct").isEqualTo(2);

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
    public void testCreateQuizExerciseForExam() throws Exception {
        quizExercise = createQuizOnServerForExam();

        // General assertions
        assertThat(quizExercise.getQuizQuestions().size()).as("Quiz questions were saved").isEqualTo(3);
        assertThat(quizExercise.getDuration()).as("Quiz duration was correctly set").isEqualTo(3600);
        assertThat(quizExercise.getDifficulty()).as("Quiz difficulty was correctly set").isEqualTo(DifficultyLevel.MEDIUM);

        // Quiz type specific assertions
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                assertThat(multipleChoiceQuestion.getAnswerOptions().size()).as("Multiple choice question answer options were saved").isEqualTo(2);
                assertThat(multipleChoiceQuestion.getTitle()).as("Multiple choice question title is correct").isEqualTo("MC");
                assertThat(multipleChoiceQuestion.getText()).as("Multiple choice question text is correct").isEqualTo("Q1");
                assertThat(multipleChoiceQuestion.getPoints()).as("Multiple choice question score is correct").isEqualTo(4);

                List<AnswerOption> answerOptions = multipleChoiceQuestion.getAnswerOptions();
                assertThat(answerOptions.get(0).getText()).as("Text for answer option is correct").isEqualTo("A");
                assertThat(answerOptions.get(0).getHint()).as("Hint for answer option is correct").isEqualTo("H1");
                assertThat(answerOptions.get(0).getExplanation()).as("Explanation for answer option is correct").isEqualTo("E1");
                assertThat(answerOptions.get(0).isIsCorrect()).as("Is correct for answer option is correct").isTrue();
                assertThat(answerOptions.get(1).getText()).as("Text for answer option is correct").isEqualTo("B");
                assertThat(answerOptions.get(1).getHint()).as("Hint for answer option is correct").isEqualTo("H2");
                assertThat(answerOptions.get(1).getExplanation()).as("Explanation for answer option is correct").isEqualTo("E2");
                assertThat(answerOptions.get(1).isIsCorrect()).as("Is correct for answer option is correct").isFalse();
                answerOptions.get(1).getQuestion();
            }
            else if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                assertThat(dragAndDropQuestion.getDropLocations().size()).as("Drag and drop question drop locations were saved").isEqualTo(3);
                assertThat(dragAndDropQuestion.getDragItems().size()).as("Drag and drop question drag items were saved").isEqualTo(3);
                assertThat(dragAndDropQuestion.getTitle()).as("Drag and drop question title is correct").isEqualTo("DnD");
                assertThat(dragAndDropQuestion.getText()).as("Drag and drop question text is correct").isEqualTo("Q2");
                assertThat(dragAndDropQuestion.getPoints()).as("Drag and drop question score is correct").isEqualTo(3);

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
            else if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
                assertThat(shortAnswerQuestion.getSpots().size()).as("Short answer question spots were saved").isEqualTo(2);
                assertThat(shortAnswerQuestion.getSolutions().size()).as("Short answer question solutions were saved").isEqualTo(2);
                assertThat(shortAnswerQuestion.getTitle()).as("Short answer question title is correct").isEqualTo("SA");
                assertThat(shortAnswerQuestion.getText()).as("Short answer question text is correct").isEqualTo("This is a long answer text");
                assertThat(shortAnswerQuestion.getPoints()).as("Short answer question score is correct").isEqualTo(2);

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
    public void createQuizExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        QuizExercise quizExercise = ModelFactory.generateQuizExerciseForExam(exerciseGroup);
        quizExercise.setCourse(exerciseGroup.getExam().getCourse());
        request.postWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createQuizExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        QuizExercise quizExercise = ModelFactory.generateQuizExerciseForExam(null);
        request.postWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createQuizExercise_InvalidMaxScore() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        quizExercise.setMaxPoints(0.0);
        request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createQuizExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        quizExercise.setMaxPoints(10.0);
        quizExercise.setBonusPoints(1.0);
        quizExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createQuizExercise_NotIncludedInvalidBonusPoints() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        quizExercise.setMaxPoints(10.0);
        quizExercise.setBonusPoints(1.0);
        quizExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testEditQuizExercise() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);

        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        mc.getAnswerOptions().remove(0);
        mc.getAnswerOptions().add(new AnswerOption().text("C").hint("H3").explanation("E3").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("D").hint("H4").explanation("E4").isCorrect(true));

        DragAndDropQuestion dnd = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        dnd.getDropLocations().remove(0);
        dnd.getCorrectMappings().remove(0);
        dnd.getDragItems().remove(0);

        ShortAnswerQuestion sa = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);
        sa.getSpots().remove(0);
        sa.getSolutions().remove(0);
        sa.getCorrectMappings().remove(0);

        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.OK);

        // Quiz type specific assertions
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                assertThat(multipleChoiceQuestion.getAnswerOptions().size()).as("Multiple choice question answer options were saved").isEqualTo(3);
                assertThat(multipleChoiceQuestion.getTitle()).as("Multiple choice question title is correct").isEqualTo("MC");
                assertThat(multipleChoiceQuestion.getText()).as("Multiple choice question text is correct").isEqualTo("Q1");
                assertThat(multipleChoiceQuestion.getPoints()).as("Multiple choice question score is correct").isEqualTo(4);

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
            else if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                assertThat(dragAndDropQuestion.getDropLocations().size()).as("Drag and drop question drop locations were saved").isEqualTo(2);
                assertThat(dragAndDropQuestion.getDragItems().size()).as("Drag and drop question drag items were saved").isEqualTo(2);
                assertThat(dragAndDropQuestion.getTitle()).as("Drag and drop question title is correct").isEqualTo("DnD");
                assertThat(dragAndDropQuestion.getText()).as("Drag and drop question text is correct").isEqualTo("Q2");
                assertThat(dragAndDropQuestion.getPoints()).as("Drag and drop question score is correct").isEqualTo(3);

                List<DropLocation> dropLocations = dragAndDropQuestion.getDropLocations();
                assertThat(dropLocations.get(0).getPosX()).as("Pos X for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(0).getPosY()).as("Pos Y for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(0).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(0).getHeight()).as("Height for drop location is correct").isEqualTo(10);

                List<DragItem> dragItems = dragAndDropQuestion.getDragItems();
                assertThat(dragItems.get(0).getText()).as("Text for drag item is correct").isEqualTo("D2");
            }
            else if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
                assertThat(shortAnswerQuestion.getSpots().size()).as("Short answer question spots were saved").isEqualTo(1);
                assertThat(shortAnswerQuestion.getSolutions().size()).as("Short answer question solutions were saved").isEqualTo(1);
                assertThat(shortAnswerQuestion.getTitle()).as("Short answer question title is correct").isEqualTo("SA");
                assertThat(shortAnswerQuestion.getText()).as("Short answer question text is correct").isEqualTo("This is a long answer text");
                assertThat(shortAnswerQuestion.getPoints()).as("Short answer question score is correct").isEqualTo(2);

                List<ShortAnswerSpot> spots = shortAnswerQuestion.getSpots();
                assertThat(spots.get(0).getSpotNr()).as("Spot nr for spot is correct").isEqualTo(2);
                assertThat(spots.get(0).getWidth()).as("Width for spot is correct").isEqualTo(2);

                List<ShortAnswerSolution> solutions = shortAnswerQuestion.getSolutions();
                assertThat(solutions.get(0).getText()).as("Text for solution is correct").isEqualTo("long");
            }
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testEditQuizExerciseForExam() throws Exception {
        quizExercise = createQuizOnServerForExam();

        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        mc.getAnswerOptions().remove(0);
        mc.getAnswerOptions().add(new AnswerOption().text("C").hint("H3").explanation("E3").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("D").hint("H4").explanation("E4").isCorrect(true));

        DragAndDropQuestion dnd = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        dnd.getDropLocations().remove(0);
        dnd.getCorrectMappings().remove(0);
        dnd.getDragItems().remove(0);

        ShortAnswerQuestion sa = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);
        sa.getSpots().remove(0);
        sa.getSolutions().remove(0);
        sa.getCorrectMappings().remove(0);

        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.OK);

        // Quiz type specific assertions
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                assertThat(multipleChoiceQuestion.getAnswerOptions().size()).as("Multiple choice question answer options were saved").isEqualTo(3);
                assertThat(multipleChoiceQuestion.getTitle()).as("Multiple choice question title is correct").isEqualTo("MC");
                assertThat(multipleChoiceQuestion.getText()).as("Multiple choice question text is correct").isEqualTo("Q1");
                assertThat(multipleChoiceQuestion.getPoints()).as("Multiple choice question score is correct").isEqualTo(4);

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
            else if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                assertThat(dragAndDropQuestion.getDropLocations().size()).as("Drag and drop question drop locations were saved").isEqualTo(2);
                assertThat(dragAndDropQuestion.getDragItems().size()).as("Drag and drop question drag items were saved").isEqualTo(2);
                assertThat(dragAndDropQuestion.getTitle()).as("Drag and drop question title is correct").isEqualTo("DnD");
                assertThat(dragAndDropQuestion.getText()).as("Drag and drop question text is correct").isEqualTo("Q2");
                assertThat(dragAndDropQuestion.getPoints()).as("Drag and drop question score is correct").isEqualTo(3);

                List<DropLocation> dropLocations = dragAndDropQuestion.getDropLocations();
                assertThat(dropLocations.get(0).getPosX()).as("Pos X for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(0).getPosY()).as("Pos Y for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(0).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(0).getHeight()).as("Height for drop location is correct").isEqualTo(10);

                List<DragItem> dragItems = dragAndDropQuestion.getDragItems();
                assertThat(dragItems.get(0).getText()).as("Text for drag item is correct").isEqualTo("D2");
            }
            else if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
                assertThat(shortAnswerQuestion.getSpots().size()).as("Short answer question spots were saved").isEqualTo(1);
                assertThat(shortAnswerQuestion.getSolutions().size()).as("Short answer question solutions were saved").isEqualTo(1);
                assertThat(shortAnswerQuestion.getTitle()).as("Short answer question title is correct").isEqualTo("SA");
                assertThat(shortAnswerQuestion.getText()).as("Short answer question text is correct").isEqualTo("This is a long answer text");
                assertThat(shortAnswerQuestion.getPoints()).as("Short answer question score is correct").isEqualTo(2);

                List<ShortAnswerSpot> spots = shortAnswerQuestion.getSpots();
                assertThat(spots.get(0).getSpotNr()).as("Spot nr for spot is correct").isEqualTo(2);
                assertThat(spots.get(0).getWidth()).as("Width for spot is correct").isEqualTo(2);

                List<ShortAnswerSolution> solutions = shortAnswerQuestion.getSolutions();
                assertThat(solutions.get(0).getText()).as("Text for solution is correct").isEqualTo("long");
            }
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateQuizExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        quizExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateQuizExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        quizExercise.setCourse(null);

        request.putWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateQuizExercise_convertFromCourseToExamExercise_badRequest() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);

        quizExercise.setCourse(null);
        quizExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateQuizExercise_convertFromExamToCourseExercise_badRequest() throws Exception {
        Course course = database.addEmptyCourse();
        database.addExerciseGroupWithExamAndCourse(true);
        QuizExercise quizExercise = createQuizOnServerForExam();

        quizExercise.setExerciseGroup(null);
        quizExercise.setCourse(course);

        request.putWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    private QuizExercise createQuizOnServer(ZonedDateTime releaseDate, ZonedDateTime dueDate) throws Exception {
        Course course = database.createCourse();

        QuizExercise quizExercise = database.createQuiz(course, releaseDate, dueDate);
        quizExercise.setDuration(3600);
        QuizExercise quizExerciseServer = request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.CREATED);
        QuizExercise quizExerciseDatabase = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExerciseServer.getId());
        assertThat(quizExerciseDatabase).isNotNull();
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

            assertThat(question.getPoints()).as("Question scores are correct").isEqualTo(questionDatabase.getPoints());
            assertThat(question.getPoints()).as("Question scores are correct").isEqualTo(questionServer.getPoints());
        }
        return quizExerciseServer;
    }

    private QuizExercise createQuizOnServerForExam() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);

        QuizExercise quizExercise = database.createQuizForExam(exerciseGroup);
        quizExercise.setDuration(3600);
        QuizExercise quizExerciseServer = request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.CREATED);
        QuizExercise quizExerciseDatabase = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExerciseServer.getId());
        assertThat(quizExerciseDatabase).isNotNull();
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

            assertThat(question.getPoints()).as("Question scores are correct").isEqualTo(questionDatabase.getPoints());
            assertThat(question.getPoints()).as("Question scores are correct").isEqualTo(questionServer.getPoints());
        }
        return quizExerciseServer;
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteQuizExercise() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);

        assertThat(quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is created correctly").isNotNull();
        request.delete("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK);
        assertThat(quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is deleted correctly").isNull();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteQuizExerciseWithSubmittedAnswers() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(1));

        assertThat(quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is created correctly").isNotNull();

        final var username = "student1";
        final Principal principal = () -> username;
        QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, 1, true, null);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Quiz submissions are not yet in database
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(0);

        quizScheduleService.processCachedQuizSubmissions();

        // Quiz submissions are now in database
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(1);

        request.delete("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK);
        assertThat(quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is deleted correctly").isNull();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateNotExistingQuizExercise() throws Exception {
        Course course = database.createCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().plusHours(5), null);
        QuizExercise quizExerciseServer = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.CREATED);
        assertThat(quizExerciseServer.getId()).isNotNull();
        assertThat(quizExerciseServer.getQuizQuestions()).hasSize(quizExercise.getQuizQuestions().size());
        assertThat(quizExerciseServer.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(quizExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertThat(quizExerciseServer.getMaxPoints()).isEqualTo(quizExercise.getMaxPoints());
        // TODO: check more values for equality
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateRunningQuizExercise() throws Exception {
        Course course = database.createCourse();
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
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);

        QuizExercise newQuizExerciseServer = request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        assertThat(newQuizExerciseServer).isNull();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetQuizExercise() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);

        QuizExercise quizExerciseGet = request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK, QuizExercise.class);
        checkQuizExercises(quizExercise, quizExerciseGet);

        // get all exercises for a course
        List<QuizExercise> allQuizExercisesForCourse = request.getList("/api/courses/" + quizExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/quiz-exercises",
                HttpStatus.OK, QuizExercise.class);
        assertThat(allQuizExercisesForCourse.size()).isEqualTo(1);
        assertThat(allQuizExercisesForCourse.get(0)).isEqualTo(quizExercise);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetQuizExercise_asStudent() throws Exception {
        Course course = database.createCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().plusHours(5), null);
        quizExercise.setDuration(360);
        quizExercise = quizExerciseService.save(quizExercise);

        // get not started exercise for students
        QuizExercise quizExerciseForStudent_notStarted = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        checkQuizExerciseForStudent(quizExerciseForStudent_notStarted);

        // get started exercise for students
        quizExercise.setReleaseDate(ZonedDateTime.now().minusMinutes(5));
        quizExercise.setDueDate(ZonedDateTime.now().plusMinutes(1)); // total 6min = 360s
        quizExercise = quizExerciseService.save(quizExercise);
        assertThat(quizExercise.isSubmissionAllowed()).isTrue();
        assertThat(quizExercise.isStarted()).isTrue();

        QuizExercise quizExerciseForStudent_Started = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        checkQuizExerciseForStudent(quizExerciseForStudent_Started);

        // get finished exercise for students
        quizExercise.setReleaseDate(ZonedDateTime.now().minusMinutes(5));
        quizExercise.setDueDate(ZonedDateTime.now().minusMinutes(2));
        quizExercise.setDuration(180);
        quizExercise = quizExerciseService.save(quizExercise);
        assertThat(quizExercise.isSubmissionAllowed()).isFalse();
        assertThat(quizExercise.isStarted()).isTrue();

        QuizExercise quizExerciseForStudent_Finished = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        checkQuizExerciseForStudent(quizExerciseForStudent_Finished);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetQuizExercisesForExam() throws Exception {
        quizExercise = createQuizOnServerForExam();
        var examId = quizExercise.getExerciseGroup().getExam().getId();
        List<QuizExercise> quizExercises = request.getList("/api/" + examId + "/quiz-exercises", HttpStatus.OK, QuizExercise.class);
        assertThat(quizExercises).as("Quiz exercise was retrieved").isNotNull();
        assertThat(quizExercises.size()).as("Quiz exercise was retrieved").isEqualTo(1L);
        assertThat(quizExercise.getId()).as("Quiz exercise with the right id was retrieved").isEqualTo(quizExercises.get(0).getId());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetExamQuizExercise() throws Exception {
        quizExercise = createQuizOnServerForExam();

        QuizExercise quizExerciseGet = request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK, QuizExercise.class);
        checkQuizExercises(quizExercise, quizExerciseGet);

        assertThat(quizExerciseGet).isEqualTo(quizExercise);
        assertThat(quizExerciseGet).as("Quiz exercise was retrieved").isNotNull();
        assertThat(quizExerciseGet.getId()).as("Quiz exercise with the right id was retrieved").isEqualTo(quizExerciseGet.getId());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetExamQuizExercise_asTutor_forbidden() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        quizExercise = database.createQuizForExam(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);
        request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.FORBIDDEN, QuizExercise.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testRecalculateStatistics() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);

        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.OK);

        var now = ZonedDateTime.now();

        // generate submissions for each student
        int numberOfParticipants = 10;

        for (int i = 1; i <= numberOfParticipants; i++) {
            QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, i, true, now.minusHours(3));
            database.addSubmission(quizExercise, quizSubmission, "student" + i);
            database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
        }

        // calculate statistics
        QuizExercise quizExerciseWithRecalculatedStatistics = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", HttpStatus.OK,
                QuizExercise.class);

        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getPointCounters().size()).isEqualTo(10);
        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getParticipantsRated()).isEqualTo(numberOfParticipants);

        for (PointCounter pointCounter : quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getPoints().equals(0.0)) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(3);
            }
            else if (pointCounter.getPoints().equals(3.0) || pointCounter.getPoints().equals(4.0) || pointCounter.getPoints().equals(6.0)) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(2);
            }
            else if (pointCounter.getPoints().equals(7.0)) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(1);
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
            }
        }

        // add more submissions and recalculate
        for (int i = numberOfParticipants; i <= 14; i++) {
            QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, i, true, now.minusHours(3));
            database.addSubmission(quizExercise, quizSubmission, "student" + i);
            database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
        }

        // calculate statistics
        quizExerciseWithRecalculatedStatistics = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", HttpStatus.OK, QuizExercise.class);

        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getPointCounters().size()).isEqualTo(10);
        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getParticipantsRated()).isEqualTo(numberOfParticipants + 4);

        for (PointCounter pointCounter : quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getPoints().equals(0.0)) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(5);
            }
            else if (pointCounter.getPoints().equals(4.0)) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(3);
            }
            else if (pointCounter.getPoints().equals(3.0) || pointCounter.getPoints().equals(6.0)) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(2);
            }
            else if (pointCounter.getPoints().equals(7.0)) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(1);
            }
            else if (pointCounter.getPoints().equals(9.0)) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(1);
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(0);
            }
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testReevaluateStatistics() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusSeconds(5), null);

        // we expect a bad request because the quiz has not ended yet
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        // quizExercise.setDuration(3600);
        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.OK);

        // generate rated submissions for each student
        int numberOfParticipants = 10;

        for (int i = 1; i <= numberOfParticipants; i++) {
            if (i != 1 && i != 5) {
                QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, i, true, ZonedDateTime.now().minusHours(1));
                database.addSubmission(quizExercise, quizSubmission, "student" + i);
                database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
            }
        }

        assertThat(submittedAnswerRepository.findAll().size()).isEqualTo((numberOfParticipants - 2) * 3);

        // submission with everything selected
        QuizSubmission quizSubmission = database.generateSpecialSubmissionWithResult(quizExercise, true, ZonedDateTime.now().minusHours(1), true);
        database.addSubmission(quizExercise, quizSubmission, "student1");
        database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);

        // submission with nothing selected
        quizSubmission = database.generateSpecialSubmissionWithResult(quizExercise, true, ZonedDateTime.now().minusHours(1), false);
        database.addSubmission(quizExercise, quizSubmission, "student5");
        database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);

        assertThat(studentParticipationRepository.findAll().size()).isEqualTo(numberOfParticipants);
        assertThat(resultRepository.findAll().size()).isEqualTo(numberOfParticipants);
        assertThat(quizSubmissionRepository.findAll().size()).isEqualTo(numberOfParticipants);
        assertThat(submittedAnswerRepository.findAll().size()).isEqualTo(numberOfParticipants * 3);

        // calculate statistics
        quizExercise = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", HttpStatus.OK, QuizExercise.class);

        System.out.println("QuizPointStatistic before re-evaluate: " + quizExercise.getQuizPointStatistic());

        // check that the statistic is correct before any re-evaluate
        assertQuizPointStatisticsPointCounters(quizExercise, Map.of(0.0, pc30, 3.0, pc20, 4.0, pc20, 6.0, pc20, 7.0, pc10));

        // reevaluate without changing anything and check if statistics are still correct (i.e. unchanged)
        QuizExercise quizExerciseWithReevaluatedStatistics = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate/", quizExercise,
                QuizExercise.class, HttpStatus.OK);
        checkStatistics(quizExercise, quizExerciseWithReevaluatedStatistics);

        System.out.println("QuizPointStatistic after re-evaluate (without changes): " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        // remove wrong answer option and reevaluate
        var multipleChoiceQuestion = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        multipleChoiceQuestion.getAnswerOptions().remove(1);

        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.OK);

        // load the exercise again after it was re-evaluated
        quizExerciseWithReevaluatedStatistics = request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK, QuizExercise.class);

        var multipleChoiceQuestionAfterReevaluate = (MultipleChoiceQuestion) quizExerciseWithReevaluatedStatistics.getQuizQuestions().get(0);
        assertThat(multipleChoiceQuestionAfterReevaluate.getAnswerOptions()).hasSize(1);

        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic()).isEqualTo(quizExercise.getQuizPointStatistic());

        // one student should get a higher score
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters().size())
                .isEqualTo(quizExercise.getQuizPointStatistic().getPointCounters().size());
        System.out.println("QuizPointStatistic after 1st re-evaluate: " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(0.0, pc20, 3.0, pc20, 4.0, pc30, 6.0, pc20, 7.0, pc10));

        // set a question invalid and reevaluate
        var shortAnswerQuestion = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);
        shortAnswerQuestion.setInvalid(true);

        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.OK);
        // load the exercise again after it was re-evaluated
        quizExerciseWithReevaluatedStatistics = request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK, QuizExercise.class);

        var shortAnswerQuestionAfterReevaluation = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);
        assertThat(shortAnswerQuestionAfterReevaluation.isInvalid()).isTrue();

        // several students should get a higher score
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters().size())
                .isEqualTo(quizExercise.getQuizPointStatistic().getPointCounters().size());
        System.out.println("QuizPointStatistic after 2nd re-evaluate: " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());
        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(2.0, pc20, 5.0, pc20, 6.0, pc50, 9.0, pc10));

        // delete a question and reevaluate
        quizExercise.getQuizQuestions().remove(1);

        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.OK);
        // load the exercise again after it was re-evaluated
        quizExerciseWithReevaluatedStatistics = request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK, QuizExercise.class);

        assertThat(quizExerciseWithReevaluatedStatistics.getQuizQuestions()).hasSize(2);

        // max score should be less
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters().size())
                .isEqualTo(quizExercise.getQuizPointStatistic().getPointCounters().size() - 3);
        System.out.println("QuizPointStatistic after 3rd re-evaluate: " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());
        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(2.0, pc40, 6.0, pc60));
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testReevaluateStatistics_Practice() throws Exception {

        quizExercise = createQuizOnServer(ZonedDateTime.now().plusSeconds(5), null);
        // use the exact other scoring types to cover all combinations in the tests
        quizExercise.getQuizQuestions().get(0).setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);   // MC
        quizExercise.getQuizQuestions().get(1).setScoringType(ScoringType.ALL_OR_NOTHING);              // DnD
        quizExercise.getQuizQuestions().get(2).setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);   // SA

        // we expect a bad request because the quiz has not ended yet
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        quizExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        quizExercise.setDuration(3600);
        quizExercise.setIsOpenForPractice(true);
        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.OK);

        // generate unrated submissions for each student
        int numberOfParticipants = 10;

        for (int i = 1; i <= numberOfParticipants; i++) {
            if (i != 1 && i != 5) {
                QuizSubmission quizSubmissionPractice = database.generateSubmissionForThreeQuestions(quizExercise, i, true, ZonedDateTime.now());
                database.addSubmission(quizExercise, quizSubmissionPractice, "student" + i);
                database.addResultToSubmission(quizSubmissionPractice, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmissionPractice), false);
            }
        }

        // submission with everything selected
        QuizSubmission quizSubmissionPractice = database.generateSpecialSubmissionWithResult(quizExercise, true, ZonedDateTime.now(), true);
        database.addSubmission(quizExercise, quizSubmissionPractice, "student1");
        database.addResultToSubmission(quizSubmissionPractice, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmissionPractice), false);

        // submission with nothing selected
        quizSubmissionPractice = database.generateSpecialSubmissionWithResult(quizExercise, true, ZonedDateTime.now(), false);
        database.addSubmission(quizExercise, quizSubmissionPractice, "student5");
        database.addResultToSubmission(quizSubmissionPractice, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmissionPractice), false);

        assertThat(studentParticipationRepository.findAll().size()).isEqualTo(10);
        assertThat(resultRepository.findAll().size()).isEqualTo(10);

        // calculate statistics
        quizExercise = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", HttpStatus.OK, QuizExercise.class);

        System.out.println("QuizPointStatistic before re-evaluate: " + quizExercise.getQuizPointStatistic());

        // reevaluate without changing anything and check if statistics are still correct
        QuizExercise quizExerciseWithReevaluatedStatistics = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate/", quizExercise,
                QuizExercise.class, HttpStatus.OK);
        checkStatistics(quizExercise, quizExerciseWithReevaluatedStatistics);

        System.out.println("QuizPointStatistic after re-evaluate (without changes): " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        // remove wrong answer option and reevaluate
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExerciseWithReevaluatedStatistics.getQuizQuestions().get(0);
        mc.getAnswerOptions().remove(1);

        quizExerciseWithReevaluatedStatistics = request.putWithResponseBody("/api/quiz-exercises/" + quizExerciseWithReevaluatedStatistics.getId() + "/re-evaluate/",
                quizExerciseWithReevaluatedStatistics, QuizExercise.class, HttpStatus.OK);

        // one student should get a higher score
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters().size())
                .isEqualTo(quizExercise.getQuizPointStatistic().getPointCounters().size());

        System.out.println("QuizPointStatistic after 1st re-evaluate: " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(0.0, pc02, 3.0, pc02, 4.0, pc03, 6.0, pc02, 7.0, pc01));

        // set a question invalid and reevaluate
        quizExerciseWithReevaluatedStatistics.getQuizQuestions().get(2).setInvalid(true);

        quizExerciseWithReevaluatedStatistics = request.putWithResponseBody("/api/quiz-exercises/" + quizExerciseWithReevaluatedStatistics.getId() + "/re-evaluate/",
                quizExerciseWithReevaluatedStatistics, QuizExercise.class, HttpStatus.OK);

        // several students should get a higher score
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters().size())
                .isEqualTo(quizExercise.getQuizPointStatistic().getPointCounters().size());
        System.out.println("QuizPointStatistic after 2nd re-evaluate: " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(2.0, pc02, 5.0, pc02, 6.0, pc05, 9.0, pc01));

        // delete a question and reevaluate
        quizExerciseWithReevaluatedStatistics.getQuizQuestions().remove(1);

        quizExerciseWithReevaluatedStatistics = request.putWithResponseBody("/api/quiz-exercises/" + quizExerciseWithReevaluatedStatistics.getId() + "/re-evaluate/",
                quizExerciseWithReevaluatedStatistics, QuizExercise.class, HttpStatus.OK);

        // max score should be less
        System.out.println("QuizPointStatistic after 3rd re-evaluate: " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters().size())
                .isEqualTo(quizExercise.getQuizPointStatistic().getPointCounters().size() - 3);

        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(2.0, pc04, 6.0, pc06));
    }

    private PointCounter pc(int rated, int unrated) {
        var pointCounter = new PointCounter();
        pointCounter.setRatedCounter(rated);
        pointCounter.setUnRatedCounter(unrated);
        return pointCounter;
    }

    private void assertQuizPointStatisticsPointCounters(QuizExercise quizExercise, Map<Double, PointCounter> expectedPointCounters) {
        for (PointCounter pointCounter : quizExercise.getQuizPointStatistic().getPointCounters()) {
            var expectedPointCounter = expectedPointCounters.get(pointCounter.getPoints());
            if (expectedPointCounter != null) {
                assertThat(pointCounter.getRatedCounter()).as(pointCounter.getPoints() + " should have a rated counter of " + expectedPointCounter.getRatedCounter())
                        .isEqualTo(expectedPointCounter.getRatedCounter());
                assertThat(pointCounter.getUnRatedCounter()).as(pointCounter.getPoints() + " should have an unrated counter of " + expectedPointCounter.getUnRatedCounter())
                        .isEqualTo(expectedPointCounter.getUnRatedCounter());
            }
            else {
                assertThat(pointCounter.getRatedCounter()).as(pointCounter.getPoints() + " should have a rated counter of 0").isEqualTo(0);
                assertThat(pointCounter.getUnRatedCounter()).as(pointCounter.getPoints() + " should have a rated counter of 0").isEqualTo(0);
            }
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testReEvaluateQuizQuestionWithMoreSolutions() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().minusHours(5), ZonedDateTime.now().minusHours(2));
        QuizQuestion question = quizExercise.getQuizQuestions().get(2);

        if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
            // demonstrate that the initial shortAnswerQuestion has 2 correct mappings and 2 solutions
            assertThat(shortAnswerQuestion.getCorrectMappings().size()).isEqualTo(2);
            assertThat(shortAnswerQuestion.getCorrectMappings().size()).isEqualTo(2);

            // add a solution with an mapping onto spot number 0
            ShortAnswerSolution newSolution = new ShortAnswerSolution();
            newSolution.setText("text");
            newSolution.setId(3L);
            shortAnswerQuestion.getSolutions().add(newSolution);
            ShortAnswerMapping newMapping = new ShortAnswerMapping();
            newMapping.setId(3L);
            newMapping.setInvalid(false);
            newMapping.setShortAnswerSolutionIndex(2);
            newMapping.setSolution(newSolution);
            newMapping.setSpot(shortAnswerQuestion.getSpots().get(0));
            newMapping.setShortAnswerSpotIndex(0);
            shortAnswerQuestion.getCorrectMappings().add(newMapping);
            quizExercise.getQuizQuestions().remove(2);
            quizExercise.getQuizQuestions().add(shortAnswerQuestion);
        }
        // PUT Request with the newly modified quizExercise
        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate", quizExercise, QuizExercise.class,
                HttpStatus.OK);
        // Check that the updatedQuizExercise is equal to the modified quizExercise with special focus on the newly added solution and mapping
        assertThat(updatedQuizExercise).isEqualTo(quizExercise);
        ShortAnswerQuestion receivedShortAnswerQuestion = (ShortAnswerQuestion) updatedQuizExercise.getQuizQuestions().get(2);
        assertThat(receivedShortAnswerQuestion.getSolutions().size()).isEqualTo(3);
        assertThat(receivedShortAnswerQuestion.getCorrectMappings().size()).isEqualTo(3);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testPerformStartNow() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);

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
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);

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
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);

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

    /**
     * test non instructors cant create quiz exercises
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCreateQuizExerciseAsTutorForbidden() throws Exception {
        final Course course = database.createCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now(), ZonedDateTime.now().plusHours(5));
        // remove instructor rights
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.FORBIDDEN);
        assertThat(course.getExercises()).isEmpty();
    }

    /**
     * test non instructors cant get all quiz exercises
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllQuizExercisesAsStudentForbidden() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise("Titel");
        assertThat(course.getExercises()).isNotEmpty();
        List<QuizExercise> quizExercises;
        // remove instructor rights
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        quizExercises = request.getList("/api/courses/" + course.getId() + "/quiz-exercises", HttpStatus.FORBIDDEN, QuizExercise.class);
        assertThat(quizExercises).isNull();
    }

    /**
     * test non instructors cant perform start-now, set-visible or open-for-practice on quiz exercises
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testPerformPutActionAsTutorForbidden() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        assertThat(course.getExercises()).isNotEmpty();
        quizExercise = quizExerciseRepository.findByCourseId(course.getId()).get(0);
        assertThat(quizExercise.isIsOpenForPractice()).isFalse();
        // remove instructor rights
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);

        request.put("/api/quiz-exercises/" + quizExercise.getId() + "/open-for-practice", quizExercise, HttpStatus.FORBIDDEN);
        assertThat(quizExerciseRepository.findByCourseId(course.getId()).get(0).isIsOpenForPractice()).isFalse();
    }

    /**
     * test non instructors cant see the exercise if it is not set to visible
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testViewQuizExerciseAsStudentNotVisible() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        quizExercise = quizExerciseRepository.findByCourseId(course.getId()).get(0);
        assertThat(quizExercise.isVisibleToStudents()).isFalse();
        // remove instructor rights in course
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.FORBIDDEN, QuizExercise.class);
    }

    /**
     * test non instructors cant delete an exercise
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteQuizExerciseAsNonInstructor() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        quizExercise = quizExerciseRepository.findByCourseId(course.getId()).get(0);
        // remove instructor rights in course
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        request.delete("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.FORBIDDEN);
    }

    /**
     * test non tutors cant recalculate quiz exercise statistics
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testRecalculateStatisticsAsNonInstructor() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        quizExercise = quizExerciseRepository.findByCourseId(course.getId()).get(0);
        // remove instructor rights in course
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        request.get("/api/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", HttpStatus.FORBIDDEN, QuizExercise.class);
    }

    /**
     * test students not in course cant get quiz exercises
     * */
    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetQuizExerciseForStudentNotInCourseForbiden() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        quizExercise = quizExerciseRepository.findByCourseId(course.getId()).get(0);
        // remove instructor rights in course
        User user = database.getUserByLogin("student1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.FORBIDDEN, QuizExercise.class);
    }

    /**
     * test non instructors in this course cant re-evaluate quiz exercises
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testReEvaluateQuizAsNonInstructorForbidden() throws Exception {
        final Course course = database.createCourse();
        quizExercise = database.createQuiz(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusHours(1));
        quizExercise.setTitle("Titel");
        quizExercise.setDuration(200);
        assertThat(quizExercise.isValid()).isTrue().as("is not valid!");
        assertThat(quizExercise.isExamExercise()).isFalse().as("Is an exam exercise!");
        assertThat(quizExercise.isEnded()).isTrue().as("Is not ended!");
        course.addExercises(quizExercise);

        courseRepo.save(course);
        quizExerciseRepository.save(quizExercise);
        // remove instructor rights in course
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        request.put("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate", quizExercise, HttpStatus.FORBIDDEN);
    }

    /**
     * test unfinished exam cannot be re-evaluated
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testUnfinishedExamReEvaluateBadRequest() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        quizExercise = database.createQuizForExam(exerciseGroup);
        quizExercise.setTitle("Titel");
        quizExercise.setDuration(200);
        assertThat(quizExercise.isValid()).isTrue().as("is not valid!");
        quizExerciseRepository.save(quizExercise);
        request.put("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate", quizExercise, HttpStatus.BAD_REQUEST);
    }

    /**
     * test non editor cant update quiz exercise
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateQuizExerciseAsNonEditorForbidden() throws Exception {
        final Course course = database.createCourse();
        quizExercise = database.createQuiz(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusHours(1));
        quizExercise.setTitle("Titel");
        quizExercise.setDuration(200);
        assertThat(quizExercise.isValid()).isTrue().as("is not valid!");
        assertThat(quizExercise.isExamExercise()).isFalse().as("Is an exam exercise!");
        assertThat(quizExercise.isEnded()).isTrue().as("Is not ended!");
        course.addExercises(quizExercise);
        courseRepo.save(course);
        quizExerciseRepository.save(quizExercise);
        // change some stuff
        quizExercise.setTitle("new Titel");
        quizExercise.setIsVisibleBeforeStart(true);
        // remove instructor rights in course
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        request.put("/api/quiz-exercises", quizExercise, HttpStatus.FORBIDDEN);
    }

    /**
     * test quiz exercise cant be edited to be invalid
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateQuizExerciseInvalidBadRequest() throws Exception {
        final Course course = database.createCourse();
        quizExercise = database.createQuiz(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusHours(1));
        quizExercise.setTitle("Titel");
        quizExercise.setDuration(200);
        assertThat(quizExercise.isValid()).isTrue().as("is not valid!");
        assertThat(quizExercise.isExamExercise()).isFalse().as("Is an exam exercise!");
        assertThat(quizExercise.isEnded()).isTrue().as("Is not ended!");
        course.addExercises(quizExercise);
        courseRepo.save(course);
        quizExerciseRepository.save(quizExercise);
        // change some stuff
        quizExercise.setTitle(null);
        assertThat(quizExercise.isValid()).isFalse();
        request.put("/api/quiz-exercises", quizExercise, HttpStatus.BAD_REQUEST);
    }

    /**
     * test update quiz exercise with notificationText
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateQuizExerciseWithNotificationText() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);

        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        mc.getAnswerOptions().remove(0);
        mc.getAnswerOptions().add(new AnswerOption().text("C").hint("H3").explanation("E3").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("D").hint("H4").explanation("E4").isCorrect(true));

        DragAndDropQuestion dnd = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        dnd.getDropLocations().remove(0);
        dnd.getCorrectMappings().remove(0);
        dnd.getDragItems().remove(0);

        ShortAnswerQuestion sa = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);
        sa.getSpots().remove(0);
        sa.getSolutions().remove(0);
        sa.getCorrectMappings().remove(0);

        var params = new LinkedMultiValueMap<String, String>();
        params.add("notificationText", "NotificationTextTEST!");
        request.putWithResponseBodyAndParams("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.OK, params);
        // TODO check if notifications arrived correctly
    }

    /**
     * test redundant actions performed on quiz exercises will result in bad request
     * */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testRedundantActionsBadRequest() throws Exception {
        // set-visible
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null);
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/set-visible", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        // start-now
        quizExercise = createQuizOnServer(ZonedDateTime.now().minusDays(1), null);
        assertThat(quizExercise.isStarted()).isTrue();
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/start-now", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        // open-for-practice
        quizExercise = createQuizOnServer(ZonedDateTime.now().minusDays(1), null);
        quizExercise.setIsOpenForPractice(true);
        quizExerciseRepository.save(quizExercise);
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/open-for-practice", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        // misspelled request
        quizExercise = createQuizOnServer(ZonedDateTime.now().minusDays(1), null);
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/lorem-ipsum", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * test that a quiz question with an explanation within valid length can be created
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testMultipleChoiceQuizExplanationLength_Valid() throws Exception {
        int validityThreshold = 500;

        QuizExercise quizExercise = createMultipleChoiceQuizExerciseDummy();
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        question.setExplanation("0".repeat(validityThreshold));

        QuizExercise response = request.postWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.CREATED);
        assertNotNull(response);
    }

    /**
     * test that a quiz question with an explanation without valid length can't be created
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testMultipleChoiceQuizExplanationLength_Invalid() throws Exception {
        int validityThreshold = 500;

        QuizExercise quizExercise = createMultipleChoiceQuizExerciseDummy();
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        question.setExplanation("0".repeat(validityThreshold + 1));

        request.postWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * test that a quiz question with an option with an explanation with valid length can be created
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testMultipleChoiceQuizOptionExplanationLength_Valid() throws Exception {
        int validityThreshold = 500;

        QuizExercise quizExercise = createMultipleChoiceQuizExerciseDummy();
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        question.getAnswerOptions().get(0).setExplanation("0".repeat(validityThreshold));

        QuizExercise response = request.postWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.CREATED);
        assertNotNull(response);
    }

    /**
     * test that a quiz question with an option with an explanation without valid length can't be created
     */
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testMultipleChoiceQuizOptionExplanationLength_Inalid() throws Exception {
        int validityThreshold = 500;

        QuizExercise quizExercise = createMultipleChoiceQuizExerciseDummy();
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        question.getAnswerOptions().get(0).setExplanation("0".repeat(validityThreshold + 1));

        request.postWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private QuizExercise createMultipleChoiceQuizExerciseDummy() {
        Course course = database.createCourse();
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(4).text("Q1");
        QuizExercise quizExercise = ModelFactory.generateQuizExercise(ZonedDateTime.now().plusHours(5), null, course);

        question.setScoringType(ScoringType.ALL_OR_NOTHING);
        question.getAnswerOptions().add(new AnswerOption().text("A").hint("H1").explanation("E1").isCorrect(true));
        question.getAnswerOptions().add(new AnswerOption().text("B").hint("H2").explanation("E2").isCorrect(false));
        question.setExplanation("Explanation");
        question.copyQuestionId();

        quizExercise.addQuestions(question);
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        quizExercise.setGradingInstructions(null);

        return quizExercise;
    }

    /**
     * Check that the general information of two exercises is equal.
     */
    private void checkQuizExercises(QuizExercise quizExercise, QuizExercise quizExercise2) {
        assertThat(quizExercise.getQuizQuestions()).as("Same amount of questions saved").hasSize(quizExercise2.getQuizQuestions().size());
        assertThat(quizExercise.getTitle()).as("Title saved correctly").isEqualTo(quizExercise2.getTitle());
        assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Number of attempts saved correctly").isEqualTo(quizExercise2.getAllowedNumberOfAttempts());
        assertThat(quizExercise.getMaxPoints()).as("Max score saved correctly").isEqualTo(quizExercise2.getMaxPoints());
        assertThat(quizExercise.getDuration()).as("Duration saved correctly").isEqualTo(quizExercise2.getDuration());
        assertThat(quizExercise.getType()).as("Type saved correctly").isEqualTo(quizExercise2.getType());
    }

    /**
     * Check that the general statistics of two exercises are equal.
     */
    private void checkStatistics(QuizExercise quizExercise, QuizExercise quizExercise2) {
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

        for (var quizQuestion : quizExercise.getQuizQuestions()) {
            var statistic = quizQuestion.getQuizQuestionStatistic();
            if (statistic instanceof MultipleChoiceQuestionStatistic mcStatistic) {
                assertThat(mcStatistic.getAnswerCounters()).isNotEmpty();
                for (var counter : mcStatistic.getAnswerCounters()) {
                    System.out.println("AnswerCounters: " + counter.toString());
                    System.out.println("MultipleChoiceQuestionStatistic: " + counter.getMultipleChoiceQuestionStatistic());
                }
            }
            else if (statistic instanceof DragAndDropQuestionStatistic dndStatistic) {
                assertThat(dndStatistic.getDropLocationCounters()).isNotEmpty();
                for (var counter : dndStatistic.getDropLocationCounters()) {
                    System.out.println("DropLocationCounters: " + counter.toString());
                    System.out.println("DragAndDropQuestionStatistic: " + counter.getDragAndDropQuestionStatistic());
                }
            }
            else if (statistic instanceof ShortAnswerQuestionStatistic saStatistic) {
                assertThat(saStatistic.getShortAnswerSpotCounters()).isNotEmpty();
                for (var counter : saStatistic.getShortAnswerSpotCounters()) {
                    System.out.println("ShortAnswerSpotCounters: " + counter.toString());
                    System.out.println("ShortAnswerQuestionStatistic: " + counter.getShortAnswerQuestionStatistic());
                }
            }
        }
    }

    /**
     * Check if a QuizExercise contains the correct information for students.
     * @param quizExercise QuizExercise to check
     */
    private void checkQuizExerciseForStudent(QuizExercise quizExercise) {
        assertThat(quizExercise.getQuizPointStatistic()).isNull();
        assertThat(quizExercise.getGradingInstructions()).isNull();
        assertThat(quizExercise.getGradingCriteria()).isEmpty();
        if (quizExercise.isStarted()) {
            for (QuizQuestion question : quizExercise.getQuizQuestions()) {
                assertThat(question.getExplanation()).isNull();
                assertThat(question.getQuizQuestionStatistic()).isNull();
            }
        }
        else {
            assertThat(quizExercise.getQuizQuestions()).hasSize(0);
        }

    }
}
