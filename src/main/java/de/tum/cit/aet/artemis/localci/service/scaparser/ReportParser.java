package de.tum.cit.aet.artemis.localci.service.scaparser;

import de.tum.cit.aet.artemis.localci.service.scaparser.exception.UnsupportedToolException;
import de.tum.cit.aet.artemis.localci.service.scaparser.strategy.ParserPolicy;
import de.tum.cit.aet.artemis.localci.service.scaparser.strategy.ParserStrategy;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

/**
 * Public API for parsing of static code analysis reports
 */
public class ReportParser {

    private static final ParserPolicy parserPolicy = new ParserPolicy();

    /**
     * Builds the document using the provided string and parses it to a Report object.
     *
     * @param reportContent String containing the static code analysis report
     * @param fileName      filename of the report used for configuring a parser
     * @return Report containing the static code analysis issues
     * @throws UnsupportedToolException if the static code analysis tool which created the report is not supported
     */
    public static StaticCodeAnalysisReportDTO getReport(String reportContent, String fileName) {
        ParserStrategy parserStrategy = parserPolicy.configure(fileName);
        return parserStrategy.parse(reportContent);
    }
}
