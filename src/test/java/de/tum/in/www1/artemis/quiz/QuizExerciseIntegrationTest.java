package de.tum.in.www1.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;
import static org.junit.jupiter.api.Assertions.*;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.service.scheduled.cache.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.util.ExerciseIntegrationTestUtils;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.QuizUtilService;
import de.tum.in.www1.artemis.web.rest.dto.QuizBatchJoinDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.websocket.QuizSubmissionWebsocketService;

class QuizExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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

    @Autowired
    private QuizUtilService quizUtilService;

    @Autowired
    private QuizBatchRepository quizBatchRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ExerciseIntegrationTestUtils exerciseIntegrationTestUtils;

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
    void init() {
        database.addUsers(15, 5, 1, 1);
        quizScheduleService.startSchedule(5 * 1000);
    }

    @AfterEach
    void tearDown() {
        // database.resetDatabase();
        quizScheduleService.stopSchedule();
        quizScheduleService.clearAllQuizData();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testCreateQuizExercise(QuizMode quizMode) throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, quizMode);

        // General assertions
        assertThat(quizExercise.getQuizQuestions()).as("Quiz questions were saved").hasSize(3);
        assertThat(quizExercise.getDuration()).as("Quiz duration was correctly set").isEqualTo(3600);
        assertThat(quizExercise.getDifficulty()).as("Quiz difficulty was correctly set").isEqualTo(DifficultyLevel.MEDIUM);

        // Quiz type specific assertions
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                assertThat(multipleChoiceQuestion.getAnswerOptions()).as("Multiple choice question answer options were saved").hasSize(2);
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
                assertThat(dragAndDropQuestion.getDropLocations()).as("Drag and drop question drop locations were saved").hasSize(3);
                assertThat(dragAndDropQuestion.getDragItems()).as("Drag and drop question drag items were saved").hasSize(3);
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
                assertThat(shortAnswerQuestion.getSpots()).as("Short answer question spots were saved").hasSize(2);
                assertThat(shortAnswerQuestion.getSolutions()).as("Short answer question solutions were saved").hasSize(2);
                assertThat(shortAnswerQuestion.getTitle()).as("Short answer question title is correct").isEqualTo("SA");
                assertThat(shortAnswerQuestion.getText()).as("Short answer question text is correct").isEqualTo("This is a long answer text");
                assertThat(shortAnswerQuestion.getPoints()).as("Short answer question score is correct").isEqualTo(2);

                List<ShortAnswerSpot> spots = shortAnswerQuestion.getSpots();
                assertThat(spots.get(0).getSpotNr()).as("Spot nr for spot is correct").isZero();
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCreateQuizExerciseForExam() throws Exception {
        quizExercise = createQuizOnServerForExam();

        // General assertions
        assertThat(quizExercise.getQuizQuestions()).as("Quiz questions were saved").hasSize(3);
        assertThat(quizExercise.getDuration()).as("Quiz duration was correctly set").isEqualTo(3600);
        assertThat(quizExercise.getDifficulty()).as("Quiz difficulty was correctly set").isEqualTo(DifficultyLevel.MEDIUM);

        // Quiz type specific assertions
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                assertThat(multipleChoiceQuestion.getAnswerOptions()).as("Multiple choice question answer options were saved").hasSize(2);
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
                assertThat(dragAndDropQuestion.getDropLocations()).as("Drag and drop question drop locations were saved").hasSize(3);
                assertThat(dragAndDropQuestion.getDragItems()).as("Drag and drop question drag items were saved").hasSize(3);
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
                assertThat(shortAnswerQuestion.getSpots()).as("Short answer question spots were saved").hasSize(2);
                assertThat(shortAnswerQuestion.getSolutions()).as("Short answer question solutions were saved").hasSize(2);
                assertThat(shortAnswerQuestion.getTitle()).as("Short answer question title is correct").isEqualTo("SA");
                assertThat(shortAnswerQuestion.getText()).as("Short answer question text is correct").isEqualTo("This is a long answer text");
                assertThat(shortAnswerQuestion.getPoints()).as("Short answer question score is correct").isEqualTo(2);

                List<ShortAnswerSpot> spots = shortAnswerQuestion.getSpots();
                assertThat(spots.get(0).getSpotNr()).as("Spot nr for spot is correct").isZero();
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createQuizExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        QuizExercise quizExercise = ModelFactory.generateQuizExerciseForExam(exerciseGroup);
        quizExercise.setCourse(exerciseGroup.getExam().getCourse());
        request.postWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createQuizExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        QuizExercise quizExercise = ModelFactory.generateQuizExerciseForExam(null);
        request.postWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createQuizExercise_InvalidMaxScore() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setMaxPoints(0.0);
        request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createQuizExercise_InvalidDates_badRequest() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now()));
        request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createQuizExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setMaxPoints(10.0);
        quizExercise.setBonusPoints(1.0);
        quizExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createQuizExercise_NotIncludedInvalidBonusPoints() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setMaxPoints(10.0);
        quizExercise.setBonusPoints(1.0);
        quizExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testEditQuizExercise(QuizMode quizMode) throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, quizMode);

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
                assertThat(multipleChoiceQuestion.getAnswerOptions()).as("Multiple choice question answer options were saved").hasSize(3);
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
                assertThat(dragAndDropQuestion.getDropLocations()).as("Drag and drop question drop locations were saved").hasSize(2);
                assertThat(dragAndDropQuestion.getDragItems()).as("Drag and drop question drag items were saved").hasSize(2);
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
                assertThat(shortAnswerQuestion.getSpots()).as("Short answer question spots were saved").hasSize(1);
                assertThat(shortAnswerQuestion.getSolutions()).as("Short answer question solutions were saved").hasSize(1);
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testEditQuizExercise_SingleChoiceMC_AllOrNothing() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        mc.setSingleChoice(true);
        mc.setScoringType(ScoringType.ALL_OR_NOTHING);
        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.OK);
        assertThat(quizExercise.getQuizQuestions().get(0).getScoringType()).as("Scoring type was changed").isEqualTo(ScoringType.ALL_OR_NOTHING);

        // multiple correct answers are not allowed
        mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        mc.getAnswerOptions().get(1).setIsCorrect(true);
        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testEditQuizExercise_SingleChoiceMC_Proportional() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        mc.setSingleChoice(true);
        mc.setScoringType(ScoringType.PROPORTIONAL_WITHOUT_PENALTY);
        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testEditQuizExercise_SingleChoiceMC_ProportionalPenalty() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        mc.setSingleChoice(true);
        mc.setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);
        quizExercise = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testEditQuizExerciseForExam() throws Exception {
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
                assertThat(multipleChoiceQuestion.getAnswerOptions()).as("Multiple choice question answer options were saved").hasSize(3);
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
                assertThat(dragAndDropQuestion.getDropLocations()).as("Drag and drop question drop locations were saved").hasSize(2);
                assertThat(dragAndDropQuestion.getDragItems()).as("Drag and drop question drag items were saved").hasSize(2);
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
                assertThat(shortAnswerQuestion.getSpots()).as("Short answer question spots were saved").hasSize(1);
                assertThat(shortAnswerQuestion.getSolutions()).as("Short answer question solutions were saved").hasSize(1);
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateQuizExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateQuizExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setCourse(null);

        request.putWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateQuizExercise_InvalidDates_badRequest() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now()));
        request.putWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateQuizExercise_convertFromCourseToExamExercise_badRequest() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);

        quizExercise.setCourse(null);
        quizExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateQuizExercise_convertFromExamToCourseExercise_badRequest() throws Exception {
        Course course = database.addEmptyCourse();
        database.addExerciseGroupWithExamAndCourse(true);
        QuizExercise quizExercise = createQuizOnServerForExam();

        quizExercise.setExerciseGroup(null);
        quizExercise.setCourse(course);

        request.putWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    private QuizExercise createQuizOnServer(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) throws Exception {
        Course course = database.createCourse();

        QuizExercise quizExercise = database.createQuiz(course, releaseDate, dueDate, quizMode);
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
        return quizExerciseDatabase; // TODO: @sleiss: Check if this is fine
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

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testDeleteQuizExercise(QuizMode quizMode) throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, quizMode);

        assertThat(quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is created correctly").isNotNull();
        request.delete("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK);
        assertThat(quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is deleted correctly").isNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testDeleteQuizExerciseWithSubmittedAnswers(QuizMode quizMode) throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(1), quizMode);

        assertThat(quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is created correctly").isNotNull();

        final var username = "student1";
        final Principal principal = () -> username;
        QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, 1, true, null);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        quizUtilService.prepareBatchForSubmitting(quizExercise, authentication, SecurityUtils.makeAuthorizationObject(username));
        quizSubmissionWebsocketService.saveSubmission(quizExercise.getId(), quizSubmission, principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Quiz submissions are not yet in database
        assertThat(quizSubmissionRepository.findAll()).isEmpty();

        quizScheduleService.processCachedQuizSubmissions();

        // Quiz submissions are now in database
        assertThat(quizSubmissionRepository.findAll()).hasSize(1);

        request.delete("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK);
        assertThat(quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is deleted correctly").isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateNotExistingQuizExercise() throws Exception {
        Course course = database.createCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        QuizExercise quizExerciseServer = request.putWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.CREATED);
        assertThat(quizExerciseServer.getId()).isNotNull();
        assertThat(quizExerciseServer.getQuizQuestions()).hasSize(quizExercise.getQuizQuestions().size());
        assertThat(quizExerciseServer.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(quizExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertThat(quizExerciseServer.getMaxPoints()).isEqualTo(quizExercise.getMaxPoints());
        // TODO: check more values for equality
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateRunningQuizExercise() throws Exception {
        Course course = database.createCourse();
        // create QuizExercise that already started
        QuizExercise startedQuizExercise = database.createQuiz(course, ZonedDateTime.now().minusHours(1), null, QuizMode.SYNCHRONIZED);
        QuizExercise startedQuizExerciseServer = request.postWithResponseBody("/api/quiz-exercises", startedQuizExercise, QuizExercise.class, HttpStatus.CREATED);

        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) startedQuizExerciseServer.getQuizQuestions().get(0);
        mc.getAnswerOptions().remove(0);
        mc.getAnswerOptions().add(new AnswerOption().text("C").hint("H3").explanation("E3").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("D").hint("H4").explanation("E4").isCorrect(true));

        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz-exercises", startedQuizExerciseServer, QuizExercise.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedQuizExercise).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCreateExistingQuizExercise() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

        QuizExercise newQuizExerciseServer = request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        assertThat(newQuizExerciseServer).isNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testGetQuizExercise(QuizMode quizMode) throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, quizMode);

        QuizExercise quizExerciseGet = request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK, QuizExercise.class);
        checkQuizExercises(quizExercise, quizExerciseGet);
        // Start Date picker at Quiz Edit page should be populated correctly
        if (quizMode == QuizMode.SYNCHRONIZED) {
            assertThat(quizExerciseGet.getQuizBatches()).isNotEmpty();
        }

        // get all exercises for a course
        List<QuizExercise> allQuizExercisesForCourse = request.getList("/api/courses/" + quizExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/quiz-exercises",
                HttpStatus.OK, QuizExercise.class);
        assertThat(allQuizExercisesForCourse).hasSize(1);
        assertThat(allQuizExercisesForCourse.get(0)).isEqualTo(quizExercise);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void testGetQuizExercise_asStudent(QuizMode quizMode) throws Exception {
        Course course = database.createCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusHours(5), null, quizMode);
        quizExercise.setDuration(360);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now().plusHours(5)));
        quizExercise = quizExerciseService.save(quizExercise);

        // get not started exercise for students
        QuizExercise quizExerciseForStudent_notStarted = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        checkQuizExerciseForStudent(quizExerciseForStudent_notStarted);

        // get started exercise for students
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now().minusMinutes(5)));
        quizExercise = quizExerciseService.save(quizExercise);
        assertThat(quizExercise.getQuizBatches()).allMatch(QuizBatch::isStarted);
        assertThat(quizExercise.getQuizBatches()).allMatch(QuizBatch::isSubmissionAllowed);

        QuizExercise quizExerciseForStudent_Started = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        checkQuizExerciseForStudent(quizExerciseForStudent_Started);

        // get finished exercise for students
        quizExerciseService.endQuiz(quizExercise, ZonedDateTime.now().minusMinutes(2));
        quizExercise = quizExerciseService.save(quizExercise);
        assertThat(quizExercise.getQuizBatches()).allMatch(QuizBatch::isStarted);
        assertThat(quizExercise.getQuizBatches()).noneMatch(QuizBatch::isSubmissionAllowed);

        QuizExercise quizExerciseForStudent_Finished = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        checkQuizExerciseForStudent(quizExerciseForStudent_Finished);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testSetQuizBatchStartTimeForNonSynchronizedQuizExercises_asStudent() throws Exception {
        Course course = database.createCourse();
        QuizExercise quizExercise = database.createQuizWithQuizBatchedExercises(course, ZonedDateTime.now().minusHours(5), null, QuizMode.INDIVIDUAL);
        quizExercise.setDuration(400);
        // get started exercise for students
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now().minusMinutes(5)));
        quizExercise = quizExerciseService.save(quizExercise);

        // when exercise due date is null
        QuizExercise quizExerciseForStudent = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        assertThat(quizExerciseForStudent.getQuizBatches().stream().allMatch(quizBatch -> quizBatch.getStartTime() != null)).isTrue();

        // when exercise due date is later than now
        quizExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        quizExerciseService.save(quizExercise);
        quizExerciseForStudent = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        assertThat(quizExerciseForStudent.getQuizBatches().stream().allMatch(quizBatch -> quizBatch.getStartTime() != null)).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateQuizExerciseWithoutQuizBatchForSynchronizedQuiz_asStudent() throws Exception {
        Course course = database.createCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now().minusHours(4), null, QuizMode.SYNCHRONIZED);
        quizExercise.setDuration(400);
        quizExercise.setQuizBatches(null);
        quizExercise = quizExerciseService.save(quizExercise);

        QuizExercise quizExerciseForStudent = request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.OK, QuizExercise.class);
        assertThat(quizExerciseForStudent.getQuizBatches()).hasSize(1);
        checkQuizExerciseForStudent(quizExerciseForStudent);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetQuizExercisesForExam() throws Exception {
        quizExercise = createQuizOnServerForExam();
        var examId = quizExercise.getExerciseGroup().getExam().getId();
        List<QuizExercise> quizExercises = request.getList("/api/" + examId + "/quiz-exercises", HttpStatus.OK, QuizExercise.class);
        assertThat(quizExercises).as("Quiz exercise was retrieved").hasSize(1);
        assertThat(quizExercise.getId()).as("Quiz exercise with the right id was retrieved").isEqualTo(quizExercises.get(0).getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetExamQuizExercise() throws Exception {
        quizExercise = createQuizOnServerForExam();

        QuizExercise quizExerciseGet = request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK, QuizExercise.class);
        checkQuizExercises(quizExercise, quizExerciseGet);

        assertThat(quizExerciseGet).as("Quiz exercise was retrieved").isEqualTo(quizExercise).isNotNull();
        assertThat(quizExerciseGet.getId()).as("Quiz exercise with the right id was retrieved").isEqualTo(quizExerciseGet.getId());
        assertThat(quizExerciseGet.getQuizBatches()).isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetExamQuizExercise_asTutor_forbidden() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        quizExercise = database.createQuizForExam(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);
        request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.FORBIDDEN, QuizExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testInstructorSearchTermMatchesId() throws Exception {
        testSearchTermMatchesId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminSearchTermMatchesId() throws Exception {
        testSearchTermMatchesId();
    }

    private void testSearchTermMatchesId() throws Exception {
        final Course course = database.addEmptyCourse();
        final var now = ZonedDateTime.now();
        QuizExercise exercise = ModelFactory.generateQuizExercise(now.minusDays(1), now.minusHours(2), QuizMode.INDIVIDUAL, course);
        exercise.setTitle("LoremIpsum");
        exercise = quizExerciseRepository.save(exercise);

        final var searchTerm = database.configureSearch(exercise.getId().toString());
        final var searchResult = request.get("/api/quiz-exercises", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchTerm));
        assertThat(searchResult.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCourseAndExamFiltersAsInstructor() throws Exception {
        setupFilterTestCase();
        exerciseIntegrationTestUtils.testCourseAndExamFilters("/api/quiz-exercises/");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCourseAndExamFiltersAsAdmin() throws Exception {
        setupFilterTestCase();
        exerciseIntegrationTestUtils.testCourseAndExamFilters("/api/quiz-exercises/");
    }

    private void setupFilterTestCase() {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        quizExercise = database.createQuizForExam(exerciseGroup);
        quizExercise.setTitle("Ankh-Morpork");
        quizExerciseRepository.save(quizExercise);

        final Course course = database.addEmptyCourse();
        final var now = ZonedDateTime.now();
        QuizExercise exercise = ModelFactory.generateQuizExercise(now.minusDays(1), now.minusHours(2), QuizMode.INDIVIDUAL, course);
        exercise.setTitle("Ankh");
        quizExerciseRepository.save(exercise);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testRecalculateStatistics() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

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

        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getPointCounters()).hasSize(10);
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
                assertThat(pointCounter.getRatedCounter()).isZero();
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

        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getPointCounters()).hasSize(10);
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
                assertThat(pointCounter.getRatedCounter()).isZero();
            }
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReevaluateStatistics() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusSeconds(5), null, QuizMode.SYNCHRONIZED);

        // we expect a bad request because the quiz has not ended yet
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExerciseService.endQuiz(quizExercise, ZonedDateTime.now().minusMinutes(1));
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

        assertThat(submittedAnswerRepository.findAll()).hasSize((numberOfParticipants - 2) * 3);

        // submission with everything selected
        QuizSubmission quizSubmission = database.generateSpecialSubmissionWithResult(quizExercise, true, ZonedDateTime.now().minusHours(1), true);
        database.addSubmission(quizExercise, quizSubmission, "student1");
        database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);

        // submission with nothing selected
        quizSubmission = database.generateSpecialSubmissionWithResult(quizExercise, true, ZonedDateTime.now().minusHours(1), false);
        database.addSubmission(quizExercise, quizSubmission, "student5");
        database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);

        assertThat(studentParticipationRepository.findAll()).hasSize(numberOfParticipants);
        assertThat(resultRepository.findAll()).hasSize(numberOfParticipants);
        assertThat(quizSubmissionRepository.findAll()).hasSize(numberOfParticipants);
        assertThat(submittedAnswerRepository.findAll()).hasSize(numberOfParticipants * 3);

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
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSameSizeAs(quizExercise.getQuizPointStatistic().getPointCounters());
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
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSameSizeAs(quizExercise.getQuizPointStatistic().getPointCounters());
        System.out.println("QuizPointStatistic after 2nd re-evaluate: " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());
        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(2.0, pc20, 5.0, pc20, 6.0, pc50, 9.0, pc10));

        // delete a question and reevaluate
        quizExercise.getQuizQuestions().remove(1);

        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.OK);
        // load the exercise again after it was re-evaluated
        quizExerciseWithReevaluatedStatistics = request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK, QuizExercise.class);

        assertThat(quizExerciseWithReevaluatedStatistics.getQuizQuestions()).hasSize(2);

        // max score should be less
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSize(quizExercise.getQuizPointStatistic().getPointCounters().size() - 3);
        System.out.println("QuizPointStatistic after 3rd re-evaluate: " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());
        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(2.0, pc40, 6.0, pc60));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReevaluateStatistics_Practice() throws Exception {

        quizExercise = createQuizOnServer(ZonedDateTime.now().plusSeconds(5), null, QuizMode.SYNCHRONIZED);
        // use the exact other scoring types to cover all combinations in the tests
        quizExercise.getQuizQuestions().get(0).setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);   // MC
        quizExercise.getQuizQuestions().get(1).setScoringType(ScoringType.ALL_OR_NOTHING);              // DnD
        quizExercise.getQuizQuestions().get(2).setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);   // SA

        // we expect a bad request because the quiz has not ended yet
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate/", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        quizExercise.setDuration(3600);
        quizExerciseService.endQuiz(quizExercise, ZonedDateTime.now().minusHours(1));
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

        assertThat(studentParticipationRepository.findAll()).hasSize(10);
        assertThat(resultRepository.findAll()).hasSize(10);

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
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSameSizeAs(quizExercise.getQuizPointStatistic().getPointCounters());

        System.out.println("QuizPointStatistic after 1st re-evaluate: " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(0.0, pc02, 3.0, pc02, 4.0, pc03, 6.0, pc02, 7.0, pc01));

        // set a question invalid and reevaluate
        quizExerciseWithReevaluatedStatistics.getQuizQuestions().get(2).setInvalid(true);

        quizExerciseWithReevaluatedStatistics = request.putWithResponseBody("/api/quiz-exercises/" + quizExerciseWithReevaluatedStatistics.getId() + "/re-evaluate/",
                quizExerciseWithReevaluatedStatistics, QuizExercise.class, HttpStatus.OK);

        // several students should get a higher score
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSameSizeAs(quizExercise.getQuizPointStatistic().getPointCounters());
        System.out.println("QuizPointStatistic after 2nd re-evaluate: " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(2.0, pc02, 5.0, pc02, 6.0, pc05, 9.0, pc01));

        // delete a question and reevaluate
        quizExerciseWithReevaluatedStatistics.getQuizQuestions().remove(1);

        quizExerciseWithReevaluatedStatistics = request.putWithResponseBody("/api/quiz-exercises/" + quizExerciseWithReevaluatedStatistics.getId() + "/re-evaluate/",
                quizExerciseWithReevaluatedStatistics, QuizExercise.class, HttpStatus.OK);

        // max score should be less
        System.out.println("QuizPointStatistic after 3rd re-evaluate: " + quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSize(quizExercise.getQuizPointStatistic().getPointCounters().size() - 3);

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
                assertThat(pointCounter.getRatedCounter()).as(pointCounter.getPoints() + " should have a rated counter of 0").isZero();
                assertThat(pointCounter.getUnRatedCounter()).as(pointCounter.getPoints() + " should have a rated counter of 0").isZero();
            }
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateQuizQuestionWithMoreSolutions() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().minusHours(5), ZonedDateTime.now().minusHours(2), QuizMode.SYNCHRONIZED);
        QuizQuestion question = quizExercise.getQuizQuestions().get(2);

        if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
            // demonstrate that the initial shortAnswerQuestion has 2 correct mappings and 2 solutions
            assertThat(shortAnswerQuestion.getCorrectMappings()).hasSize(2);
            assertThat(shortAnswerQuestion.getCorrectMappings()).hasSize(2);

            // add a solution with a mapping onto spot number 0
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
        assertThat(receivedShortAnswerQuestion.getSolutions()).hasSize(3);
        assertThat(receivedShortAnswerQuestion.getCorrectMappings()).hasSize(3);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testPerformStartNow() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExercise = quizExerciseService.save(quizExercise);

        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/start-now", quizExercise, QuizExercise.class,
                HttpStatus.OK);
        long millis = ChronoUnit.MILLIS.between(updatedQuizExercise.getQuizBatches().stream().findAny().get().getStartTime(), ZonedDateTime.now());
        // actually the two dates should be "exactly" the same, but for the sake of slow CI testing machines and to prevent flaky tests, we live with the following rule
        assertThat(millis).isCloseTo(0, byLessThan(2000L));
        assertThat(updatedQuizExercise.getQuizBatches()).allMatch(QuizBatch::isStarted);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @EnumSource(value = QuizMode.class, names = { "SYNCHRONIZED" }, mode = EnumSource.Mode.EXCLUDE)
    void testPerformStartNow_invalidMode(QuizMode quizMode) throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, quizMode);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExercise = quizExerciseService.save(quizExercise);

        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/start-now", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testPerformSetVisible(QuizMode quizMode) throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().minusHours(5), null, quizMode);

        // we expect a bad request because the quiz is already visible
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/set-visible", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        quizExercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        quizExerciseService.save(quizExercise);
        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/set-visible", quizExercise, QuizExercise.class,
                HttpStatus.OK);
        assertThat(updatedQuizExercise.isVisibleToStudents()).isTrue();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testPerformOpenForPractice(QuizMode quizMode) throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, quizMode);

        // we expect a bad request because the quiz has not ended yet
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/open-for-practice", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        quizExercise.setDuration(180);
        quizExerciseService.endQuiz(quizExercise, ZonedDateTime.now().minusMinutes(2));
        quizExerciseService.save(quizExercise);
        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/open-for-practice", quizExercise, QuizExercise.class,
                HttpStatus.OK);
        assertThat(updatedQuizExercise.isIsOpenForPractice()).isTrue();
    }

    private static List<Arguments> testPerformJoin_args() {
        var now = ZonedDateTime.now();
        var longPast = now.minusHours(4);
        var past = now.minusMinutes(30);
        var future = now.plusMinutes(30);
        var longFuture = now.plusHours(4);

        var batchLongPast = new QuizBatch();
        batchLongPast.setStartTime(longPast);
        batchLongPast.setPassword("12345678");
        var batchPast = new QuizBatch();
        batchPast.setStartTime(past);
        batchPast.setPassword("12345678");
        var batchFuture = new QuizBatch();
        batchFuture.setStartTime(future);
        batchFuture.setPassword("12345678");

        return List.of(Arguments.of(QuizMode.SYNCHRONIZED, future, null, null, null, HttpStatus.FORBIDDEN), // start in future
                Arguments.of(QuizMode.SYNCHRONIZED, past, null, null, null, HttpStatus.NOT_FOUND), // synchronized
                Arguments.of(QuizMode.SYNCHRONIZED, past, null, null, "12345678", HttpStatus.NOT_FOUND), // synchronized
                Arguments.of(QuizMode.SYNCHRONIZED, longPast, past, null, null, HttpStatus.FORBIDDEN), // due date passed
                Arguments.of(QuizMode.INDIVIDUAL, future, null, null, null, HttpStatus.FORBIDDEN), // start in future
                Arguments.of(QuizMode.INDIVIDUAL, longPast, null, null, null, HttpStatus.OK), Arguments.of(QuizMode.INDIVIDUAL, longPast, longFuture, null, null, HttpStatus.OK),
                Arguments.of(QuizMode.INDIVIDUAL, longPast, future, null, null, HttpStatus.OK), // NOTE: reduced working time because of due date
                Arguments.of(QuizMode.INDIVIDUAL, longPast, past, null, null, HttpStatus.FORBIDDEN), // after due date
                Arguments.of(QuizMode.BATCHED, future, null, null, null, HttpStatus.FORBIDDEN), // start in future
                Arguments.of(QuizMode.BATCHED, longPast, null, null, null, HttpStatus.NOT_FOUND), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, longFuture, null, null, HttpStatus.NOT_FOUND), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, future, null, null, HttpStatus.NOT_FOUND), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, past, null, null, HttpStatus.FORBIDDEN), // after due date
                Arguments.of(QuizMode.BATCHED, longPast, null, batchLongPast, null, HttpStatus.NOT_FOUND), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchPast, null, HttpStatus.NOT_FOUND), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchFuture, null, HttpStatus.NOT_FOUND), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchLongPast, "87654321", HttpStatus.NOT_FOUND), // wrong pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchPast, "87654321", HttpStatus.NOT_FOUND), // wrong pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchFuture, "87654321", HttpStatus.NOT_FOUND), // wrong pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchLongPast, "12345678", HttpStatus.NOT_FOUND), // batch done
                Arguments.of(QuizMode.BATCHED, longPast, null, batchPast, "12345678", HttpStatus.OK), // NOTE: reduced working time because batch had already started
                Arguments.of(QuizMode.BATCHED, longPast, null, batchFuture, "12345678", HttpStatus.OK));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @MethodSource(value = "testPerformJoin_args")
    void testPerformJoin(QuizMode quizMode, ZonedDateTime release, ZonedDateTime due, QuizBatch batch, String password, HttpStatus result) throws Exception {
        quizExercise = createQuizOnServer(release, due, quizMode);
        if (batch != null) {
            batch.setQuizExercise(quizExercise);
            quizBatchRepository.save(batch);
        }

        // switch to student
        SecurityContextHolder.getContext().setAuthentication(SecurityUtils.makeAuthorizationObject("student1"));

        request.postWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(password), QuizBatch.class, result);

        if (result == HttpStatus.OK) {
            // if joining was successful repeating the request should fail, otherwise with the same reason as the first attempt
            result = HttpStatus.BAD_REQUEST;
        }

        request.postWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(password), QuizBatch.class, result);
    }

    /**
     * test non-instructors cant create quiz exercises
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCreateQuizExerciseAsTutorForbidden() throws Exception {
        final Course course = database.createCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now(), ZonedDateTime.now().plusHours(5), QuizMode.SYNCHRONIZED);
        // remove instructor rights
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        request.postWithResponseBody("/api/quiz-exercises", quizExercise, QuizExercise.class, HttpStatus.FORBIDDEN);
        assertThat(course.getExercises()).isEmpty();
    }

    /**
     * test non-instructors cant get all quiz exercises
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAllQuizExercisesAsStudentForbidden() throws Exception {
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
     * test non-instructors can't perform start-now, set-visible or open-for-practice on quiz exercises
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testPerformPutActionAsTutorForbidden() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        assertThat(course.getExercises()).isNotEmpty();
        quizExercise = quizExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        assertThat(quizExercise.isIsOpenForPractice()).isFalse();
        // remove instructor rights
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);

        request.put("/api/quiz-exercises/" + quizExercise.getId() + "/open-for-practice", quizExercise, HttpStatus.FORBIDDEN);
        assertThat(quizExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0).isIsOpenForPractice()).isFalse();
    }

    /**
     * test non-instructors can't see the exercise if it is not set to visible
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testViewQuizExerciseAsStudentNotVisible() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        quizExercise = quizExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        assertThat(quizExercise.isVisibleToStudents()).isFalse();
        // remove instructor rights in course
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        request.get("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.FORBIDDEN, QuizExercise.class);
    }

    /**
     * test non-instructors cant delete an exercise
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteQuizExerciseAsNonInstructor() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        quizExercise = quizExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testRecalculateStatisticsAsNonInstructor() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        quizExercise = quizExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        // remove instructor rights in course
        User user = database.getUserByLogin("instructor1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        request.get("/api/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", HttpStatus.FORBIDDEN, QuizExercise.class);
    }

    /**
     * test students not in course can't get quiz exercises
     * */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetQuizExerciseForStudentNotInCourseForbiden() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        quizExercise = quizExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        // remove instructor rights in course
        User user = database.getUserByLogin("student1");
        user.setGroups(Collections.emptySet());
        userRepo.save(user);
        request.get("/api/quiz-exercises/" + quizExercise.getId() + "/for-student", HttpStatus.FORBIDDEN, QuizExercise.class);
    }

    /**
     * test non-instructors in this course cant re-evaluate quiz exercises
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateQuizAsNonInstructorForbidden() throws Exception {
        final Course course = database.createCourse();
        quizExercise = database.createQuiz(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusHours(1), QuizMode.SYNCHRONIZED);
        quizExercise.setTitle("Titel");
        quizExercise.setDuration(200);
        assertThat(quizExercise.isValid()).as("is not valid!").isTrue();
        assertThat(quizExercise.isExamExercise()).as("Is an exam exercise!").isFalse();
        assertThat(quizExercise.isQuizEnded()).as("Is not ended!").isTrue();
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUnfinishedExamReEvaluateBadRequest() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        quizExercise = database.createQuizForExam(exerciseGroup);
        quizExercise.setTitle("Titel");
        quizExercise.setDuration(200);
        assertThat(quizExercise.isValid()).as("is not valid!").isTrue();
        quizExerciseRepository.save(quizExercise);
        request.put("/api/quiz-exercises/" + quizExercise.getId() + "/re-evaluate", quizExercise, HttpStatus.BAD_REQUEST);
    }

    /**
     * test non editor cant update quiz exercise
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizExerciseAsNonEditorForbidden() throws Exception {
        final Course course = database.createCourse();
        quizExercise = database.createQuiz(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusHours(1), QuizMode.SYNCHRONIZED);
        quizExercise.setTitle("Titel");
        quizExercise.setDuration(200);
        assertThat(quizExercise.isValid()).as("is not valid!").isTrue();
        assertThat(quizExercise.isExamExercise()).as("Is an exam exercise!").isFalse();
        assertThat(quizExercise.isQuizEnded()).as("Is not ended!").isTrue();
        course.addExercises(quizExercise);
        courseRepo.save(course);
        quizExerciseRepository.save(quizExercise);
        // change some stuff
        quizExercise.setTitle("new Titel");
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizExerciseInvalidBadRequest() throws Exception {
        final Course course = database.createCourse();
        quizExercise = database.createQuiz(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusHours(1), QuizMode.SYNCHRONIZED);
        quizExercise.setTitle("Titel");
        quizExercise.setDuration(200);
        assertThat(quizExercise.isValid()).as("is not valid!").isTrue();
        assertThat(quizExercise.isExamExercise()).as("Is an exam exercise!").isFalse();
        assertThat(quizExercise.isQuizEnded()).as("Is not ended!").isTrue();
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizExerciseWithNotificationText() throws Exception {
        quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

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
     * test import quiz exercise to same course and check if fields are correctly set for import
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importQuizExerciseToSameCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course = database.addEmptyCourse();
        quizExercise = database.createQuiz(course, now.plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);

        QuizExercise changedQuiz = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(changedQuiz).isNotNull();
        changedQuiz.setTitle("New title");
        changedQuiz.setReleaseDate(now);
        QuizExercise importedExercise = request.postWithResponseBody("/api/quiz-exercises/import/" + changedQuiz.getId(), changedQuiz, QuizExercise.class, HttpStatus.CREATED);

        assertThat(importedExercise.getId()).as("Imported exercise has different id").isNotEqualTo(quizExercise.getId());
        assertThat(importedExercise.getTitle()).as("Imported exercise has updated title").isEqualTo("New title");
        assertThat(importedExercise.getReleaseDate()).as("Imported exercise has updated release data").isEqualTo(now);
        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("Imported exercise has same course")
                .isEqualTo(quizExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertThat(importedExercise.getQuizQuestions().size()).as("Imported exercise has same number of questions").isEqualTo(quizExercise.getQuizQuestions().size());
    }

    /**
     * test import quiz exercise to a different course
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importQuizExerciseFromCourseToCourseT() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        quizExercise = database.createQuiz(course1, now.plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        quizExercise.setCourse(course2);

        QuizExercise importedExercise = request.postWithResponseBody("/api/quiz-exercises/import/" + quizExercise.getId(), quizExercise, QuizExercise.class, HttpStatus.CREATED);

        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember()).as("Quiz was imported for different course").isEqualTo(course2);
    }

    /**
     * test import quiz exercise from a course to an exam
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importQuizExerciseFromCourseToExam() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        quizExercise = database.createQuiz(course1, now.plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        quizExercise.setReleaseDate(null);
        quizExercise.setCourse(null);
        quizExercise.setDueDate(null);
        quizExercise.setAssessmentDueDate(null);
        quizExercise.setQuizBatches(new HashSet<>());
        quizExercise.setExerciseGroup(exerciseGroup1);

        QuizExercise importedExercise = request.postWithResponseBody("/api/quiz-exercises/import/" + quizExercise.getId(), quizExercise, QuizExercise.class, HttpStatus.CREATED);

        assertThat(importedExercise.getExerciseGroup()).as("Quiz was imported for different exercise group").isEqualTo(exerciseGroup1);
    }

    /**
     * test import quiz exercise to exam with invalid roles
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "TA")
    void importQuizExerciseFromCourseToExam_forbidden() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        quizExercise = database.createQuiz(course1, now.plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        quizExercise.setCourse(null);
        quizExercise.setExerciseGroup(exerciseGroup1);

        request.postWithResponseBody("/api/quiz-exercises/import/" + quizExercise.getId(), quizExercise, QuizExercise.class, HttpStatus.FORBIDDEN);
    }

    /**
     * test import quiz exercise from exam to course
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importQuizExerciseFromExamToCourse() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        quizExercise = database.createQuizForExam(exerciseGroup1);
        Course course1 = database.addEmptyCourse();
        quizExerciseService.save(quizExercise);
        quizExercise.setCourse(course1);
        quizExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/quiz-exercises/import/" + quizExercise.getId(), quizExercise, QuizExercise.class, HttpStatus.CREATED);
    }

    /**
     * test import quiz exercise from exam to course with invalid roles
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "TA")
    void importQuizExerciseFromExamToCourse_forbidden() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        quizExercise = database.createQuizForExam(exerciseGroup1);
        Course course1 = database.addEmptyCourse();
        quizExerciseService.save(quizExercise);
        quizExercise.setCourse(course1);
        quizExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/quiz-exercises/import/" + quizExercise.getId(), quizExercise, QuizExercise.class, HttpStatus.FORBIDDEN);
    }

    /**
     * test import quiz exercise from one exam to a different exam
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importQuizExerciseFromExamToExam() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        ExerciseGroup exerciseGroup2 = database.addExerciseGroupWithExamAndCourse(true);
        quizExercise = database.createQuizForExam(exerciseGroup1);
        quizExerciseService.save(quizExercise);
        quizExercise.setExerciseGroup(exerciseGroup2);

        QuizExercise importedExercise = request.postWithResponseBody("/api/quiz-exercises/import/" + quizExercise.getId(), quizExercise, QuizExercise.class, HttpStatus.CREATED);

        assertThat(importedExercise.getExerciseGroup()).as("Quiz was imported for different exercise group").isEqualTo(exerciseGroup2);
    }

    /**
     * test import quiz exercise with a bad request (no course or exam)
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToCourse_badRequest() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        quizExercise = database.createQuiz(course1, now.plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        quizExercise.setCourse(null);

        request.postWithResponseBody("/api/quiz-exercises/import/" + quizExercise.getId(), quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * test import quiz exercise with changed team mode
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testImportQuizExercise_team_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        quizExercise = database.createQuiz(course1, now.plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);

        QuizExercise changedQuiz = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(changedQuiz).isNotNull();
        changedQuiz.setMode(ExerciseMode.TEAM);

        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(changedQuiz);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        changedQuiz.setTeamAssignmentConfig(teamAssignmentConfig);
        changedQuiz.setCourse(course2);
        changedQuiz.setMaxPoints(1.0);

        changedQuiz = request.postWithResponseBody("/api/quiz-exercises/import/" + quizExercise.getId(), changedQuiz, QuizExercise.class, HttpStatus.CREATED);

        assertEquals(course2.getId(), changedQuiz.getCourseViaExerciseGroupOrCourseMember().getId(), course2.getId());
        assertEquals(ExerciseMode.TEAM, changedQuiz.getMode());
        assertEquals(teamAssignmentConfig.getMinTeamSize(), changedQuiz.getTeamAssignmentConfig().getMinTeamSize());
        assertEquals(teamAssignmentConfig.getMaxTeamSize(), changedQuiz.getTeamAssignmentConfig().getMaxTeamSize());
        assertEquals(0, teamRepository.findAllByExerciseIdWithEagerStudents(changedQuiz, null).size());

        quizExercise = quizExerciseRepository.findById(quizExercise.getId()).get();
        assertEquals(course1.getId(), quizExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertEquals(ExerciseMode.INDIVIDUAL, quizExercise.getMode());
        assertNull(quizExercise.getTeamAssignmentConfig());
        assertEquals(0, teamRepository.findAllByExerciseIdWithEagerStudents(quizExercise, null).size());
    }

    /**
     * test import quiz exercise with changed team mode
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testImportQuizExercise_individual_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        quizExercise = database.createQuiz(course1, now.plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExercise.setMode(ExerciseMode.TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(quizExercise);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        quizExercise.setTeamAssignmentConfig(teamAssignmentConfig);
        quizExercise.setCourse(course1);  // remove line

        quizExercise = quizExerciseService.save(quizExercise);
        var team = new Team();
        team.setShortName("t" + UUID.randomUUID().toString().substring(0, 3));
        teamRepository.save(quizExercise, team);

        QuizExercise changedQuiz = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(changedQuiz).isNotNull();
        changedQuiz.setMode(ExerciseMode.INDIVIDUAL);
        changedQuiz.setCourse(course2);
        changedQuiz.setMaxPoints(1.0);

        changedQuiz = request.postWithResponseBody("/api/quiz-exercises/import/" + quizExercise.getId(), changedQuiz, QuizExercise.class, HttpStatus.CREATED);

        assertEquals(course2.getId(), changedQuiz.getCourseViaExerciseGroupOrCourseMember().getId(), course2.getId());
        assertEquals(ExerciseMode.INDIVIDUAL, changedQuiz.getMode());
        assertNull(changedQuiz.getTeamAssignmentConfig());
        assertEquals(0, teamRepository.findAllByExerciseIdWithEagerStudents(changedQuiz, null).size());

        quizExercise = quizExerciseRepository.findById(quizExercise.getId()).get();
        assertEquals(course1.getId(), quizExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertEquals(ExerciseMode.TEAM, quizExercise.getMode());
        assertEquals(1, teamRepository.findAllByExerciseIdWithEagerStudents(quizExercise, null).size());
    }

    /**
     * test import quiz exercise with changed quiz mode
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testImportQuizExerciseChangeQuizMode() throws Exception {
        var now = ZonedDateTime.now();
        Course course = database.addEmptyCourse();
        quizExercise = database.createQuiz(course, now.plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);

        QuizExercise changedQuiz = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(changedQuiz).isNotNull();
        changedQuiz.setQuizMode(QuizMode.INDIVIDUAL);
        changedQuiz.setReleaseDate(now);
        QuizExercise importedExercise = request.postWithResponseBody("/api/quiz-exercises/import/" + changedQuiz.getId(), changedQuiz, QuizExercise.class, HttpStatus.CREATED);

        assertThat(importedExercise.getId()).as("Imported exercise has different id").isNotEqualTo(quizExercise.getId());
        assertThat(importedExercise.getQuizMode()).as("Imported exercise has different quiz mode").isEqualTo(QuizMode.INDIVIDUAL);
        assertThat(importedExercise.getReleaseDate()).as("Imported exercise has updated release data").isEqualTo(now);
    }

    /**
     * test redundant actions performed on quiz exercises will result in bad request
     * */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testRedundantActionsBadRequest() throws Exception {
        // set-visible
        quizExercise = createQuizOnServer(ZonedDateTime.now().minusHours(5), null, QuizMode.SYNCHRONIZED);
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/set-visible", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        // start-now
        quizExercise = createQuizOnServer(ZonedDateTime.now().minusDays(1), null, QuizMode.SYNCHRONIZED);
        assertThat(quizExercise.isQuizStarted()).isTrue();
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/start-now", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        // open-for-practice
        quizExercise = createQuizOnServer(ZonedDateTime.now().minusDays(1), null, QuizMode.SYNCHRONIZED);
        quizExercise.setIsOpenForPractice(true);
        quizExerciseRepository.save(quizExercise);
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/open-for-practice", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        // misspelled request
        quizExercise = createQuizOnServer(ZonedDateTime.now().minusDays(1), null, QuizMode.SYNCHRONIZED);
        request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/lorem-ipsum", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * test that a quiz question with an explanation within valid length can be created
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testMultipleChoiceQuizExplanationLength_Valid() throws Exception {
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testMultipleChoiceQuizExplanationLength_Invalid() throws Exception {
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testMultipleChoiceQuizOptionExplanationLength_Valid() throws Exception {
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testMultipleChoiceQuizOptionExplanationLength_Inalid() throws Exception {
        int validityThreshold = 500;

        QuizExercise quizExercise = createMultipleChoiceQuizExerciseDummy();
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().get(0);
        question.getAnswerOptions().get(0).setExplanation("0".repeat(validityThreshold + 1));

        request.postWithResponseBody("/api/quiz-exercises/", quizExercise, QuizExercise.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private QuizExercise createMultipleChoiceQuizExerciseDummy() {
        Course course = database.createCourse();
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(4).text("Q1");
        QuizExercise quizExercise = ModelFactory.generateQuizExercise(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED, course);

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
        assertThat(quizExercise.getQuizPointStatistic().getPointCounters()).hasSameSizeAs(quizExercise2.getQuizPointStatistic().getPointCounters());
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
        if (!quizExercise.isQuizEnded()) {
            for (QuizQuestion question : quizExercise.getQuizQuestions()) {
                assertThat(question.getExplanation()).isNull();
                assertThat(question.getQuizQuestionStatistic()).isNull();
            }
        }
        else if (!quizExercise.isQuizStarted()) {
            assertThat(quizExercise.getQuizQuestions()).isEmpty();
        }
    }
}
