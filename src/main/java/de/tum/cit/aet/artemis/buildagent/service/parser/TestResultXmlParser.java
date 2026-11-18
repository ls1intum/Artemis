package de.tum.cit.aet.artemis.buildagent.service.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.aiworker.api.JUnitXmlParser;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;

public final class TestResultXmlParser {

    private static int maxFeedbackLength = 20_000;

    private TestResultXmlParser() {
        // Utility class.
    }

    /**
     * Sets the maximum length for feedback messages before processing test result files.
     *
     * @param maxLength the maximum length for feedback messages
     */
    public static void setMaxFeedbackLength(int maxLength) {
        maxFeedbackLength = maxLength;
    }

    /**
     * Parses JUnit XML, preserving nested suite names and test class information. Skipped tests are ignored.
     *
     * @param testResultFileString the report content
     * @param failedTests          receives failed tests
     * @param successfulTests      receives successful tests
     * @throws IOException if reading the report fails
     */
    public static void processTestResultFile(String testResultFileString, List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests) throws IOException {
        processTestResultFile(testResultFileString, failedTests, successfulTests, new ArrayList<>());
    }

    /**
     * Parses JUnit XML, preserving nested suite names and test class information. Skipped tests (e.g. a disabled test or
     * a failed assumption) are collected separately so they can be told apart from tests that produced no result at all.
     *
     * @param testResultFileString the report content
     * @param failedTests          receives failed tests
     * @param successfulTests      receives successful tests
     * @param skippedTests         receives skipped tests, their skip reason as the only message
     * @throws IOException if reading the report fails
     */
    public static void processTestResultFile(String testResultFileString, List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests,
            List<LocalCITestJobDTO> skippedTests) throws IOException {
        JUnitXmlParser.processTestResultFile(testResultFileString, maxFeedbackLength,
                test -> failedTests.add(new LocalCITestJobDTO(test.name(), test.testMessages(), test.testClass())),
                test -> successfulTests.add(new LocalCITestJobDTO(test.name(), test.testMessages(), test.testClass())),
                test -> skippedTests.add(new LocalCITestJobDTO(test.name(), test.testMessages(), test.testClass())));
    }
}
