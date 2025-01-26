package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.service.parser.CustomFeedbackParser;

class CustomFeedbackParserTest {

    private final List<LocalCITestJobDTO> failedTests = new ArrayList<>();

    private final List<LocalCITestJobDTO> successfulTests = new ArrayList<>();

    private final String fileName = "name.json";

    private final String testCaseName = "CustomName";

    @BeforeEach
    void init() {
        failedTests.clear();
        successfulTests.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void shouldParseCustomFeedback(final boolean successful) {
        final var message = "This is info about what went wrong / right";
        final var fileContent = String.format("""
                {
                  "name": "%s",
                  "successful": "%b",
                  "message": "%s"
                }""", testCaseName, successful, message);

        CustomFeedbackParser.processTestResultFile(fileName, fileContent, failedTests, successfulTests);

        assertThat(failedTests).hasSize(successful ? 0 : 1);
        assertThat(successfulTests).hasSize(successful ? 1 : 0);
        var parsedTest = successful ? successfulTests.getFirst() : failedTests.getFirst();
        assertThat(parsedTest.name()).isEqualTo(testCaseName);
        assertThat(parsedTest.testMessages()).hasSize(1);
        var testMessage = parsedTest.testMessages().getFirst();
        assertThat(testMessage).isEqualTo(message);
    }

    @Test
    void shouldParseSuccessfulCustomFeedbackWithEmptyMessage() {
        final var fileContent = String.format("""
                {
                  "name": "%s",
                  "successful": true,
                  "message": ""
                }""", testCaseName);

        CustomFeedbackParser.processTestResultFile(fileName, fileContent, failedTests, successfulTests);

        assertThat(successfulTests).hasSize(1);
        var successfulTest = successfulTests.getFirst();
        assertThat(successfulTest.name()).isEqualTo(testCaseName);
        assertThat(successfulTest.testMessages()).hasSize(1);
    }

    @Test
    void shouldParseSuccessfulCustomFeedbackWithNullMessage() {
        final var fileContent = String.format("""
                {
                  "name": "%s",
                  "successful": true,
                  "message": null
                }""", testCaseName);

        CustomFeedbackParser.processTestResultFile(fileName, fileContent, failedTests, successfulTests);

        assertThat(successfulTests).hasSize(1);
        var successfulTest = successfulTests.getFirst();
        assertThat(successfulTest.name()).isEqualTo(testCaseName);
        assertThat(successfulTest.testMessages()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " " })
    void shouldNotParseFailedCustomFeedbackWithEmptyMessage(String name) {
        final var fileContent = String.format("""
                {
                  "name": "%s",
                  "successful": false,
                  "message": "%s"
                }""", testCaseName, name);

        CustomFeedbackParser.processTestResultFile(fileName, fileContent, failedTests, successfulTests);

        assertThat(failedTests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " " })
    void shouldNotParseFailedCustomFeedbackWithNullMessage() {
        final var fileContent = String.format("""
                {
                  "name": "%s",
                  "successful": false,
                  "message": null
                }""", testCaseName);

        CustomFeedbackParser.processTestResultFile(fileName, fileContent, failedTests, successfulTests);

        assertThat(failedTests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " " })
    void shouldNotParseCustomFeedbackWithoutName(String name) {
        final var fileContent = String.format("""
                {
                  "name": "%s",
                  "successful": true,
                  "message": "%s"
                }""", name, testCaseName);

        CustomFeedbackParser.processTestResultFile(fileName, fileContent, failedTests, successfulTests);

        assertThat(successfulTests).isEmpty();
    }

    @Test
    void shouldNotParseCustomFeedbackWithNullName() {
        final var fileContent = String.format("""
                {
                  "name": null,
                  "successful": true,
                  "message": "%s"
                }""", testCaseName);

        CustomFeedbackParser.processTestResultFile(fileName, fileContent, failedTests, successfulTests);

        assertThat(successfulTests).isEmpty();
    }
}
