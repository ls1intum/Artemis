package de.tum.in.www1.artemis.service.connectors.localci.scaParser.strategy;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue;

public class GCCParser implements ParserStrategy {

    // General output is grouped into issues per function. At the beginning of these groups is a function part.
    private static final int FUNCTION_PART = 0;

    // After the function part, the actual issues follow. These need to be split to get individual issues.
    private static final int ISSUE_PART = 1;

    // Output per function is split into two parts, one containing the function, the other containing the issues.
    private static final int PART_COUNT = 2;

    // The message is split into two segments
    private static final int SEGMENTS_COUNT = 2;

    // Segment 0 (Header): Contains info about filename, row, column, type etc.
    private static final int HEADER_SEGMENT_POS = 0;

    // Segment 1 (Body): Body contains the trace of the error (possibly rendered with ASCII art)
    private static final int BODY_SEGMENT_POS = 1;

    // Locations in regex group
    private static final int FILE_POS = 1;

    private static final int ROW_POS = 2;

    private static final int COLUMN_POS = 3;

    private static final int TYPE_POS = 4;

    private static final int DESCRIPTION_POS = 5;

    private static final int ERROR_POS = 6;

    // Map that contains the matching category for each error
    private static final Map<String, String> categories = new HashMap<>();

    private static final String ANALYZER_PREFIX = "[-Wanalyzer";

    // Categories
    private static final String MEMORY = "Memory";

    private static final String BAD_PRACTICE = "BadPractice";

    private static final String SECURITY = "Security";

    private static final String UNDEFINED_BEHAVIOR = "UndefinedBehavior";

    // For various other results that are not part of the static analysis
    private static final String MISC = "Misc";

    // Used to parse the first line of an issue which contains all the essential data
    // e.g. "ascii_table.c:7:13: warning: variable ‘arr’ set but not used [-Wunused-but-set-variable]".
    // A colon ":" is the separator symbol used by GCC.
    private static final String HEADER_REGEX = "([^:^\\n]+):(\\d+):(\\d+):\\s(\\w+\\s*\\w*):\\s(.+)(\\[.+])";

    /*
     * ^ ^ ^ ^ ^ ^
     * | | | | | |
     * | | | | | |
     * | | | | | +- error name eg. "[-Wunused-but-set-variable]"
     * | | | | +- message text e.g. " warning: variable ‘arr’ set but not used" (note the leading whitespace)
     * | | | +- type (error|warning|note)
     * | | +- column e.g. "13"
     * | +- row e.g. "7"
     * +- filename e.g. "ascii_table.c"
     */

    // All issues belonging to a function have a preceding message that states the functions name,
    // e.g "buddy.c: In function ‘init_FL’: " For meaning of the individual regex expressions refer to HEADER_REGEX.
    private static final String FUNCTION_REGEX = "([^:^\\n]+): In function\\s[^:^\\n]+:\n";

    // A look ahead regex (see "?=") is used, since we need to keep the delimiter (HEADER_REGEX) after the
    // split, so we can extract the issue information.
    // We also need to include a new line (\n), so we can guarantee that we actually match to a new message.
    private static final String DELIM_REGEX = "\\n(?=" + FUNCTION_REGEX + ")";

    @Override
    public StaticCodeAnalysisReportDTO parse(Document doc) {
        StaticCodeAnalysisReportDTO report = new StaticCodeAnalysisReportDTO();
        report.setTool(StaticCodeAnalysisTool.GCC);
        extractIssues(doc, report);
        return report;
    }

