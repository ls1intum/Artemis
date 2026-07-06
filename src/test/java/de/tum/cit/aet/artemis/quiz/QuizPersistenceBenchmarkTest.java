package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.core.util.HibernateQueryInterceptor.CapturedQueries;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;

class QuizPersistenceBenchmarkTest extends AbstractQuizExerciseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(QuizPersistenceBenchmarkTest.class);

    private static final String TEST_PREFIX = "quizpersistencebenchmark";

    private static final int WARMUP_RUNS = 3;

    private static final int MEASURED_RUNS = 10;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Autowired
    private EntityManager entityManager;

    private Long benchmarkCourseId;

    @BeforeEach
    void initBenchmarkUsers() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        ZonedDateTime now = ZonedDateTime.now();
        Course benchmarkCourse = quizExerciseUtilService.createAndSaveCourse(null, now.minusDays(1), now.plusDays(30), Set.of());
        benchmarkCourseId = benchmarkCourse.getId();
    }

    @Test
    @Tag("benchmark")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void benchmarkLoadQuizForEditing() throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();
        for (BenchmarkDataSet dataSet : BenchmarkDataSet.values()) {
            results.add(runBenchmark("Load quiz for editing", dataSet, this::createMixedQuizFixture, this::loadQuizForEditing));
        }
        logResults(results);
    }

    private BenchmarkResult runBenchmark(String workflow, BenchmarkDataSet dataSet, FixtureFactory fixtureFactory, BenchmarkRequest benchmarkRequest) throws Exception {
        List<Measurement> measurements = new ArrayList<>();
        for (int run = 0; run < WARMUP_RUNS + MEASURED_RUNS; run++) {
            QuizExercise fixture = fixtureFactory.create(dataSet);
            Measurement measurement = measureRestCall(() -> benchmarkRequest.execute(fixture));
            if (run >= WARMUP_RUNS) {
                measurements.add(measurement);
            }
        }
        return BenchmarkResult.of(workflow, dataSet, measurements);
    }

    private Measurement measureRestCall(MeasuredRequest measuredRequest) throws Exception {
        flushAndClearPersistenceContext();
        queryInterceptor.startQueryCapture();
        long startNanos = System.nanoTime();
        try {
            MvcResult result = measuredRequest.execute();
            long elapsedNanos = System.nanoTime() - startNanos;
            CapturedQueries capturedQueries = queryInterceptor.stopQueryCapture();
            request.restoreSecurityContext();
            assertThat(result.getResponse().getContentAsString()).isNotBlank();
            return Measurement.of(elapsedNanos, capturedQueries);
        }
        catch (Exception e) {
            queryInterceptor.stopQueryCapture();
            request.restoreSecurityContext();
            throw e;
        }
    }

    private MvcResult loadQuizForEditing(QuizExercise quizExercise) throws Exception {
        return request.performMvcRequest(MockMvcRequestBuilders.get("/api/quiz/quiz-exercises/" + quizExercise.getId())).andExpect(status().is(HttpStatus.OK.value())).andReturn();
    }

    private QuizExercise createMixedQuizFixture(BenchmarkDataSet dataSet) {
        ZonedDateTime releaseDate = ZonedDateTime.now().minusMinutes(5);
        Course course = courseRepository.findById(benchmarkCourseId).orElseThrow();
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(releaseDate, releaseDate.plusHours(1), QuizMode.SYNCHRONIZED, course);
        quizExercise.setTitle("Quiz persistence benchmark " + dataSet.label() + " " + UUID.randomUUID());
        quizExercise.setQuizQuestions(new ArrayList<>());
        quizExercise.addQuestion(createMultipleChoiceQuestion(dataSet.multipleChoiceAnswerOptions()));
        quizExercise.addQuestion(createDragAndDropQuestion(dataSet.dragAndDropComponents()));
        quizExercise.addQuestion(createShortAnswerQuestion(dataSet.shortAnswerComponents()));
        return quizExerciseService.save(quizExercise);
    }

    private static MultipleChoiceQuestion createMultipleChoiceQuestion(int answerOptions) {
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("Benchmark MC").score(4D).text("Select all correct answers.");
        question.setScoringType(ScoringType.ALL_OR_NOTHING);
        question.setRandomizeOrder(false);
        for (int i = 0; i < answerOptions; i++) {
            question.addAnswerOption(new AnswerOption().text("Answer option " + i).hint("Hint " + i).explanation("Explanation " + i).isCorrect(i % 2 == 0));
        }
        return question;
    }

    private static DragAndDropQuestion createDragAndDropQuestion(int componentCount) {
        DragAndDropQuestion question = (DragAndDropQuestion) new DragAndDropQuestion().title("Benchmark DnD").score(4D).text("Match all drag items.");
        question.setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);
        question.setRandomizeOrder(false);
        for (int i = 0; i < componentCount; i++) {
            DragItem dragItem = new DragItem().text("Drag item " + i);
            DropLocation dropLocation = new DropLocation().posX((double) (i * 10)).posY((double) (i * 10)).height(10D).width(10D);
            question.addDragItem(dragItem);
            question.addDropLocation(dropLocation);
            question.addCorrectMapping(new DragAndDropMapping().dragItem(dragItem).dropLocation(dropLocation));
        }
        return question;
    }

    private static ShortAnswerQuestion createShortAnswerQuestion(int componentCount) {
        ShortAnswerQuestion question = (ShortAnswerQuestion) new ShortAnswerQuestion().title("Benchmark SA").score(4D).text("Fill all answer spots.");
        question.setScoringType(ScoringType.PROPORTIONAL_WITHOUT_PENALTY);
        question.setMatchLetterCase(true);
        question.setSimilarityValue(100);
        question.setRandomizeOrder(false);
        for (int i = 0; i < componentCount; i++) {
            ShortAnswerSpot spot = new ShortAnswerSpot().spotNr(i).width(10);
            ShortAnswerSolution solution = new ShortAnswerSolution().text("solution-" + i);
            question.addSpot(spot);
            question.addSolution(solution);
            question.addCorrectMapping(new ShortAnswerMapping().spot(spot).solution(solution));
        }
        return question;
    }

    private void flushAndClearPersistenceContext() {
        quizExerciseTestRepository.flush();
        entityManager.clear();
    }

    private static long countWriteStatements(List<String> queries) {
        return queries.stream().map(String::stripLeading).map(query -> query.toLowerCase(Locale.ROOT))
                .filter(query -> query.startsWith("insert ") || query.startsWith("update ") || query.startsWith("delete ")).count();
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
        StringBuilder markdown = new StringBuilder("\n| Workflow | Data set | Median queries | Median time ms | Median writes | Median joins |\n");
        markdown.append("|---|---|---:|---:|---:|---:|\n");
        for (BenchmarkResult result : results) {
            markdown.append(String.format(Locale.ROOT, "| %s | %s | %.1f | %.2f | %.1f | %.1f |%n", result.workflow(), result.dataSet().label(), result.medianQueries(),
                    result.medianMillis(), result.medianWrites(), result.medianJoins()));
        }

        StringBuilder csv = new StringBuilder("\nworkflow,data_set,median_queries,median_time_ms,median_writes,median_joins\n");
        for (BenchmarkResult result : results) {
            csv.append(String.format(Locale.ROOT, "%s,%s,%.1f,%.2f,%.1f,%.1f%n", result.workflow(), result.dataSet().label(), result.medianQueries(), result.medianMillis(),
                    result.medianWrites(), result.medianJoins()));
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

    private record Measurement(long elapsedNanos, long queries, long writes, long joins) {

        private static Measurement of(long elapsedNanos, CapturedQueries capturedQueries) {
            return new Measurement(elapsedNanos, capturedQueries.count(), countWriteStatements(capturedQueries.queries()), countJoinOccurrences(capturedQueries.queries()));
        }
    }

    private record BenchmarkResult(String workflow, BenchmarkDataSet dataSet, double medianQueries, double medianMillis, double medianWrites, double medianJoins) {

        private static BenchmarkResult of(String workflow, BenchmarkDataSet dataSet, List<Measurement> measurements) {
            List<Long> elapsedNanos = measurements.stream().map(Measurement::elapsedNanos).toList();
            List<Long> queries = measurements.stream().map(Measurement::queries).toList();
            List<Long> writes = measurements.stream().map(Measurement::writes).toList();
            List<Long> joins = measurements.stream().map(Measurement::joins).toList();
            return new BenchmarkResult(workflow, dataSet, median(queries), median(elapsedNanos) / 1_000_000D, median(writes), median(joins));
        }
    }

    @FunctionalInterface
    private interface FixtureFactory {

        QuizExercise create(BenchmarkDataSet dataSet);
    }

    @FunctionalInterface
    private interface BenchmarkRequest {

        MvcResult execute(QuizExercise quizExercise) throws Exception;
    }

    @FunctionalInterface
    private interface MeasuredRequest {

        MvcResult execute() throws Exception;
    }
}
