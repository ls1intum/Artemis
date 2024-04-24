package de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception.UnsupportedToolException;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

/**
 * Context class for the parser strategies.
 */
public final class ParserContext {

    private ParserContext() {
    }

    /**
     * Builds the document using the provided file and parses it to a Report object using ObjectMapper.
     *
     * @param file File referencing the static code analysis report
     * @return Report containing the static code analysis issues
     * @throws UnsupportedToolException if the static code analysis tool which created the report is not supported
     * @throws IOException              if the file could not be read
     */
    public static StaticCodeAnalysisReportDTO getReport(File file) throws IOException {
        String xmlContent = Files.readString(file.toPath());
        return parseXmlContent(xmlContent, file);
    }

    private static StaticCodeAnalysisReportDTO parseXmlContent(String xmlContent, File file) {
        ParserPolicy parserPolicy = new ParserPolicy();
        ParserStrategy parserStrategy = parserPolicy.configure(file.getName());
        return parserStrategy.parse(xmlContent);  // Pass the whole XML content directly
    }
}
