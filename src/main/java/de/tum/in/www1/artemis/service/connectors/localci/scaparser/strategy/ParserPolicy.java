package de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy;

import org.w3c.dom.Document;

import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception.UnsupportedToolException;

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
        StaticCodeAnalysisTool tool = StaticCodeAnalysisTool.getToolByFilePattern(filePattern)
                .orElseThrow(() -> new UnsupportedToolException("Tool for identifying filePattern " + filePattern + " not found"));

        return switch (tool) {
            case SPOTBUGS -> new SpotbugsParser();
            case CHECKSTYLE -> new CheckstyleParser();
            case SWIFTLINT -> new SwiftLintParser();
            case PMD -> new PMDParser();
            case PMD_CPD -> new PMDCPDParser();
            case GCC -> new GCCParser();
        };
    }

}
