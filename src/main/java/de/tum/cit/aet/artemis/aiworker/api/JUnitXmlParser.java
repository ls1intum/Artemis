package de.tum.cit.aet.artemis.aiworker.api;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

/** Portable JUnit XML decoding shared by LocalCI and isolated generation verification. */
public final class JUnitXmlParser {

    /** A non-skipped test, retaining its optional declaring class. */
    public record TestCaseResult(String name, List<String> testMessages, String testClass) {
    }

    private static final XmlMapper mapper = new XmlMapper();

    // https://stackoverflow.com/a/4237934
    private static final Pattern INVALID_XML_CHARACTER = Pattern.compile("[^\t\r\n -\uD7FF\uE000-�\uD800\uDC00-\uDBFF\uDFFF]");

    // The root element can be preceded by processing instructions (<? ... ?>), comments (<!-- ... -->),
    // a doctype declaration (<!DOCTYPE ... >) and whitespace.
    // Comments cannot contain the string "--".
    private static final Pattern XML_ROOT_TAG_IS_TESTSUITES = Pattern.compile("^(<\\?([^?]|\\?[^>])*\\?>|<!--(-?[^-])*-->|<!DOCTYPE[^>]*>|\\s)*<testsuites(\\s|/?>)",
            Pattern.DOTALL);

    private JUnitXmlParser() {
        // Utility class.
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
     * @param maxFeedbackLength    maximum number of characters retained per failure message
     * @param failedTests          receives failed tests in report order
     * @param successfulTests      receives successful tests in report order
     * @throws IOException If an I/O error occurs while reading the test result file.
     */
    public static void processTestResultFile(String testResultFileString, int maxFeedbackLength, Consumer<TestCaseResult> failedTests, Consumer<TestCaseResult> successfulTests)
            throws IOException {
        testResultFileString = INVALID_XML_CHARACTER.matcher(testResultFileString).replaceAll("");

        // The root element can be <testsuites> or <testsuite>
        if (XML_ROOT_TAG_IS_TESTSUITES.matcher(testResultFileString).find()) {
            TestSuites testSuites = mapper.readValue(testResultFileString, TestSuites.class);
            if (testSuites.testSuites().size() == 1) {
                TestSuite suite = testSuites.testSuites().getFirst();
                processTestSuiteWithNamePrefix(suite, maxFeedbackLength, failedTests, successfulTests, "");
            }
            else {
                for (TestSuite suite : testSuites.testSuites()) {
                    processInnerTestSuite(suite, maxFeedbackLength, failedTests, successfulTests, "");
                }
            }
        }
        else {
            TestSuite testSuite = mapper.readValue(testResultFileString, TestSuite.class);
            processTestSuiteWithNamePrefix(testSuite, maxFeedbackLength, failedTests, successfulTests, "");
        }
    }

    /**
     * Processes an inner (nested) test suite, applying a name prefix to its test cases.
     *
     * @param testSuite         The inner test suite to process.
     * @param maxFeedbackLength maximum number of characters retained per failure message
     * @param failedTests       receives failed tests
     * @param successfulTests   receives successful tests
     * @param outerNamePrefix   The name prefix for the test suite, derived from its parent suites.
     */
    private static void processInnerTestSuite(TestSuite testSuite, int maxFeedbackLength, Consumer<TestCaseResult> failedTests, Consumer<TestCaseResult> successfulTests,
            String outerNamePrefix) {
        // namePrefix recursively accumulates all parent testsuite names separated with dots
        String namePrefix;
        if (testSuite.name() != null) {
            namePrefix = outerNamePrefix + testSuite.name() + ".";
        }
        else {
            namePrefix = outerNamePrefix;
        }

        processTestSuiteWithNamePrefix(testSuite, maxFeedbackLength, failedTests, successfulTests, namePrefix);
    }

    /**
     * Processes a test suite, categorizing its test cases into failed or successful lists,
     * and recursively handling nested test suites.
     *
     * @param testSuite         The test suite to process.
     * @param maxFeedbackLength maximum number of characters retained per failure message
     * @param failedTests       receives failed tests
     * @param successfulTests   receives successful tests
     * @param namePrefix        The name prefix for the test cases within the suite.
     */
    private static void processTestSuiteWithNamePrefix(TestSuite testSuite, int maxFeedbackLength, Consumer<TestCaseResult> failedTests, Consumer<TestCaseResult> successfulTests,
            String namePrefix) {
        for (TestCase testCase : testSuite.testCases()) {
            if (testCase.isSkipped()) {
                continue;
            }
            Failure failure = testCase.extractFailure();
            if (failure != null) {
                // Truncate feedback message if it exceeds maximum length to avoid polluting the network or database with too long messages
                final var truncatedFeedbackMessage = StringUtils.truncate(failure.extractMessage(), maxFeedbackLength);
                failedTests.accept(new TestCaseResult(namePrefix + testCase.name(), List.of(truncatedFeedbackMessage), testCase.classname()));
            }
            else {
                successfulTests.accept(new TestCaseResult(namePrefix + testCase.name(), List.of(), testCase.classname()));
            }
        }

        for (TestSuite suite : testSuite.testSuites()) {
            processInnerTestSuite(suite, maxFeedbackLength, failedTests, successfulTests, namePrefix);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestSuites(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "testsuite") List<TestSuite> testSuites) {

        TestSuites {
            testSuites = Objects.requireNonNullElse(testSuites, List.of());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestSuite(@JacksonXmlProperty(isAttribute = true, localName = "name") String name,
            @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "testcase") List<TestCase> testCases,
            @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "testsuite") List<TestSuite> testSuites) {

        TestSuite {
            testCases = Objects.requireNonNullElse(testCases, List.of());
            testSuites = Objects.requireNonNullElse(testSuites, List.of());
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
