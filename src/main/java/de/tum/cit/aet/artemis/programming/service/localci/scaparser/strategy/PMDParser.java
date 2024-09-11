package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
record PMDReport(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "file") List<FileViolation> files) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record FileViolation(@JacksonXmlProperty(isAttribute = true, localName = "name") String fileName,

        @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "violation") List<Violation> violations) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Violation(@JacksonXmlProperty(isAttribute = true, localName = "rule") String rule,

        @JacksonXmlProperty(isAttribute = true, localName = "ruleset") String ruleset,

        @JacksonXmlProperty(isAttribute = true, localName = "priority") String priority,

        @JacksonXmlProperty(isAttribute = true, localName = "beginline") int beginLine,

        @JacksonXmlProperty(isAttribute = true, localName = "endline") int endLine,

        @JacksonXmlProperty(isAttribute = true, localName = "begincolumn") int beginColumn,

        @JacksonXmlProperty(isAttribute = true, localName = "endcolumn") int endColumn,

        @JacksonXmlProperty(localName = "") @JacksonXmlText String message // inner text
) {
}

class PMDParser implements ParserStrategy {

    private final XmlMapper xmlMapper = new XmlMapper();

    @Override
    public StaticCodeAnalysisReportDTO parse(String xmlContent) {
        try {
            PMDReport pmdReport = xmlMapper.readValue(xmlContent, PMDReport.class);
            return createReportFromPMDReport(pmdReport);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to parse XML", e);
        }
    }

    private StaticCodeAnalysisReportDTO createReportFromPMDReport(PMDReport pmdReport) {
        List<StaticCodeAnalysisIssue> issues = new ArrayList<>();
        StaticCodeAnalysisReportDTO report = new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.PMD, issues);

        if (pmdReport.files() == null) {
            return report;
        }

        for (FileViolation fileViolation : pmdReport.files()) {
            String unixPath = ParserStrategy.transformToUnixPath(fileViolation.fileName());

            for (Violation violation : fileViolation.violations()) {
                // The penalty is decided by the course instructor, there is no penalty information in the xml
                StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue(unixPath, violation.beginLine(), violation.endLine(), violation.beginColumn(), violation.endColumn(),
                        violation.rule(), violation.ruleset(), violation.message().strip(), violation.priority(), null);
                issues.add(issue);
            }
        }
        return report;
    }
}
