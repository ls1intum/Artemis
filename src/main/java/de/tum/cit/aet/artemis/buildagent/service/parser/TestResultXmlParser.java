package de.tum.cit.aet.artemis.buildagent.service.parser;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.testsuite.TestSuite;

public final class TestResultXmlParser extends AbstractParser {

    private static final XmlMapper mapper = new XmlMapper();

    // https://stackoverflow.com/a/4237934
    private static final String INVALID_XML_CHARS = "[^\t\r\n -\uD7FF\uE000-�\uD800\uDC00-\uDBFF\uDFFF]";

    private TestResultXmlParser() {
    }

    /**
     * Parses the test result file and extracts failed and successful tests.
     *
     * @param testResultFileString The content of the test result file as a String.
     * @param failedTests          A list of failed tests. This list will be populated by the method.
     * @param successfulTests      A list of successful tests. This list will be populated by the method.
     * @throws IOException If an I/O error occurs while reading the test result file.
     */
    public static void processTestResultFile(String testResultFileString, final List<BuildResult.LocalCITestJobDTO> failedTests,
            final List<BuildResult.LocalCITestJobDTO> successfulTests) throws IOException {
        testResultFileString = testResultFileString.replaceAll(INVALID_XML_CHARS, "");
        TestSuite testSuite = mapper.readValue(testResultFileString, TestSuite.class);

        // A toplevel <testsuites> element is parsed like a <testsuite>
        processTestSuite(testSuite, failedTests, successfulTests);
    }
}
