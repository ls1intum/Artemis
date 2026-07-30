package de.tum.cit.aet.artemis.exercise.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Splits the captured test-reference group of an Artemis {@code [task][name](refs)} marker into individual references,
 * and owns the {@code <testid>N</testid>} grammar shared by every consumer of those references.
 * <p>
 * Splitting happens on top-level commas only: parameterized test names may themselves contain commas inside round
 * brackets (e.g. {@code testInsert(InsertMock, 1)}). This grammar is shared by the problem-statement renderer and
 * {@code ProgrammingExerciseTaskService}; it must stay in sync with the client-side task extension.
 * <p>
 * Empty references are dropped everywhere, including a trailing one (e.g. {@code "testA,"} returns {@code ["testA"]}).
 * This is a deliberate tightening of the previous behavior, approved to ensure reference counts remain accurate.
 */
public final class TestReferenceParser {

    /** {@code <testid>N</testid>}. Group 1 is the numeric identifier. */
    private static final Pattern TESTID_PATTERN = Pattern.compile("<testid>(\\d+)</testid>");

    private TestReferenceParser() {
    }

    /**
     * Extracts the test id from a reference that <em>contains</em> a {@code <testid>N</testid>} wrapper.
     * <p>
     * Matching is deliberately lenient (contains, not equals), mirroring {@code ProgrammingExerciseTaskService}: a
     * reference such as {@code testName<testid>5</testid>} is authored in practice and must resolve to test 5. This is
     * the single implementation for every consumer, so the task spans and the PlantUML diagram colors cannot classify
     * the same reference differently.
     *
     * @param reference the authored test reference
     * @return the wrapped test id, or {@code null} if the reference carries no {@code <testid>} wrapper
     */
    public static @Nullable Long extractTestId(String reference) {
        Matcher matcher = TESTID_PATTERN.matcher(reference);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    /**
     * Strips {@code <testid>N</testid>} wrappers from the given text, leaving the numeric identifier. Used to clean up
     * prose and PlantUML source so neither the reader nor the layout engine sees unknown XML-looking tokens.
     *
     * @param text the source text potentially containing {@code <testid>N</testid>} wrappers
     * @return the text with those wrappers replaced by their numeric identifier
     */
    public static String stripTestIdWrappers(String text) {
        return TESTID_PATTERN.matcher(text).replaceAll("$1");
    }

    /**
     * Splits the captured test case references by top-level commas.
     *
     * @param capturedTestCaseNames the captured references from a task marker, may be null
     * @return the individual, trimmed references; empty if there are none
     */
    public static List<String> splitTestReferences(@Nullable String capturedTestCaseNames) {
        List<String> references = new ArrayList<>();
        if (capturedTestCaseNames == null || capturedTestCaseNames.isEmpty()) {
            return references;
        }

        int numberUnclosedRoundedBrackets = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < capturedTestCaseNames.length(); i++) {
            char currentChar = capturedTestCaseNames.charAt(i);

            if (currentChar == ',' && numberUnclosedRoundedBrackets == 0) {
                addIfNotBlank(references, current);
                current = new StringBuilder();
                continue;
            }
            if (currentChar == '(') {
                numberUnclosedRoundedBrackets++;
            }
            else if (currentChar == ')') {
                numberUnclosedRoundedBrackets--;
            }
            current.append(currentChar);
        }
        addIfNotBlank(references, current);
        return references;
    }

    private static void addIfNotBlank(List<String> references, StringBuilder candidate) {
        String value = candidate.toString().strip();
        if (!value.isEmpty()) {
            references.add(value);
        }
    }
}
