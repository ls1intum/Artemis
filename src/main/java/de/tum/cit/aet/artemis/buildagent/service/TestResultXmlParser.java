package de.tum.cit.aet.artemis.buildagent.service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;

public class TestResultXmlParser {

    private static final XmlMapper mapper = new XmlMapper();

    // https://stackoverflow.com/a/4237934
    private static final String INVALID_XML_CHARS = "[^\t\r\n -\uD7FF\uE000-�\uD800\uDC00-\uDBFF\uDFFF]";

    /**
     * Parses the test result file and extracts failed and successful tests.
     * The name of nested testsuite elements are prepended with dots to the testcase name.
     * A singular top-level testsuite is not included in the name.
     * If multiple top-level testsuite elements are present, their names will be included.
     * Top-level testsuite elements refer to direct children of the root &lt;testsuites&gt; element or
     * the root &lt;testsuite&gt; element itself.
     *
     * @param testResultFileString The content of the test result file as a String.
     * @param failedTests          A list of failed tests. This list will be populated by the method.
     * @param successfulTests      A list of successful tests. This list will be populated by the method.
     * @throws IOException If an I/O error occurs while reading the test result file.
     */
    public static void processTestResultFile(String testResultFileString, List<BuildResult.LocalCITestJobDTO> failedTests, List<BuildResult.LocalCITestJobDTO> successfulTests)
            throws IOException {
        testResultFileString = testResultFileString.replaceAll(INVALID_XML_CHARS, "");
        TestSuite testSuite = mapper.readValue(testResultFileString, TestSuite.class);

        // The root element can be <testsuites> or <testsuite>
        if (testResultFileString.contains("<testsuites")) {
            if (testSuite.testSuites().size() == 1) {
                TestSuite suite = testSuite.testSuites().getFirst();
                processTopLevelTestSuite(failedTests, successfulTests, suite);
            }
            else {
                for (TestSuite suite : testSuite.testSuites()) {
                    processInnerTestSuite(suite, failedTests, successfulTests, "");
                }
            }
        }
        else {
            processTopLevelTestSuite(failedTests, successfulTests, testSuite);
        }
    }

    private static void processTopLevelTestSuite(List<BuildResult.LocalCITestJobDTO> failedTests, List<BuildResult.LocalCITestJobDTO> successfulTests, TestSuite suite) {
        processTestSuiteWithNamePrefix(suite, failedTests, successfulTests, "");
    }

    private static void processInnerTestSuite(TestSuite testSuite, List<BuildResult.LocalCITestJobDTO> failedTests, List<BuildResult.LocalCITestJobDTO> successfulTests,
            String outerNamePrefix) {
        String namePrefix;
        if (testSuite.name() != null) {
            namePrefix = outerNamePrefix + testSuite.name() + ".";
        }
        else {
            namePrefix = outerNamePrefix;
        }

        processTestSuiteWithNamePrefix(testSuite, failedTests, successfulTests, namePrefix);
    }

    private static void processTestSuiteWithNamePrefix(TestSuite testSuite, List<BuildResult.LocalCITestJobDTO> failedTests, List<BuildResult.LocalCITestJobDTO> successfulTests,
            String namePrefix) {
        for (TestCase testCase : testSuite.testCases()) {
            if (testCase.isSkipped()) {
                continue;
            }
            Failure failure = testCase.extractFailure();
            if (failure != null) {
                failedTests.add(new BuildResult.LocalCITestJobDTO(namePrefix + testCase.name(), List.of(failure.extractMessage())));
            }
            else {
                successfulTests.add(new BuildResult.LocalCITestJobDTO(namePrefix + testCase.name(), List.of()));
            }
        }

        for (TestSuite suite : testSuite.testSuites()) {
            processInnerTestSuite(suite, failedTests, successfulTests, namePrefix);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestSuite(@JacksonXmlProperty(isAttribute = true, localName = "name") String name,
            @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "testcase") List<TestCase> testCases,
            @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "testsuite") List<TestSuite> testSuites) {

        TestSuite {
            testCases = Objects.requireNonNullElse(testCases, Collections.emptyList());
            testSuites = Objects.requireNonNullElse(testSuites, Collections.emptyList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestCase(@JacksonXmlProperty(isAttribute = true, localName = "name") String name, @JacksonXmlProperty(localName = "failure") Failure failure,
            @JacksonXmlProperty(localName = "error") Failure error, @JacksonXmlProperty(localName = "skipped") Skip skipped) {

        private boolean isSkipped() {
            return skipped != null;
        }

        private Failure extractFailure() {
            return failure != null ? failure : error;
        }
    }

    // Intentionally empty record to represent the skipped tag (<skipped/>)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Skip() {
    }

    // Due to issues with Jackson this currently cannot be a record.
    // See https://github.com/FasterXML/jackson-module-kotlin/issues/138#issuecomment-1062725140
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Failure {

        private String message;

        private String detailedMessage;

        private String extractMessage() {
            if (message != null) {
                return message;
            }
            else if (detailedMessage != null) {
                return detailedMessage;
            }
            // empty text nodes are deserialized as null instead of a string, see: https://github.com/FasterXML/jackson-dataformat-xml/issues/565
            // note that this workaround does not fix the issue entirely, as strings of only whitespace become the empty string
            return "";
        }

        @JacksonXmlProperty(isAttribute = true, localName = "message")
        public void setMessage(String message) {
            this.message = message;
        }

        @JacksonXmlText
        public void setDetailedMessage(String detailedMessage) {
            this.detailedMessage = detailedMessage;
        }
    }
}
