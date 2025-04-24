package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy;

import java.nio.file.Path;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.exception.UnsupportedToolException;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif.ClippyCategorizer;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif.DartAnalyzeCategorizer;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif.RubocopCategorizer;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif.RubocopMessageProcessor;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif.RuffCategorizer;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif.SarifParser;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif.SingleCategoryCategorizer;

/**
 * Policy class for the parser strategies.
 */
public class ParserPolicy {

    /**
     * This is used as the category of all code issues if the analysis tool does not assign a category.
     */
    public static final String GENERIC_LINT_CATEGORY = "Lint";

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
            case CHECKSTYLE -> new CheckstyleParser();
            case CLANG_TIDY -> new SarifParser(StaticCodeAnalysisTool.CLANG_TIDY, new SingleCategoryCategorizer(GENERIC_LINT_CATEGORY));
            case CLIPPY -> new SarifParser(StaticCodeAnalysisTool.CLIPPY, new ClippyCategorizer());
            case DART_ANALYZE -> new SarifParser(StaticCodeAnalysisTool.DART_ANALYZE, new DartAnalyzeCategorizer());
            case ESLINT -> new SarifParser(StaticCodeAnalysisTool.ESLINT, new SingleCategoryCategorizer(GENERIC_LINT_CATEGORY));
            case LINTR -> new SarifParser(StaticCodeAnalysisTool.LINTR, new SingleCategoryCategorizer(GENERIC_LINT_CATEGORY));
            case PMD -> new PMDParser();
            case PMD_CPD -> new PMDCPDParser();
            case RUBOCOP -> new SarifParser(StaticCodeAnalysisTool.RUBOCOP, new RubocopCategorizer(), new RubocopMessageProcessor());
            case RUFF -> new SarifParser(StaticCodeAnalysisTool.RUFF, new RuffCategorizer());
            case SPOTBUGS -> new SpotbugsParser();
            default -> throw new UnsupportedToolException("Tool " + tool + " is not supported");
        };
    }

    private String extractFilePattern(String fileName) {
        return Path.of(fileName).getFileName().toString();
    }
}
