package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.util.HibernateQueryInterceptor.CapturedQueries;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseCreateDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithStatisticsDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.UpdateQuizExerciseDTO;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionFromStudentDTO;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;

/**
 * Optional, non-gating benchmark for comparing relational and JSON quiz persistence revisions.
 * <p>
 * Run explicitly with {@code ./gradlew test --tests de.tum.cit.aet.artemis.quiz.QuizPersistenceBenchmarkTest -DincludeTags=benchmark -x webapp}. Use the
 * {@code testMysql} task instead of {@code test} for the reduced MySQL compatibility run.
 */
class QuizPersistenceBenchmarkTest extends AbstractQuizExerciseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(QuizPersistenceBenchmarkTest.class);

    private static final String TEST_PREFIX = "quizpersistencebenchmark";

    private static final int WARMUP_RUNS = 3;

    private static final int MEASURED_RUNS = 10;

    private static final int REEVALUATION_SUBMISSIONS = 100;

    private static final long NEW_DND_ITEM_TEMP_ID = -101L;

    private static final long NEW_DND_LOCATION_TEMP_ID = -102L;

    private static final long NEW_SA_SPOT_TEMP_ID = -103L;

    private static final long NEW_SA_SOLUTION_TEMP_ID = -104L;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private QuizSubmissionTestRepository quizSubmissionTestRepository;

    private Long benchmarkCourseId;

    @BeforeEach
    void initBenchmarkUsers() {
        userUtilService.addUsers(TEST_PREFIX, REEVALUATION_SUBMISSIONS, 1, 1, 1);
        ZonedDateTime now = ZonedDateTime.now();
        Course benchmarkCourse = quizExerciseUtilService.createAndSaveCourse(null, now.minusDays(1), now.plusDays(30), Set.of());
        benchmarkCourseId = benchmarkCourse.getId();
    }

    @Test
    @Tag("benchmark")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void benchmarkInstructorWorkflows() throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();
        for (BenchmarkDataSet dataSet : BenchmarkDataSet.values()) {
            results.add(runBenchmark("Load quiz for editing", dataSet, this::prepareLoadScenario));
            results.add(runBenchmark("Save text-only edit", dataSet, this::prepareTextEditScenario));
            results.add(runBenchmark("Save structural component edit", dataSet, this::prepareStructuralEditScenario));
            results.add(runBenchmark("Duplicate quiz", dataSet, this::prepareDuplicateScenario));
        }
        logResults(results);
    }

    @Test
    @Tag("benchmark")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void benchmarkReEvaluationWorkflow() throws Exception {
        logResults(List.of(runBenchmark("Re-evaluate 100 submissions", BenchmarkDataSet.TYPICAL, this::prepareReEvaluationScenario)));
    }

    @Test
    @Tag("benchmark")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void benchmarkStudentSubmissionWorkflow() throws Exception {
        logResults(List.of(runBenchmark("Submit student answer", BenchmarkDataSet.TYPICAL, this::preparePracticeSubmissionScenario)));
    }

    private BenchmarkResult runBenchmark(String workflow, BenchmarkDataSet dataSet, ScenarioFactory scenarioFactory) throws Exception {
        List<Measurement> measurements = new ArrayList<>();
        for (int run = 0; run < WARMUP_RUNS + MEASURED_RUNS; run++) {
            BenchmarkScenario scenario = scenarioFactory.prepare(dataSet);
            Measurement measurement = measureScenario(scenario);
            if (run >= WARMUP_RUNS) {
                measurements.add(measurement);
            }
        }
        return BenchmarkResult.of(workflow, dataSet, measurements);
    }

    private Measurement measureScenario(BenchmarkScenario scenario) throws Exception {
        flushAndClearPersistenceContext();
        queryInterceptor.startQueryCapture();
        long startNanos = System.nanoTime();
        MvcResult result;
        CapturedQueries capturedQueries;
        try {
            result = scenario.request().execute();
            long elapsedNanos = System.nanoTime() - startNanos;
            capturedQueries = queryInterceptor.stopQueryCapture();
            request.restoreSecurityContext();

            assertThat(result.getResponse().getStatus()).isEqualTo(scenario.expectedStatus().value());
            Long targetQuizId = scenario.verifier().verifyAndResolveTargetQuiz(result);
            entityManager.clear();
            QuizExercise persistedQuiz = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(targetQuizId);
            long persistedDefinitionBytes = objectMapper.writeValueAsBytes(QuizExerciseCreateDTO.of(persistedQuiz)).length;
            return Measurement.of(elapsedNanos, scenario.requestBytes(), result.getResponse().getContentAsByteArray().length, persistedDefinitionBytes, capturedQueries);
        }
        catch (Exception exception) {
            queryInterceptor.stopQueryCapture();
            request.restoreSecurityContext();
            throw exception;
        }
    }

    private BenchmarkScenario prepareLoadScenario(BenchmarkDataSet dataSet) {
        QuizExercise fixture = createMixedQuizFixture(dataSet, QuizLifecycle.AUTHORING);
        var builder = MockMvcRequestBuilders.get("/api/quiz/quiz-exercises/" + fixture.getId());
        return new BenchmarkScenario(0, HttpStatus.OK, () -> request.performMvcRequest(builder).andReturn(), result -> {
            assertThat(result.getResponse().getContentAsString()).isNotBlank();
            verifyQuizDefinition(fixture.getId(), dataSet, 0);
            return fixture.getId();
        });
    }

    private BenchmarkScenario prepareTextEditScenario(BenchmarkDataSet dataSet) throws Exception {
        QuizExercise fixture = createMixedQuizFixture(dataSet, QuizLifecycle.AUTHORING);
        ObjectNode update = objectMapper.valueToTree(UpdateQuizExerciseDTO.of(fixture));
        String updatedTitle = "Text-only benchmark " + UUID.randomUUID();
        update.put("title", updatedTitle);
        for (JsonNode question : update.withArray("quizQuestions")) {
            ((ObjectNode) question).put("text", question.path("text").asText() + " edited");
        }
        byte[] payload = objectMapper.writeValueAsBytes(update);
        var builder = multipartRequest(HttpMethod.PUT, "/api/quiz/quiz-exercises/" + fixture.getId(), payload);
        return new BenchmarkScenario(payload.length, HttpStatus.OK, () -> request.performMvcRequest(builder).andReturn(), result -> {
            assertThat(result.getResponse().getContentAsString()).isNotBlank();
            QuizExercise updatedQuiz = verifyQuizDefinition(fixture.getId(), dataSet, 0);
            assertThat(updatedQuiz.getTitle()).isEqualTo(updatedTitle);
            return fixture.getId();
        });
    }

    private BenchmarkScenario prepareStructuralEditScenario(BenchmarkDataSet dataSet) throws Exception {
        QuizExercise fixture = createMixedQuizFixture(dataSet, QuizLifecycle.AUTHORING);
        ObjectNode update = objectMapper.valueToTree(UpdateQuizExerciseDTO.of(fixture));
        addStructuralComponents(update);
        byte[] payload = objectMapper.writeValueAsBytes(update);
        var builder = multipartRequest(HttpMethod.PUT, "/api/quiz/quiz-exercises/" + fixture.getId(), payload);
        return new BenchmarkScenario(payload.length, HttpStatus.OK, () -> request.performMvcRequest(builder).andReturn(), result -> {
            assertThat(result.getResponse().getContentAsString()).isNotBlank();
            verifyQuizDefinition(fixture.getId(), dataSet, 1);
            return fixture.getId();
        });
    }

    private BenchmarkScenario prepareDuplicateScenario(BenchmarkDataSet dataSet) throws Exception {
        QuizExercise fixture = createMixedQuizFixture(dataSet, QuizLifecycle.AUTHORING);
        ObjectNode duplicate = objectMapper.valueToTree(QuizExerciseCreateDTO.of(fixture));
        duplicate.put("title", "Duplicated benchmark quiz " + UUID.randomUUID());
        duplicate.set("quizBatches", objectMapper.createArrayNode());
        byte[] payload = objectMapper.writeValueAsBytes(duplicate);
        var builder = multipartRequest(HttpMethod.POST, "/api/quiz/courses/" + benchmarkCourseId + "/quiz-exercises", payload);
        return new BenchmarkScenario(payload.length, HttpStatus.CREATED, () -> request.performMvcRequest(builder).andReturn(), result -> {
            QuizExerciseWithStatisticsDTO response = objectMapper.readValue(result.getResponse().getContentAsByteArray(), QuizExerciseWithStatisticsDTO.class);
            Long duplicateId = response.quizExercise().id();
            assertThat(duplicateId).isNotEqualTo(fixture.getId());
            verifyQuizDefinition(duplicateId, dataSet, 0);
            return duplicateId;
        });
    }

    private BenchmarkScenario prepareReEvaluationScenario(BenchmarkDataSet dataSet) throws Exception {
        QuizExercise fixture = createMixedQuizFixture(dataSet, QuizLifecycle.ENDED);
        createReEvaluationSubmissions(fixture);
        ObjectNode reEvaluation = objectMapper.valueToTree(QuizExerciseReEvaluateDTO.of(fixture));
        ObjectNode multipleChoiceQuestion = findQuestionByType(reEvaluation.withArray("quizQuestions"), "multiple-choice");
        ObjectNode firstAnswerOption = (ObjectNode) multipleChoiceQuestion.withArray("answerOptions").get(0);
        firstAnswerOption.put("isCorrect", !firstAnswerOption.path("isCorrect").asBoolean());
        byte[] payload = objectMapper.writeValueAsBytes(reEvaluation);
        var builder = multipartRequest(HttpMethod.PUT, "/api/quiz/quiz-exercises/" + fixture.getId() + "/re-evaluate", payload);
        return new BenchmarkScenario(payload.length, HttpStatus.OK, () -> request.performMvcRequest(builder).andReturn(), result -> {
            assertThat(studentParticipationRepository.findByExerciseId(fixture.getId())).hasSize(REEVALUATION_SUBMISSIONS);
            assertThat(resultRepository.findAllBySubmissionParticipationExerciseId(fixture.getId())).hasSize(REEVALUATION_SUBMISSIONS);
            verifyQuizDefinition(fixture.getId(), dataSet, 0);
            return fixture.getId();
        });
    }

    private BenchmarkScenario preparePracticeSubmissionScenario(BenchmarkDataSet dataSet) throws Exception {
        QuizExercise fixture = createMixedQuizFixture(dataSet, QuizLifecycle.ENDED);
        QuizSubmission submission = createSubmission(fixture, SubmissionProfile.CORRECT);
        byte[] payload = objectMapper.writeValueAsBytes(QuizSubmissionFromStudentDTO.of(submission));
        var builder = MockMvcRequestBuilders.post("/api/quiz/exercises/" + fixture.getId() + "/submissions/practice").contentType(MediaType.APPLICATION_JSON).content(payload);
        return new BenchmarkScenario(payload.length, HttpStatus.OK, () -> request.performMvcRequest(builder).andReturn(), result -> {
            assertThat(result.getResponse().getContentAsString()).isNotBlank();
            assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(fixture.getId())).hasSize(1);
            assertThat(resultRepository.findAllBySubmissionParticipationExerciseId(fixture.getId())).hasSize(1);
            verifyQuizDefinition(fixture.getId(), dataSet, 0);
            return fixture.getId();
        });
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder multipartRequest(HttpMethod method, String url, byte[] payload) {
        var builder = MockMvcRequestBuilders.multipart(method, url);
        builder.file(new MockMultipartFile("exercise", "", MediaType.APPLICATION_JSON_VALUE, payload)).contentType(MediaType.MULTIPART_FORM_DATA);
        return builder;
    }

    private void addStructuralComponents(ObjectNode update) {
        ObjectNode multipleChoiceQuestion = findQuestionByType(update.withArray("quizQuestions"), "multiple-choice");
        ObjectNode answerOption = multipleChoiceQuestion.withArray("answerOptions").addObject();
        answerOption.put("text", "New benchmark answer");
        answerOption.put("hint", "New benchmark hint");
        answerOption.put("explanation", "New benchmark explanation");
        answerOption.put("isCorrect", false);

        ObjectNode dragAndDropQuestion = findQuestionByType(update.withArray("quizQuestions"), "drag-and-drop");
        ObjectNode dragItem = dragAndDropQuestion.withArray("dragItems").addObject();
        dragItem.put("tempID", NEW_DND_ITEM_TEMP_ID);
        dragItem.put("text", "New benchmark drag item");
        ObjectNode dropLocation = dragAndDropQuestion.withArray("dropLocations").addObject();
        dropLocation.put("tempID", NEW_DND_LOCATION_TEMP_ID);
        dropLocation.put("posX", 250D);
        dropLocation.put("posY", 250D);
        dropLocation.put("width", 10D);
        dropLocation.put("height", 10D);
        ObjectNode dragAndDropMapping = dragAndDropQuestion.withArray("correctMappings").addObject();
        dragAndDropMapping.put("dragItemTempId", NEW_DND_ITEM_TEMP_ID);
        dragAndDropMapping.put("dropLocationTempId", NEW_DND_LOCATION_TEMP_ID);

        ObjectNode shortAnswerQuestion = findQuestionByType(update.withArray("quizQuestions"), "short-answer");
        int spotNumber = shortAnswerQuestion.withArray("spots").size();
        ObjectNode spot = shortAnswerQuestion.withArray("spots").addObject();
        spot.put("tempID", NEW_SA_SPOT_TEMP_ID);
        spot.put("width", 10);
        spot.put("spotNr", spotNumber);
        ObjectNode solution = shortAnswerQuestion.withArray("solutions").addObject();
        solution.put("tempID", NEW_SA_SOLUTION_TEMP_ID);
        solution.put("text", "new-benchmark-solution");
        ObjectNode shortAnswerMapping = shortAnswerQuestion.withArray("correctMappings").addObject();
        shortAnswerMapping.put("solutionTempId", NEW_SA_SOLUTION_TEMP_ID);
        shortAnswerMapping.put("spotTempId", NEW_SA_SPOT_TEMP_ID);
        shortAnswerQuestion.put("text", shortAnswerQuestion.path("text").asText() + " [-spot" + spotNumber + "]");
    }

    private static ObjectNode findQuestionByType(ArrayNode questions, String type) {
        for (JsonNode question : questions) {
            if (type.equals(question.path("type").asText())) {
                return (ObjectNode) question;
            }
        }
        throw new AssertionError("Missing quiz question of type " + type);
    }

    private QuizExercise createMixedQuizFixture(BenchmarkDataSet dataSet, QuizLifecycle lifecycle) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime releaseDate = lifecycle == QuizLifecycle.AUTHORING ? now.plusHours(1) : now.minusHours(5);
        ZonedDateTime dueDate = lifecycle == QuizLifecycle.AUTHORING ? now.plusHours(2) : now.minusHours(2);
        Course course = courseRepository.findById(benchmarkCourseId).orElseThrow();
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(releaseDate, dueDate, QuizMode.INDIVIDUAL, course);
        quizExercise.setTitle("Quiz persistence benchmark " + dataSet.label() + " " + UUID.randomUUID());
        quizExercise.setDuration(3600);
        quizExercise.setQuizQuestions(new ArrayList<>());
        quizExercise.addQuestion(createMultipleChoiceQuestion(dataSet.multipleChoiceAnswerOptions()));
        quizExercise.addQuestion(createDragAndDropQuestion(dataSet.dragAndDropComponents()));
        quizExercise.addQuestion(createShortAnswerQuestion(dataSet.shortAnswerComponents()));
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        return quizExerciseService.save(quizExercise);
    }

    private void createReEvaluationSubmissions(QuizExercise quizExercise) {
        ZonedDateTime submissionDate = ZonedDateTime.now().minusHours(3);
        for (int studentIndex = 1; studentIndex <= REEVALUATION_SUBMISSIONS; studentIndex++) {
            SubmissionProfile profile = studentIndex <= 50 ? SubmissionProfile.CORRECT : studentIndex <= 75 ? SubmissionProfile.PARTIAL : SubmissionProfile.INCORRECT;
            QuizSubmission submission = createSubmission(quizExercise, profile);
            submission.setSubmissionDate(submissionDate);
            participationUtilService.addSubmission(quizExercise, submission, TEST_PREFIX + "student" + studentIndex);
            Submission submissionWithResult = participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null,
                    quizExercise.getScoreForSubmission(submission), true);
            assertThat(submissionWithResult.getResults()).hasSize(1);
        }
    }

    private static QuizSubmission createSubmission(QuizExercise quizExercise, SubmissionProfile profile) {
        QuizSubmission submission = new QuizSubmission();
        for (int questionIndex = 0; questionIndex < quizExercise.getQuizQuestions().size(); questionIndex++) {
            QuizQuestion question = quizExercise.getQuizQuestions().get(questionIndex);
            boolean correct = switch (profile) {
                case CORRECT -> true;
                case PARTIAL -> questionIndex == 0;
                case INCORRECT -> false;
            };
            submission.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerFor(question, correct));
        }
        submission.setSubmitted(true);
        return submission;
    }

    private QuizExercise verifyQuizDefinition(Long quizExerciseId, BenchmarkDataSet dataSet, int structuralIncrement) {
        QuizExercise quizExercise = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        assertThat(quizExercise.getQuizQuestions()).hasSize(3);
        MultipleChoiceQuestion multipleChoiceQuestion = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);
        assertThat(multipleChoiceQuestion.getAnswerOptions()).hasSize(dataSet.multipleChoiceAnswerOptions() + structuralIncrement);
        assertThat(dragAndDropQuestion.getDragItems()).hasSize(dataSet.dragAndDropComponents() + structuralIncrement);
        assertThat(dragAndDropQuestion.getDropLocations()).hasSize(dataSet.dragAndDropComponents() + structuralIncrement);
        assertThat(dragAndDropQuestion.getCorrectMappings()).hasSize(dataSet.dragAndDropComponents() + structuralIncrement);
        assertThat(shortAnswerQuestion.getSpots()).hasSize(dataSet.shortAnswerComponents() + structuralIncrement);
        assertThat(shortAnswerQuestion.getSolutions()).hasSize(dataSet.shortAnswerComponents() + structuralIncrement);
        assertThat(shortAnswerQuestion.getCorrectMappings()).hasSize(dataSet.shortAnswerComponents() + structuralIncrement);
        return quizExercise;
    }

    private static MultipleChoiceQuestion createMultipleChoiceQuestion(int answerOptions) {
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("Benchmark MC").score(4D).text("Select all correct answers.");
        question.setExplanation("Multiple choice explanation");
        question.setScoringType(ScoringType.ALL_OR_NOTHING);
        question.setRandomizeOrder(false);
        for (int index = 0; index < answerOptions; index++) {
            question.addAnswerOption(new AnswerOption().text("Answer option " + index).hint("Hint " + index).explanation("Explanation " + index).isCorrect(index % 2 == 0));
        }
        return question;
    }

    private static DragAndDropQuestion createDragAndDropQuestion(int componentCount) {
        DragAndDropQuestion question = (DragAndDropQuestion) new DragAndDropQuestion().title("Benchmark DnD").score(4D).text("Match all drag items.");
        question.setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);
        question.setRandomizeOrder(false);
        for (int index = 0; index < componentCount; index++) {
            DragItem dragItem = new DragItem().text("Drag item " + index);
            DropLocation dropLocation = new DropLocation().posX((double) (index * 10)).posY((double) (index * 10)).height(10D).width(10D);
            question.addDragItem(dragItem);
            question.addDropLocation(dropLocation);
            question.addCorrectMapping(new DragAndDropMapping().dragItem(dragItem).dropLocation(dropLocation));
        }
        return question;
    }

    private static ShortAnswerQuestion createShortAnswerQuestion(int componentCount) {
        ShortAnswerQuestion question = (ShortAnswerQuestion) new ShortAnswerQuestion().title("Benchmark SA").score(4D);
        question.setScoringType(ScoringType.PROPORTIONAL_WITHOUT_PENALTY);
        question.setMatchLetterCase(true);
        question.setSimilarityValue(100);
        question.setRandomizeOrder(false);
        StringBuilder questionText = new StringBuilder("Fill all answer spots:");
        for (int index = 0; index < componentCount; index++) {
            ShortAnswerSpot spot = new ShortAnswerSpot().spotNr(index).width(10);
            ShortAnswerSolution solution = new ShortAnswerSolution().text("solution-" + index);
            question.addSpot(spot);
            question.addSolution(solution);
            question.addCorrectMapping(new ShortAnswerMapping().spot(spot).solution(solution));
            questionText.append(" [-spot").append(index).append(']');
        }
        question.setText(questionText.toString());
        return question;
    }

    private void flushAndClearPersistenceContext() {
        quizExerciseTestRepository.flush();
        entityManager.clear();
    }

    private static long countWriteStatements(List<String> queries) {
        return queries.stream().map(String::stripLeading).map(query -> query.toLowerCase(Locale.ROOT))
                .filter(query -> query.startsWith("insert ") || query.startsWith("update ") || query.startsWith("delete ") || query.startsWith("merge ")).count();
    }

    private static long countJoinOccurrences(List<String> queries) {
        return queries.stream().map(query -> query.toLowerCase(Locale.ROOT)).mapToLong(query -> query.split("\\sjoin\\s", -1).length - 1L).sum();
    }

    private static double median(List<Long> values) {
        List<Long> sortedValues = values.stream().sorted().toList();
        int middle = sortedValues.size() / 2;
        if (sortedValues.size() % 2 == 1) {
            return sortedValues.get(middle);
        }
        return (sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2D;
    }

    private static void logResults(List<BenchmarkResult> results) {
        StringBuilder markdown = new StringBuilder(
                "\n| Workflow | Data set | Median queries | Median time ms | Median writes | Median joins | Request JSON bytes | Response bytes | Definition DTO bytes |\n");
        markdown.append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (BenchmarkResult result : results) {
            markdown.append(String.format(Locale.ROOT, "| %s | %s | %.1f | %.2f | %.1f | %.1f | %.1f | %.1f | %.1f |%n", result.workflow(), result.dataSet().label(),
                    result.medianQueries(), result.medianMillis(), result.medianWrites(), result.medianJoins(), result.medianRequestBytes(), result.medianResponseBytes(),
                    result.medianDefinitionBytes()));
        }

        StringBuilder csv = new StringBuilder(
                "\nworkflow,data_set,median_queries,median_time_ms,median_writes,median_joins,median_request_json_bytes,median_response_bytes,median_definition_dto_bytes\n");
        for (BenchmarkResult result : results) {
            csv.append(String.format(Locale.ROOT, "%s,%s,%.1f,%.2f,%.1f,%.1f,%.1f,%.1f,%.1f%n", result.workflow(), result.dataSet().label(), result.medianQueries(),
                    result.medianMillis(), result.medianWrites(), result.medianJoins(), result.medianRequestBytes(), result.medianResponseBytes(), result.medianDefinitionBytes()));
        }

        log.info("Quiz persistence benchmark results:{}{}", markdown, csv);
    }

    private enum BenchmarkDataSet {

        TYPICAL("Typical", 4, 8, 8),

        LARGE("Large", 10, 20, 20);

        private final String label;

        private final int multipleChoiceAnswerOptions;

        private final int dragAndDropComponents;

        private final int shortAnswerComponents;

        BenchmarkDataSet(String label, int multipleChoiceAnswerOptions, int dragAndDropComponents, int shortAnswerComponents) {
            this.label = label;
            this.multipleChoiceAnswerOptions = multipleChoiceAnswerOptions;
            this.dragAndDropComponents = dragAndDropComponents;
            this.shortAnswerComponents = shortAnswerComponents;
        }

        String label() {
            return label;
        }

        int multipleChoiceAnswerOptions() {
            return multipleChoiceAnswerOptions;
        }

        int dragAndDropComponents() {
            return dragAndDropComponents;
        }

        int shortAnswerComponents() {
            return shortAnswerComponents;
        }
    }

    private enum QuizLifecycle {
        AUTHORING, ENDED
    }

    private enum SubmissionProfile {
        CORRECT, PARTIAL, INCORRECT
    }

    private record BenchmarkScenario(long requestBytes, HttpStatus expectedStatus, BenchmarkRequest request, ScenarioVerifier verifier) {
    }

    private record Measurement(long elapsedNanos, long queries, long writes, long joins, long requestBytes, long responseBytes, long definitionBytes) {

        private static Measurement of(long elapsedNanos, long requestBytes, long responseBytes, long definitionBytes, CapturedQueries capturedQueries) {
            return new Measurement(elapsedNanos, capturedQueries.count(), countWriteStatements(capturedQueries.queries()), countJoinOccurrences(capturedQueries.queries()),
                    requestBytes, responseBytes, definitionBytes);
        }
    }

    private record BenchmarkResult(String workflow, BenchmarkDataSet dataSet, double medianQueries, double medianMillis, double medianWrites, double medianJoins,
            double medianRequestBytes, double medianResponseBytes, double medianDefinitionBytes) {

        private static BenchmarkResult of(String workflow, BenchmarkDataSet dataSet, List<Measurement> measurements) {
            List<Long> elapsedNanos = measurements.stream().map(Measurement::elapsedNanos).toList();
            List<Long> queries = measurements.stream().map(Measurement::queries).toList();
            List<Long> writes = measurements.stream().map(Measurement::writes).toList();
            List<Long> joins = measurements.stream().map(Measurement::joins).toList();
            List<Long> requestBytes = measurements.stream().map(Measurement::requestBytes).toList();
            List<Long> responseBytes = measurements.stream().map(Measurement::responseBytes).toList();
            List<Long> definitionBytes = measurements.stream().map(Measurement::definitionBytes).toList();
            return new BenchmarkResult(workflow, dataSet, median(queries), median(elapsedNanos) / 1_000_000D, median(writes), median(joins), median(requestBytes),
                    median(responseBytes), median(definitionBytes));
        }
    }

    @FunctionalInterface
    private interface ScenarioFactory {

        BenchmarkScenario prepare(BenchmarkDataSet dataSet) throws Exception;
    }

    @FunctionalInterface
    private interface BenchmarkRequest {

        MvcResult execute() throws Exception;
    }

    @FunctionalInterface
    private interface ScenarioVerifier {

        Long verifyAndResolveTargetQuiz(MvcResult result) throws Exception;
    }
}
