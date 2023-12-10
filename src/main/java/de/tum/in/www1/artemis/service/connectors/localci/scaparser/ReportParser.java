package de.tum.in.www1.artemis.service.connectors.localci.scaparser;

import static de.tum.in.www1.artemis.service.connectors.localci.scaparser.utils.ReportUtils.createErrorReport;
import static de.tum.in.www1.artemis.service.connectors.localci.scaparser.utils.ReportUtils.createFileTooLargeReport;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception.ParserException;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy.ParserContext;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.utils.FileUtils;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

/**
 * Public API for parsing of static code analysis reports
 */
public class ReportParser {

    // Reports that are bigger then the threshold will not be parsed
    // and an issue will be generated. The unit is in megabytes.
    private static final int STATIC_CODE_ANALYSIS_REPORT_FILESIZE_LIMIT_IN_MB = 1;

    /**
     * Transform a given static code analysis report into a JSON representation.
     * All supported tools share the same JSON format.
     *
     * @param file Reference to the static code analysis report
     * @return Static code analysis report represented as a JSON String
     * @throws ParserException - If an exception occurs that is not already handled by the parser itself, e.g. caused by the json-parsing
     */
    public String transformToJSONReport(File file) throws ParserException {
        try {
            StaticCodeAnalysisReportDTO report = transformToReport(file);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(report);
        }
        catch (Exception e) {
            System.err.println("Error while parsing static code analysis report: " + e.getMessage());
            throw new ParserException(e.getMessage(), e);
        }
    }

    /**
     * Transform a given static code analysis report given as a file into a plain Java object.
     *
     * @param file Reference to the static code analysis report
     * @return Static code analysis report represented as a plain Java object
     */
    public StaticCodeAnalysisReportDTO transformToReport(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File must not be null");
        }

        // The static code analysis parser only supports xml files.
        if (!FileUtils.getExtension(file).equals("xml")) {
            throw new IllegalArgumentException("File must be xml format");
        }
        try {
            // Reject any file larger than the given threshold
            if (FileUtils.isFilesizeGreaterThan(file, STATIC_CODE_ANALYSIS_REPORT_FILESIZE_LIMIT_IN_MB)) {
                return createFileTooLargeReport(file.getName());
            }

            ParserContext context = new ParserContext();
            return context.getReport(file);
        }
        catch (Exception e) {
            return createErrorReport(file.getName(), e);
        }
    }
}
