package de.tum.cit.aet.artemis.exercise.service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;

/**
 * Resolves the test references used in problem statements ({@code <testid>N</testid>} wrappers or exact test names)
 * against the test results supplied with a render request, and maps a reference to its tri-state outcome.
 * <p>
 * Shared by the task renderer and the PlantUML color resolver so both paths classify a test identically: without a
 * single implementation a task could render as passed while its diagram renders grey.
 * <p>
 * Name matching is exact and case-sensitive, including any trailing parentheses ({@code testMergeSort()}), matching
 * {@code ProgrammingExerciseTaskService}. This class is stateless with respect to the database: it only ever sees
 * the results provided in the request.
 */
public final class TestFeedbackLookup {

    private static final Pattern TESTID_PATTERN = Pattern.compile("<testid>(\\d+)</testid>");

    private final @Nullable Map<Long, TestFeedbackInputDTO> byId;

    private final Map<String, TestFeedbackInputDTO> byName;

    private TestFeedbackLookup(@Nullable Map<Long, TestFeedbackInputDTO> byId, Map<String, TestFeedbackInputDTO> byName) {
        this.byId = byId;
        this.byName = byName;
    }

    /**
     * Creates a lookup over the given test results.
     *
     * @param testResults map of test id → feedback, or {@code null} when no result is available at all
     * @return the lookup
     */
    public static TestFeedbackLookup of(@Nullable Map<Long, TestFeedbackInputDTO> testResults) {
        if (testResults == null) {
            return new TestFeedbackLookup(null, Map.of());
        }
        Map<String, TestFeedbackInputDTO> byName = new HashMap<>();
        for (TestFeedbackInputDTO detail : testResults.values()) {
            byName.put(detail.testName(), detail);
        }
        return new TestFeedbackLookup(testResults, byName);
    }

    /**
     * @return whether any result was supplied. {@code false} means "no result", which renders differently from
     *         "a result exists but this test is unknown".
     */
    public boolean hasResults() {
        return byId != null;
    }

    /**
     * Resolves a single authored test reference.
     *
     * @param reference the reference as authored, either {@code <testid>N</testid>} or an exact test name
     * @return the matching feedback, or {@code null} if the reference cannot be resolved
     */
    public @Nullable TestFeedbackInputDTO resolve(String reference) {
        if (byId == null) {
            return null;
        }
        String trimmed = reference.strip();
        Matcher idMatcher = TESTID_PATTERN.matcher(trimmed);
        if (idMatcher.matches()) {
            return byId.get(Long.parseLong(idMatcher.group(1)));
        }
        return byName.get(trimmed);
    }

    /**
     * @param reference the authored test reference
     * @return the tri-state outcome; unresolved references and feedback without a verdict are {@link TestOutcome#NOT_EXECUTED}
     */
    public TestOutcome outcomeOf(String reference) {
        return outcomeOf(resolve(reference));
    }

    /**
     * @param detail the resolved feedback, may be {@code null}
     * @return the tri-state outcome of the given feedback
     */
    public static TestOutcome outcomeOf(@Nullable TestFeedbackInputDTO detail) {
        if (detail == null || detail.passed() == null) {
            return TestOutcome.NOT_EXECUTED;
        }
        return Boolean.TRUE.equals(detail.passed()) ? TestOutcome.PASSED : TestOutcome.FAILED;
    }
}
