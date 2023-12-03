package de.tum.in.www1.artemis.service.connectors.localci.scaParser.strategy;

import static de.tum.in.www1.artemis.service.connectors.localci.scaParser.utils.XmlUtils.getChildElements;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue;

class PMDParser implements ParserStrategy {

    // XSD for PMD XML reports: https://github.com/pmd/pmd/blob/master/pmd-core/src/main/resources/report_2_0_0.xsd
    private static final String FILE_TAG = "file";

    private static final String FILE_ATT_NAME = "name";

    private static final String VIOLATION_ATT_RULE = "rule";

    private static final String VIOLATION_ATT_RULESET = "ruleset";

    private static final String VIOLATION_ATT_PRIORITY = "priority";

    private static final String VIOLATION_ATT_BEGINLINE = "beginline";

    private static final String VIOLATION_ATT_ENDLINE = "endline";

    private static final String VIOLATION_ATT_BEGINCOLUMN = "begincolumn";

    private static final String VIOLATION_ATT_ENDCOLUMN = "endcolumn";

    @Override
    public StaticCodeAnalysisReportDTO parse(Document doc) {
        StaticCodeAnalysisReportDTO report = new StaticCodeAnalysisReportDTO();
        report.setTool(StaticCodeAnalysisTool.PMD);
        List<StaticCodeAnalysisIssue> issues = new ArrayList<>();
        Element root = doc.getDocumentElement();

        // Iterate over all <file> elements
        for (Element fileElement : getChildElements(root, FILE_TAG)) {
            // Extract the file path
            String unixPath = ParserUtils.transformToUnixPath(fileElement.getAttribute(FILE_ATT_NAME));

            // Iterate over all <violation> elements
            for (Element violationElement : getChildElements(fileElement)) {
                StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue();
                issue.setFilePath(unixPath);

                issue.setRule(violationElement.getAttribute(VIOLATION_ATT_RULE));
                issue.setCategory(violationElement.getAttribute(VIOLATION_ATT_RULESET));
                issue.setPriority(violationElement.getAttribute(VIOLATION_ATT_PRIORITY));
                issue.setStartLine(ParserUtils.extractInt(violationElement, VIOLATION_ATT_BEGINLINE));
                issue.setEndLine(ParserUtils.extractInt(violationElement, VIOLATION_ATT_ENDLINE));
                issue.setStartColumn(ParserUtils.extractInt(violationElement, VIOLATION_ATT_BEGINCOLUMN));
                issue.setEndColumn(ParserUtils.extractInt(violationElement, VIOLATION_ATT_ENDCOLUMN));
                issue.setMessage(ParserUtils.stripNewLinesAndWhitespace(violationElement.getTextContent()));

                issues.add(issue);
            }
        }
        report.setIssues(issues);
        return report;
    }
}
