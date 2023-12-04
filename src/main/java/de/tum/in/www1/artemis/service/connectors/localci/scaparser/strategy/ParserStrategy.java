package de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy;

import org.w3c.dom.Document;

import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

/**
 * Strategy interface for the parser strategies.
 */
public interface ParserStrategy {

    /**
     * Parse a static code analysis report into a common Java representation.
     *
     * @param doc XML DOM Document
     * @return Report object containing the parsed report information
     */
    StaticCodeAnalysisReportDTO parse(Document doc);
}