    /**
     * Constructs issues and adds them to the static analysis report.
     *
     * @param doc    Contains the output from GCC SCA.
     * @param report The report the issues will be added to.
     */
    private void extractIssues(Document doc, StaticCodeAnalysisReportDTO report) {
        Element gccLog = doc.getDocumentElement();
        List<String> sectionsPerFunction = new ArrayList<>(Arrays.asList(gccLog.getTextContent().split(DELIM_REGEX)));
        // Do we have at least one match?
        if (sectionsPerFunction.isEmpty()) {
            return;
        }
        // Remove string before first match
        sectionsPerFunction.remove(0);

        initCategoryMapping();

        Pattern pattern = Pattern.compile(HEADER_REGEX);
        Matcher matcher = pattern.matcher("");

        List<StaticCodeAnalysisIssue> issues = new ArrayList<>();

        for (String sectionPerFunction : sectionsPerFunction) {
            String[] parts = sectionPerFunction.split("\n", PART_COUNT);
            if (parts.length < 2) {
                continue;
            }
            String function = parts[FUNCTION_PART];
            String[] issueTextPerFunction = parts[ISSUE_PART].split("\n(?=" + HEADER_REGEX + ")");

            for (String issueText : issueTextPerFunction) {
                String[] segments = issueText.split("\n", SEGMENTS_COUNT);
                String header = segments[HEADER_SEGMENT_POS];
                String body = segments[BODY_SEGMENT_POS];

                matcher.reset(header);

                if (!matcher.find()) {
                    continue;
                }

                // Construct issueText details based on regex groups
                String filename = matcher.group(FILE_POS).trim();
                Integer row = Integer.parseInt(matcher.group(ROW_POS));
                Integer col = Integer.parseInt(matcher.group(COLUMN_POS));
                String type = matcher.group(TYPE_POS);
                String description = matcher.group(DESCRIPTION_POS);
                String warningName = matcher.group(ERROR_POS);

                // Only output warnings that have a name associated with it
                if (warningName == null) {
                    continue;
                }

                // warningName is included in the description, as it will not be shown be Artemis otherwise
                String message = function + "\n" + warningName + description + "\nTrace:\n" + body;

                StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue();

                issue.setMessage(message);
                issue.setFilePath(filename);
                issue.setStartLine(row);
                issue.setEndLine(row);
                issue.setStartColumn(col);
                issue.setEndColumn(col);
                issue.setRule(warningName);
                issue.setPriority(type); // Could potentially be used for sorting at some point, not displayed by Artemis

                boolean isAnalyzerIssue = warningName.startsWith(ANALYZER_PREFIX);

                // Set correct category, only real static analysis issues are categorized, see https://gcc.gnu.org/onlinedocs/gcc-11.1.0/gcc/Static-Analyzer-Options.html
                if (isAnalyzerIssue) {
                    String category = categories.get(warningName);
                    issue.setCategory(category);
                }
                else {
                    issue.setCategory(MISC);
                }
                issues.add(issue);
            }
        }
        report.setIssues(issues);
    }

    private void initCategoryMapping() {
        // Memory warnings
        categories.put("[-Wanalyzer-free-of-non-heap]", MEMORY);
        categories.put("[-Wanalyzer-malloc-leak]", MEMORY);
        categories.put("[-Wanalyzer-file-leak]", MEMORY);
        categories.put("[-Wanalyzer-mismatching-deallocation]", MEMORY);

        // Undefined behavior
        categories.put("[-Wanalyzer-double-free]", UNDEFINED_BEHAVIOR);
        categories.put("[-Wanalyzer-null-argument]", UNDEFINED_BEHAVIOR);
        categories.put("[-Wanalyzer-use-after-free]", UNDEFINED_BEHAVIOR);
        categories.put("[-Wanalyzer-use-of-uninitialized-value]", UNDEFINED_BEHAVIOR);
        categories.put("[-Wanalyzer-write-to-const]", UNDEFINED_BEHAVIOR);
        categories.put("[-Wanalyzer-write-to-string-literal]", UNDEFINED_BEHAVIOR);
        categories.put("[-Wanalyzer-possible-null-argument]", UNDEFINED_BEHAVIOR);
        categories.put("[-Wanalyzer-possible-null-dereference]", UNDEFINED_BEHAVIOR);

        // Bad Practice
        categories.put("[-Wanalyzer-double-fclose]", BAD_PRACTICE);
        categories.put("[-Wanalyzer-too-complex]", BAD_PRACTICE);
        categories.put("[-Wanalyzer-stale-setjmp-buffer]", BAD_PRACTICE);

        // Security
        categories.put("[-Wanalyzer-exposure-through-output-file]", SECURITY);
        categories.put("[-Wanalyzer-unsafe-call-within-signal-handler]", SECURITY);
        categories.put("[-Wanalyzer-use-of-pointer-in-stale-stack-frame]", SECURITY);
        categories.put("[-Wanalyzer-tainted-array-index]", SECURITY);
    }
}
