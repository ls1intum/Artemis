package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

/**
 * Parser for GCC compiler warnings wrapped in XML by the Converter.py script.
 * <p>
 * The expected XML format is:
 *
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8" ?>
 * <root>
 * file.c:10:5: warning: unused variable 'x' [-Wunused-variable]
 * file.c:20:1: error: implicit declaration of function 'foo' [-Wimplicit-function-declaration]
 * </root>
 * }</pre>
 */
public class GccParser implements ParserStrategy {

    // Matches: file.c:line:column: severity: message [-Wflag]
    // Groups: 1=file, 2=line, 3=column, 4=severity, 5=message, 6=flag (optional)
    private static final Pattern GCC_WARNING_PATTERN = Pattern.compile("^(.+?):(\\d+):(\\d+):\\s*(warning|error|note):\\s*(.+?)(?:\\s+\\[(-W[^\\]]+)\\])?$");

    private final XmlMapper xmlMapper = new XmlMapper();

    @Override
    public StaticCodeAnalysisReportDTO parse(String reportContent) {
        String textContent = extractTextContent(reportContent);
        List<StaticCodeAnalysisIssue> issues = parseGccWarnings(textContent);
        return new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.GCC, issues);
    }

    private String extractTextContent(String reportContent) {
        try {
            GccRoot root = xmlMapper.readValue(reportContent, GccRoot.class);
            return root.content != null ? root.content : "";
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to parse GCC XML report", e);
        }
    }

    private List<StaticCodeAnalysisIssue> parseGccWarnings(String textContent) {
        List<StaticCodeAnalysisIssue> issues = new ArrayList<>();
        String[] lines = textContent.split("\n");

        for (String line : lines) {
            Matcher matcher = GCC_WARNING_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                String filePath = ParserStrategy.transformToUnixPath(matcher.group(1));
                int lineNumber = Integer.parseInt(matcher.group(2));
                int column = Integer.parseInt(matcher.group(3));
                String severity = matcher.group(4);
                String message = matcher.group(5).trim();
                String flag = matcher.group(6); // may be null

                String rule = flag != null ? flag : "compiler-" + severity;
                String category = categorizeWarning(flag);

                issues.add(new StaticCodeAnalysisIssue(filePath, lineNumber, lineNumber, column, column, rule, category, message, severity, null));
            }
        }
        return issues;
    }

    private String categorizeWarning(String flag) {
        if (flag == null) {
            return "Misc";
        }
        if (flag.startsWith("-Wunused")) {
            return "BadPractice";
        }
        if (flag.contains("implicit") || flag.contains("declaration")) {
            return "UndefinedBehavior";
        }
        if (flag.contains("format")) {
            return "Security";
        }
        if (flag.contains("uninitialized")) {
            return "UndefinedBehavior";
        }
        return "Misc";
    }

    private static class GccRoot {

        @JacksonXmlText
        String content;
    }
}
