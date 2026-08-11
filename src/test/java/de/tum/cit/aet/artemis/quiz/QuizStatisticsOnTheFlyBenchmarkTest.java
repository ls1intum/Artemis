package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.AnswerCounter;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMappingSelection;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswerSelection;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.DropLocationCounter;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswerSelection;
import de.tum.cit.aet.artemis.quiz.domain.PointCounter;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpotCounter;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswerSelection;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerTextSelection;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.service.QuizStatisticService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.SubmittedAnswerTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizStatisticProjections.FlatResult;
import de.tum.cit.aet.artemis.quiz.util.QuizStatisticProjections.PointBucket;
import de.tum.cit.aet.artemis.quiz.util.QuizStatisticProjections.QuestionAggregate;
import de.tum.cit.aet.artemis.quiz.util.QuizStatisticProjections.QuizOverviewAggregate;
import de.tum.cit.aet.artemis.quiz.util.QuizStatisticProjections.RatedSelection;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import me.xdrop.fuzzywuzzy.FuzzySearch;

/**
 * Measures whether the five quiz statistics pages can be served by computing their numbers on demand, so that the {@code quiz_statistic} and {@code quiz_statistic_counter} tables
 * can be dropped.
 * <p>
 * The test seeds one quiz with a multiple-choice, a drag-and-drop and a short-answer question and grows it to 100, 500, 1000 and 2000 participations, each with a submitted quiz
 * submission, one submitted answer per question and a rated result. At every scale it computes each statistics page from scratch and reports the wall-clock time.
 * <p>
 * Two properties are checked besides the timings:
 * <ul>
 * <li>every on-the-fly number equals the number the current, incrementally maintained statistics produce (see {@link QuizStatisticService#recalculateStatistics}), so the
 * measurement is of the right computation, not just of a fast query;</li>
 * <li>only one statistics page is computed per measurement, mirroring how an instructor navigates: opening the multiple-choice page never pays for the other four.</li>
 * </ul>
 * <p>
 * Every statement involved is plain JPQL (see {@link SubmittedAnswerTestRepository}) and therefore runs unchanged on MySQL and PostgreSQL. Run this class with
 * {@code SPRING_PROFILES_INCLUDE=mysql} to confirm the MySQL side.
 */
// Isolated: the measurements are wall-clock timings, so no other test class may compete for the CPU or the database while this one runs.
@Isolated
class QuizStatisticsOnTheFlyBenchmarkTest extends AbstractSpringIntegrationIndependentTest {

    private static final Logger log = LoggerFactory.getLogger(QuizStatisticsOnTheFlyBenchmarkTest.class);

    private static final String TEST_PREFIX = "quizstatbench";

    /**
     * The participation counts to measure. Each entry grows the data set to that size, so seeding cost stays linear in the largest scale. 5000 is beyond any realistic Artemis
     * quiz and is only measured to show how the numbers extrapolate.
     */
    private static final int[] SCALES = { 100, 500, 1000, 2000, 5000 };

    /**
     * One student per participation, as in reality: {@code participation} has a unique constraint over (student, exercise, initialization state, attempt), so participations
     * cannot share a student.
     */
    private static final int STUDENT_COUNT = SCALES[SCALES.length - 1];

    private static final int WARMUP_ROUNDS = 5;

    private static final int MEASURED_ROUNDS = 15;

    private static final double MC_POINTS = 5.0;

    private static final double DND_POINTS = 5.0;

    private static final double SA_POINTS = 5.0;

    private static final ZonedDateTime QUIZ_RELEASE = ZonedDateTime.now().minusDays(2);

    private static final ZonedDateTime QUIZ_DUE = ZonedDateTime.now().minusDays(1);

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private QuizSubmissionTestRepository quizSubmissionRepository;

    @Autowired
    private SubmittedAnswerTestRepository submittedAnswerRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private QuizExerciseService quizExerciseService;

    @Autowired
    private QuizStatisticService quizStatisticService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<User> students;

    private long seededParticipations;

    private long seededQuizId;

    /**
     * Consumes every computed page so that neither the JIT nor the compiler can elide a measured call.
     */
    private volatile Object blackHole;

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldComputeEveryStatisticsPageOnTheFlyFastEnoughToDropTheStatisticsTables() {
        // Seeding thousands of results would otherwise keep the asynchronous participant-score scheduler busy for the whole run and pollute every wall-clock measurement. It has
        // no bearing on the statistics queries; the base class shuts it down again after the test.
        participantScoreScheduleService.shutdown();

        long userSeedingStart = System.nanoTime();
        students = userUtilService.addUsers(TEST_PREFIX, STUDENT_COUNT, 0, 0, 1).stream().filter(user -> user.getLogin().startsWith(TEST_PREFIX + "student")).toList();
        assertThat(students).hasSize(STUDENT_COUNT);
        log.info("Created {} students in {} ms", STUDENT_COUNT, (System.nanoTime() - userSeedingStart) / 1_000_000);

        QuizExercise quiz = createQuiz();
        MultipleChoiceQuestion multipleChoiceQuestion = (MultipleChoiceQuestion) quiz.getQuizQuestions().get(0);
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quiz.getQuizQuestions().get(1);
        ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) quiz.getQuizQuestions().get(2);

