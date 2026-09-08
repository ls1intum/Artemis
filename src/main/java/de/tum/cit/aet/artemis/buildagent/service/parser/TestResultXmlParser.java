package de.tum.cit.aet.artemis.buildagent.service.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.hyperion.runtime.verification.JUnitReportParser;

/** Adapts the shared production JUnit parser to LocalCI's result DTOs. */
public class TestResultXmlParser {

    private static int maxFeedbackLength = 20_000;

    /**
     * Configures feedback truncation before parsing reports.
     *
     * @param maxLength maximum retained failure-message length
     */
    public static void setMaxFeedbackLength(int maxLength) {
        maxFeedbackLength = maxLength;
    }

    /**
     * Parses non-skipped cases using the shared suite-name and failure-message rules.
     *
     * @param testResultFileString raw JUnit XML
     * @param failedTests          failed cases to append
     * @param successfulTests      successful cases to append
     * @throws IOException if the report cannot be parsed
     */
    public static void processTestResultFile(String testResultFileString, List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests) throws IOException {
        var failures = new ArrayList<JUnitReportParser.TestCaseResult>();
        var successes = new ArrayList<JUnitReportParser.TestCaseResult>();
        new JUnitReportParser(maxFeedbackLength).processTestResultFile(testResultFileString, failures, successes);
        failures.forEach(test -> failedTests.add(new LocalCITestJobDTO(test.name(), test.testMessages())));
        successes.forEach(test -> successfulTests.add(new LocalCITestJobDTO(test.name(), test.testMessages())));
    }
}
