package de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy;

import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

/**
 * Strategy interface for the parser strategies.
 */
public interface ParserStrategy {

    static String transformToUnixPath(String path) {
        return path.replace("\\", "/");
    }

    /**
     * Parse a static code analysis report from an XML string into a common Java representation.
     *
     * @param xmlContent The XML content as a String
     * @return Report object containing the parsed report information
     */
    StaticCodeAnalysisReportDTO parse(String xmlContent);
}
