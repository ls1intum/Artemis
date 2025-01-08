package de.tum.cit.aet.artemis.buildagent.service.parser;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.testsuite.Failure;
import de.tum.cit.aet.artemis.buildagent.dto.testsuite.TestCase;
import de.tum.cit.aet.artemis.buildagent.dto.testsuite.TestSuite;
import de.tum.cit.aet.artemis.buildagent.dto.testsuite.TestSuites;

public class TestResultXmlParser {

    private static final XmlMapper mapper = new XmlMapper();

    // https://stackoverflow.com/a/4237934
    private static final String INVALID_XML_CHARS = "[^\t\r\n -\uD7FF\uE000-�\uD800\uDC00-\uDBFF\uDFFF]";

    // The root element can be preceded by processing instructions (<? ... ?>), comments (<!-- ... -->),
    // a doctype declaration (<!DOCTYPE ... >) and whitespace.
    // Comments cannot contain the string "--".
    private static final Pattern XML_ROOT_TAG_IS_TESTSUITES = Pattern.compile("^(<\\?([^?]|\\?[^>])*\\?>|<!--(-?[^-])*-->|<!DOCTYPE[^>]*>|\\s)*<testsuites(\\s|/?>)",
            Pattern.DOTALL);

    private TestResultXmlParser() {

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
                failedTests.add(new LocalCITestJobDTO(namePrefix + testCase.name(), List.of(failure.extractMessage())));
            }
            else {
                successfulTests.add(new LocalCITestJobDTO(namePrefix + testCase.name(), List.of()));
            }
        }

        for (TestSuite suite : testSuite.testSuites()) {
            processInnerTestSuite(suite, failedTests, successfulTests, namePrefix);
        }
    }

}
