package de.tum.in.www1.artemis.service.connectors.localci.scaParser.strategy;

import org.w3c.dom.Document;

import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue;

class SwiftLintParser extends CheckstyleFormatParser {

    @Override
    public StaticCodeAnalysisReportDTO parse(Document doc) {
        StaticCodeAnalysisReportDTO report = new StaticCodeAnalysisReportDTO();
        report.setTool(StaticCodeAnalysisTool.SWIFTLINT);
        extractIssues(doc, report);
        return report;
    }

    /**
     * Extracts and sets the rule and the category given the check's package name.
     *
     * @param issue       issue under construction
     * @param errorSource package like swiftlint.rules.trailing_semicolon
     */
    @Override
    protected void extractRuleAndCategory(StaticCodeAnalysisIssue issue, String errorSource) {
        String[] errorSourceSegments = errorSource.split("\\.");
        int noOfSegments = errorSourceSegments.length;
        String rule = errorSourceSegments[noOfSegments - 1]; // e.g. trailing_semicolon
        String category = "swiftLint";

        issue.setRule(rule);
        issue.setCategory(category);
    }
}
