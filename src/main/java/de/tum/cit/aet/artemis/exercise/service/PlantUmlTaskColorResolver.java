package de.tum.cit.aet.artemis.exercise.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;

/**
 * Resolves the Artemis-specific {@code testsColor(...)} tokens in PlantUML source to concrete colors
 * based on the current test results.
 * <p>
 * Three authoring forms are supported (kept in sync with the Angular renderer so the two paths produce
 * identical diagrams):
 * <ul>
 * <li>{@code <color:testsColor(testName)>text</color>} — coloring inside a color tag</li>
 * <li>{@code #testsColor(testName)} — arrow / element coloring</li>
 * <li>{@code #text:testsColor(testName)} — text coloring</li>
 * </ul>
 * A test name may be either the exact test name or a {@code <testid>N</testid>} wrapper.
 */
public final class PlantUmlTaskColorResolver {

    /** Captures a single test identifier inside {@code testsColor(...)}. */
    private static final String TESTS_COLOR_INNER = "(\\s*[^()\\s]+(?:\\([^()]*\\))?)";

    /** {@code <color:testsColor(testName)>text</color>}. Group 1: test identifier, Group 2: inner text. */
    private static final Pattern TESTS_COLOR_TAG_PATTERN = Pattern.compile("<color:testsColor\\(" + TESTS_COLOR_INNER + "\\)>(.*?)</color>");

    /** {@code #testsColor(testName)}. Group 1: test identifier. */
    private static final Pattern TESTS_COLOR_ARROW_PATTERN = Pattern.compile("#testsColor\\(" + TESTS_COLOR_INNER + "\\)");

    /** {@code #text:testsColor(testName)}. Group 1: test identifier. */
    private static final Pattern TESTS_COLOR_TEXT_PATTERN = Pattern.compile("#text:testsColor\\(" + TESTS_COLOR_INNER + "\\)");

    private PlantUmlTaskColorResolver() {
    }

    /**
     * Rewrites every {@code testsColor(...)} token in the given PlantUML source to a concrete color
     * (green, red, or grey) based on the test results. Other PlantUML content is returned unchanged.
     *
     * @param source         the PlantUML source text
     * @param testResults    map of test id → feedback, or {@code null} if no results are available
     * @param allTestsPassed whether the request declared that every test passed although it carries no per-test
     *                           feedback. Only honored when {@code testResults} is {@code null}: individual test
     *                           outcomes always win, so a request that carries both never colors a failing test green.
     * @return PlantUML source with test-color tokens resolved
     */
    public static String resolve(String source, @Nullable Map<Long, TestFeedbackInputDTO> testResults, boolean allTestsPassed) {
        TestFeedbackLookup lookup = TestFeedbackLookup.of(testResults);
        // Callers hand over the raw request flag, so the predicate is applied here, once, on the same two inputs the
        // task renderer uses: task markers and the diagram beside them must never disagree.
        boolean allPassed = allTestsPassed && testResults == null;

        String resolved = TESTS_COLOR_TAG_PATTERN.matcher(source).replaceAll(match -> {
            String color = colorFor(lookup, match.group(1), allPassed);
            return Matcher.quoteReplacement("<color:" + color + ">" + match.group(2) + "</color>");
        });
        // Text coloring must be checked before plain arrow/element coloring to avoid partial matches.
        resolved = TESTS_COLOR_TEXT_PATTERN.matcher(resolved).replaceAll(match -> Matcher.quoteReplacement("#text:" + colorFor(lookup, match.group(1), allPassed)));
        resolved = TESTS_COLOR_ARROW_PATTERN.matcher(resolved).replaceAll(match -> Matcher.quoteReplacement("#" + colorFor(lookup, match.group(1), allPassed)));
        return resolved;
    }

    private static String colorFor(TestFeedbackLookup lookup, String testRef, boolean allPassed) {
        if (allPassed) {
            // No feedback can resolve at all in this case, so every reference (by id or by name) is green.
            return "green";
        }
        return switch (lookup.outcomeOf(testRef)) {
            case PASSED -> "green";
            case FAILED -> "red";
            case NOT_EXECUTED -> "grey";
        };
    }
}
