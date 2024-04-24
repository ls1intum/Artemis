package de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy;

import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception.UnsupportedToolException;

/**
 * Policy class for the parser strategies.
 */
public class ParserPolicy {

    /**
     * Selects the appropriate parsing strategy based on the filename of the static code analysis XML report.
     *
     * @param fileName Name of the file that contains the static code analysis report
     * @return the parser strategy
     * @throws UnsupportedToolException - If the specified tool is not supported
     */
    public ParserStrategy configure(String fileName) {
        String filePattern = extractFilePattern(fileName);
        StaticCodeAnalysisTool tool = StaticCodeAnalysisTool.getToolByFilePattern(filePattern)
                .orElseThrow(() -> new UnsupportedToolException("Tool for identifying filePattern " + filePattern + " not found"));

        return switch (tool) {
            case SPOTBUGS -> new SpotbugsParser();
            case CHECKSTYLE -> new CheckstyleParser();
            case PMD -> new PMDParser();
            case PMD_CPD -> new PMDCPDParser();
            // so far, we do not support swiftlint and gcc only SCA for Java
            default -> throw new UnsupportedToolException("Tool " + tool + " is not supported");
        };
    }

    private String extractFilePattern(String fileName) {
        // Find the index of the last '/'
        int lastIndex = fileName.lastIndexOf('/');
        // If '/' is found, extract the substring after it; otherwise, keep the original string
        return (lastIndex != -1) ? fileName.substring(lastIndex + 1) : fileName;
    }
}
