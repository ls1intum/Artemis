package de.tum.cit.aet.artemis.localci.service.scaparser.strategy;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

/**
 * Parses the report of SwiftLint in the format of its {@code checkstyle} reporter, which every SwiftLint version supports.
 * <p>
 * An example of such a report:
 *
 * <pre>
 * {@code
 * <checkstyle version="4.3">
 *     <file name="/path/Sources/Sorting.swift">
 *         <error line="3" column="10" severity="warning" message="Colons should be next to the identifier ..." source="swiftlint.rules.colon"/>
 *     </file>
 * </checkstyle>
 * }
 * </pre>
 *
 * The identifier of the violated rule is the part of {@code source} after the last dot. SwiftLint reports no category, so the category of an issue is the
 * kind of its rule, see {@link SwiftLintRuleKinds}.
 */
public class SwiftLintParser implements ParserStrategy {

    /**
     * The root element of the report. SwiftLint writes {@code <checkstyle version="4.3"></checkstyle>} without any file element if it found no violation,
     * which the root element must be able to represent as a report without files.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SwiftLintReport(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "file") List<CheckstyleFile> files) {
    }

    private final XmlMapper xmlMapper = new XmlMapper();

    @Override
    public StaticCodeAnalysisReportDTO parse(String reportContent) {
        try {
            SwiftLintReport report = xmlMapper.readValue(reportContent, SwiftLintReport.class);
            return createReportFromFiles(report.files() == null ? List.of() : report.files());
        }
        catch (JacksonException e) {
            throw new RuntimeException("Failed to parse XML", e);
        }
    }

    private StaticCodeAnalysisReportDTO createReportFromFiles(List<CheckstyleFile> files) {
        List<StaticCodeAnalysisIssue> issues = new ArrayList<>();
        for (CheckstyleFile file : files) {
            if (file.errors() == null) {
                continue;
            }
            for (CheckstyleError error : file.errors()) {
                String rule = extractRule(error.source());
                // SwiftLint reports no end line and no end column
                issues.add(new StaticCodeAnalysisIssue(file.name(), error.line(), error.line(), error.column(), error.column(), rule, SwiftLintRuleKinds.kindOf(rule),
                        error.message(), error.severity(), null));
            }
        }
        return new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.SWIFTLINT, issues);
    }

    /**
     * Extracts the rule identifier from the source of an error. The source of SwiftLint has the form {@code swiftlint.rules.trailing_whitespace}.
     *
     * @param source the source of the error
     * @return the rule identifier, or {@code Unknown} if the error has no source
     */
    private static String extractRule(String source) {
        if (source == null || source.isBlank()) {
            return "Unknown";
        }
        return source.substring(source.lastIndexOf('.') + 1);
    }
}
