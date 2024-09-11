package de.tum.cit.aet.artemis.core.service.connectors.localci.scaparser.strategy;

import de.tum.cit.aet.artemis.service.dto.StaticCodeAnalysisReportDTO;

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