        List<String> pageReport = new ArrayList<>();
        pageReport.add("| participations | quiz statistic (overview) | quiz point statistic | MC question | DnD question | SA question (fuzzy recheck) "
                + "| SA question (stored flag) |");
        pageReport.add("|---:|---:|---:|---:|---:|---:|---:|");
        List<String> breakdownReport = new ArrayList<>();
        breakdownReport.add("| participations | point stat: aggregate | point stat: fold in Java | question: counts query | question: selections query | MC fold | DnD fold "
                + "| SA fold (fuzzy) |");
        breakdownReport.add("|---:|---:|---:|---:|---:|---:|---:|---:|");

        for (int scale : SCALES) {
            long seedingMillis = growTo(quiz, scale);
            refreshDatabaseStatistics();
            log.info("Seeded quiz {} up to {} participations in {} ms", quiz.getId(), scale, seedingMillis);

            if (scale == SCALES[0]) {
                assertOnTheFlyMatchesStoredStatistics(quiz, multipleChoiceQuestion, dragAndDropQuestion, shortAnswerQuestion);
            }

            Timing overviewTiming = measure(() -> computeQuizOverviewStatistic(quiz.getId()));
            Timing pointTiming = measure(() -> computePointStatistic(quiz.getId()));
            Timing multipleChoiceTiming = measure(() -> computeMultipleChoiceStatistic(multipleChoiceQuestion.getId()));
            Timing dragAndDropTiming = measure(() -> computeDragAndDropStatistic(dragAndDropQuestion.getId()));
            Timing shortAnswerRecheckTiming = measure(() -> computeShortAnswerStatistic(shortAnswerQuestion.getId(), true));
            Timing shortAnswerStoredTiming = measure(() -> computeShortAnswerStatistic(shortAnswerQuestion.getId(), false));
            pageReport.add("| %d | %s | %s | %s | %s | %s | %s |".formatted(scale, overviewTiming, pointTiming, multipleChoiceTiming, dragAndDropTiming, shortAnswerRecheckTiming,
                    shortAnswerStoredTiming));

            // Where the time of a page actually goes: the aggregate queries are flat, the per-element counters pay for streaming one JSON document per participation.
            Timing pointAggregateTiming = measure(() -> submittedAnswerRepository.findPointStatistic(quiz.getId()));
            Timing pointFoldTiming = measure(() -> computePointStatisticByFolding(quiz.getId()));
            Timing countsTiming = measure(() -> submittedAnswerRepository.findQuestionAggregate(multipleChoiceQuestion.getId(), MC_POINTS));
            Timing selectionsTiming = measure(() -> submittedAnswerRepository.findSelectionsForQuestion(multipleChoiceQuestion.getId()));
            breakdownReport.add("| %d | %s | %s | %s | %s | %s | %s | %s |".formatted(scale, pointAggregateTiming, pointFoldTiming, countsTiming, selectionsTiming,
                    multipleChoiceTiming, dragAndDropTiming, shortAnswerRecheckTiming));

            log.info("""

                    === quiz statistics computed on the fly, {} participations ===
                    quiz point statistic         {}
                    multiple choice question     {}
                    drag and drop question       {}
                    short answer question        {} (correctness re-checked with the fuzzy matcher)
                    short answer question        {} (correctness read from the stored isCorrect flag)
                    --- breakdown ---
                    point statistic aggregate    {}
                    point statistic folded       {}
                    question counts query        {}
                    question selections query    {}
                    """, scale, pointTiming, multipleChoiceTiming, dragAndDropTiming, shortAnswerRecheckTiming, shortAnswerStoredTiming, pointAggregateTiming, pointFoldTiming,
                    countsTiming, selectionsTiming);
        }

