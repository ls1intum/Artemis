package de.tum.cit.aet.artemis.quiz;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseIntegrationTestService;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.PointCounter;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchJoinDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseForCourseDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithoutQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.repository.SubmittedAnswerRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;

class QuizExerciseIntegrationTest extends AbstractQuizExerciseIntegrationTest {

    private static final String TEST_PREFIX = "quizexerciseintegration";

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseIntegrationTest.class);

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

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private QuizSubmissionTestRepository quizSubmissionTestRepository;

    @Autowired
    private SubmittedAnswerRepository submittedAnswerRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ExerciseIntegrationTestService exerciseIntegrationTestService;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

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

        return List.of(Arguments.of(QuizMode.SYNCHRONIZED, future, null, null, null, FORBIDDEN, FORBIDDEN), // start in future
                Arguments.of(QuizMode.SYNCHRONIZED, past, null, null, null, BAD_REQUEST, OK), // synchronized
                Arguments.of(QuizMode.SYNCHRONIZED, past, null, null, "12345678", BAD_REQUEST, OK), // synchronized
                Arguments.of(QuizMode.SYNCHRONIZED, longPast, past, null, null, FORBIDDEN, OK), // due date passed
                Arguments.of(QuizMode.INDIVIDUAL, future, null, null, null, FORBIDDEN, FORBIDDEN), // start in future
                Arguments.of(QuizMode.INDIVIDUAL, longPast, null, null, null, OK, OK), Arguments.of(QuizMode.INDIVIDUAL, longPast, longFuture, null, null, OK, OK),
                Arguments.of(QuizMode.INDIVIDUAL, longPast, future, null, null, OK, OK), // NOTE: reduced working time because of due date
                Arguments.of(QuizMode.INDIVIDUAL, longPast, past, null, null, FORBIDDEN, OK), // after due date
                Arguments.of(QuizMode.BATCHED, future, null, null, null, FORBIDDEN, FORBIDDEN), // start in future
                Arguments.of(QuizMode.BATCHED, longPast, null, null, null, BAD_REQUEST, OK), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, longFuture, null, null, BAD_REQUEST, OK), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, future, null, null, BAD_REQUEST, OK), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, past, null, null, FORBIDDEN, OK), // after due date
                Arguments.of(QuizMode.BATCHED, longPast, null, batchLongPast, null, BAD_REQUEST, OK), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchPast, null, BAD_REQUEST, OK), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchFuture, null, BAD_REQUEST, OK), // no pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchLongPast, "87654321", BAD_REQUEST, OK), // wrong pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchPast, "87654321", BAD_REQUEST, OK), // wrong pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchFuture, "87654321", BAD_REQUEST, OK), // wrong pw
                Arguments.of(QuizMode.BATCHED, longPast, null, batchLongPast, "12345678", BAD_REQUEST, OK), // batch done
                Arguments.of(QuizMode.BATCHED, longPast, null, batchPast, "12345678", OK, OK), // NOTE: reduced working time because batch had already started
                Arguments.of(QuizMode.BATCHED, longPast, null, batchFuture, "12345678", OK, OK));
    }

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testCreateQuizExercise(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, quizMode);
        createdQuizAssert(quizExercise);
        checkCreatedFiles(quizExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuizExerciseForExam() throws Exception {
        QuizExercise quizExercise = createQuizOnServerForExam();
        createdQuizAssert(quizExercise);
        checkCreatedFiles(quizExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizExerciseForExamBadRequestOtherOperations() throws Exception {
        QuizExercise quizExercise = createQuizOnServerForExam();

        request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/add-batch", null, QuizBatch.class, BAD_REQUEST);
        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO("1234"), QuizBatch.class, HttpStatus.BAD_REQUEST);
        request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-now", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    private void checkCreatedFiles(QuizExercise quizExercise) throws Exception {
        List<DragAndDropQuestion> questions = quizExercise.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).map(q -> (DragAndDropQuestion) q).toList();
        for (DragAndDropQuestion question : questions) {
            if (question.getBackgroundFilePath() == null) {
                continue;
            }
            checkCreatedFile(question.getBackgroundFilePath());
            for (DragItem dragItem : question.getDragItems()) {
                if (dragItem.getPictureFilePath() == null) {
                    continue;
                }
                checkCreatedFile(dragItem.getPictureFilePath());
            }
        }
    }

    private void checkCreatedFile(String path) throws Exception {
        MediaType mediaType = path.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        String requestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, path);
        MvcResult result = request.performMvcRequest(get(requestUrl)).andExpect(status().isOk()).andExpect(content().contentType(mediaType)).andReturn();
        byte[] image = result.getResponse().getContentAsByteArray();
        assertThat(image).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createQuizExercise_InvalidDates_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now()));

        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testUpdateQuizExercise(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, quizMode);
        updateQuizAndAssert(quizExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_NoQuestions_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setQuizQuestions(List.of());
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_MissingTitle_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setTitle(null);
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_EmptyTitle_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setTitle("");
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_MissingScoringType_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            question.setScoringType(null);
        }
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_DnD_MissingTempID_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).findFirst().ifPresent(q -> {
            DragAndDropQuestion dnd = (DragAndDropQuestion) q;
            dnd.getDragItems().getFirst().setTempID(null);
        });
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_DnD_EmptyDropLocations_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).findFirst().ifPresent(q -> {
            DragAndDropQuestion dnd = (DragAndDropQuestion) q;
            dnd.setDropLocations(List.of());
        });
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_DnD_EmptyDragItems_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).findFirst().ifPresent(q -> {
            DragAndDropQuestion dnd = (DragAndDropQuestion) q;
            dnd.setDragItems(List.of());
        });
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_DnD_EmptyCorrectMappings_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).findFirst().ifPresent(q -> {
            DragAndDropQuestion dnd = (DragAndDropQuestion) q;
            dnd.setCorrectMappings(List.of());
        });
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_SA_EmptySpots_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizQuestions().stream().filter(q -> q instanceof ShortAnswerQuestion).findFirst().ifPresent(q -> {
            ShortAnswerQuestion sa = (ShortAnswerQuestion) q;
            sa.setSpots(List.of());
        });
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_SA_EmptySolutions_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizQuestions().stream().filter(q -> q instanceof ShortAnswerQuestion).findFirst().ifPresent(q -> {
            ShortAnswerQuestion sa = (ShortAnswerQuestion) q;
            sa.setSolutions(List.of());
        });
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_SA_EmptyCorrectMappings_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizQuestions().stream().filter(q -> q instanceof ShortAnswerQuestion).findFirst().ifPresent(q -> {
            ShortAnswerQuestion sa = (ShortAnswerQuestion) q;
            sa.setCorrectMappings(List.of());
        });
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_SA_MissingTempID_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizQuestions().stream().filter(q -> q instanceof ShortAnswerQuestion).findFirst().ifPresent(q -> {
            ShortAnswerQuestion sa = (ShortAnswerQuestion) q;
            sa.getSpots().getFirst().setTempID(null);
        });
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_MC_EmptyAnswerOptions_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizQuestions().stream().filter(q -> q instanceof MultipleChoiceQuestion).findFirst().ifPresent(q -> {
            MultipleChoiceQuestion mc = (MultipleChoiceQuestion) q;
            mc.setAnswerOptions(List.of());
        });
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuiz_MC_MissingAnswerText_badRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizQuestions().stream().filter(q -> q instanceof MultipleChoiceQuestion).findFirst().ifPresent(q -> {
            MultipleChoiceQuestion mc = (MultipleChoiceQuestion) q;
            mc.getAnswerOptions().getFirst().setText(null);
        });
        createQuizExerciseWithFiles(quizExercise, HttpStatus.BAD_REQUEST, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizExerciseForExam() throws Exception {
        QuizExercise quizExercise = createQuizOnServerForExam();
        updateQuizAndAssert(quizExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizExercise_SingleChoiceMC_AllOrNothing() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

        // multiple correct answers are not allowed
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        mc.setSingleChoice(true);
        mc.getAnswerOptions().get(1).setIsCorrect(true);

        updateQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(value = ScoringType.class, names = { "PROPORTIONAL_WITHOUT_PENALTY", "PROPORTIONAL_WITH_PENALTY" })
    void testUpdateQuizExercise_SingleChoiceMC_badRequest(ScoringType scoringType) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        mc.setSingleChoice(true);
        mc.setScoringType(scoringType);

        updateQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateQuizExercise_invalidDates_badRequest() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now()));
        updateQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testUpdateQuizExercise_partialTitleUpdate(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, quizMode);
        Integer originalDuration = quizExercise.getDuration();

        quizExercise.setTitle("Patched Title");

        QuizExercise updatedQuizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK, null);

        // Assert title changed, other fields unchanged
        assertThat(updatedQuizExercise.getTitle()).isEqualTo("Patched Title");
        assertThat(updatedQuizExercise.getDuration()).isEqualTo(originalDuration);
        assertThat(updatedQuizExercise.getQuizMode()).isEqualTo(quizMode);
        assertThat(updatedQuizExercise.getQuizQuestions()).hasSameSizeAs(quizExercise.getQuizQuestions());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizExercise_addNewQuestion() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        int originalQuestionCount = quizExercise.getQuizQuestions().size();

        // Create a new question to add
        MultipleChoiceQuestion newQuestion = QuizExerciseFactory.createMultipleChoiceQuestion();
        newQuestion.setTitle("New MC Question");
        quizExercise.getQuizQuestions().add(newQuestion);

        QuizExercise updatedQuizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK, null);

        // Assert new question added
        assertThat(updatedQuizExercise.getQuizQuestions()).hasSize(originalQuestionCount + 1);
        assertThat(updatedQuizExercise.getQuizQuestions().stream().anyMatch(q -> q.getTitle().equals("New MC Question"))).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizExercise_updateDragAndDropBackgroundFile() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        String newBackgroundFilePath = "updatedBackground.png";

        // Find DnD question and update background path
        DragAndDropQuestion dndQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).findFirst().orElseThrow();
        dndQuestion.setBackgroundFilePath(newBackgroundFilePath);

        QuizExercise updatedQuizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(newBackgroundFilePath), OK, null);

        // Assert background file updated
        DragAndDropQuestion updatedDnd = (DragAndDropQuestion) updatedQuizExercise.getQuizQuestions().stream().filter(q -> q.getId().equals(dndQuestion.getId())).findFirst()
                .orElseThrow();
        assertThat(updatedDnd.getBackgroundFilePath()).contains(newBackgroundFilePath);
        // Verify file exists
        checkCreatedFile(updatedDnd.getBackgroundFilePath());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizExercise_startedQuizStructuralChange_badRequest() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().minusHours(1), null, QuizMode.SYNCHRONIZED);

        // Attempt to add a new question after start
        MultipleChoiceQuestion newQuestion = QuizExerciseFactory.createMultipleChoiceQuestion();
        quizExercise.getQuizQuestions().add(newQuestion);

        updateQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.BAD_REQUEST, null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testDeleteQuizExercise(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusHours(5), null, quizMode);

        assertThat(quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is created correctly").isNotNull();
        request.delete("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK);
        assertThat(quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is deleted correctly").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteQuizExerciseWithChannel() throws Exception {
        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        QuizExercise quizExercise = (QuizExercise) course.getExercises().stream().findFirst().orElseThrow();
        Channel exerciseChannel = conversationUtilService.addChannelToExercise(quizExercise);

        request.delete("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK);

        Optional<Channel> exerciseChannelAfterDelete = channelRepository.findById(exerciseChannel.getId());
        assertThat(exerciseChannelAfterDelete).isEmpty();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testDeleteQuizExerciseWithSubmittedAnswers(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(1), quizMode);
        assertThat(quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is created correctly").isNotNull();

        QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, true, null);
        quizSubmission.submitted(true);
        participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);

        // Quiz submissions are now in database
        assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(quizExercise.getId())).hasSize(1);

        request.delete("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK);
        assertThat(quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExercise.getId())).as("Exercise is deleted correctly").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateRunningQuizExercise() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusHours(1), null, QuizMode.SYNCHRONIZED);

        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        mc.getAnswerOptions().removeFirst();
        mc.getAnswerOptions().add(new AnswerOption().text("C").hint("H3").explanation("E3").isCorrect(true));

        QuizExercise updatedQuizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.BAD_REQUEST);
        assertThat(updatedQuizExercise).isNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testGetQuizExercise(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusHours(5), null, quizMode);

        QuizExercise quizExerciseGet = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK, QuizExercise.class);
        checkQuizExercises(quizExercise, quizExerciseGet);

        // Start Date picker at Quiz Edit page should be populated correctly
        if (quizMode == QuizMode.SYNCHRONIZED) {
            assertThat(quizExerciseGet.getQuizBatches()).isNotEmpty();
        }

        // get all exercises for a course
        List<QuizExerciseForCourseDTO> allQuizExercisesForCourse = request
                .getList("/api/quiz/courses/" + quizExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/quiz-exercises", OK, QuizExerciseForCourseDTO.class);
        assertThat(allQuizExercisesForCourse).hasSize(1);
        QuizExerciseForCourseDTO fromServer = allQuizExercisesForCourse.getFirst();
        assertThat(fromServer).isEqualTo(QuizExerciseForCourseDTO.of(quizExerciseGet, true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void testGetQuizExercise_asStudent(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusHours(5), null, quizMode);
        quizExercise.setDuration(360);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now().plusHours(5)));

        // get not yet started exercise for students
        QuizExercise quizExerciseForStudent_notStarted = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/for-student", OK, QuizExercise.class);
        checkQuizExerciseForStudent(quizExerciseForStudent_notStarted);

        // set exercise started 5 min ago
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now().minusMinutes(5)));
        assertThat(quizExercise.getQuizBatches()).allMatch(QuizBatch::isStarted);
        assertThat(quizExercise.getQuizBatches()).allMatch(QuizBatch::isSubmissionAllowed);

        // get started exercise for students
        QuizExercise quizExerciseForStudent_Started = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/for-student", OK, QuizExercise.class);
        checkQuizExerciseForStudent(quizExerciseForStudent_Started);

        // get finished exercise for students
        quizExerciseService.endQuiz(quizExercise);
        assertThat(quizExercise.getQuizBatches()).allMatch(QuizBatch::isStarted);
        assertThat(quizExercise.getQuizBatches()).noneMatch(QuizBatch::isSubmissionAllowed);

        QuizExercise quizExerciseForStudent_Finished = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/for-student", OK, QuizExercise.class);
        checkQuizExerciseForStudent(quizExerciseForStudent_Finished);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSetQuizBatchStartTimeForNonSynchronizedQuizExercises_asStudent() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusHours(5), null, QuizMode.INDIVIDUAL);
        quizExercise.setDuration(400);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now().minusMinutes(5)));

        // get started exercise for students
        // when exercise due date is null
        QuizExercise quizExerciseForStudent = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/for-student", OK, QuizExercise.class);
        assertThat(quizExerciseForStudent.getQuizBatches()).usingRecursiveAssertion().hasNoNullFields();

        // when exercise due date is later than now
        quizExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        quizExerciseForStudent = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/for-student", OK, QuizExercise.class);
        assertThat(quizExerciseForStudent.getQuizBatches()).usingRecursiveAssertion().hasNoNullFields();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateQuizExerciseWithoutQuizBatchForSynchronizedQuiz_asStudent() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusHours(4), null, QuizMode.SYNCHRONIZED);
        quizExercise.setDuration(400);
        quizExercise.setQuizBatches(null);

        QuizExercise quizExerciseForStudent = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/for-student", OK, QuizExercise.class);
        assertThat(quizExerciseForStudent.getQuizBatches()).hasSize(1);
        checkQuizExerciseForStudent(quizExerciseForStudent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQuizExercisesForExam() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveExamQuiz(ZonedDateTime.now().minusHours(4), ZonedDateTime.now().plusHours(4));
        long examId = quizExercise.getExerciseGroup().getExam().getId();

        List<QuizExerciseForCourseDTO> quizExercises = request.getList("/api/quiz/exams/" + examId + "/quiz-exercises", OK, QuizExerciseForCourseDTO.class);
        assertThat(quizExercises).as("Quiz exercise was retrieved").hasSize(1);
        assertThat(quizExercise.getId()).as("Quiz exercise with the right id was retrieved").isEqualTo(quizExercises.getFirst().id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamQuizExercise() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveExamQuiz(ZonedDateTime.now().minusHours(5), ZonedDateTime.now().minusHours(10));

        QuizExercise quizExerciseGet = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK, QuizExercise.class);
        checkQuizExercises(quizExercise, quizExerciseGet);

        assertThat(quizExerciseGet).as("Quiz exercise was retrieved").isEqualTo(quizExercise).isNotNull();
        assertThat(quizExerciseGet.getId()).as("Quiz exercise with the right id was retrieved").isEqualTo(quizExercise.getId());
        assertThat(quizExerciseGet.getQuizBatches()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamQuizExercise_asTutor_forbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveExamQuiz(ZonedDateTime.now().minusHours(5), ZonedDateTime.now().minusHours(10));
        request.get("/api/quiz/quiz-exercises/" + quizExercise.getId(), FORBIDDEN, QuizExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorSearchTermMatchesId() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().minusHours(2), QuizMode.INDIVIDUAL);
        long exerciseId = quizExercise.getId();

        var searchTerm = pageableSearchUtilService.configureSearch(String.valueOf(exerciseId));
        SearchResultPageDTO<QuizExercise> searchResult = request.getSearchResult("/api/quiz/quiz-exercises", OK, QuizExercise.class,
                pageableSearchUtilService.searchMapping(searchTerm));

        assertThat(searchResult.getResultsOnPage()).filteredOn(quiz -> quiz.getId() == exerciseId).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCourseAndExamFiltersAsInstructor() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusHours(1), QuizMode.SYNCHRONIZED);
        QuizExercise examQuizExercise = quizExerciseUtilService.createAndSaveExamQuiz(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().minusHours(2));

        String searchTerm = "very unique quiz title";
        quizExerciseUtilService.renameAndSaveQuiz(quizExercise, searchTerm);
        quizExerciseUtilService.renameAndSaveQuiz(examQuizExercise, searchTerm + "-Morpork");

        exerciseIntegrationTestService.testCourseAndExamFilters("/api/quiz/quiz-exercises", searchTerm);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRecalculateStatistics() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        quizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK);

        var now = ZonedDateTime.now();

        // generate submissions for each student
        int numberOfParticipants = 10;
        userUtilService.addStudents(TEST_PREFIX, 2, 14);

        for (int i = 1; i <= numberOfParticipants; i++) {
            QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i, true, now.minusHours(3));
            participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student" + i);
            participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
        }

        // calculate statistics
        QuizExercise quizExerciseWithRecalculatedStatistics = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", OK, QuizExercise.class);

        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getPointCounters()).hasSize(10);
        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getParticipantsRated()).isEqualTo(numberOfParticipants);

        assertQuizPointStatisticsPointCounters(quizExerciseWithRecalculatedStatistics, Map.of(0.0, pc30, 3.0, pc20, 4.0, pc20, 6.0, pc20, 7.0, pc10));

        // add more submissions and recalculate
        for (int i = numberOfParticipants; i <= 14; i++) {
            QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i, true, now.minusHours(3));
            participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student" + i);
            participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
        }

        // calculate statistics
        quizExerciseWithRecalculatedStatistics = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", OK, QuizExercise.class);

        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getPointCounters()).hasSize(10);
        assertThat(quizExerciseWithRecalculatedStatistics.getQuizPointStatistic().getParticipantsRated()).isEqualTo(numberOfParticipants + 4);

        assertQuizPointStatisticsPointCounters(quizExerciseWithRecalculatedStatistics, Map.of(0.0, pc50, 3.0, pc20, 4.0, pc30, 6.0, pc20, 7.0, pc10, 9.0, pc10));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReevaluateStatistics() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusSeconds(5), null, QuizMode.SYNCHRONIZED);

        // we expect a bad request because the quiz has not ended yet
        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), HttpStatus.BAD_REQUEST);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExerciseService.endQuiz(quizExercise);
        quizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK);

        // generate rated submissions for each student
        int numberOfParticipants = 10;
        userUtilService.addStudents(TEST_PREFIX, 2, 10);

        for (int i = 1; i <= numberOfParticipants; i++) {
            if (i != 1 && i != 5) {
                QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i, true, ZonedDateTime.now().minusHours(1));
                participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student" + i);
                participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
                assertThat(submittedAnswerRepository.findBySubmission(quizSubmission)).hasSize(3);
            }
        }

        // submission with everything selected
        QuizSubmission quizSubmission = QuizExerciseFactory.generateSpecialSubmissionWithResult(quizExercise, true, ZonedDateTime.now().minusHours(1), true);
        participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);

        // submission with nothing selected
        quizSubmission = QuizExerciseFactory.generateSpecialSubmissionWithResult(quizExercise, true, ZonedDateTime.now().minusHours(1), false);
        participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student5");
        participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);

        assertThat(studentParticipationRepository.findByExerciseId(quizExercise.getId())).hasSize(numberOfParticipants);
        assertThat(resultRepository.findAllBySubmissionParticipationExerciseId(quizExercise.getId())).hasSize(numberOfParticipants);
        assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(quizExercise.getId())).hasSize(numberOfParticipants);
        assertThat(submittedAnswerRepository.findBySubmission(quizSubmission)).hasSize(3);

        // calculate statistics
        quizExercise = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", OK, QuizExercise.class);

        log.debug("QuizPointStatistic before re-evaluate: {}", quizExercise.getQuizPointStatistic());

        // check that the statistic is correct before any re-evaluate
        assertQuizPointStatisticsPointCounters(quizExercise, Map.of(0.0, pc30, 3.0, pc20, 4.0, pc20, 6.0, pc20, 7.0, pc10));

        // reevaluate without changing anything and check if statistics are still correct (i.e. unchanged)
        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), OK);
        QuizExercise quizExerciseWithReevaluatedStatistics = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
        checkStatistics(quizExercise, quizExerciseWithReevaluatedStatistics);

        log.debug("QuizPointStatistic after re-evaluate (without changes): {}", quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        // remove wrong answer option and reevaluate
        var multipleChoiceQuestion = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        multipleChoiceQuestion.getAnswerOptions().remove(1);

        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), OK);

        // load the exercise again after it was re-evaluated
        quizExerciseWithReevaluatedStatistics = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK, QuizExercise.class);

        var multipleChoiceQuestionAfterReevaluate = (MultipleChoiceQuestion) quizExerciseWithReevaluatedStatistics.getQuizQuestions().getFirst();
        assertThat(multipleChoiceQuestionAfterReevaluate.getAnswerOptions()).hasSize(1);

        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic()).isEqualTo(quizExercise.getQuizPointStatistic());

        // one student should get a higher score
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSameSizeAs(quizExercise.getQuizPointStatistic().getPointCounters());
        log.debug("QuizPointStatistic after 1st re-evaluate: {}", quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(0.0, pc20, 3.0, pc20, 4.0, pc30, 6.0, pc20, 7.0, pc10));

        // set a question invalid and reevaluate
        var shortAnswerQuestion = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);
        shortAnswerQuestion.setInvalid(true);

        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), OK);
        // load the exercise again after it was re-evaluated
        quizExerciseWithReevaluatedStatistics = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK, QuizExercise.class);

        var shortAnswerQuestionAfterReevaluation = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);
        assertThat(shortAnswerQuestionAfterReevaluation.isInvalid()).isTrue();

        // several students should get a higher score
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSameSizeAs(quizExercise.getQuizPointStatistic().getPointCounters());
        log.debug("QuizPointStatistic after 2nd re-evaluate: {}", quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());
        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(2.0, pc20, 5.0, pc20, 6.0, pc50, 9.0, pc10));

        // delete a question and reevaluate
        quizExercise.getQuizQuestions().remove(1);

        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), OK);
        // load the exercise again after it was re-evaluated
        quizExerciseWithReevaluatedStatistics = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK, QuizExercise.class);

        assertThat(quizExerciseWithReevaluatedStatistics.getQuizQuestions()).hasSize(2);

        // max score should be less
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSize(quizExercise.getQuizPointStatistic().getPointCounters().size() - 3);
        log.debug("QuizPointStatistic after 3rd re-evaluate: {}", quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());
        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(2.0, pc40, 6.0, pc60));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReevaluateStatistics_Practice() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusSeconds(5), null, QuizMode.SYNCHRONIZED);
        // use the exact other scoring types to cover all combinations in the tests
        quizExercise.getQuizQuestions().getFirst().setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);   // MC
        quizExercise.getQuizQuestions().get(1).setScoringType(ScoringType.ALL_OR_NOTHING);              // DnD
        quizExercise.getQuizQuestions().get(2).setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);   // SA

        // we expect a bad request because the quiz has not ended yet
        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), HttpStatus.BAD_REQUEST);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        quizExercise.setDuration(3600);
        quizExerciseService.endQuiz(quizExercise);
        quizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK);

        // generate unrated submissions for each student
        int numberOfParticipants = 10;
        userUtilService.addStudents(TEST_PREFIX, 2, 10);

        for (int i = 1; i <= numberOfParticipants; i++) {
            if (i != 1 && i != 5) {
                QuizSubmission quizSubmissionPractice = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i, true, ZonedDateTime.now());
                participationUtilService.addSubmission(quizExercise, quizSubmissionPractice, TEST_PREFIX + "student" + i);
                participationUtilService.addResultToSubmission(quizSubmissionPractice, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmissionPractice),
                        false);
            }
        }

        // submission with everything selected
        QuizSubmission quizSubmissionPractice = QuizExerciseFactory.generateSpecialSubmissionWithResult(quizExercise, true, ZonedDateTime.now(), true);
        participationUtilService.addSubmission(quizExercise, quizSubmissionPractice, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(quizSubmissionPractice, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmissionPractice), false);

        // submission with nothing selected
        quizSubmissionPractice = QuizExerciseFactory.generateSpecialSubmissionWithResult(quizExercise, true, ZonedDateTime.now(), false);
        participationUtilService.addSubmission(quizExercise, quizSubmissionPractice, TEST_PREFIX + "student5");
        participationUtilService.addResultToSubmission(quizSubmissionPractice, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmissionPractice), false);

        assertThat(studentParticipationRepository.countParticipationsByExerciseIdAndTestRun(quizExercise.getId(), false)).isEqualTo(10);
        assertThat(resultRepository.findAllBySubmissionParticipationExerciseId(quizExercise.getId())).hasSize(10);

        // calculate statistics
        quizExercise = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", OK, QuizExercise.class);

        log.debug("QuizPointStatistic before re-evaluate: {}", quizExercise.getQuizPointStatistic());

        // reevaluate without changing anything and check if statistics are still correct
        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), OK);
        QuizExercise quizExerciseWithReevaluatedStatistics = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
        checkStatistics(quizExercise, quizExerciseWithReevaluatedStatistics);

        log.debug("QuizPointStatistic after re-evaluate (without changes): {}", quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        // remove wrong answer option and reevaluate
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExerciseWithReevaluatedStatistics.getQuizQuestions().getFirst();
        mc.getAnswerOptions().remove(1);

        reevalQuizExerciseWithFiles(quizExerciseWithReevaluatedStatistics, quizExercise.getId(), List.of(), OK);
        quizExerciseWithReevaluatedStatistics = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());

        // one student should get a higher score
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSameSizeAs(quizExercise.getQuizPointStatistic().getPointCounters());

        log.debug("QuizPointStatistic after 1st re-evaluate: {}", quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(0.0, pc02, 3.0, pc02, 4.0, pc03, 6.0, pc02, 7.0, pc01));

        // set a question invalid and reevaluate
        quizExerciseWithReevaluatedStatistics.getQuizQuestions().get(2).setInvalid(true);

        reevalQuizExerciseWithFiles(quizExerciseWithReevaluatedStatistics, quizExercise.getId(), List.of(), OK);
        quizExerciseWithReevaluatedStatistics = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());

        // several students should get a higher score
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSameSizeAs(quizExercise.getQuizPointStatistic().getPointCounters());
        log.debug("QuizPointStatistic after 2nd re-evaluate: {}", quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());

        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(2.0, pc02, 5.0, pc02, 6.0, pc05, 9.0, pc01));

        // delete a question and reevaluate
        quizExerciseWithReevaluatedStatistics.getQuizQuestions().remove(1);

        reevalQuizExerciseWithFiles(quizExerciseWithReevaluatedStatistics, quizExercise.getId(), List.of(), OK);
        quizExerciseWithReevaluatedStatistics = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());

        // max score should be less
        log.debug("QuizPointStatistic after 3rd re-evaluate: {}", quizExerciseWithReevaluatedStatistics.getQuizPointStatistic());
        assertThat(quizExerciseWithReevaluatedStatistics.getQuizPointStatistic().getPointCounters()).hasSize(quizExercise.getQuizPointStatistic().getPointCounters().size() - 3);

        assertQuizPointStatisticsPointCounters(quizExerciseWithReevaluatedStatistics, Map.of(2.0, pc04, 6.0, pc06));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateQuizQuestionWithMoreSolutions() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().minusHours(5), ZonedDateTime.now().minusHours(2), QuizMode.SYNCHRONIZED);
        QuizQuestion question = quizExercise.getQuizQuestions().get(2);

        if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
            // demonstrate that the initial shortAnswerQuestion has 2 correct mappings and 2 solutions
            assertThat(shortAnswerQuestion.getCorrectMappings()).hasSize(2);
            assertThat(shortAnswerQuestion.getCorrectMappings()).hasSize(2);

            // add a solution with a mapping onto spot number 0
            ShortAnswerSolution newSolution = new ShortAnswerSolution();
            newSolution.setText("text");
            newSolution.setTempID(3L);
            shortAnswerQuestion.getSolutions().add(newSolution);
            ShortAnswerMapping newMapping = new ShortAnswerMapping();
            newMapping.setId(3L);
            newMapping.setInvalid(false);
            newMapping.setShortAnswerSolutionIndex(2);
            newMapping.setSolution(newSolution);
            newMapping.setSpot(shortAnswerQuestion.getSpots().getFirst());
            newMapping.setShortAnswerSpotIndex(0);
            shortAnswerQuestion.getCorrectMappings().add(newMapping);
            quizExercise.getQuizQuestions().remove(2);
            quizExercise.getQuizQuestions().add(shortAnswerQuestion);
        }
        // PUT Request with the newly modified quizExercise
        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), OK);
        QuizExercise updatedQuizExercise = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
        // Check that the updatedQuizExercise is equal to the modified quizExercise with special focus on the newly added solution and mapping
        assertThat(updatedQuizExercise).isEqualTo(quizExercise);
        ShortAnswerQuestion receivedShortAnswerQuestion = (ShortAnswerQuestion) updatedQuizExercise.getQuizQuestions().get(2);
        assertThat(receivedShortAnswerQuestion.getSolutions()).hasSize(3);
        assertThat(receivedShortAnswerQuestion.getCorrectMappings()).hasSize(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReevaluate_DragItemRemoved() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusSeconds(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExerciseService.endQuiz(quizExercise);
        quizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK);

        int numberOfParticipants = 5;
        userUtilService.addStudents(TEST_PREFIX, 2, 6);
        var now = ZonedDateTime.now();
        for (int i = 1; i <= numberOfParticipants; i++) {
            QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i, true, now.minusHours(3));
            participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student" + i);
            participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
        }

        DragAndDropQuestion dndQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).findFirst().orElseThrow();
        dndQuestion.getDragItems().removeFirst(); // Remove first drag item
        dndQuestion.getCorrectMappings().removeIf(mapping -> mapping.getDragItem() == null);

        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), OK);
        QuizExercise reevaluatedQuiz = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK, QuizExercise.class);

        DragAndDropQuestion updatedDnd = (DragAndDropQuestion) reevaluatedQuiz.getQuizQuestions().stream().filter(q -> q.getId().equals(dndQuestion.getId())).findFirst()
                .orElseThrow();
        assertThat(updatedDnd.getDragItems()).hasSize(dndQuestion.getDragItems().size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReevaluate_DragItemSetInvalid() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusSeconds(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExerciseService.endQuiz(quizExercise);
        quizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK);

        int numberOfParticipants = 5;
        userUtilService.addStudents(TEST_PREFIX, 2, 6);
        var now = ZonedDateTime.now();
        for (int i = 1; i <= numberOfParticipants; i++) {
            QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i, true, now.minusHours(3));
            participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student" + i);
            participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
        }

        quizExercise = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", OK, QuizExercise.class);

        DragAndDropQuestion dndQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).findFirst().orElseThrow();
        dndQuestion.getDragItems().getFirst().setInvalid(true);

        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), OK);
        QuizExercise reevaluatedQuiz = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK, QuizExercise.class);

        DragAndDropQuestion updatedDnd = (DragAndDropQuestion) reevaluatedQuiz.getQuizQuestions().stream().filter(q -> q.getId().equals(dndQuestion.getId())).findFirst()
                .orElseThrow();
        assertThat(updatedDnd.getDragItems().getFirst().isInvalid()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReevaluateStatistics_DragItemNeitherPictureNorText() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusSeconds(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExerciseService.endQuiz(quizExercise);
        quizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK);

        DragAndDropQuestion dndQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).findFirst().orElseThrow();
        dndQuestion.getDragItems().getFirst().setText(null);
        dndQuestion.getDragItems().getFirst().setPictureFilePath(null);

        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReevaluateStatistics_DragItemBothPictureAndText() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusSeconds(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));
        quizExerciseService.endQuiz(quizExercise);
        quizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK);

        DragAndDropQuestion dndQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).findFirst().orElseThrow();
        dndQuestion.getDragItems().getFirst().setText("Invalid text");
        dndQuestion.getDragItems().getFirst().setPictureFilePath("invalid/path.jpg");

        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateQuizExercise_CopyExistingBackground() throws Exception {
        // Create and save initial quiz with DnD background
        QuizExercise initialQuiz = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        DragAndDropQuestion initialDnd = (DragAndDropQuestion) initialQuiz.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).findFirst().orElseThrow();
        String existingBgPath = initialDnd.getBackgroundFilePath();

        QuizExercise newQuiz = importQuizExerciseWithFiles(initialQuiz, List.of(), HttpStatus.CREATED);

        DragAndDropQuestion savedDnd = (DragAndDropQuestion) newQuiz.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).findFirst().orElseThrow();
        String newBgPath = savedDnd.getBackgroundFilePath();
        assertThat(newBgPath).isNotEqualTo(existingBgPath);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testEvaluateQuizKeepsResult() throws Exception {
        var quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusHours(5), ZonedDateTime.now().minusHours(2), QuizMode.SYNCHRONIZED);
        var quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, true, ZonedDateTime.now().minusHours(3));
        var submission = participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student1");
        submission = participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
        assertThat(submission.getResults()).hasSize(1);

        request.postWithoutResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/evaluate", null, OK);

        var newResult = resultRepository.findDistinctBySubmissionId(submission.getId());
        assertThat(newResult).isPresent();
        assertThat(newResult.get()).isEqualTo(submission.getResults().getFirst());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testEvaluateQuizCreatesResult() throws Exception {
        var quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusHours(5), ZonedDateTime.now().minusHours(2), QuizMode.SYNCHRONIZED);
        var quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, true, ZonedDateTime.now().minusHours(3));
        var submission = participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student1");
        assertThat(submission.getResults()).isEmpty();

        request.postWithoutResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/evaluate", null, OK);

        var newResult = resultRepository.findDistinctBySubmissionId(submission.getId());
        assertThat(newResult).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAddAndStartQuizBatch() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.BATCHED);

        QuizBatch batch = request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/add-batch", null, QuizBatch.class, OK);
        request.put("/api/quiz/quiz-exercises/" + batch.getId() + "/start-batch", null, OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAddAndStartQuizBatch_AsStudentNotAllowed() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.BATCHED);

        request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/add-batch", null, QuizBatch.class, FORBIDDEN);
        request.put("/api/quiz/quiz-exercises/" + null + "/start-batch", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPerformStartNow() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));

        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-now", quizExercise, QuizExercise.class, OK);

        long millis = ChronoUnit.MILLIS.between(Objects.requireNonNull(updatedQuizExercise.getQuizBatches().stream().findAny().orElseThrow().getStartTime()), ZonedDateTime.now());
        // actually the two dates should be "exactly" the same, but for the sake of slow CI testing machines and to prevent flaky tests, we live with the following rule
        assertThat(millis).isCloseTo(0, byLessThan(2000L));
        assertThat(updatedQuizExercise.getQuizBatches()).allMatch(QuizBatch::isStarted);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(value = QuizMode.class, names = { "SYNCHRONIZED" }, mode = EnumSource.Mode.EXCLUDE)
    void testPerformStartNow_invalidMode(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusHours(5), null, quizMode);
        quizExercise.setReleaseDate(ZonedDateTime.now().minusHours(5));

        request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-now", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testPerformSetVisible(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusHours(5), null, quizMode);

        // we expect a bad request because the quiz is already visible
        request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/set-visible", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
        quizExercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        quizExerciseService.save(quizExercise);

        QuizExercise updatedQuizExercise = request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/set-visible", quizExercise, QuizExercise.class, OK);
        assertThat(updatedQuizExercise.isVisibleToStudents()).isTrue();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @MethodSource(value = "testPerformJoin_args")
    void testPerformJoin(QuizMode quizMode, ZonedDateTime release, ZonedDateTime due, QuizBatch batch, String password, HttpStatus resultJoin, HttpStatus resultStart)
            throws Exception {
        QuizExercise quizExercise = createQuizOnServer(release, due, quizMode);
        if (batch != null) {
            quizExerciseUtilService.setQuizBatchExerciseAndSave(batch, quizExercise);
        }
        // switch to student
        SecurityContextHolder.getContext().setAuthentication(SecurityUtils.makeAuthorizationObject(TEST_PREFIX + "student1"));

        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, resultStart);
        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(password), QuizBatch.class, resultJoin);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void testCannotPerformJoinTwice(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusMinutes(2), ZonedDateTime.now().plusMinutes(2), quizMode);
        QuizBatch batch = new QuizBatch();
        batch.setStartTime(ZonedDateTime.now().minusMinutes(1));
        batch.setPassword("1234");
        quizExerciseUtilService.setQuizBatchExerciseAndSave(batch, quizExercise);
        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, HttpStatus.OK);

        if (quizMode != QuizMode.SYNCHRONIZED) {
            request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO("1234"), QuizBatch.class, HttpStatus.OK);
        }
        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO("1234"), QuizBatch.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * test non-editors can't create quiz exercises
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateQuizExerciseAsNonEditorForbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusDays(5), null, QuizMode.SYNCHRONIZED);

        createQuizExerciseWithFiles(quizExercise, FORBIDDEN, true);
        assertThat(quizExercise.getCourseViaExerciseGroupOrCourseMember().getExercises()).isEmpty();
    }

    /**
     * test non-tutors can't get all quiz exercises
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllQuizExercisesAsNonTutorForbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusDays(1), null, QuizMode.SYNCHRONIZED);

        request.getList("/api/quiz/courses/" + quizExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/quiz-exercises", FORBIDDEN, QuizExercise.class);
    }

    /**
     * test non-editors can't perform start-now, set-visible or open-for-practice on quiz exercises
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    @ValueSource(strings = { "start-now", "set-visible" })
    void testPerformPutActionAsNonEditorForbidden(String action) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusDays(1), null, QuizMode.SYNCHRONIZED);

        request.put("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/" + action, quizExercise, FORBIDDEN);
    }

    /**
     * test non-tutors can't see the exercise if it is not set to visible
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testViewQuizExerciseAsNonTutorNotVisibleForbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusDays(1), null, QuizMode.SYNCHRONIZED);

        request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/for-student", FORBIDDEN, QuizExercise.class);
    }

    /**
     * test non-instructors can't delete an exercise
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testDeleteQuizExerciseAsNonInstructorForbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusDays(1), null, QuizMode.SYNCHRONIZED);

        request.delete("/api/quiz/quiz-exercises/" + quizExercise.getId(), FORBIDDEN);
    }

    /**
     * test non-tutors can't recalculate quiz exercise statistics
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRecalculateStatisticsAsNonTutorForbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().minusHours(1), QuizMode.SYNCHRONIZED);

        request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/recalculate-statistics", FORBIDDEN, QuizExercise.class);
    }

    /**
     * test non-tutors not in course can't access a quiz exercise
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetQuizExerciseForNonTutorNotInCourseForbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusHours(4), null, QuizMode.SYNCHRONIZED);
        userUtilService.removeUserFromAllCourses(TEST_PREFIX + "student1");

        request.get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/for-student", FORBIDDEN, QuizExercise.class);
    }

    /**
     * test non-instructors in this course can't re-evaluate quiz exercises
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testReEvaluateQuizAsNonInstructorForbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusDays(2), ZonedDateTime.now().plusDays(2), QuizMode.SYNCHRONIZED);

        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), FORBIDDEN);
    }

    /**
     * test unfinished exam cannot be re-evaluated
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUnfinishedExamReEvaluateBadRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveExamQuiz(ZonedDateTime.now().minusDays(2), ZonedDateTime.now().plusDays(2));

        reevalQuizExerciseWithFiles(quizExercise, quizExercise.getId(), List.of(), HttpStatus.BAD_REQUEST);
    }

    /**
     * test non-editor can't update quiz exercise
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateQuizExerciseAsNonEditorForbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusHours(1), QuizMode.SYNCHRONIZED);
        quizExercise.setTitle("New Title");

        updateQuizExerciseWithFiles(quizExercise, List.of(), FORBIDDEN);
    }

    /**
     * test quiz exercise can't be edited to be invalid
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateQuizExerciseInvalidBadRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusHours(1), QuizMode.SYNCHRONIZED);
        assertThat(quizExercise.isValid()).isTrue();

        // make the exercise invalid
        quizExercise.setTitle(null);
        assertThat(quizExercise.isValid()).isFalse();
        updateQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.BAD_REQUEST);
    }

    /**
     * test update quiz exercise with notificationText
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateQuizExerciseWithNotificationText() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

        updateMultipleChoice(quizExercise);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("notificationText", "NotificationTextTEST!");

        updateQuizExerciseWithFiles(quizExercise, List.of(), OK, params);
        // TODO check if notifications arrived correctly
    }

    /*
     * test that an instructor cannot edit a quiz exercise after a submission has been made
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCannotEditQuizAfterSubmission() throws Exception {
        // Create a quiz exercise in individual mode
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1), QuizMode.INDIVIDUAL);
        final long courseId = quizExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        final Long quizExerciseId = quizExercise.getId();

        List<QuizExerciseForCourseDTO> quizzesBefore = request.getList("/api/quiz/courses/" + courseId + "/quiz-exercises", OK, QuizExerciseForCourseDTO.class);
        QuizExerciseForCourseDTO dtoBefore = quizzesBefore.stream().filter(q -> q.id() == quizExerciseId).findFirst().orElseThrow();
        assertThat(dtoBefore.isEditable()).isTrue();

        // Perform a structural edit before any submissions (should succeed)
        quizExercise.setTitle("New Title");
        quizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK);

        // Switch to student to create a submission
        SecurityContextHolder.getContext().setAuthentication(SecurityUtils.makeAuthorizationObject(TEST_PREFIX + "student1"));

        // Start participation
        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, OK);

        // Join batch for INDIVIDUAL mode
        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(null), QuizBatch.class, OK);

        // Create and submit a quiz submission
        QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, false, null);
        request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/live?submit=true", quizSubmission, QuizSubmission.class, OK);

        // Switch back to instructor
        SecurityContextHolder.getContext().setAuthentication(SecurityUtils.makeAuthorizationObject(TEST_PREFIX + "instructor1"));

        List<QuizExerciseForCourseDTO> quizzesAfter = request.getList("/api/quiz/courses/" + courseId + "/quiz-exercises", OK, QuizExerciseForCourseDTO.class);
        QuizExerciseForCourseDTO dtoAfter = quizzesAfter.stream().filter(q -> q.id() == quizExerciseId).findFirst().orElseThrow();
        assertThat(dtoAfter.isEditable()).isFalse();

        // Attempt another structural edit after submission (should fail)
        updateMultipleChoice(quizExercise);
        updateQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.BAD_REQUEST);
    }

    /**
     * test that an instructor cannot edit a quiz exercise after the due date has passed
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizWithDueDateInPast() throws Exception {
        // Create a quiz exercise with due date in the past
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().minusHours(2), ZonedDateTime.now().minusHours(1), QuizMode.SYNCHRONIZED);
        long courseId = quizExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        // Call the quiz-exercises API
        List<QuizExerciseForCourseDTO> quizzes = request.getList("/api/quiz/courses/" + courseId + "/quiz-exercises", OK, QuizExerciseForCourseDTO.class);

        // Find the DTO for the created quiz
        QuizExerciseForCourseDTO dto = quizzes.stream().filter(q -> q.id() == quizExercise.getId()).findFirst().orElseThrow();

        // Assert the quiz is in the list and due date is in the past
        assertThat(dto.dueDate()).isBefore(ZonedDateTime.now());

        // Assert isEditable is false for ended quiz
        assertThat(dto.isEditable()).isFalse();
    }

    /**
     * test create and update quiz exercise with competency
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateAndUpdateQuizWithCompetency() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1), QuizMode.INDIVIDUAL);
        Course quizCourse = quizExercise.getCourseViaExerciseGroupOrCourseMember();

        // Create a simple course competency
        Competency competency = new Competency();
        competency.setTitle("Test Competency");
        competency.setDescription("This is a test competency");
        competency.setCourse(quizCourse);
        competency.setMasteryThreshold(1);
        competency = request.postWithResponseBody("/api/atlas/courses/" + quizCourse.getId() + "/competencies", competency, Competency.class, HttpStatus.CREATED);

        // Update the quiz exercise
        quizExercise.setTitle("Updated Quiz Title");
        quizExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, quizExercise, 0.25)));
        quizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK);

        // Get the quiz exercise as instructor and verify competency link
        QuizExercise retrievedQuiz = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK, QuizExercise.class);
        assertThat(retrievedQuiz.getCompetencyLinks()).hasSize(1);
        CompetencyExerciseLink link = retrievedQuiz.getCompetencyLinks().iterator().next();
        assertThat(link.getCompetency().getId()).isEqualTo(competency.getId());
        assertThat(link.getWeight()).isEqualTo(0.25);

        Competency fakeCompetency = new Competency();
        fakeCompetency.setId(999L);
        quizExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(fakeCompetency, quizExercise, 0.25)));
        updateQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(value = QuizMode.class, names = { "INDIVIDUAL", "BATCHED" })
    void testGetQuizExerciseForStudent_NotJoined_NonSynchronized(QuizMode quizMode) throws Exception {
        // Create quiz not started
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().minusMinutes(5), null, quizMode);

        // Add batch
        request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/add-batch", null, QuizBatch.class, OK);

        // Get as student - no join, so no batch
        SecurityContextHolder.getContext().setAuthentication(SecurityUtils.makeAuthorizationObject(TEST_PREFIX + "student1"));
        MvcResult result = request.performMvcRequest(get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/for-student")).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(content);
        assertThat(json.has("quizQuestions")).isFalse();

        QuizExerciseWithoutQuestionsDTO dto = objectMapper.readValue(content, QuizExerciseWithoutQuestionsDTO.class);
        assertThat(dto.id()).isEqualTo(quizExercise.getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testGetQuizExerciseForStudent_Ended(QuizMode quizMode) throws Exception {
        // Create ended quiz
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().minusMinutes(15), ZonedDateTime.now().minusMinutes(5), quizMode);

        // If mode is SYNCHRONIZED, end the quiz
        if (quizMode == QuizMode.SYNCHRONIZED) {
            quizExerciseService.endQuiz(quizExercise);
            quizExerciseService.save(quizExercise);
        }

        // Get
        SecurityContextHolder.getContext().setAuthentication(SecurityUtils.makeAuthorizationObject(TEST_PREFIX + "student1"));
        MvcResult result = request.performMvcRequest(get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/for-student")).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(content);
        assertThat(json.has("quizQuestions")).isTrue();
        assertThat(json.get("quizQuestions").size()).isEqualTo(3);

        // Assume first MC, check has isCorrect
        JsonNode mc = json.get("quizQuestions").get(0);
        JsonNode ao = mc.get("answerOptions").get(0);
        assertThat(ao.has("isCorrect")).isTrue();
        assertThat(ao.get("isCorrect").isBoolean()).isTrue();

        QuizExerciseWithSolutionDTO dto = objectMapper.readValue(content, QuizExerciseWithSolutionDTO.class);
        assertThat(dto.quizQuestions()).hasSize(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetQuizExerciseForStudent_Active_Individual_StartedParticipation() throws Exception {
        // Create active individual quiz
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().minusMinutes(5), ZonedDateTime.now().plusMinutes(5), QuizMode.BATCHED);

        // As instructor, add and start batch
        SecurityContextHolder.getContext().setAuthentication(SecurityUtils.makeAuthorizationObject(TEST_PREFIX + "instructor1"));
        QuizBatch batch = request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/add-batch", null, QuizBatch.class, OK);
        request.put("/api/quiz/quiz-exercises/" + batch.getId() + "/start-batch", null, OK);

        // As student, join batch
        SecurityContextHolder.getContext().setAuthentication(SecurityUtils.makeAuthorizationObject(TEST_PREFIX + "student1"));
        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, OK);
        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(batch.getPassword()), QuizBatch.class, OK);

        // Start participation
        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, OK);

        // Get for-student
        MvcResult result = request.performMvcRequest(get("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/for-student")).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(content);
        assertThat(json.has("quizQuestions")).isTrue();
        assertThat(json.get("quizQuestions").size()).isEqualTo(3);

        // Check no solutions, e.g. MC no isCorrect
        JsonNode mc = json.get("quizQuestions").get(0);
        JsonNode ao = mc.get("answerOptions").get(0);
        assertThat(ao.has("isCorrect")).isFalse();

        QuizExerciseWithQuestionsDTO dto = objectMapper.readValue(content, QuizExerciseWithQuestionsDTO.class);
        assertThat(dto.quizQuestions()).hasSize(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizWithGradingCriterion() throws Exception {
        // Create a quiz exercise
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);

        // Create a grading criterion
        GradingCriterion criterion = new GradingCriterion();
        criterion.setTitle("Test Criterion");
        criterion.setExercise(quizExercise);

        // Create a grading instruction
        GradingInstruction instruction = new GradingInstruction();
        instruction.setCredits(2.0);
        instruction.setGradingScale("Good");
        instruction.setInstructionDescription("Use this for testing");
        instruction.setFeedback("Test feedback");
        instruction.setUsageCount(0);
        instruction.setGradingCriterion(criterion);

        criterion.setStructuredGradingInstructions(Set.of(instruction));

        // Set grading criteria to the quiz
        quizExercise.setGradingCriteria(Set.of(criterion));
        quizExercise = quizExerciseService.save(quizExercise);

        // Fetch the quiz exercise
        QuizExercise fetchedQuiz = request.get("/api/quiz/quiz-exercises/" + quizExercise.getId(), OK, QuizExercise.class);

        // Verify the grading criterion is in the response
        assertThat(fetchedQuiz.getGradingCriteria()).hasSize(1);
        GradingCriterion fetchedCriterion = fetchedQuiz.getGradingCriteria().iterator().next();
        assertThat(fetchedCriterion.getTitle()).isEqualTo("Test Criterion");
        assertThat(fetchedCriterion.getStructuredGradingInstructions()).hasSize(1);
        GradingInstruction fetchedInstruction = fetchedCriterion.getStructuredGradingInstructions().iterator().next();
        assertThat(fetchedInstruction.getCredits()).isEqualTo(2.0);
        assertThat(fetchedInstruction.getGradingScale()).isEqualTo("Good");
        assertThat(fetchedInstruction.getInstructionDescription()).isEqualTo("Use this for testing");
        assertThat(fetchedInstruction.getFeedback()).isEqualTo("Test feedback");
    }

    /**
     * test import quiz exercise to same course and check if fields are correctly set for import
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importQuizExerciseToSameCourse() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(now.plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.handleDndQuizFileCreation(quizExercise,
                List.of(new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage2.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes()),
                        new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage4.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes())));
        quizExerciseService.save(quizExercise);

        QuizExercise changedQuiz = quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(changedQuiz).isNotNull();
        changedQuiz.setTitle("New title");
        changedQuiz.setReleaseDate(now);

        QuizExercise importedExercise = importQuizExerciseWithFiles(changedQuiz, List.of(), HttpStatus.CREATED);

        assertThat(importedExercise.getId()).as("Imported exercise has different id").isNotEqualTo(quizExercise.getId());
        assertThat(importedExercise.getTitle()).as("Imported exercise has updated title").isEqualTo("New title");
        assertThat(importedExercise.getReleaseDate()).as("Imported exercise has updated release data").isEqualTo(now);
        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("Imported exercise has same course")
                .isEqualTo(quizExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember()).isEqualTo(quizExercise.getCourseViaExerciseGroupOrCourseMember());
        assertThat(importedExercise.getQuizQuestions()).as("Imported exercise has same number of questions").hasSameSizeAs(quizExercise.getQuizQuestions());
    }

    /**
     * test import quiz exercise to a different course
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importQuizExerciseFromCourseToCourse() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(now.plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.handleDndQuizFileCreation(quizExercise,
                List.of(new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage2.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes()),
                        new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage4.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes())));
        quizExerciseService.save(quizExercise);

        Course course = courseUtilService.addEmptyCourse();
        quizExercise.setCourse(course);

        QuizExercise importedExercise = importQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.CREATED);
        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember()).as("Quiz was imported for different course").isEqualTo(course);
    }

    /**
     * test import quiz exercise from a course to an exam
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importQuizExerciseFromCourseToExam() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(2), null, QuizMode.SYNCHRONIZED);
        ExerciseGroup exerciseGroup = examUtilService.createAndSaveActiveExerciseGroup(createEmptyCourse(), true);
        quizExerciseService.handleDndQuizFileCreation(quizExercise,
                List.of(new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage2.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes()),
                        new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage4.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes())));
        quizExerciseService.save(quizExercise);
        quizExerciseUtilService.emptyOutQuizExercise(quizExercise);
        quizExercise.setExerciseGroup(exerciseGroup);

        QuizExercise importedExercise = importQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.CREATED);
        assertThat(importedExercise.getExerciseGroup()).as("Quiz was imported for different exercise group").isEqualTo(exerciseGroup);
    }

    /**
     * test import quiz exercise to exam with invalid roles
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void importQuizExerciseFromCourseToExam_forbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusHours(2), null, QuizMode.SYNCHRONIZED);
        ExerciseGroup exerciseGroup = examUtilService.createAndSaveActiveExerciseGroup(createEmptyCourse(), true);

        quizExerciseUtilService.emptyOutQuizExercise(quizExercise);
        quizExercise.setExerciseGroup(exerciseGroup);

        importQuizExerciseWithFiles(quizExercise, List.of(), FORBIDDEN);
    }

    /**
     * test import quiz exercise from exam to course
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importQuizExerciseFromExamToCourse() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveExamQuiz(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1));
        quizExercise.setExerciseGroup(null);
        Course course = courseUtilService.addEmptyCourse();
        quizExerciseService.handleDndQuizFileCreation(quizExercise,
                List.of(new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage2.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes()),
                        new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage4.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes())));
        quizExerciseService.save(quizExercise);
        quizExercise = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
        quizExercise.setCourse(course);

        QuizExercise importedExercise = importQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.CREATED);
        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember()).isEqualTo(course);
    }

    /**
     * test import quiz exercise from exam to course with invalid roles
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void importQuizExerciseFromExamToCourse_forbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveExamQuiz(ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));

        quizExercise.setExerciseGroup(null);
        Course course1 = courseUtilService.addEmptyCourse();
        quizExercise.setCourse(course1);

        importQuizExerciseWithFiles(quizExercise, List.of(), FORBIDDEN);
    }

    /**
     * test import quiz exercise from one exam to a different exam
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importQuizExerciseFromExamToExam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.createAndSaveActiveExerciseGroup(createEmptyCourse(), true);
        QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exerciseGroup);
        quizExerciseService.handleDndQuizFileCreation(quizExercise,
                List.of(new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage2.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes()),
                        new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage4.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes())));
        quizExerciseService.save(quizExercise);

        QuizExercise importedExercise = importQuizExerciseWithFiles(quizExercise, List.of(), HttpStatus.CREATED);
        assertThat(importedExercise.getExerciseGroup()).as("Quiz was imported for different exercise group").isEqualTo(exerciseGroup);
    }

    /**
     * test import quiz exercise with changed team mode
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportQuizExercise_individual_modeChange() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExercise.setMode(ExerciseMode.TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(quizExercise);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        quizExercise.setTeamAssignmentConfig(teamAssignmentConfig);

        quizExerciseService.handleDndQuizFileCreation(quizExercise,
                List.of(new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage2.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes()),
                        new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage4.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes())));

        quizExercise = quizExerciseService.save(quizExercise);
        var team = new Team();
        team.setShortName(TEST_PREFIX + "testImportQuizExercise_individual_modeChange");
        teamRepository.save(quizExercise, team);

        QuizExercise changedQuiz = quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(changedQuiz).isNotNull();

        changedQuiz.setMode(ExerciseMode.INDIVIDUAL);
        Course course = courseUtilService.addEmptyCourse();
        changedQuiz.setCourse(course);

        changedQuiz = importQuizExerciseWithFiles(changedQuiz, List.of(), HttpStatus.CREATED);

        assertThat(changedQuiz.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course.getId());
        assertThat(changedQuiz.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(changedQuiz.getTeamAssignmentConfig()).isNull();
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(changedQuiz, null)).isEmpty();

        quizExercise = quizExerciseTestRepository.findByIdElseThrow(quizExercise.getId());
        assertThat(quizExercise.getMode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(quizExercise, null)).hasSize(1);
    }

    /**
     * test import quiz exercise with changed quiz mode
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportQuizExerciseChangeQuizMode() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(2), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.handleDndQuizFileCreation(quizExercise,
                List.of(new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage2.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes()),
                        new MockMultipartFile("files", "drag-and-drop/drag-items/dragItemImage4.png", MediaType.IMAGE_PNG_VALUE, "dragItemImage".getBytes())));
        quizExerciseService.save(quizExercise);

        QuizExercise changedQuiz = quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(changedQuiz).isNotNull();
        changedQuiz.setQuizMode(QuizMode.INDIVIDUAL);

        QuizExercise importedExercise = importQuizExerciseWithFiles(changedQuiz, List.of(), HttpStatus.CREATED);

        assertThat(importedExercise.getId()).as("Imported exercise has different id").isNotEqualTo(quizExercise.getId());
        assertThat(importedExercise.getQuizMode()).as("Imported exercise has different quiz mode").isEqualTo(QuizMode.INDIVIDUAL);
    }

    /**
     * test redundant actions performed on quiz exercises will result in bad request
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRedundantActionsBadRequest() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusHours(5), null, QuizMode.SYNCHRONIZED);

        // set-visible
        assertThat(quizExercise.isVisibleToStudents()).isTrue();
        request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/set-visible", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);

        // start-now
        assertThat(quizExercise.isQuizStarted()).isTrue();
        request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-now", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);

        // misspelled request
        request.putWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/lorem-ipsum", quizExercise, QuizExercise.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * test that a quiz question with an explanation within valid length can be created
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testMultipleChoiceQuizExplanationLength_Valid() throws Exception {
        int validityThreshold = 500;
        QuizExercise quizExercise = createMultipleChoiceQuizExercise();

        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        question.setExplanation("0".repeat(validityThreshold));

        createQuizExerciseWithFiles(quizExercise, HttpStatus.CREATED, true);
    }

    /**
     * test that a quiz question with an explanation without valid length can't be created
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testMultipleChoiceQuizExplanationLength_Invalid() throws Exception {
        int validityThreshold = 500;
        QuizExercise quizExercise = createMultipleChoiceQuizExercise();

        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        question.setExplanation("0".repeat(validityThreshold + 1));

        createQuizExerciseWithFiles(quizExercise, HttpStatus.INTERNAL_SERVER_ERROR, true);
    }

    /**
     * test that a quiz question with an option with an explanation with valid length can be created
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testMultipleChoiceQuizOptionExplanationLength_Valid() throws Exception {
        int validityThreshold = 500;
        QuizExercise quizExercise = createMultipleChoiceQuizExercise();

        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        question.getAnswerOptions().getFirst().setExplanation("0".repeat(validityThreshold));

        createQuizExerciseWithFiles(quizExercise, HttpStatus.CREATED, true);
    }

    /**
     * test that a quiz question with an option with an explanation without valid length can't be created
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testMultipleChoiceQuizOptionExplanationLength_Invalid() throws Exception {
        int validityThreshold = 500;
        QuizExercise quizExercise = createMultipleChoiceQuizExercise();

        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        question.getAnswerOptions().getFirst().setExplanation("0".repeat(validityThreshold + 1));

        createQuizExerciseWithFiles(quizExercise, HttpStatus.INTERNAL_SERVER_ERROR, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReset() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().plusHours(2), null, QuizMode.SYNCHRONIZED);

        for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
            quizQuestion.setInvalid(true);
        }
        request.delete("/api/exercise/exercises/" + quizExercise.getId() + "/reset", OK);

        quizExercise = quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExercise).isNotNull();
        assertThat(quizExercise.getReleaseDate()).as("Quiz Question is released").isBeforeOrEqualTo(ZonedDateTime.now());
        assertThat(quizExercise.getDueDate()).as("Quiz Question due date has been set to null").isNull();
        assertThat(quizExercise.getQuizBatches()).as("Quiz Question batches has been set to empty").isEmpty();

        for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
            assertThat(quizQuestion.isInvalid()).as("Quiz Question invalid flag has been set to false").isFalse();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createQuizExercise_dragAndDrop_withoutBackgroundFile() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setDuration(3600);
        createQuizExerciseWithFiles(quizExercise, HttpStatus.CREATED, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createQuizExercise_withoutDragAndDrop() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setQuizQuestions(quizExercise.getQuizQuestions().stream().filter(question -> !(question instanceof DragAndDropQuestion)).toList());
        quizExercise.setDuration(3600);
        createQuizExerciseWithFiles(quizExercise, HttpStatus.CREATED, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateQuizExercise_withoutDragAndDrop() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setQuizQuestions(quizExercise.getQuizQuestions().stream().filter(question -> !(question instanceof DragAndDropQuestion)).toList());
        quizExercise.setDuration(3600);
        quizExercise = createQuizExerciseWithFiles(quizExercise, HttpStatus.CREATED, false);
        updateQuizExerciseWithFiles(quizExercise, null, OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateQuizExercise_dragAndDrop_withoutFileArrayProvided() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        updateQuizExerciseWithFiles(quizExercise, null, OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateQuizExercise_dragAndDrop_withFileChanges() throws Exception {
        QuizExercise quizExercise = createQuizOnServer(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
        String newBackgroundFilePath = "newBackgroundFile.png";
        String newPictureFilePath = "newPictureFile.jpg";

        List<DragAndDropQuestion> dragAndDropQuestions = quizExercise.getQuizQuestions().stream().filter(q -> q instanceof DragAndDropQuestion).map(q -> (DragAndDropQuestion) q)
                .toList();
        DragAndDropQuestion question = dragAndDropQuestions.getFirst();
        question.setBackgroundFilePath(newBackgroundFilePath);
        DragItem item = question.getDragItems().get(1);
        item.setPictureFilePath(newPictureFilePath);

        updateQuizExerciseWithFiles(quizExercise, List.of(newBackgroundFilePath, newPictureFilePath), OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFilterForCourseDashboard_QuizSubmissionButNoParticipation() {
        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        QuizExercise quizExercise = (QuizExercise) course.getExercises().stream().findFirst().get();

        QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, true, ZonedDateTime.now());
        participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student1");

        exerciseService.filterExerciseForCourseDashboard(quizExercise, Set.of((StudentParticipation) quizSubmission.getParticipation()), true);

        assertThat(quizExercise.getStudentParticipations()).hasSize(1);
        assertThat(quizExercise.getStudentParticipations()).containsExactlyInAnyOrder((StudentParticipation) quizSubmission.getParticipation());
    }

    private void createdQuizAssert(QuizExercise quizExercise) throws Exception {
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
                assertThat(answerOptions.getFirst().getText()).as("Text for answer option is correct").isEqualTo("A");
                assertThat(answerOptions.getFirst().getHint()).as("Hint for answer option is correct").isEqualTo("H1");
                assertThat(answerOptions.getFirst().getExplanation()).as("Explanation for answer option is correct").isEqualTo("E1");
                assertThat(answerOptions.getFirst().isIsCorrect()).as("Is correct for answer option is correct").isTrue();
                assertThat(answerOptions.get(1).getText()).as("Text for answer option is correct").isEqualTo("B");
                assertThat(answerOptions.get(1).getHint()).as("Hint for answer option is correct").isEqualTo("H2");
                assertThat(answerOptions.get(1).getExplanation()).as("Explanation for answer option is correct").isEqualTo("E2");
                assertThat(answerOptions.get(1).isIsCorrect()).as("Is correct for answer option is correct").isFalse();
            }
            else if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                assertThat(dragAndDropQuestion.getDropLocations()).as("Drag and drop question drop locations were saved").hasSize(4);
                assertThat(dragAndDropQuestion.getDragItems()).as("Drag and drop question drag items were saved").hasSize(4);
                assertThat(dragAndDropQuestion.getTitle()).as("Drag and drop question title is correct").isEqualTo("DnD");
                assertThat(dragAndDropQuestion.getText()).as("Drag and drop question text is correct").isEqualTo("Q2");
                assertThat(dragAndDropQuestion.getPoints()).as("Drag and drop question score is correct").isEqualTo(3);

                List<DropLocation> dropLocations = dragAndDropQuestion.getDropLocations();
                assertThat(dropLocations.getFirst().getPosX()).as("Pos X for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.getFirst().getPosY()).as("Pos Y for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.getFirst().getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.getFirst().getHeight()).as("Height for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.getFirst().getQuestion()).isNotNull();
                assertThat(dropLocations.get(1).getPosX()).as("Pos X for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(1).getPosY()).as("Pos Y for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.get(1).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(1).getHeight()).as("Height for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(1).getQuestion()).isNotNull();
                assertThat(dropLocations.get(2).getPosX()).as("Pos X for drop location is correct").isEqualTo(30);
                assertThat(dropLocations.get(2).getPosY()).as("Pos Y for drop location is correct").isEqualTo(30);
                assertThat(dropLocations.get(2).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(2).getHeight()).as("Height for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(2).getQuestion()).isNotNull();
                assertThat(dropLocations.get(3).getPosX()).as("Pos X for drop location is correct").isEqualTo(40);
                assertThat(dropLocations.get(3).getPosY()).as("Pos Y for drop location is correct").isEqualTo(40);
                assertThat(dropLocations.get(3).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(3).getHeight()).as("Height for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(3).getQuestion()).isNotNull();

                List<DragItem> dragItems = dragAndDropQuestion.getDragItems();
                assertThat(dragItems.getFirst().getText()).as("Text for drag item is correct").isEqualTo("D1");
                assertThat(dragItems.getFirst().getPictureFilePath()).as("Picture file path for drag item is correct").isNull();
                assertThat(dragItems.get(1).getText()).as("Text for drag item is correct").isNull();
                assertThat(dragItems.get(1).getPictureFilePath()).as("Picture file path for drag item is correct").isNotEmpty();
                assertThat(dragItems.get(2).getText()).as("Text for drag item is correct").isEqualTo("D3");
                assertThat(dragItems.get(2).getPictureFilePath()).as("Picture file path for drag item is correct").isNull();
                assertThat(dragItems.get(3).getText()).as("Text for drag item is correct").isNull();
                assertThat(dragItems.get(3).getPictureFilePath()).as("Picture file path for drag item is correct").isNotEmpty();

                String requestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, dragAndDropQuestion.getBackgroundFilePath());
                assertThat(request.get(requestUrl, OK, byte[].class)).isNotEmpty();

                for (DragItem dragItem : dragItems) {
                    if (dragItem.getPictureFilePath() != null) {
                        String requestUrlPath = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, dragItem.getPictureFilePath());
                        assertThat(request.get(requestUrlPath, OK, byte[].class)).isNotEmpty();
                    }
                }
            }
            else if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
                assertThat(shortAnswerQuestion.getSpots()).as("Short answer question spots were saved").hasSize(2);
                assertThat(shortAnswerQuestion.getSolutions()).as("Short answer question solutions were saved").hasSize(2);
                assertThat(shortAnswerQuestion.getTitle()).as("Short answer question title is correct").isEqualTo("SA");
                assertThat(shortAnswerQuestion.getText()).as("Short answer question text is correct").isEqualTo("This is a long answer text");
                assertThat(shortAnswerQuestion.getPoints()).as("Short answer question score is correct").isEqualTo(2);

                List<ShortAnswerSpot> spots = shortAnswerQuestion.getSpots();
                assertThat(spots.getFirst().getSpotNr()).as("Spot nr for spot is correct").isZero();
                assertThat(spots.getFirst().getWidth()).as("Width for spot is correct").isEqualTo(1);
                assertThat(spots.get(1).getSpotNr()).as("Spot nr for spot is correct").isEqualTo(2);
                assertThat(spots.get(1).getWidth()).as("Width for spot is correct").isEqualTo(2);

                List<ShortAnswerSolution> solutions = shortAnswerQuestion.getSolutions();
                assertThat(solutions.getFirst().getText()).as("Text for solution is correct").isEqualTo("is");
                assertThat(solutions.get(1).getText()).as("Text for solution is correct").isEqualTo("long");
            }
        }
    }

    private void updateQuizAndAssert(QuizExercise quizExercise) throws Exception {
        updateMultipleChoice(quizExercise);

        quizExercise = updateQuizExerciseWithFiles(quizExercise, List.of(), OK);

        // Quiz type specific assertions
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                assertThat(multipleChoiceQuestion.getAnswerOptions()).as("Multiple choice question answer options were saved").hasSize(3);
                assertThat(multipleChoiceQuestion.getTitle()).as("Multiple choice question title is correct").isEqualTo("MC");
                assertThat(multipleChoiceQuestion.getText()).as("Multiple choice question text is correct").isEqualTo("Q1");
                assertThat(multipleChoiceQuestion.getPoints()).as("Multiple choice question score is correct").isEqualTo(4);

                List<AnswerOption> answerOptions = multipleChoiceQuestion.getAnswerOptions();
                assertThat(answerOptions.getFirst().getText()).as("Text for answer option is correct").isEqualTo("B");
                assertThat(answerOptions.getFirst().getHint()).as("Hint for answer option is correct").isEqualTo("H2");
                assertThat(answerOptions.getFirst().getExplanation()).as("Explanation for answer option is correct").isEqualTo("E2");
                assertThat(answerOptions.getFirst().isIsCorrect()).as("Is correct for answer option is correct").isFalse();
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
                assertThat(dragAndDropQuestion.getDropLocations()).as("Drag and drop question drop locations were saved").hasSize(3);
                assertThat(dragAndDropQuestion.getDragItems()).as("Drag and drop question drag items were saved").hasSize(3);
                assertThat(dragAndDropQuestion.getTitle()).as("Drag and drop question title is correct").isEqualTo("DnD");
                assertThat(dragAndDropQuestion.getText()).as("Drag and drop question text is correct").isEqualTo("Q2");
                assertThat(dragAndDropQuestion.getPoints()).as("Drag and drop question score is correct").isEqualTo(3);

                List<DropLocation> dropLocations = dragAndDropQuestion.getDropLocations();
                assertThat(dropLocations.getFirst().getPosX()).as("Pos X for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.getFirst().getPosY()).as("Pos Y for drop location is correct").isEqualTo(20);
                assertThat(dropLocations.getFirst().getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.getFirst().getHeight()).as("Height for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(1).getPosX()).as("Pos X for drop location is correct").isEqualTo(30);
                assertThat(dropLocations.get(1).getPosY()).as("Pos Y for drop location is correct").isEqualTo(30);
                assertThat(dropLocations.get(1).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(1).getHeight()).as("Height for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(2).getPosX()).as("Pos X for drop location is correct").isEqualTo(40);
                assertThat(dropLocations.get(2).getPosY()).as("Pos Y for drop location is correct").isEqualTo(40);
                assertThat(dropLocations.get(2).getWidth()).as("Width for drop location is correct").isEqualTo(10);
                assertThat(dropLocations.get(2).getHeight()).as("Height for drop location is correct").isEqualTo(10);

                List<DragItem> dragItems = dragAndDropQuestion.getDragItems();
                assertThat(dragItems.getFirst().getText()).as("Text for drag item is correct").isNull();
                assertThat(dragItems.getFirst().getPictureFilePath()).as("Picture file path for drag item is correct").isNotEmpty();
                assertThat(dragItems.get(1).getText()).as("Text for drag item is correct").isEqualTo("D3");
                assertThat(dragItems.get(1).getPictureFilePath()).as("Picture file path for drag item is correct").isNull();
                assertThat(dragItems.get(2).getText()).as("Text for drag item is correct").isNull();
                assertThat(dragItems.get(2).getPictureFilePath()).as("Picture file path for drag item is correct").isNotEmpty();
            }
            else if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
                assertThat(shortAnswerQuestion.getSpots()).as("Short answer question spots were saved").hasSize(1);
                assertThat(shortAnswerQuestion.getSolutions()).as("Short answer question solutions were saved").hasSize(1);
                assertThat(shortAnswerQuestion.getTitle()).as("Short answer question title is correct").isEqualTo("SA");
                assertThat(shortAnswerQuestion.getText()).as("Short answer question text is correct").isEqualTo("This is a long answer text");
                assertThat(shortAnswerQuestion.getPoints()).as("Short answer question score is correct").isEqualTo(2);

                List<ShortAnswerSpot> spots = shortAnswerQuestion.getSpots();
                assertThat(spots.getFirst().getSpotNr()).as("Spot nr for spot is correct").isEqualTo(2);
                assertThat(spots.getFirst().getWidth()).as("Width for spot is correct").isEqualTo(2);

                List<ShortAnswerSolution> solutions = shortAnswerQuestion.getSolutions();
                assertThat(solutions.getFirst().getText()).as("Text for solution is correct").isEqualTo("long");
            }
        }
    }

    private void updateMultipleChoice(QuizExercise quizExercise) {
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        mc.getAnswerOptions().removeFirst();
        mc.getAnswerOptions().add(new AnswerOption().text("C").hint("H3").explanation("E3").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("D").hint("H4").explanation("E4").isCorrect(true));

        DragAndDropQuestion dnd = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        dnd.getDropLocations().removeFirst();
        dnd.getCorrectMappings().removeFirst();
        dnd.getDragItems().removeFirst();

        ShortAnswerQuestion sa = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);
        sa.getSpots().removeFirst();
        sa.getSolutions().removeFirst();
        sa.getCorrectMappings().removeFirst();
    }

    private QuizExercise createMultipleChoiceQuizExercise() {
        Course course = quizExerciseUtilService.createAndSaveCourse(null, ZonedDateTime.now().minusDays(1), null, Set.of());
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED, course);
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(4d).text("Q1");

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
        assertThat(quizExercise.getQuizQuestions()).as("Same amount of questions saved").hasSameSizeAs(quizExercise2.getQuizQuestions());
        assertThat(quizExercise.getTitle()).as("Title saved correctly").isEqualTo(quizExercise2.getTitle());
        assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Number of attempts saved correctly").isEqualTo(quizExercise2.getAllowedNumberOfAttempts());
        assertThat(quizExercise.getMaxPoints()).as("Max score saved correctly").isEqualTo(quizExercise2.getMaxPoints());
        assertThat(quizExercise.getDuration()).as("Duration saved correctly").isEqualTo(quizExercise2.getDuration());
        assertThat(quizExercise.getType()).as("Type saved correctly").isEqualTo(quizExercise2.getType());
    }

    private PointCounter pc(int rated, int unrated) {
        PointCounter pointCounter = new PointCounter();
        pointCounter.setRatedCounter(rated);
        pointCounter.setUnRatedCounter(unrated);
        return pointCounter;
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
                    log.debug("AnswerCounters: {}", counter.toString());
                    log.debug("MultipleChoiceQuestionStatistic: {}", counter.getMultipleChoiceQuestionStatistic());
                }
            }
            else if (statistic instanceof DragAndDropQuestionStatistic dndStatistic) {
                assertThat(dndStatistic.getDropLocationCounters()).isNotEmpty();
                for (var counter : dndStatistic.getDropLocationCounters()) {
                    log.debug("DropLocationCounters: {}", counter.toString());
                    log.debug("DragAndDropQuestionStatistic: {}", counter.getDragAndDropQuestionStatistic());
                }
            }
            else if (statistic instanceof ShortAnswerQuestionStatistic saStatistic) {
                assertThat(saStatistic.getShortAnswerSpotCounters()).isNotEmpty();
                for (var counter : saStatistic.getShortAnswerSpotCounters()) {
                    log.debug("ShortAnswerSpotCounters: {}", counter.toString());
                    log.debug("ShortAnswerQuestionStatistic: {}", counter.getShortAnswerQuestionStatistic());
                }
            }
        }
    }

    private void assertQuizPointStatisticsPointCounters(QuizExercise quizExercise, Map<Double, PointCounter> expectedPointCounters) {
        for (PointCounter pointCounter : quizExercise.getQuizPointStatistic().getPointCounters()) {
            PointCounter expectedPointCounter = expectedPointCounters.get(pointCounter.getPoints());
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

    /**
     * Check if a QuizExercise contains the correct information for students.
     *
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
