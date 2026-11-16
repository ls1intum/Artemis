package de.tum.cit.aet.artemis.hyperion.runtime.verification;

import java.io.IOException;
import java.util.List;

import de.tum.cit.aet.artemis.aiworker.api.JUnitXmlParser;

/** Parses JUnit XML for isolated generation verification using the same report semantics as LocalCI. */
public final class JUnitReportParser {

    /** One non-skipped test and its failure messages. */
    public record TestCaseResult(String name, List<String> testMessages) {
    }

    private final int maxFeedbackLength;

    /**
     * @param maxFeedbackLength maximum number of characters retained per failure message
     */
    public JUnitReportParser(int maxFeedbackLength) {
        if (maxFeedbackLength < 0) {
            throw new IllegalArgumentException("Feedback length must not be negative");
        }
        this.maxFeedbackLength = maxFeedbackLength;
    }

    /**
     * Parses JUnit XML, ignoring skipped tests and retaining the configured maximum feedback length.
     *
     * @param testResultFileString the report content
     * @param failedTests          receives failed tests
     * @param successfulTests      receives successful tests
     * @throws IOException if reading the report fails
     */
    public void processTestResultFile(String testResultFileString, List<TestCaseResult> failedTests, List<TestCaseResult> successfulTests) throws IOException {
        JUnitXmlParser.processTestResultFile(testResultFileString, maxFeedbackLength, test -> failedTests.add(new TestCaseResult(test.name(), test.testMessages())),
                test -> successfulTests.add(new TestCaseResult(test.name(), test.testMessages())));
    }
}
