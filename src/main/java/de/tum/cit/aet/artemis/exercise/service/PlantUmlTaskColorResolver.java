package de.tum.cit.aet.artemis.exercise.service;

import java.util.HashMap;
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

    /** {@code <testid>NNN</testid>}. */
    private static final Pattern TESTID_PATTERN = Pattern.compile("<testid>(\\d+)</testid>");

    private PlantUmlTaskColorResolver() {
    }

    /**
     * Rewrites every {@code testsColor(...)} token in the given PlantUML source to a concrete color
     * (green, red, or grey) based on the test results. Other PlantUML content is returned unchanged.
     *
     * @param source      the PlantUML source text
     * @param testResults map of test id → feedback, or {@code null} if no results are available
     * @return PlantUML source with test-color tokens resolved
     */
    public static String resolve(String source, @Nullable Map<Long, TestFeedbackInputDTO> testResults) {
        Map<String, TestFeedbackInputDTO> byName = buildByNameLookup(testResults);

        String resolved = TESTS_COLOR_TAG_PATTERN.matcher(source).replaceAll(match -> {
            String color = resolveColor(match.group(1).trim(), testResults, byName);
            return Matcher.quoteReplacement("<color:" + color + ">" + match.group(2) + "</color>");
        });
        // Text coloring must be checked before plain arrow/element coloring to avoid partial matches.
        resolved = TESTS_COLOR_TEXT_PATTERN.matcher(resolved).replaceAll(match -> {
            String color = resolveColor(match.group(1).trim(), testResults, byName);
            return Matcher.quoteReplacement("#text:" + color);
        });
        resolved = TESTS_COLOR_ARROW_PATTERN.matcher(resolved).replaceAll(match -> {
            String color = resolveColor(match.group(1).trim(), testResults, byName);
            return Matcher.quoteReplacement("#" + color);
        });
        return resolved;
    }

    /**
     * Strips {@code <testid>N</testid>} wrappers from the given text, leaving the numeric identifier.
     * Used to clean up PlantUML source so the layout engine does not see unknown XML-looking tokens.
     *
     * @param text the source text potentially containing {@code <testid>N</testid>} wrappers
     * @return the text with those wrappers replaced by their numeric identifier
     */
    public static String stripTestIdWrappers(String text) {
        return TESTID_PATTERN.matcher(text).replaceAll("$1");
    }

    private static Map<String, TestFeedbackInputDTO> buildByNameLookup(@Nullable Map<Long, TestFeedbackInputDTO> testResults) {
        if (testResults == null) {
            return Map.of();
        }
        Map<String, TestFeedbackInputDTO> byName = new HashMap<>();
        for (TestFeedbackInputDTO detail : testResults.values()) {
            byName.put(detail.testName(), detail);
        }
        return byName;
    }

    private static String resolveColor(String testRef, @Nullable Map<Long, TestFeedbackInputDTO> testResults, Map<String, TestFeedbackInputDTO> byName) {
        if (testResults == null) {
            return "grey";
        }
        Matcher idMatcher = TESTID_PATTERN.matcher(testRef);
        if (idMatcher.matches()) {
            long testId = Long.parseLong(idMatcher.group(1));
            TestFeedbackInputDTO detail = testResults.get(testId);
            return detail == null ? "grey" : detail.passed() ? "green" : "red";
        }
        TestFeedbackInputDTO detail = byName.get(testRef);
        return detail == null ? "grey" : detail.passed() ? "green" : "red";
    }
}
