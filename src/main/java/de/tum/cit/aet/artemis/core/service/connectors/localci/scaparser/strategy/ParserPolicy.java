package de.tum.cit.aet.artemis.core.service.connectors.localci.scaparser.strategy;

import java.nio.file.Path;

import de.tum.cit.aet.artemis.core.service.connectors.localci.scaparser.exception.UnsupportedToolException;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;

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
        return Path.of(fileName).getFileName().toString();
    }
}
