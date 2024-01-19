package de.tum.in.www1.artemis.staticcodeanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import de.tum.in.www1.artemis.service.connectors.localci.scaparser.ReportParser;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception.ParserException;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.utils.XmlUtils;

/**
 * Tests each parser with an example file
 */
class StaticCodeAnalysisIntegrationTest {

    private static final Path EXPECTED_FOLDER_PATH = Paths.get("src", "test", "resources", "test-data", "static-code-analysis", "expected");

    private static final Path REPORTS_FOLDER_PATH = Paths.get("src", "test", "resources", "test-data", "static-code-analysis", "reports");

    /**
     * Compares the parsed JSON report with the expected JSON report
     *
     * @param toolGeneratedReportFileName The name of the file contains the report as generated by the different tools
     * @param expectedJSONReportFileName  The name of the file that contains the parsed report
     * @throws ParserException If an exception occurs that is not already handled by the parser itself, e.g. caused by the json-parsing
     */
    private void testParserWithFile(String toolGeneratedReportFileName, String expectedJSONReportFileName) throws ParserException, IOException {
        File toolReport = REPORTS_FOLDER_PATH.resolve(toolGeneratedReportFileName).toFile();

        ReportParser parser = new ReportParser();
        String actual = parser.transformToJSONReport(toolReport);

        try (BufferedReader reader = Files.newBufferedReader(EXPECTED_FOLDER_PATH.resolve(expectedJSONReportFileName))) {
            String expected = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            assertThat(actual).isEqualTo(expected);
        }
    }

    private void testParserWithNullValue() throws ParserException {
        ReportParser parser = new ReportParser();
        parser.transformToJSONReport(null);
    }

    /**
     * Compares the parsed JSON report with the expected JSON report
     *
     * @param fileName The name of the file contains the report as generated by the different tools
     * @param expected The expected output
     * @throws ParserException If an exception occurs that is not already handled by the parser itself, e.g. caused by the json-parsing
     */
    private void testParserWithString(String fileName, String expected) throws ParserException {
        File toolReport = REPORTS_FOLDER_PATH.resolve(fileName).toFile();

        ReportParser parser = new ReportParser();
        String actual = parser.transformToJSONReport(toolReport);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testCheckstyleParser() throws IOException {
        try {
            testParserWithFile("checkstyle-result.xml", "checkstyle.txt");
        }
        catch (ParserException e) {
            fail("Checkstyle parser failed with exception: " + e.getMessage());
        }
    }

    @Test
    void testPMDCPDParser() throws IOException {
        try {
            testParserWithFile("cpd.xml", "pmd_cpd.txt");
        }
        catch (ParserException e) {
            fail("PMD-CPD parser failed with exception: " + e.getMessage());
        }
    }

    @Test
    void testPMDParser() throws IOException {
        try {
            testParserWithFile("pmd.xml", "pmd.txt");
        }
        catch (ParserException e) {
            fail("PMD parser failed with exception: " + e.getMessage());
        }
    }

    @Test
    void testSpotbugsParser() throws IOException {
        try {
            testParserWithFile("spotbugsXml.xml", "spotbugs.txt");
        }
        catch (ParserException e) {
            fail("Spotbugs parser failed with exception: " + e.getMessage());
        }
    }

    @Test
    void testParseInvalidFilename() throws IOException {
        try {
            testParserWithFile("cpd_invalid.txt", "invalid_filename.txt");
            fail("Expected ParserException");
        }
        catch (ParserException ignored) {
        }
    }

    @Test
    void testParseInvalidXML() throws IOException {
        SAXParseException saxParseException = catchThrowableOfType(
                () -> XmlUtils.createDocumentBuilder().parse(new File(REPORTS_FOLDER_PATH.resolve("invalid_xml.xml").toString())), SAXParseException.class);

        assertThat(saxParseException).isNotNull();

        try (BufferedReader reader = Files.newBufferedReader(EXPECTED_FOLDER_PATH.resolve("invalid_xml.txt"))) {
            String expectedInvalidXML = reader.readLine();
            // JSON transform escapes quotes, so we need to escape them too
            try {
                testParserWithString("invalid_xml.xml", String.format(expectedInvalidXML, saxParseException.toString().replaceAll("\"", "\\\\\"")));
            }
            catch (ParserException e) {
                fail("Parser failed with exception: " + e.getMessage());
            }
        }
    }

    @Test
    void testInvalidName() throws IOException {
        try {
            testParserWithFile("invalid_name.xml", "invalid_name.txt");
        }
        catch (ParserException e) {
            fail("Parser failed with exception: " + e.getMessage());
        }
    }

    @Test
    void testThrowsParserException() {
        assertThatExceptionOfType(ParserException.class).isThrownBy(this::testParserWithNullValue);
    }
}
