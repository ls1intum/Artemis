package de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisIssue;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

record CheckstyleFile(@JacksonXmlProperty(isAttribute = true, localName = "name") String name,

        @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "error") List<CheckstyleError> errors) {
}

record CheckstyleError(@JacksonXmlProperty(isAttribute = true, localName = "line") Integer line,

        @JacksonXmlProperty(isAttribute = true, localName = "column") Integer column,

        @JacksonXmlProperty(isAttribute = true, localName = "severity") String severity,

        @JacksonXmlProperty(isAttribute = true, localName = "message") String message,

        @JacksonXmlProperty(isAttribute = true, localName = "source") String source) {
}

public class CheckstyleParser implements ParserStrategy {

    private static final String CATEGORY_DELIMITER = "checks";

    private static final String CATEGORY_MISCELLANEOUS = "miscellaneous";

    private final XmlMapper xmlMapper = new XmlMapper();

    @Override
    public StaticCodeAnalysisReportDTO parse(String xmlContent) {
        try {
            List<CheckstyleFile> files = xmlMapper.readValue(xmlContent, new com.fasterxml.jackson.core.type.TypeReference<List<CheckstyleFile>>() {
            });
            return createReportFromFiles(files);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to parse XML", e);
        }
    }

    private StaticCodeAnalysisReportDTO createReportFromFiles(List<CheckstyleFile> files) {
        List<StaticCodeAnalysisIssue> issues = new ArrayList<>();
        StaticCodeAnalysisReportDTO report = new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.CHECKSTYLE, issues);

        for (CheckstyleFile file : files) {
            for (CheckstyleError error : file.errors()) {
                StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue(file.name(), error.line(), error.line(),  // As Checkstyle does not support an end line
                        error.column(), error.column(),  // As Checkstyle does not support an end column
                        extractRule(error.source()),  // Method to extract the rule from source
                        extractCategory(error.source()),  // Method to extract the category from source
                        error.message(), error.severity(), null  // Assuming no penalty
                );
                issues.add(issue);
            }
        }
        return report;
    }

    private String extractRule(String errorSource) {
        String[] parts = errorSource.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : "Unknown";
    }

    private String extractCategory(String errorSource) {
        String[] parts = errorSource.split("\\.");
        if (parts.length > 1 && parts[parts.length - 2].equals(CATEGORY_DELIMITER)) {
            return CATEGORY_MISCELLANEOUS;
        }
        return parts.length > 1 ? parts[parts.length - 2] : "Unknown";
    }
}
