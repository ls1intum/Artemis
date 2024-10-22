package de.tum.cit.aet.artemis.programming.service.localci.scaparser;

import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.exception.UnsupportedToolException;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.ParserPolicy;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.ParserStrategy;

/**
 * Public API for parsing of static code analysis reports
 */
public class ReportParser {

    /**
     * Builds the document using the provided string and parses it to a Report object.
     *
     * @param reportContent String containing the static code analysis report
     * @param fileName      filename of the report used for configuring a parser
     * @return Report containing the static code analysis issues
     * @throws UnsupportedToolException if the static code analysis tool which created the report is not supported
     */
    public static StaticCodeAnalysisReportDTO getReport(String reportContent, String fileName) {
        ParserPolicy parserPolicy = new ParserPolicy();
        ParserStrategy parserStrategy = parserPolicy.configure(fileName);
        return parserStrategy.parse(reportContent);
    }
}
