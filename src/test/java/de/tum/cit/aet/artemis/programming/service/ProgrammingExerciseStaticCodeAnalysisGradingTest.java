package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackMessage;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.service.FeedbackMessageService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localci.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;

@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseStaticCodeAnalysisGradingTest {

    @Mock
    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    @Mock
    private StaticCodeAnalysisCategoryRepository categoryRepository;

    @Mock
    private ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    @Spy
    private TestCasePointsService testCasePointsService = new TestCasePointsService(null);

    @InjectMocks
    private ProgrammingExerciseGradingService gradingService;

    private ProgrammingExercise exercise;

    private Result result;

    private StaticCodeAnalysisCategory category;

    @BeforeEach
    void setUp() {
        exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setCourse(new Course());
        exercise.setMaxPoints(10.0);
        exercise.setStaticCodeAnalysisEnabled(true);

        category = new StaticCodeAnalysisCategory();
        category.setName("Naming & Formatting");
        category.setState(CategoryState.GRADED);
        category.setPenalty(0.1);
        when(categoryRepository.findByExerciseId(1L)).thenReturn(Set.of(category));

        var testCase = new ProgrammingExerciseTestCase();
        testCase.setId(1L);
        testCase.setTestName("test");
        testCase.setWeight(1.0);
        when(testCaseRepository.findByExerciseIdAndActive(1L, true)).thenReturn(Set.of(testCase));

        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setExercise(exercise);
        var submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        result = new Result();
        result.setSubmission(submission);
        var testFeedback = new TestCaseFeedback();
        testFeedback.setTestCase(testCase);
        testFeedback.setPositive(true);
        result.addTestCaseFeedback(testFeedback);
    }

    @ParameterizedTest
    @ValueSource(strings = { "WhitespaceAroundCheck", "WhitespaceAfterCheck" })
    void chargesSameCheckstyleOccurrenceOnceAndKeepsDiagnostics(String secondRule) {
        var first = issue("WhitespaceAfterCheck");
        var second = issue(secondRule);

        grade(first, second);

        assertThat(result.getScore()).isEqualTo(99.0);
        assertThat(result.getScaFeedbacks()).containsExactly(first, second);
        assertThat(first.getPenalty()).isEqualTo(0.1);
        assertThat(second.getPenalty()).isZero();
        assertThat(first.getRule()).isEqualTo("WhitespaceAfterCheck");
        assertThat(second.getRule()).isEqualTo(secondRule);
        assertThat(result.calculateTotalPointsForProgrammingExercises(Map.of(1L, 10.0))).isCloseTo(9.9, within(0.00001));

        // Existing persisted results pass through the same grading path on re-evaluation.
        gradingService.calculateScoreForResult(result, exercise, false);
        assertThat(result.getScore()).isEqualTo(99.0);
        assertThat(second.getPenalty()).isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = { 10L, 11L })
    void gradesDetachedMessageReferencesWithoutLoadingTheirText(long secondMessageId) {
        var first = issue("WhitespaceAfterCheck");
        var second = issue("WhitespaceAroundCheck");
        first.setMessage(detachedMessage(10L));
        second.setMessage(detachedMessage(secondMessageId));

        grade(first, second);

        assertThat(result.getScore()).isEqualTo(secondMessageId == 10L ? 99.0 : 98.0);
        assertThat(second.getPenalty()).isEqualTo(secondMessageId == 10L ? 0.0 : 0.1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "file", "line", "column", "endLine", "endColumn", "message", "tool" })
    void chargesDistinctOccurrencesSeparately(String difference) {
        var first = issue("WhitespaceAfterCheck");
        var second = issue("WhitespaceAroundCheck");
        switch (difference) {
            case "file" -> second.setFilePath("src/Other.java");
            case "line" -> second.setStartLine(9);
            case "column" -> second.setStartColumn(10);
            case "endLine" -> second.setEndLine(9);
            case "endColumn" -> second.setEndColumn(10);
            case "message" -> second.getMessage().setText("'if' is not preceded with whitespace.");
            case "tool" -> second.setTool(StaticCodeAnalysisTool.PMD);
            default -> throw new IllegalArgumentException(difference);
        }

        grade(first, second);

        assertThat(result.getScore()).isEqualTo(98.0);
        assertThat(result.getScaFeedbacks()).extracting(ScaFeedback::getPenalty).containsExactly(0.1, 0.1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "file", "blankFile", "unavailableFile", "line", "zeroLine", "endLine", "zeroEndLine", "reversedLines", "column", "zeroColumn", "endColumn",
            "zeroEndColumn", "reversedColumns", "message", "blankMessage" })
    void doesNotMergeIssuesWithoutEnoughInformation(String missing) {
        var first = issue("WhitespaceAfterCheck");
        var second = issue("WhitespaceAroundCheck");
        for (var feedback : List.of(first, second)) {
            switch (missing) {
                case "file" -> feedback.setFilePath(null);
                case "blankFile" -> feedback.setFilePath(" ");
                case "unavailableFile" -> feedback.setFilePath("notAvailable");
                case "line" -> feedback.setStartLine(null);
                case "zeroLine" -> feedback.setStartLine(0);
                case "endLine" -> feedback.setEndLine(null);
                case "zeroEndLine" -> feedback.setEndLine(0);
                case "reversedLines" -> feedback.setEndLine(7);
                case "column" -> feedback.setStartColumn(null);
                case "zeroColumn" -> feedback.setStartColumn(0);
                case "endColumn" -> feedback.setEndColumn(null);
                case "zeroEndColumn" -> feedback.setEndColumn(0);
                case "reversedColumns" -> feedback.setEndColumn(8);
                case "message" -> feedback.setMessage(null);
                case "blankMessage" -> feedback.getMessage().setText(" ");
                default -> throw new IllegalArgumentException(missing);
            }
        }

        grade(first, second);

        assertThat(result.getScore()).isEqualTo(98.0);
        assertThat(result.getScaFeedbacks()).extracting(ScaFeedback::getPenalty).containsExactly(0.1, 0.1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void chargesReportsWithoutFilePathsSeparatelyAfterIngestion(String filePath) {
        var feedback = ingestIssues(filePath, filePath);

        grade(feedback.toArray(ScaFeedback[]::new));

        assertThat(result.getScore()).isEqualTo(98.0);
        assertThat(result.getScaFeedbacks()).hasSize(2);
        assertThat(result.getScaFeedbacks()).extracting(ScaFeedback::getPenalty).containsExactly(0.1, 0.1);
    }

    @ParameterizedTest
    @CsvSource({ "511, 99.0", "512, 98.0", "513, 98.0" })
    void onlyDeduplicatesPathsBelowTheIngestionLimit(int pathLength, double expectedScore) {
        var first = issue("WhitespaceAfterCheck");
        var second = issue("WhitespaceAroundCheck");
        first.setFilePath("x".repeat(pathLength));
        second.setFilePath(first.getFilePath());

        grade(first, second);

        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.getScaFeedbacks()).hasSize(2);
    }

    @Test
    void chargesDifferentFilesSeparatelyWhenIngestionTruncatesTheirPathsToTheSamePrefix() {
        var commonPrefix = "x".repeat(ScaFeedback.MAX_FILE_PATH_LENGTH);
        var feedback = ingestIssues(commonPrefix + "First.java", commonPrefix + "Second.java");
        assertThat(feedback).extracting(ScaFeedback::getFilePath).containsExactly(commonPrefix, commonPrefix);

        grade(feedback.toArray(ScaFeedback[]::new));

        assertThat(result.getScore()).isEqualTo(98.0);
        assertThat(result.getScaFeedbacks()).extracting(ScaFeedback::getPenalty).containsExactly(0.1, 0.1);
    }

    @Test
    void keepsPenaltiesFromDifferentCategories() {
        var first = issue("WhitespaceAfterCheck");
        var second = issue("WhitespaceAroundCheck");
        second.setCategory("Other category");
        var otherCategory = new StaticCodeAnalysisCategory();
        otherCategory.setName("Other category");
        otherCategory.setPenalty(0.1);
        otherCategory.setState(CategoryState.GRADED);
        when(categoryRepository.findByExerciseId(1L)).thenReturn(Set.of(category, otherCategory));

        grade(first, second);

        assertThat(result.getScore()).isEqualTo(98.0);
        assertThat(result.getScaFeedbacks()).extracting(ScaFeedback::getPenalty).containsExactly(0.1, 0.1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void appliesCapsToUniqueOccurrencesAndKeepsFeedbackCreditsConsistent(boolean exerciseCap) {
        if (exerciseCap) {
            exercise.setMaxStaticCodeAnalysisPenalty(1);
        }
        else {
            category.setMaxPenalty(0.1);
        }
        var first = issue("WhitespaceAfterCheck");
        var duplicate = issue("WhitespaceAroundCheck");
        var distinct = issue("WhitespaceAfterCheck");
        distinct.setStartLine(10);
        distinct.setEndLine(10);

        grade(first, duplicate, distinct);

        assertThat(result.getScore()).isEqualTo(99.0);
        assertThat(result.getScaFeedbacks()).extracting(ScaFeedback::getPenalty).containsExactly(0.05, 0.0, 0.05);
        assertThat(result.calculateTotalPointsForProgrammingExercises(Map.of(1L, 10.0))).isCloseTo(9.9, within(0.00001));
    }

    private void grade(ScaFeedback... feedback) {
        for (var issue : feedback) {
            result.addScaFeedback(issue);
        }
        gradingService.calculateScoreForResult(result, exercise, false);
    }

    private ScaFeedback issue(String rule) {
        var feedback = new ScaFeedback();
        feedback.setTool(StaticCodeAnalysisTool.CHECKSTYLE);
        feedback.setCategory(category.getName());
        feedback.setRule(rule);
        feedback.setFilePath("src/de/tum/cit/aet/pse/BinarySearch.java");
        feedback.setStartLine(8);
        feedback.setEndLine(8);
        feedback.setStartColumn(9);
        feedback.setEndColumn(9);
        var message = new FeedbackMessage();
        message.setText("'if' is not followed by whitespace.");
        feedback.setMessage(message);
        return feedback;
    }

    private List<ScaFeedback> ingestIssues(String firstPath, String secondPath) {
        var message = new FeedbackMessage();
        message.setId(10L);
        message.setText("'if' is not followed by whitespace.");
        var messageService = mock(FeedbackMessageService.class);
        when(messageService.getOrCreate(message.getText())).thenReturn(message);
        var ingestionService = new ProgrammingExerciseFeedbackCreationService(testCaseRepository, null, null, null, categoryRepository, messageService, Optional.empty(), null,
                null, "main");
        var issues = List.of(new StaticCodeAnalysisIssue(firstPath, 8, 8, 9, 9, "WhitespaceAfterCheck", "whitespace", message.getText(), "error", null),
                new StaticCodeAnalysisIssue(secondPath, 8, 8, 9, 9, "WhitespaceAroundCheck", "whitespace", message.getText(), "error", null));
        var feedback = ingestionService.createFeedbackFromStaticCodeAnalysisReports(List.of(new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.CHECKSTYLE, issues)));
        feedback.forEach(issue -> issue.setCategory(category.getName()));
        return feedback;
    }

    private FeedbackMessage detachedMessage(long id) {
        // A detached Hibernate reference exposes its identifier, but reading its text requires a session.
        var message = mock(FeedbackMessage.class, invocation -> {
            if (invocation.getMethod().getName().equals("getText")) {
                throw new LazyInitializationException("Detached feedback message");
            }
            return CALLS_REAL_METHODS.answer(invocation);
        });
        message.setId(id);
        return message;
    }
}