        log.info("\n=== on-the-fly quiz statistics: whole page, median (p95/min/max) ===\n{}\n", String.join("\n", pageReport));
        log.info("\n=== on-the-fly quiz statistics: cost breakdown, median (p95/min/max) ===\n{}\n", String.join("\n", breakdownReport));
    }

    // ----------------------------------------------------------------------------------------------------------------
    // The on-the-fly computations: one method per statistics page.
    // ----------------------------------------------------------------------------------------------------------------

    /**
     * Computes the quiz point statistic page: how many participants reached each point bucket, split by rated and unrated. One aggregate query, and a fold of at most a few dozen
     * rows into the integer buckets the chart draws.
     *
     * @param exerciseId the quiz exercise
     * @return the point buckets and the participant counts
     */
    private PointStatistic computePointStatistic(long exerciseId) {
        QuizExercise quiz = quizExerciseRepository.findByIdWithQuestionsElseThrow(exerciseId);
        double overallPoints = quiz.getOverallQuizPoints();

        Map<Double, long[]> counters = new TreeMap<>();
        for (double points = 0.0; points <= overallPoints; points++) {
            counters.put(points, new long[2]);
        }
        long ratedParticipants = 0;
        long unratedParticipants = 0;

        for (PointBucket bucket : submittedAnswerRepository.findPointStatistic(exerciseId)) {
            // Mirrors QuizPointStatistic#changeStatisticBasedOnResult: the percentage score is mapped onto the integer point buckets, deliberately not rounded by course settings.
            double points = Math.round(overallPoints * (bucket.getScore() / 100));
            long[] counter = counters.computeIfAbsent(points, key -> new long[2]);
            if (Boolean.TRUE.equals(bucket.getRated())) {
                counter[0] += bucket.getParticipantCount();
                ratedParticipants += bucket.getParticipantCount();
            }
            else {
                counter[1] += bucket.getParticipantCount();
                unratedParticipants += bucket.getParticipantCount();
            }
        }
        return new PointStatistic(counters, ratedParticipants, unratedParticipants);
    }

    /**
     * The same page as {@link #computePointStatistic}, computed without the correlated subquery: every result of the quiz is streamed out and reduced to the latest rated and
     * latest unrated result per participation in Java.
     *
     * @param exerciseId the quiz exercise
     * @return the point buckets and the participant counts
     */
    private PointStatistic computePointStatisticByFolding(long exerciseId) {
        QuizExercise quiz = quizExerciseRepository.findByIdWithQuestionsElseThrow(exerciseId);
        double overallPoints = quiz.getOverallQuizPoints();

        Map<Long, FlatResult> latestRated = new HashMap<>();
        Map<Long, FlatResult> latestUnrated = new HashMap<>();
        for (FlatResult result : submittedAnswerRepository.findResultsForPointStatistic(exerciseId)) {
            Map<Long, FlatResult> latest = Boolean.TRUE.equals(result.getRated()) ? latestRated : latestUnrated;
            latest.merge(result.getParticipationId(), result, (existing, candidate) -> existing.getCompletionDate().isAfter(candidate.getCompletionDate()) ? existing : candidate);
        }

        Map<Double, long[]> counters = new TreeMap<>();
        for (double points = 0.0; points <= overallPoints; points++) {
            counters.put(points, new long[2]);
        }
        for (FlatResult result : latestRated.values()) {
            counters.computeIfAbsent((double) Math.round(overallPoints * (result.getScore() / 100)), key -> new long[2])[0]++;
        }
        for (FlatResult result : latestUnrated.values()) {
            counters.computeIfAbsent((double) Math.round(overallPoints * (result.getScore() / 100)), key -> new long[2])[1]++;
        }
        return new PointStatistic(counters, latestRated.size(), latestUnrated.size());
    }

    /**
     * Computes the multiple-choice question statistics page: participants, fully correct answers and how often each answer option was selected.
     *
     * @param questionId the question the page shows
     * @return the counters the chart draws
     */
    private QuestionStatistic computeMultipleChoiceStatistic(long questionId) {
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizQuestion(questionId);
        QuestionStatistic statistic = questionAggregate(questionId, question.getPoints());

        Map<Long, long[]> perOption = new TreeMap<>();
        for (AnswerOption answerOption : question.getAnswerOptions()) {
            perOption.put(answerOption.getId(), new long[2]);
        }
        for (RatedSelection row : submittedAnswerRepository.findSelectionsForQuestion(questionId)) {
            if (!(row.getSelection() instanceof MultipleChoiceSubmittedAnswerSelection selection)) {
                continue;
            }
            int index = Boolean.TRUE.equals(row.getRated()) ? 0 : 1;
            for (Long selectedOptionId : selection.getSelectedOptionIds()) {
                long[] counter = perOption.get(selectedOptionId);
                if (counter != null) {
                    counter[index]++;
                }
            }
        }
        statistic.elementCounters().putAll(perOption);
        return statistic;
    }

    /**
     * Computes the drag-and-drop question statistics page: participants, fully correct answers and how often each drop location was filled correctly.
     *
     * @param questionId the question the page shows
     * @return the counters the chart draws
     */
    private QuestionStatistic computeDragAndDropStatistic(long questionId) {
        DragAndDropQuestion question = (DragAndDropQuestion) quizQuestion(questionId);
        QuestionStatistic statistic = questionAggregate(questionId, question.getPoints());

        // The correct drag items per drop location, resolved once instead of once per submitted answer.
        Map<Long, Set<Long>> correctDragItemsPerDropLocation = new HashMap<>();
        for (DropLocation dropLocation : question.getDropLocations()) {
            correctDragItemsPerDropLocation.put(dropLocation.getId(), new HashSet<>());
        }
        for (DragAndDropMapping mapping : question.getCorrectMappings()) {
            Set<Long> correctDragItems = correctDragItemsPerDropLocation.get(mapping.getDropLocation().getId());
            if (correctDragItems != null) {
                correctDragItems.add(mapping.getDragItem().getId());
            }
        }

        Map<Long, long[]> perDropLocation = new TreeMap<>();
        for (DropLocation dropLocation : question.getDropLocations()) {
            perDropLocation.put(dropLocation.getId(), new long[2]);
        }
        for (RatedSelection row : submittedAnswerRepository.findSelectionsForQuestion(questionId)) {
            if (!(row.getSelection() instanceof DragAndDropSubmittedAnswerSelection selection)) {
                continue;
            }
            int index = Boolean.TRUE.equals(row.getRated()) ? 0 : 1;
            Map<Long, Long> submittedDragItemPerDropLocation = new HashMap<>();
            for (DragAndDropMappingSelection mapping : selection.getMappings()) {
                submittedDragItemPerDropLocation.putIfAbsent(mapping.dropLocationId(), mapping.dragItemId());
            }
            for (Map.Entry<Long, long[]> entry : perDropLocation.entrySet()) {
                // Mirrors DragAndDropQuestion#isDropLocationCorrect: a drop location counts as correct when it was meant to stay empty and stayed empty, or when the dropped drag
                // item is one of the correct ones.
                Set<Long> correctDragItems = correctDragItemsPerDropLocation.getOrDefault(entry.getKey(), Set.of());
                Long submittedDragItem = submittedDragItemPerDropLocation.get(entry.getKey());
                boolean correct = (correctDragItems.isEmpty() && submittedDragItem == null) || (submittedDragItem != null && correctDragItems.contains(submittedDragItem));
                if (correct) {
                    entry.getValue()[index]++;
                }
            }
        }
        statistic.elementCounters().putAll(perDropLocation);
        return statistic;
    }

    /**
     * Computes the short-answer question statistics page: participants, fully correct answers and how often each spot was filled in correctly.
     *
     * @param questionId              the question the page shows
     * @param recheckCorrectnessFuzzy whether the per-spot correctness is re-derived with the fuzzy matcher, as the current statistics code does, or read from the {@code isCorrect}
     *                                    flag the scoring pass already persisted into the selection JSON
     * @return the counters the chart draws
     */
    private QuestionStatistic computeShortAnswerStatistic(long questionId, boolean recheckCorrectnessFuzzy) {
        ShortAnswerQuestion question = (ShortAnswerQuestion) quizQuestion(questionId);
        QuestionStatistic statistic = questionAggregate(questionId, question.getPoints());

        Map<Long, List<String>> solutionsPerSpot = new HashMap<>();
        for (ShortAnswerSpot spot : question.getSpots()) {
            solutionsPerSpot.put(spot.getId(), question.getCorrectSolutionForSpot(spot).stream().map(ShortAnswerSolution::getText).toList());
        }
        int similarityValue = question.getSimilarityValue() != null ? question.getSimilarityValue() : 85;
        boolean matchLetterCase = Boolean.TRUE.equals(question.getMatchLetterCase());

        Map<Long, long[]> perSpot = new TreeMap<>();
        for (ShortAnswerSpot spot : question.getSpots()) {
            perSpot.put(spot.getId(), new long[2]);
        }
        for (RatedSelection row : submittedAnswerRepository.findSelectionsForQuestion(questionId)) {
            if (!(row.getSelection() instanceof ShortAnswerSubmittedAnswerSelection selection)) {
                continue;
            }
            int index = Boolean.TRUE.equals(row.getRated()) ? 0 : 1;
            for (ShortAnswerTextSelection submittedText : selection.getSubmittedTexts()) {
                long[] counter = perSpot.get(submittedText.getSpotId());
                if (counter == null) {
                    continue;
                }
                boolean correct = recheckCorrectnessFuzzy
                        ? isAnySolutionMatched(submittedText.getText(), solutionsPerSpot.getOrDefault(submittedText.getSpotId(), List.of()), similarityValue, matchLetterCase)
                        : Boolean.TRUE.equals(submittedText.getIsCorrect());
                if (correct) {
                    counter[index]++;
                }
            }
        }
        statistic.elementCounters().putAll(perSpot);
        return statistic;
    }

    /**
     * The rated / unrated participant and fully-correct counts of one question, which every question statistics page needs.
     *
     * @param questionId     the question
     * @param questionPoints the question's maximum points
     * @return the aggregate, with the per-element counters still empty
     */
    /**
     * Computes the quiz statistics overview page: one bar per question with the number of participants who answered that question completely correctly. A single grouped aggregate
     * covers the whole page — no selection JSON has to be read, because the page shows no per-element counters.
     *
     * @param exerciseId the quiz exercise
     * @return the per-question rated / unrated participant and correct counts
     */
    private Map<Long, QuestionStatistic> computeQuizOverviewStatistic(long exerciseId) {
        Map<Long, long[]> perQuestion = new TreeMap<>();
        for (QuizOverviewAggregate aggregate : submittedAnswerRepository.findQuestionAggregatesForQuiz(exerciseId)) {
            long[] counters = perQuestion.computeIfAbsent(aggregate.getQuestionId(), key -> new long[4]);
            int offset = Boolean.TRUE.equals(aggregate.getRated()) ? 0 : 1;
            counters[offset] = aggregate.getParticipantCount();
            counters[2 + offset] = aggregate.getCorrectCount();
        }
        Map<Long, QuestionStatistic> statistics = new TreeMap<>();
        perQuestion.forEach((questionId, counters) -> statistics.put(questionId, new QuestionStatistic(counters[0], counters[1], counters[2], counters[3], new TreeMap<>())));
        return statistics;
    }

    private QuestionStatistic questionAggregate(long questionId, double questionPoints) {
        long ratedParticipants = 0;
        long unratedParticipants = 0;
        long ratedCorrect = 0;
        long unratedCorrect = 0;
        for (QuestionAggregate aggregate : submittedAnswerRepository.findQuestionAggregate(questionId, questionPoints)) {
            if (Boolean.TRUE.equals(aggregate.getRated())) {
                ratedParticipants = aggregate.getParticipantCount();
                ratedCorrect = aggregate.getCorrectCount();
            }
            else {
                unratedParticipants = aggregate.getParticipantCount();
                unratedCorrect = aggregate.getCorrectCount();
            }
        }
        return new QuestionStatistic(ratedParticipants, unratedParticipants, ratedCorrect, unratedCorrect, new TreeMap<>());
    }

    /**
     * Mirrors {@link ShortAnswerSubmittedText#isSubmittedTextCorrect} without needing a submitted-answer object to reach the question's similarity settings.
     *
     * @param submittedText   the text the student typed
     * @param solutions       the accepted solutions for the spot
     * @param similarityValue the question's similarity threshold
     * @param matchLetterCase whether the comparison is case sensitive
     * @return true if the submitted text matches any accepted solution
     */
    private static boolean isAnySolutionMatched(String submittedText, List<String> solutions, int similarityValue, boolean matchLetterCase) {
        for (String solution : solutions) {
            if (Objects.equals(submittedText, solution)) {
                return true;
            }
            if (submittedText == null) {
                continue;
            }
            if (matchLetterCase) {
                if (FuzzySearch.ratio(submittedText.trim(), solution.trim()) >= similarityValue) {
                    return true;
                }
            }
            else if (FuzzySearch.ratio(submittedText.toLowerCase(Locale.ROOT).trim(), solution.toLowerCase(Locale.ROOT).trim()) >= similarityValue) {
                return true;
            }
        }
        return false;
    }

    private QuizQuestion quizQuestion(long questionId) {
        return quizExerciseRepository.findByIdWithQuestionsElseThrow(seededQuizId).getQuizQuestions().stream().filter(question -> question.getId() == questionId).findFirst()
                .orElseThrow();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Parity with the statistics that are maintained in the database today.
    // ----------------------------------------------------------------------------------------------------------------

    private void assertOnTheFlyMatchesStoredStatistics(QuizExercise quiz, MultipleChoiceQuestion multipleChoiceQuestion, DragAndDropQuestion dragAndDropQuestion,
            ShortAnswerQuestion shortAnswerQuestion) {
        QuizExercise quizWithStatistics = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quiz.getId());
        quizStatisticService.recalculateStatistics(quizWithStatistics);
        QuizExercise stored = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quiz.getId());

        PointStatistic onTheFlyPoints = computePointStatistic(quiz.getId());
        assertThat(onTheFlyPoints.ratedParticipants()).as("rated participants of the point statistic").isEqualTo((long) stored.getQuizPointStatistic().getParticipantsRated());
        assertThat(onTheFlyPoints.unratedParticipants()).as("unrated participants of the point statistic")
                .isEqualTo((long) stored.getQuizPointStatistic().getParticipantsUnrated());
        for (PointCounter pointCounter : stored.getQuizPointStatistic().getPointCounters()) {
            long[] onTheFly = onTheFlyPoints.counters().getOrDefault(pointCounter.getPoints(), new long[2]);
            assertThat(onTheFly[0]).as("rated counter of point bucket %s", pointCounter.getPoints()).isEqualTo((long) pointCounter.getRatedCounter());
            assertThat(onTheFly[1]).as("unrated counter of point bucket %s", pointCounter.getPoints()).isEqualTo((long) pointCounter.getUnRatedCounter());
        }

        // The folding variant has to produce the very same numbers as the aggregate variant.
        assertThat(computePointStatisticByFolding(quiz.getId())).as("the folded point statistic").isEqualTo(onTheFlyPoints);

        MultipleChoiceQuestionStatistic storedMultipleChoice = (MultipleChoiceQuestionStatistic) storedStatisticOf(stored, multipleChoiceQuestion.getId());
        QuestionStatistic onTheFlyMultipleChoice = computeMultipleChoiceStatistic(multipleChoiceQuestion.getId());
        assertQuestionParity(onTheFlyMultipleChoice, storedMultipleChoice.getParticipantsRated(), storedMultipleChoice.getParticipantsUnrated(),
                storedMultipleChoice.getRatedCorrectCounter(), storedMultipleChoice.getUnRatedCorrectCounter(), "multiple choice");
        for (AnswerCounter answerCounter : storedMultipleChoice.getAnswerCounters()) {
            long[] onTheFly = onTheFlyMultipleChoice.elementCounters().getOrDefault(answerCounter.getAnswerId(), new long[2]);
            assertThat(onTheFly[0]).as("rated counter of answer option %s", answerCounter.getAnswerId()).isEqualTo((long) answerCounter.getRatedCounter());
            assertThat(onTheFly[1]).as("unrated counter of answer option %s", answerCounter.getAnswerId()).isEqualTo((long) answerCounter.getUnRatedCounter());
        }

        DragAndDropQuestionStatistic storedDragAndDrop = (DragAndDropQuestionStatistic) storedStatisticOf(stored, dragAndDropQuestion.getId());
        QuestionStatistic onTheFlyDragAndDrop = computeDragAndDropStatistic(dragAndDropQuestion.getId());
        assertQuestionParity(onTheFlyDragAndDrop, storedDragAndDrop.getParticipantsRated(), storedDragAndDrop.getParticipantsUnrated(), storedDragAndDrop.getRatedCorrectCounter(),
                storedDragAndDrop.getUnRatedCorrectCounter(), "drag and drop");
        for (DropLocationCounter dropLocationCounter : storedDragAndDrop.getDropLocationCounters()) {
            long[] onTheFly = onTheFlyDragAndDrop.elementCounters().getOrDefault(dropLocationCounter.getDropLocationId(), new long[2]);
            assertThat(onTheFly[0]).as("rated counter of drop location %s", dropLocationCounter.getDropLocationId()).isEqualTo((long) dropLocationCounter.getRatedCounter());
            assertThat(onTheFly[1]).as("unrated counter of drop location %s", dropLocationCounter.getDropLocationId()).isEqualTo((long) dropLocationCounter.getUnRatedCounter());
        }

        ShortAnswerQuestionStatistic storedShortAnswer = (ShortAnswerQuestionStatistic) storedStatisticOf(stored, shortAnswerQuestion.getId());
        QuestionStatistic onTheFlyShortAnswer = computeShortAnswerStatistic(shortAnswerQuestion.getId(), true);
        assertQuestionParity(onTheFlyShortAnswer, storedShortAnswer.getParticipantsRated(), storedShortAnswer.getParticipantsUnrated(), storedShortAnswer.getRatedCorrectCounter(),
                storedShortAnswer.getUnRatedCorrectCounter(), "short answer");
        for (ShortAnswerSpotCounter spotCounter : storedShortAnswer.getShortAnswerSpotCounters()) {
            long[] onTheFly = onTheFlyShortAnswer.elementCounters().getOrDefault(spotCounter.getSpotId(), new long[2]);
            assertThat(onTheFly[0]).as("rated counter of spot %s", spotCounter.getSpotId()).isEqualTo((long) spotCounter.getRatedCounter());
            assertThat(onTheFly[1]).as("unrated counter of spot %s", spotCounter.getSpotId()).isEqualTo((long) spotCounter.getUnRatedCounter());
        }

        // Reading the persisted isCorrect flag instead of re-running the fuzzy matcher must not change any number.
        assertThat(computeShortAnswerStatistic(shortAnswerQuestion.getId(), false)).as("the short answer statistic derived from the stored isCorrect flag")
                .isEqualTo(onTheFlyShortAnswer);

        // The overview page aggregates all questions at once and has to agree with the per-question pages.
        Map<Long, QuestionStatistic> overview = computeQuizOverviewStatistic(quiz.getId());
        assertThat(overview).as("the overview page covers every question").containsOnlyKeys(multipleChoiceQuestion.getId(), dragAndDropQuestion.getId(),
                shortAnswerQuestion.getId());
        for (QuizQuestion question : stored.getQuizQuestions()) {
            QuizQuestionStatistic storedStatistic = question.getQuizQuestionStatistic();
            assertQuestionParity(overview.get(question.getId()), storedStatistic.getParticipantsRated(), storedStatistic.getParticipantsUnrated(),
                    storedStatistic.getRatedCorrectCounter(), storedStatistic.getUnRatedCorrectCounter(), "overview entry for question " + question.getId());
        }

        log.info("On-the-fly statistics match the stored statistics on every page (overview, points, MC, DnD, SA) at {} participations", seededParticipations);
    }

    private void assertQuestionParity(QuestionStatistic onTheFly, int ratedParticipants, int unratedParticipants, int ratedCorrect, int unratedCorrect, String questionType) {
        assertThat(onTheFly.ratedParticipants()).as("rated participants of the %s question", questionType).isEqualTo((long) ratedParticipants);
        assertThat(onTheFly.unratedParticipants()).as("unrated participants of the %s question", questionType).isEqualTo((long) unratedParticipants);
        assertThat(onTheFly.ratedCorrect()).as("rated correct counter of the %s question", questionType).isEqualTo((long) ratedCorrect);
        assertThat(onTheFly.unratedCorrect()).as("unrated correct counter of the %s question", questionType).isEqualTo((long) unratedCorrect);
    }

    private static QuizQuestionStatistic storedStatisticOf(QuizExercise quiz, long questionId) {
        return quiz.getQuizQuestions().stream().filter(question -> question.getId() == questionId).findFirst().orElseThrow().getQuizQuestionStatistic();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Seeding.
    // ----------------------------------------------------------------------------------------------------------------

    private QuizExercise createQuiz() {
        Course course = courseUtilService.createCourse();

        QuizExercise quiz = QuizExerciseFactoryLocal.generateQuiz(course, QUIZ_RELEASE, QUIZ_DUE);
        quiz.addQuestion(QuizExerciseFactoryLocal.multipleChoiceQuestion(MC_POINTS));
        quiz.addQuestion(QuizExerciseFactoryLocal.dragAndDropQuestion(DND_POINTS));
        quiz.addQuestion(QuizExerciseFactoryLocal.shortAnswerQuestion(SA_POINTS));
        quiz.setMaxPoints(quiz.getOverallQuizPoints());

        QuizExercise saved = quizExerciseService.save(quiz);
        seededQuizId = saved.getId();
        return quizExerciseRepository.findByIdWithQuestionsElseThrow(saved.getId());
    }

    /**
     * Grows the data set to the requested number of participations.
     *
     * @param quiz         the quiz to add participations to
     * @param targetAmount the total number of participations the quiz should have afterwards
     * @return how long the seeding took in milliseconds
     */
    private long growTo(QuizExercise quiz, int targetAmount) {
        long start = System.nanoTime();
        List<QuizQuestion> questions = quiz.getQuizQuestions();
        MultipleChoiceQuestion multipleChoiceQuestion = (MultipleChoiceQuestion) questions.get(0);
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) questions.get(1);
        ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) questions.get(2);

        for (long index = seededParticipations; index < targetAmount; index++) {
            StudentParticipation participation = new StudentParticipation();
            participation.setExercise(quiz);
            participation.setParticipant(students.get((int) index));
            participation.setInitializationState(InitializationState.FINISHED);
            participation.setInitializationDate(QUIZ_RELEASE);
            participation = studentParticipationRepository.save(participation);

            QuizSubmission submission = new QuizSubmission();
            submission.setParticipation(participation);
            submission.setSubmitted(true);
            submission.setSubmissionDate(QUIZ_DUE.minusMinutes(1));

            Set<SubmittedAnswer> submittedAnswers = new HashSet<>();
            submittedAnswers.add(SubmissionSeed.multipleChoiceAnswer(multipleChoiceQuestion, index));
            submittedAnswers.add(SubmissionSeed.dragAndDropAnswer(dragAndDropQuestion, index));
            submittedAnswers.add(SubmissionSeed.shortAnswerAnswer(shortAnswerQuestion, index));
            for (SubmittedAnswer submittedAnswer : submittedAnswers) {
                submittedAnswer.setSubmission(submission);
            }
            submission.setSubmittedAnswers(submittedAnswers);
            // The scores are the ones the scoring strategies produce, so that submitted_answer.score_in_points carries the same value a real evaluation would have written.
            submission.calculateAndUpdateScores(questions);
            submission = quizSubmissionRepository.save(submission);

            Result result = new Result();
            result.setSubmission(submission);
            result.setExerciseId(quiz.getId());
            result.setRated(true);
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            result.setCompletionDate(QUIZ_DUE);
            result.setScore(100.0 * submission.getScoreInPoints() / quiz.getOverallQuizPoints());
            result.setSuccessful(result.getScore() >= 100.0);
            resultRepository.save(result);
        }
        seededParticipations = targetAmount;
        return (System.nanoTime() - start) / 1_000_000;
    }

    /**
     * Brings the planner statistics up to date after a bulk insert. A benchmark inserts in seconds what a real quiz accumulates over an exam, so without this the optimizer would
     * still plan against the row counts of the previous scale and the measurement would report a plan flip rather than a cost. In production, autovacuum / the InnoDB statistics
     * refresh do the same thing on their own.
     */
    private void refreshDatabaseStatistics() {
        String databaseProduct = jdbcTemplate.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        if (databaseProduct != null && databaseProduct.toLowerCase(Locale.ROOT).contains("mysql")) {
            jdbcTemplate.execute("ANALYZE TABLE participation, submission, submitted_answer, result");
        }
        else {
            jdbcTemplate.execute("ANALYZE");
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Measurement.
    // ----------------------------------------------------------------------------------------------------------------

    private Timing measure(Supplier<?> page) {
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            blackHole = page.get();
        }
        long[] samples = new long[MEASURED_ROUNDS];
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long start = System.nanoTime();
            blackHole = page.get();
            samples[round] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        long median = samples[samples.length / 2];
        long p95 = samples[(int) Math.min(samples.length - 1L, Math.round(0.95 * samples.length) - 1)];
        return new Timing(median / 1_000_000.0, p95 / 1_000_000.0, samples[0] / 1_000_000.0, samples[samples.length - 1] / 1_000_000.0);
    }

    private record Timing(double medianMillis, double p95Millis, double minMillis, double maxMillis) {

        @Override
        public String toString() {
            return "%.1f ms (p95 %.1f, min %.1f, max %.1f)".formatted(medianMillis, p95Millis, minMillis, maxMillis);
        }
    }

    private record PointStatistic(Map<Double, long[]> counters, long ratedParticipants, long unratedParticipants) {

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PointStatistic that)) {
                return false;
            }
            if (ratedParticipants != that.ratedParticipants || unratedParticipants != that.unratedParticipants || !counters.keySet().equals(that.counters.keySet())) {
                return false;
            }
            return counters.entrySet().stream().allMatch(entry -> Arrays.equals(entry.getValue(), that.counters.get(entry.getKey())));
        }

        @Override
        public int hashCode() {
            return Long.hashCode(ratedParticipants * 31 + unratedParticipants);
        }
    }

    private record QuestionStatistic(long ratedParticipants, long unratedParticipants, long ratedCorrect, long unratedCorrect, Map<Long, long[]> elementCounters) {

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof QuestionStatistic that)) {
                return false;
            }
            if (ratedParticipants != that.ratedParticipants || unratedParticipants != that.unratedParticipants || ratedCorrect != that.ratedCorrect
                    || unratedCorrect != that.unratedCorrect || !elementCounters.keySet().equals(that.elementCounters.keySet())) {
                return false;
            }
            return elementCounters.entrySet().stream().allMatch(entry -> Arrays.equals(entry.getValue(), that.elementCounters.get(entry.getKey())));
        }

        @Override
        public int hashCode() {
            return Long.hashCode(ratedParticipants * 31 + ratedCorrect);
        }
    }

    /**
     * Builds the quiz. Kept local to this test so the question shapes (how many answer options, drop locations and spots) stay under the benchmark's control.
     */
    private static final class QuizExerciseFactoryLocal {

        private QuizExerciseFactoryLocal() {
        }

        static QuizExercise generateQuiz(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate) {
            QuizExercise quiz = new QuizExercise();
            quiz.setCourse(course);
            quiz.setTitle("Statistics benchmark quiz");
            quiz.setReleaseDate(releaseDate);
            quiz.setDueDate(dueDate);
            quiz.setDuration(600);
            quiz.setQuizMode(QuizMode.INDIVIDUAL);
            quiz.setRandomizeQuestionOrder(false);
            quiz.setAllowedNumberOfAttempts(1);
            quiz.setProblemStatement(null);
            quiz.setGradingInstructions(null);
            quiz.setPresentationScoreEnabled(false);
            return quiz;
        }

        static MultipleChoiceQuestion multipleChoiceQuestion(double points) {
            MultipleChoiceQuestion question = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(points).text("Which of these are correct?");
            question.setScoringType(ScoringType.ALL_OR_NOTHING);
            question.addAnswerOption(new AnswerOption().text("Option A").isCorrect(true));
            question.addAnswerOption(new AnswerOption().text("Option B").isCorrect(false));
            question.addAnswerOption(new AnswerOption().text("Option C").isCorrect(true));
            question.addAnswerOption(new AnswerOption().text("Option D").isCorrect(false));
            question.addAnswerOption(new AnswerOption().text("Option E").isCorrect(false));
            question.copyQuestionId();
            return question;
        }

        static DragAndDropQuestion dragAndDropQuestion(double points) {
            DragAndDropQuestion question = (DragAndDropQuestion) new DragAndDropQuestion().title("DnD").score(points).text("Assign the items");
            question.setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);
            for (int i = 0; i < 4; i++) {
                question.addDropLocation(new DropLocation().posX(10.0 * i).posY(10.0 * i).height(10d).width(10d));
                question.addDragItem(new DragItem().text("Item " + i));
            }
            for (int i = 0; i < 4; i++) {
                question.addCorrectMapping(new DragAndDropMapping().dragItem(question.getDragItems().get(i)).dropLocation(question.getDropLocations().get(i)));
            }
            question.copyQuestionId();
            return question;
        }

        static ShortAnswerQuestion shortAnswerQuestion(double points) {
            ShortAnswerQuestion question = (ShortAnswerQuestion) new ShortAnswerQuestion().title("SA").score(points).text("Fill in the [-spot 1] [-spot 2] [-spot 3] [-spot 4]");
            question.setScoringType(ScoringType.PROPORTIONAL_WITHOUT_PENALTY);
            // The default, lenient settings: this is the expensive configuration, because a non-identical text has to go through the fuzzy matcher.
            question.setMatchLetterCase(false);
            question.setSimilarityValue(85);
            String[] solutions = { "declaration", "inheritance", "polymorphism", "encapsulation" };
            for (int i = 0; i < solutions.length; i++) {
                question.addSpot(new ShortAnswerSpot().spotNr(i).width(20));
                question.addSolution(new ShortAnswerSolution().text(solutions[i]));
            }
            for (int i = 0; i < solutions.length; i++) {
                question.addCorrectMapping(new ShortAnswerMapping().spot(question.getSpots().get(i)).solution(question.getSolutions().get(i)));
            }
            question.copyQuestionId();
            return question;
        }
    }

    /**
     * Builds the submitted answers. The answers vary deterministically with the participation index so that the point statistic actually spreads over its buckets and the
     * short-answer fuzzy matcher sees a realistic mix of exact hits, typos and wrong answers.
     */
    private static final class SubmissionSeed {

        private SubmissionSeed() {
        }

        static SubmittedAnswer multipleChoiceAnswer(MultipleChoiceQuestion question, long index) {
            MultipleChoiceSubmittedAnswer answer = new MultipleChoiceSubmittedAnswer();
            answer.setQuizQuestion(question);
            List<AnswerOption> options = question.getAnswerOptions();
            // Four recurring answer patterns: fully correct, one correct option missing, one wrong option too many, and a wrong pick.
            switch ((int) (index % 4)) {
                case 0 -> {
                    answer.addSelectedOptions(options.get(0));
                    answer.addSelectedOptions(options.get(2));
                }
                case 1 -> answer.addSelectedOptions(options.get(0));
                case 2 -> {
                    answer.addSelectedOptions(options.get(0));
                    answer.addSelectedOptions(options.get(2));
                    answer.addSelectedOptions(options.get(3));
                }
                default -> {
                    answer.addSelectedOptions(options.get(1));
                    answer.addSelectedOptions(options.get(4));
                }
            }
            return answer;
        }

        static SubmittedAnswer dragAndDropAnswer(DragAndDropQuestion question, long index) {
            DragAndDropSubmittedAnswer answer = new DragAndDropSubmittedAnswer();
            answer.setQuizQuestion(question);
            List<DragItem> dragItems = question.getDragItems();
            List<DropLocation> dropLocations = question.getDropLocations();
            // Rotate the assignment: index % 4 == 0 is fully correct, the others get progressively more locations wrong.
            int shift = (int) (index % 4);
            for (int i = 0; i < dropLocations.size(); i++) {
                int dragItemIndex = i < shift ? (i + 1) % dragItems.size() : i;
                answer.addMappings(new DragAndDropMapping().dragItem(dragItems.get(dragItemIndex)).dropLocation(dropLocations.get(i)));
            }
            return answer;
        }

        static SubmittedAnswer shortAnswerAnswer(ShortAnswerQuestion question, long index) {
            ShortAnswerSubmittedAnswer answer = new ShortAnswerSubmittedAnswer();
            answer.setQuizQuestion(question);
            List<ShortAnswerSpot> spots = question.getSpots();
            for (int i = 0; i < spots.size(); i++) {
                String solution = question.getCorrectSolutionForSpot(spots.get(i)).iterator().next().getText();
                String text = switch ((int) ((index + i) % 3)) {
                    // Exact hit: the matcher returns early without running the ratio.
                    case 0 -> solution;
                    // A typo: this is the case that actually costs, because it has to go through the fuzzy ratio.
                    case 1 -> solution.substring(0, solution.length() - 1) + "x";
                    // Clearly wrong: also goes through the fuzzy ratio, and fails it.
                    default -> "something entirely different";
                };
                ShortAnswerSubmittedText submittedText = new ShortAnswerSubmittedText();
                submittedText.setSpot(spots.get(i));
                submittedText.setText(text);
                answer.addSubmittedTexts(submittedText);
            }
            return answer;
        }
    }
}
