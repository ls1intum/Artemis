package de.tum.cit.aet.artemis.buildagent.service;

import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;

import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;

public class TestResultXmlParser {

    // https://stackoverflow.com/a/4237934
    private static final String INVALID_XML_CHARS = "[^\t\r\n -\uD7FF\uE000-�\uD800\uDC00-\uDBFF\uDFFF]";

    /**
     * Parses the test result file and extracts failed and successful tests.
     * The name of nested testsuite elements are prepended with dots to the testcase name.
     * A singular top-level testsuite is not included in the name.
     * If multiple top-level testsuite elements are present, their names will be included.
     *
     * @param testResultFileString The content of the test result file as a String.
     * @param failedTests          A list of failed tests. This list will be populated by the method.
     * @param successfulTests      A list of successful tests. This list will be populated by the method.
     * @throws JAXBException If an I/O error occurs while reading the test result file.
     */
    public static void processTestResultFile(String testResultFileString, List<BuildResult.LocalCITestJobDTO> failedTests, List<BuildResult.LocalCITestJobDTO> successfulTests)
            throws JAXBException {
        testResultFileString = testResultFileString.replaceAll(INVALID_XML_CHARS, "");

        JAXBContext context = JAXBContext.newInstance(TestSuites.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        StringReader reader = new StringReader(testResultFileString);
        Object unmarshalled = unmarshaller.unmarshal(reader);

        if (unmarshalled instanceof TestSuites testSuites) {
            if (testSuites.getTestSuites().size() == 1) {
                TestSuite suite = testSuites.getTestSuites().getFirst();
                processTopLevelTestSuite(failedTests, successfulTests, suite);
            }
            else {
                for (TestSuite suite : testSuites.getTestSuites()) {
                    processInnerTestSuite(suite, failedTests, successfulTests, "");
                }
            }
        }
        else if (unmarshalled instanceof TestSuite testSuite) {
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

    @XmlRootElement(name = "testsuites")
    private static final class TestSuites {

        @XmlElement(name = "testsuite")
        private List<TestSuite> testSuites;

        public List<TestSuite> getTestSuites() {
            return testSuites;
        }
    }

    @XmlRootElement(name = "testsuite")
    private static final class TestSuite {

        @XmlAttribute
        private String name;

        @XmlElement(name = "testcase")
        private List<TestCase> testCases;

        @XmlElement(name = "testsuite")
        private List<TestSuite> testSuites;

        public String name() {
            return name;
        }

        public List<TestCase> testCases() {
            return Objects.requireNonNullElseGet(testCases, Collections::emptyList);
        }

        public List<TestSuite> testSuites() {
            return Objects.requireNonNullElseGet(testSuites, Collections::emptyList);
        }

    }

    private static final class TestCase {

        @XmlAttribute
        private String name;

        @XmlElement
        private Failure failure;

        @XmlElement
        private Failure error;

        @XmlElement
        private Skip skipped;

        private boolean isSkipped() {
            return skipped != null;
        }

        private Failure extractFailure() {
            return failure != null ? failure : error;
        }

        public String name() {
            return name;
        }

        public Failure failure() {
            return failure;
        }

        public Failure error() {
            return error;
        }

        public Skip skipped() {
            return skipped;
        }

    }

    // Intentionally empty class to represent the skipped tag (<skipped/>)
    private static class Skip {
    }

    private static final class Failure {

        @XmlAttribute
        private String message;

        @XmlValue
        private String detailedMessage;

        private String extractMessage() {
            if (message != null) {
                return message;
            }
            else if (detailedMessage != null) {
                return detailedMessage;
            }
            return "";
        }
    }
}
