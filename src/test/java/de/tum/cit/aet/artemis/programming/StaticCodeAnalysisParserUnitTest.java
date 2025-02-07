package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.ReportParser;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.exception.UnsupportedToolException;

/**
 * Tests each parser with an example file
 */
class StaticCodeAnalysisParserUnitTest {

    private static final Path EXPECTED_FOLDER_PATH = Paths.get("src", "test", "resources", "test-data", "static-code-analysis", "expected");

    private static final Path REPORTS_FOLDER_PATH = Paths.get("src", "test", "resources", "test-data", "static-code-analysis", "reports");

    private final ObjectMapper mapper = new ObjectMapper();

    private void testParserWithFile(String toolGeneratedReportFileName, String expectedJSONReportFileName) throws IOException {
        testParserWithFileNamed(toolGeneratedReportFileName, toolGeneratedReportFileName, expectedJSONReportFileName);
    }

    /**
     * Compares the parsed JSON report with the expected JSON report
     *
     * @param toolGeneratedReportFileName The name of the file contains the report as generated by the different tools
     * @param expectedJSONReportFileName  The name of the file that contains the parsed report
     */
    private void testParserWithFileNamed(String toolGeneratedReportFileName, String fileName, String expectedJSONReportFileName) throws IOException {
        Path actualReportPath = REPORTS_FOLDER_PATH.resolve(toolGeneratedReportFileName);
        File expectedJSONReportFile = EXPECTED_FOLDER_PATH.resolve(expectedJSONReportFileName).toFile();

        String actualReportContent = Files.readString(actualReportPath);
        testParserWithContent(actualReportContent, fileName, expectedJSONReportFile);
    }

    private void testParserWithContent(String actualReportContent, String actualReportFilename, File expectedJSONReportFile) throws IOException {
        StaticCodeAnalysisReportDTO actualReport = ReportParser.getReport(actualReportContent, actualReportFilename);

        StaticCodeAnalysisReportDTO expectedReport = mapper.readValue(expectedJSONReportFile, StaticCodeAnalysisReportDTO.class);

        assertThat(actualReport).isEqualTo(expectedReport);
    }

    @Test
    void testCheckstyleParser() throws IOException {
        testParserWithFile("checkstyle-result.xml", "checkstyle.txt");
    }

    @Test
    void testPMDCPDParser() throws IOException {
        testParserWithFile("cpd.xml", "pmd_cpd.txt");
    }

    @Test
    void testPMDParser() throws IOException {
        testParserWithFile("pmd.xml", "pmd.txt");
    }

    @Test
    void testSpotbugsParser() throws IOException {
        testParserWithFile("spotbugsXml.xml", "spotbugs.txt");
    }

    @Test
    void testRuffParser() throws IOException {
        testParserWithFile("ruff.sarif", "ruff.json");
    }

    @Test
    void testRubocopParser() throws IOException {
        testParserWithFile("rubocop.sarif", "rubocop.json");
    }

    @Test
    void testParseInvalidXML() {
        assertThatCode(() -> testParserWithFileNamed("invalid_xml.xml", "pmd.xml", "invalid_xml.txt")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testInvalidName() {
        assertThatCode(() -> testParserWithFile("invalid_name.xml", "invalid_name.txt")).isInstanceOf(UnsupportedToolException.class);
    }
}
