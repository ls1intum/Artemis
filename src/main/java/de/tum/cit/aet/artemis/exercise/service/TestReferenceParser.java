package de.tum.cit.aet.artemis.exercise.service;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Splits the captured test-reference group of an Artemis {@code [task][name](refs)} marker into individual references.
 * <p>
 * Splitting happens on top-level commas only: parameterized test names may themselves contain commas inside round
 * brackets (e.g. {@code testInsert(InsertMock, 1)}). This grammar is shared by the problem-statement renderer and
 * {@code ProgrammingExerciseTaskService}; it must stay in sync with the client-side task extension.
 */
public final class TestReferenceParser {

    private TestReferenceParser() {
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
