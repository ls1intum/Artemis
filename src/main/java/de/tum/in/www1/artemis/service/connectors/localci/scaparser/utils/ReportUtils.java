package de.tum.in.www1.artemis.service.connectors.localci.scaparser.utils;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue;

public final class ReportUtils {

    private ReportUtils() {
    }

    /**
     * Creates a report which states that the specified file is too large
     * to be parsed by the parser.
     *
     * @param filename name of the parsed file
     * @return report with the issue about the filesize
     */
    public static StaticCodeAnalysisReportDTO createFileTooLargeReport(String filename) {
        StaticCodeAnalysisTool tool = StaticCodeAnalysisTool.getToolByFilePattern(filename).orElse(null);
        StaticCodeAnalysisReportDTO report = new StaticCodeAnalysisReportDTO();
        report.setTool(tool);

        StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue();
        issue.setCategory("miscellaneous");
        issue.setMessage(String.format("There are too many issues found in the %s tool.", tool));
        issue.setFilePath(filename);
        issue.setStartLine(1);
        issue.setRule("TooManyIssues");

        report.setIssues(listOf(issue));
        return report;
    }

    /**
     * Creates a report wrapping an exception; Used to inform the client about any exception during parsing
     *
     * @param filename  name of the parsed file
     * @param exception exception to wrap
     * @return a report for the file with an issue wrapping the exception
     */
    public static StaticCodeAnalysisReportDTO createErrorReport(String filename, Exception exception) {
        StaticCodeAnalysisTool tool = StaticCodeAnalysisTool.getToolByFilePattern(filename).orElse(null);
        StaticCodeAnalysisReportDTO report = new StaticCodeAnalysisReportDTO();
        report.setTool(tool);

        StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue();
        issue.setCategory("miscellaneous");
        issue.setMessage(String.format("An exception occurred during parsing the report for %s. Exception: %s", tool != null ? tool : "file " + filename, exception));
        issue.setFilePath(filename);
        issue.setStartLine(1);
        issue.setRule("ExceptionDuringParsing");

        report.setIssues(listOf(issue));
        return report;
    }

    /**
     * Helper to replace List.of, not available due to Java version
     *
     * @param issue issue to wrap into a list
     * @return list containing the issue
     */
    private static List<StaticCodeAnalysisIssue> listOf(StaticCodeAnalysisIssue issue) {
        ArrayList<StaticCodeAnalysisIssue> issues = new ArrayList<>();
        issues.add(issue);
        return issues;
    }
}
