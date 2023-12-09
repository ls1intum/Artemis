package de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy;

import org.w3c.dom.Document;

import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception.UnsupportedToolException;

/**
 * Policy class for the parser strategies.
 */
public class ParserPolicy {

    /**
     * Selects the appropriate parsing strategy by looking for the identifying tag of a static code analysis tool
     *
     * @param document static code analysis xml report
     * @return the parser strategy
     * @throws UnsupportedToolException - If the specified tool is not supported
     */
    public ParserStrategy configure(Document document) {
        String filePattern = document.getDocumentURI();
        // Find the index of the last '/'
        int lastIndex = filePattern.lastIndexOf('/');
        // If '/' is found, extract the substring after it; otherwise, keep the original string
        if (lastIndex != -1) {
            filePattern = filePattern.substring(lastIndex + 1);
        }
        String finalFilePattern = filePattern;
        StaticCodeAnalysisTool tool = StaticCodeAnalysisTool.getToolByFilePattern(filePattern)
                .orElseThrow(() -> new UnsupportedToolException("Tool for identifying filePattern " + finalFilePattern + " not found"));

        return switch (tool) {
            case SPOTBUGS -> new SpotbugsParser();
            case CHECKSTYLE -> new CheckstyleParser();
            case PMD -> new PMDParser();
            case PMD_CPD -> new PMDCPDParser();
            // so far, we do not support swiftlint and gcc only SCA for Java
            default -> throw new UnsupportedToolException("Tool " + tool + " is not supported");
        };
    }

}
