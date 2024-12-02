package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
record CheckstyleFile(@JacksonXmlProperty(isAttribute = true, localName = "name") String name,

        @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "error") List<CheckstyleError> errors) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
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
    public StaticCodeAnalysisReportDTO parse(String reportContent) {
        try {
            List<CheckstyleFile> files = xmlMapper.readValue(reportContent, new com.fasterxml.jackson.core.type.TypeReference<List<CheckstyleFile>>() {
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
            if (file.errors() == null) {
                continue;
            }
            for (CheckstyleError error : file.errors()) {
                StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue(file.name(), error.line(), error.line(),  // As Checkstyle does not support an end line
                        error.column(), error.column(),  // As Checkstyle does not support an end column
                        extractRule(error.source()),  // Method to extract the rule from source
                        extractCategory(error.source()),  // Method to extract the category from source
                        error.message(), error.severity(), null // The penalty is decided by the course instructor, there is no penalty information in the xml
                );
                issues.add(issue);
            }
        }
        return report;
    }

    /**
     * The source string is full qualified name of the checkstyle rule.
     * E.g. com.puppycrawl.tools.checkstyle.checks.imports.UnusedImportsCheck
     * This method extracts the rule name (class name at the end) from the source string.
     *
     * @param errorSource The source string of the checkstyle error
     * @return the rule name
     */
    private String extractRule(String errorSource) {
        String[] parts = errorSource.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : "Unknown";
    }

    /**
     * The source string is full qualified name of the checkstyle rule.
     * E.g. com.puppycrawl.tools.checkstyle.checks.imports.UnusedImportsCheck
     * This method extracts the category name (package following after 'checks') from the source string.
     * If the class is located directly in the checks package, the category is 'miscellaneous'.
     *
     * @param errorSource The source string of the checkstyle error
     * @return the category name
     */
    private String extractCategory(String errorSource) {
        String[] parts = errorSource.split("\\.");
        if (parts.length < 2) {
            return "Unknown";
        }
        if (parts[parts.length - 2].equals(CATEGORY_DELIMITER)) {
            return CATEGORY_MISCELLANEOUS;
        }
        return parts[parts.length - 2];
    }
}
