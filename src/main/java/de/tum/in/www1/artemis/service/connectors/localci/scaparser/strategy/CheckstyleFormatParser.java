package de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy;

import static de.tum.in.www1.artemis.service.connectors.localci.scaparser.utils.XmlUtils.getChildElements;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue;

/**
 * Abstract class for parsing static code analysis reports in checkstyle format.
 */
public abstract class CheckstyleFormatParser implements ParserStrategy {

    protected static final String FILE_TAG = "file";

    protected static final String FILE_ATT_NAME = "name";

    protected static final String ERROR_ATT_SOURCE = "source";

    protected static final String ERROR_ATT_SEVERITY = "severity";

    protected static final String ERROR_ATT_MESSAGE = "message";

    protected static final String ERROR_ATT_LINENUMBER = "line";

    protected static final String ERROR_ATT_COLUMN = "column";

    protected static String getProgrammingLanguage(String path) {
        String extension = path.substring(path.lastIndexOf("."));
        return extension.substring(1);
    }

    protected void extractIssues(Document doc, StaticCodeAnalysisReportDTO report) {
        List<StaticCodeAnalysisIssue> issues = new ArrayList<>();
        Element root = doc.getDocumentElement();

        // Iterate over all <file> elements
        for (Element fileElement : getChildElements(root, FILE_TAG)) {
            String unixPath = ParserUtils.transformToUnixPath(fileElement.getAttribute(FILE_ATT_NAME));

            // Iterate over all <error> elements
            for (Element errorElement : getChildElements(fileElement)) {
                StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue();
                issue.setFilePath(unixPath);

                String errorSource = errorElement.getAttribute(ERROR_ATT_SOURCE);
                extractRuleAndCategory(issue, errorSource);

                issue.setPriority(errorElement.getAttribute(ERROR_ATT_SEVERITY));
                issue.setMessage(errorElement.getAttribute(ERROR_ATT_MESSAGE));

                // Set startLine as endLine as Checkstyle does not support an end line
                int startLine = ParserUtils.extractInt(errorElement, ERROR_ATT_LINENUMBER);
                issue.setStartLine(startLine);
                issue.setEndLine(startLine);

                // Set startColumn as endColumn as Checkstyle does not support an end column
                int startColumn = ParserUtils.extractInt(errorElement, ERROR_ATT_COLUMN);
                issue.setStartColumn(startColumn);
                issue.setEndColumn(startColumn);

                issues.add(issue);
            }
        }
        report.setIssues(issues);
    }

    protected abstract void extractRuleAndCategory(StaticCodeAnalysisIssue issue, String errorSource);
}
