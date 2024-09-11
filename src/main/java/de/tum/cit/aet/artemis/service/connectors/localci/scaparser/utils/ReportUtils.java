package de.tum.cit.aet.artemis.service.connectors.localci.scaparser.utils;

import java.util.List;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.service.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.service.dto.StaticCodeAnalysisReportDTO;

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
        List<StaticCodeAnalysisIssue> issues = List.of(new StaticCodeAnalysisIssue(filename, 1, 1, 0, 0, // Assuming there are no column details
                "TooManyIssues", "miscellaneous", String.format("There are too many issues found in the %s tool.", tool), null, // No priority for this issue
                null  // No penalty for this issue
        ));

        return new StaticCodeAnalysisReportDTO(tool, issues);
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
        List<StaticCodeAnalysisIssue> issues = List.of(new StaticCodeAnalysisIssue(filename, 1, 1, 0, 0, // Assuming there are no column details
                "ExceptionDuringParsing", "miscellaneous",
                String.format("An exception occurred during parsing the report for %s. Exception: %s", tool != null ? tool : "file " + filename, exception),
                // No priority and no penalty for this issue
                null, null));

        return new StaticCodeAnalysisReportDTO(tool, issues);
    }
}
