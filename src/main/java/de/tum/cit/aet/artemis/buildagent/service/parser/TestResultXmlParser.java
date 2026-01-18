package de.tum.cit.aet.artemis.buildagent.service.parser;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;

public class TestResultXmlParser {

    private static final XmlMapper mapper = new XmlMapper();

    // Default value, will be overridden when customized below in setMaxFeedbackLength
    private static int maxFeedbackLength = 20_000;

    // https://stackoverflow.com/a/4237934
    private static final String INVALID_XML_CHARS = "[^\t\r\n -\uD7FF\uE000-�\uD800\uDC00-\uDBFF\uDFFF]";

    // The root element can be preceded by processing instructions (<? ... ?>), comments (<!-- ... -->),
    // a doctype declaration (<!DOCTYPE ... >) and whitespace.
    // Comments cannot contain the string "--".
    private static final Pattern XML_ROOT_TAG_IS_TESTSUITES = Pattern.compile("^(<\\?([^?]|\\?[^>])*\\?>|<!--(-?[^-])*-->|<!DOCTYPE[^>]*>|\\s)*<testsuites(\\s|/?>)",
            Pattern.DOTALL);

    /**
     * Sets the maximum length for feedback messages before truncation.
     * This should be called before processing any test result files.
     *
     * @param maxLength the maximum length for feedback messages
     */
    public static void setMaxFeedbackLength(int maxLength) {
        maxFeedbackLength = maxLength;
    }

    /**
     * Parses the test result file and extracts failed and successful tests.
     * The name of nested testsuite elements are prepended with dots to the testcase name.
     * A singular top-level testsuite is not included in the name.
     * If multiple top-level testsuite elements are present, their names will be included.
     * Top-level testsuite elements refer to direct children of the root {@code <testsuites>} element or
     * the root {@code <testsuite>} element itself.
     * <p>
     * Examples of different XML structures:
     *
     * <pre>{@code
     * <testsuites>
     *   <testsuite name="ignored">
     *     <testsuite name="included">
     *       <testcase name="Test"/>
     *     </testsuite>
     *   </testsuite>
     * <testsuites>
     * }</pre>
     *
     * <pre>{@code
     * <testsuite name="ignored">
     *   <testsuite name="included">
     *     <testcase name="Test"/>
     *   </testsuite>
     * </testsuite>
     * }</pre>
     *
     * <pre>{@code
     * <testsuites>
     *   <testsuite name="included A">
     *     <testsuite name="included">
     *       <testcase name="Test"/>
     *     </testsuite>
     *   </testsuite>
     *   <testsuite name="included B">
     *     <testsuite name="included">
     *       <testcase name="Test"/>
     *     </testsuite>
     *   </testsuite>
     * <testsuites>
     * }</pre>
     *
     * @param testResultFileString The content of the test result file as a String.
     * @param failedTests          A list of failed tests. This list will be populated by the method.
     * @param successfulTests      A list of successful tests. This list will be populated by the method.
     * @throws IOException If an I/O error occurs while reading the test result file.
     */
    public static void processTestResultFile(String testResultFileString, List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests) throws IOException {
        testResultFileString = testResultFileString.replaceAll(INVALID_XML_CHARS, "");

        // The root element can be <testsuites> or <testsuite>
        if (XML_ROOT_TAG_IS_TESTSUITES.matcher(testResultFileString).find()) {
            TestSuites testSuites = mapper.readValue(testResultFileString, TestSuites.class);
            if (testSuites.testSuites().size() == 1) {
                TestSuite suite = testSuites.testSuites().getFirst();
                processTopLevelTestSuite(failedTests, successfulTests, suite);
            }
            else {
                for (TestSuite suite : testSuites.testSuites()) {
                    processInnerTestSuite(suite, failedTests, successfulTests, "");
                }
            }
        }
        else {
            TestSuite testSuite = mapper.readValue(testResultFileString, TestSuite.class);
            processTopLevelTestSuite(failedTests, successfulTests, testSuite);
        }
    }

    /**
     * Processes a top-level test suite, extracting test cases and populating the provided lists.
     *
     * @param failedTests     A list of failed tests. This list will be populated by the method.
     * @param successfulTests A list of successful tests. This list will be populated by the method.
     * @param suite           The top-level test suite to process.
     */
    private static void processTopLevelTestSuite(List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests, TestSuite suite) {
        processTestSuiteWithNamePrefix(suite, failedTests, successfulTests, "");
    }

    /**
     * Processes an inner (nested) test suite, applying a name prefix to its test cases.
     *
     * @param testSuite       The inner test suite to process.
     * @param failedTests     A list of failed tests. This list will be populated by the method.
     * @param successfulTests A list of successful tests. This list will be populated by the method.
     * @param outerNamePrefix The name prefix for the test suite, derived from its parent suites.
     */
    private static void processInnerTestSuite(TestSuite testSuite, List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests, String outerNamePrefix) {
        // namePrefix recursively accumulates all parent testsuite names seperated with dots
        String namePrefix;
        if (testSuite.name() != null) {
            namePrefix = outerNamePrefix + testSuite.name() + ".";
        }
        else {
            namePrefix = outerNamePrefix;
        }

        processTestSuiteWithNamePrefix(testSuite, failedTests, successfulTests, namePrefix);
    }

    /**
     * Processes a test suite, categorizing its test cases into failed or successful lists,
     * and recursively handling nested test suites.
     *
     * @param testSuite       The test suite to process.
     * @param failedTests     A list of failed tests. This list will be populated by the method.
     * @param successfulTests A list of successful tests. This list will be populated by the method.
     * @param namePrefix      The name prefix for the test cases within the suite.
     */
    private static void processTestSuiteWithNamePrefix(TestSuite testSuite, List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests, String namePrefix) {
        for (TestCase testCase : testSuite.testCases()) {
            if (testCase.isSkipped()) {
                continue;
            }
            Failure failure = testCase.extractFailure();
            if (failure != null) {
                // Truncate feedback message if it exceeds maximum length to avoid polluting the network or database with too long messages
                final var truncatedFeedbackMessage = truncateFeedbackMessage(failure.extractMessage());
                failedTests.add(new LocalCITestJobDTO(namePrefix + testCase.name(), testCase.classname(), List.of(truncatedFeedbackMessage)));
            }
            else {
                successfulTests.add(new LocalCITestJobDTO(namePrefix + testCase.name(), testCase.classname(), List.of()));
            }
        }

        for (TestSuite suite : testSuite.testSuites()) {
            processInnerTestSuite(suite, failedTests, successfulTests, namePrefix);
        }
    }

    /**
     * Truncates the feedback message to the maximum allowed length.
     *
     * @param message The feedback message to truncate.
     * @return The truncated feedback message.
     */
    private static String truncateFeedbackMessage(String message) {
        return StringUtils.truncate(message, maxFeedbackLength);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestSuites(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "testsuite") List<TestSuite> testSuites) {

        TestSuites {
            testSuites = Objects.requireNonNullElse(testSuites, Collections.emptyList());
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
    record TestCase(@JacksonXmlProperty(isAttribute = true, localName = "name") String name, @JacksonXmlProperty(isAttribute = true, localName = "classname") String classname,
            @JacksonXmlProperty(localName = "failure") Failure failure, @JacksonXmlProperty(localName = "error") Failure error,
            @JacksonXmlProperty(localName = "skipped") Skip skipped) {

        private boolean isSkipped() {
            return skipped != null;
        }

        private Failure extractFailure() {
            return failure != null ? failure : error;
        }

        // Intentionally empty record to represent the skipped tag (<skipped/>)
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Skip() {
        }
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
