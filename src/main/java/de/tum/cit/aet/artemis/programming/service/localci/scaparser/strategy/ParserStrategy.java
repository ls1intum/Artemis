package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy;

import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

/**
 * Strategy interface for the parser strategies.
 */
public interface ParserStrategy {

    static String transformToUnixPath(String path) {
        return path.replace("\\", "/");
    }

    /**
     * Parse a static code analysis report from a serialized string into a common Java representation.
     *
     * @param reportContent The serialized content as a String
     * @return Report object containing the parsed report information
     */
    StaticCodeAnalysisReportDTO parse(String reportContent);
}
