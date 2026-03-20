package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.MarkdownRelativeToAbsolutePathAttributeProvider;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.AssetRequirementsDTO;
import de.tum.cit.aet.artemis.exercise.dto.DiagramRenderInfoDTO;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.TaskRenderInfoDTO;
import de.tum.cit.aet.artemis.programming.service.PlantUmlService;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProblemStatementRenderingService {

    private static final Logger log = LoggerFactory.getLogger(ProblemStatementRenderingService.class);

    private static final String COMMONMARK_VERSION = "1.0.0";

    private static final String GRAALJS_VERSION = "2.0.0";

    private static final List<String> GRAALJS_REQUIRED_CSS = List.of("katex", "hljs", "github-alerts");

    // @formatter:off
    private static final String EMBEDDED_CSS = """
            <style>
            .artemis-problem-statement svg{max-width:100%;height:auto}
            .artemis-task{cursor:pointer;font-weight:600}
            .artemis-icon-success{color:#28a745}
            .artemis-icon-fail{color:#dc3545}
            .artemis-task-stats{font-weight:400;font-size:.9em;margin-left:4px;text-decoration:underline}
            .artemis-task-success .artemis-task-stats{color:#28a745}
            .artemis-task-fail .artemis-task-stats{color:#dc3545}
            .artemis-task-not-executed .artemis-task-stats{color:#6c757d}
            .markdown-alert{border-left:4px solid #17a2b8;padding:8px 16px;margin:16px 0;border-radius:0 4px 4px 0}
            .markdown-alert-title{font-weight:600}
            table.table{border-collapse:collapse;width:100%}
            table.table th,table.table td{border:1px solid #dee2e6;padding:8px}
            </style>
            """;
    // @formatter:on

    /**
     * Holds feedback detail for a single test case.
     */
    public record TestFeedbackDetail(Long testId, String testName, boolean passed, @Nullable String message) {
    }

    private static final Pattern PLANTUML_PATTERN = Pattern.compile("@startuml([^@]*)@enduml");

    private static final Pattern TASK_PATTERN = Pattern
            .compile("\\[task]\\[(?<name>[^\\[\\]]+)]\\((?<tests>(?:[^(),]+(?:\\([^()]*\\)[^(),]*)?(?:,[^(),]+(?:\\([^()]*\\)[^(),]*)?)*)?)\\)");

    private static final Pattern TESTID_PATTERN = Pattern.compile("<testid>(\\d+)</testid>");

    // Artemis-specific markup in PlantUML: <color:testsColor(<testid>N</testid>)>text</color>
    private static final Pattern TESTS_COLOR_TAG_PATTERN = Pattern.compile("<color:testsColor\\(<testid>(\\d+)</testid>\\)>(.*?)</color>");

    // Artemis-specific markup in PlantUML: #testsColor(<testid>N</testid>) on arrows
    private static final Pattern TESTS_COLOR_ARROW_PATTERN = Pattern.compile("#testsColor\\(<testid>(\\d+)</testid>\\)");

    private static final Pattern KATEX_INLINE_PATTERN = Pattern.compile("(?<!\\\\)\\$(?!\\$)(.+?)(?<!\\\\)\\$");

    private static final Pattern KATEX_BLOCK_PATTERN = Pattern.compile("\\$\\$[\\s\\S]+?\\$\\$");

    private static final Pattern KATEX_BEGIN_PATTERN = Pattern.compile("\\\\begin\\{");

    private static final Pattern FENCED_CODE_PATTERN = Pattern.compile("```(\\w+)");

    private static final String SVG_PLACEHOLDER_PREFIX = "<span class=\"artemis-svg-placeholder\" data-svg-index=\"";

    private static final String SVG_PLACEHOLDER_SUFFIX = "\"></span>";

    private static final Safelist HTML_SAFELIST = buildSafelist();

    private final PlantUmlService plantUmlService;

    private final MarkdownItRenderingService markdownItRenderingService;

    private final String serverUrl;

    private final boolean useMarkdownIt;

    public ProblemStatementRenderingService(PlantUmlService plantUmlService, MarkdownItRenderingService markdownItRenderingService, @Value("${server.url}") String serverUrl,
            @Value("${artemis.rendering.use-markdown-it:true}") boolean useMarkdownIt) {
        this.plantUmlService = plantUmlService;
        this.markdownItRenderingService = markdownItRenderingService;
        this.serverUrl = serverUrl;
        this.useMarkdownIt = useMarkdownIt;
    }

    /**
     * Renders a problem statement for the given exercise into pre-rendered HTML with structured metadata.
     *
     * @param exercise      the exercise whose problem statement should be rendered
     * @param selfContained if true, PlantUML diagrams are inlined as SVG; otherwise URLs are provided
     * @param testResults   map of testCaseId → passed (true/false), or null if no results available
     * @return the rendered problem statement DTO
     */
    public RenderedProblemStatementDTO render(Exercise exercise, boolean selfContained, @Nullable Map<Long, TestFeedbackDetail> testResults) {
        String problemStatement = exercise.getProblemStatement();
        boolean useGraalJs = useMarkdownIt && markdownItRenderingService.isAvailable();
        String diagramMode = selfContained ? "inline" : "url";

        if (problemStatement == null || problemStatement.isBlank()) {
            String rendererVersion = useGraalJs ? GRAALJS_VERSION : COMMONMARK_VERSION;
            return new RenderedProblemStatementDTO("", computeHash(""), rendererVersion, new AssetRequirementsDTO("absent", "absent", diagramMode, List.of()), List.of(),
                    List.of());
        }

        String processedMarkdown = problemStatement;

        // Step 1: Extract and replace PlantUML blocks
        List<DiagramRenderInfoDTO> diagrams = new ArrayList<>();
        List<String> inlineSvgs = new ArrayList<>();
        processedMarkdown = extractPlantUmlDiagrams(processedMarkdown, exercise.getId(), selfContained, diagrams, inlineSvgs, testResults);

        // Step 2: Extract and replace task markers
        List<TaskRenderInfoDTO> tasks = new ArrayList<>();
        processedMarkdown = extractTasks(processedMarkdown, tasks, testResults);

        // Step 3: Render markdown to HTML and determine asset requirements
        String html;
        String rendererVersion;
        AssetRequirementsDTO assets;

        if (useGraalJs) {
            html = renderWithGraalJs(processedMarkdown);
            rendererVersion = GRAALJS_VERSION;
            assets = new AssetRequirementsDTO("absent", "absent", diagramMode, GRAALJS_REQUIRED_CSS);
        }
        else {
            html = renderWithCommonMark(processedMarkdown);
            rendererVersion = COMMONMARK_VERSION;
            assets = detectAssetRequirements(processedMarkdown, diagramMode);
        }

        // Step 4: Inject PlantUML SVGs after sanitization.
        // SECURITY ASSUMPTION: We treat PlantUML-generated SVGs as trusted output. PlantUML converts
        // its own DSL into SVG shapes/text, and we assume it does not propagate user-controlled content
        // into executable SVG contexts (e.g., script, onload). This is the same trust model as the
        // existing /api/programming/plantuml/svg endpoint which serves PlantUML SVGs without HTML
        // sanitization. The SVGs are NOT passed through jsoup because jsoup cannot handle SVG
        // namespaces/attributes without being overly permissive. If this assumption is ever violated,
        // SVG sanitization must be added here.
        if (selfContained) {
            for (int i = 0; i < inlineSvgs.size(); i++) {
                String placeholder = SVG_PLACEHOLDER_PREFIX + i + SVG_PLACEHOLDER_SUFFIX;
                html = html.replace(placeholder, inlineSvgs.get(i));
            }
        }

        // Step 5: Wrap in container div
        html = "<div class=\"artemis-problem-statement\">" + html + "</div>";

        // Step 6: Strip any remaining testid tags
        html = html.replace("<testid>", "").replace("</testid>", "");

        // Step 7: Prepend embedded CSS (after sanitization, since jsoup strips <style> tags)
        html = EMBEDDED_CSS + html;

        // Step 8: Compute content hash and build DTO
        String contentHash = computeHash(html);

        return new RenderedProblemStatementDTO(html, contentHash, rendererVersion, assets, tasks, diagrams);
    }

    private String renderWithGraalJs(String markdown) {
        String html = markdownItRenderingService.render(markdown);
        return rewriteUrlsAndSanitize(html);
    }

    private String renderWithCommonMark(String markdown) {
        var extensions = List.of(TablesExtension.create(), StrikethroughExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).attributeProviderFactory(ctx -> new MarkdownRelativeToAbsolutePathAttributeProvider(serverUrl))
                .build();
        String html = renderer.render(parser.parse(markdown));
        return sanitizeHtml(html);
    }

    private static AssetRequirementsDTO detectAssetRequirements(String markdown, String diagramMode) {
        boolean needsKatex = KATEX_INLINE_PATTERN.matcher(markdown).find() || KATEX_BLOCK_PATTERN.matcher(markdown).find() || KATEX_BEGIN_PATTERN.matcher(markdown).find();
        boolean needsHighlighting = FENCED_CODE_PATTERN.matcher(markdown).find();
        return new AssetRequirementsDTO(needsKatex ? "required" : "absent", needsHighlighting ? "required" : "absent", diagramMode, List.of());
    }

    private String extractPlantUmlDiagrams(String markdown, long exerciseId, boolean selfContained, List<DiagramRenderInfoDTO> diagrams, List<String> inlineSvgs,
            @Nullable Map<Long, TestFeedbackDetail> testResults) {
        Matcher matcher = PLANTUML_PATTERN.matcher(markdown);
        StringBuilder sb = new StringBuilder();
        int diagramIndex = 0;

        while (matcher.find()) {
            String fullMatch = matcher.group(0);
            String diagramId = "uml-" + diagramIndex;
            String sourceHash = computeHash(fullMatch);

            // Extract test references from testsColor() before cleaning
            List<Long> testIds = extractTestsColorIds(fullMatch);

            // Resolve or clean Artemis-specific testsColor()/testid markup for PlantUML rendering
            String cleanedSource = resolvePlantUmlTestColors(fullMatch, testResults);

            // Build SVG URL with cleaned source (request dark theme for proper background)
            String svgUrl = serverUrl + "/api/programming/plantuml/svg?plantuml=" + java.net.URLEncoder.encode(cleanedSource, StandardCharsets.UTF_8) + "&useDarkTheme=true";

            String inlineSvg = null;
            if (selfContained) {
                try {
                    inlineSvg = plantUmlService.generateSvg(cleanedSource, true);
                }
                catch (IOException e) {
                    log.error("Failed to generate inline SVG for diagram {} in exercise {}", diagramId, exerciseId, e);
                    inlineSvg = "<div class=\"alert alert-danger\">Failed to render diagram</div>";
                }
                inlineSvgs.add(inlineSvg);
            }

            String replacement;
            if (selfContained) {
                // Use a placeholder that won't be stripped by jsoup sanitization
                replacement = "<div class=\"artemis-diagram\" data-diagram-id=\"" + diagramId + "\" data-svg-url=\"" + svgUrl + "\">" + SVG_PLACEHOLDER_PREFIX + diagramIndex
                        + SVG_PLACEHOLDER_SUFFIX + "</div>";
            }
            else {
                replacement = "<div class=\"artemis-diagram\" data-diagram-id=\"" + diagramId + "\" data-svg-url=\"" + svgUrl + "\"></div>";
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            diagrams.add(new DiagramRenderInfoDTO(diagramId, selfContained ? "inline" : "url", svgUrl, inlineSvg, sourceHash, testIds));
            diagramIndex++;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Extracts numeric test IDs from testsColor() calls in PlantUML source.
     *
     * @param plantUmlSource the PlantUML diagram source
     * @return list of test IDs referenced in testsColor() calls
     */
    private List<Long> extractTestsColorIds(String plantUmlSource) {
        List<Long> testIds = new ArrayList<>();
        Matcher matcher = TESTID_PATTERN.matcher(plantUmlSource);
        while (matcher.find()) {
            testIds.add(Long.parseLong(matcher.group(1)));
        }
        return testIds;
    }

    /**
     * Resolves Artemis-specific testsColor() markup in PlantUML source to actual colors.
     * If test results are available, uses green/red based on pass/fail status.
     * If no results, uses grey (default/uncolored).
     */
    private static String resolvePlantUmlTestColors(String source, @Nullable Map<Long, TestFeedbackDetail> testResults) {
        // <color:testsColor(<testid>N</testid>)>text</color> → <color:COLOR>text</color>
        String resolved = TESTS_COLOR_TAG_PATTERN.matcher(source).replaceAll(match -> {
            String color = resolveTestColor(match.group(1), testResults);
            return Matcher.quoteReplacement("<color:" + color + ">" + match.group(2) + "</color>");
        });
        // #testsColor(<testid>N</testid>) on arrows → #COLOR
        resolved = TESTS_COLOR_ARROW_PATTERN.matcher(resolved).replaceAll(match -> {
            String color = resolveTestColor(match.group(1), testResults);
            return Matcher.quoteReplacement("#" + color);
        });
        return resolved;
    }

    /**
     * Resolves a single test ID to a PlantUML color based on test results.
     */
    /**
     * Resolves a test ID (guaranteed digits-only by regex) to a PlantUML color.
     */
    private static String resolveTestColor(String testIdStr, @Nullable Map<Long, TestFeedbackDetail> testResults) {
        if (testResults == null) {
            return "grey";
        }
        long testId = Long.parseLong(testIdStr);
        TestFeedbackDetail detail = testResults.get(testId);
        if (detail == null) {
            return "grey";
        }
        return detail.passed() ? "green" : "red";
    }

    private String extractTasks(String markdown, List<TaskRenderInfoDTO> tasks, @Nullable Map<Long, TestFeedbackDetail> testResults) {
        Matcher matcher = TASK_PATTERN.matcher(markdown);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String taskName = matcher.group("name");
            String testsStr = matcher.group("tests");

            List<Long> testIds = new ArrayList<>();
            if (testsStr != null && !testsStr.isEmpty()) {
                Matcher testIdMatcher = TESTID_PATTERN.matcher(testsStr);
                while (testIdMatcher.find()) {
                    testIds.add(Long.parseLong(testIdMatcher.group(1)));
                }
            }

            String testStatus = computeTaskTestStatus(testIds, testResults);
            int[] counts = countTestResults(testIds, testResults);
            int successCount = counts[0];
            int failCount = counts[1];
            int total = testIds.size();

            boolean hasFeedback = testResults != null && !testIds.isEmpty();
            String taskHtml = buildTaskHtml(taskName, testIds, testStatus, successCount, total, hasFeedback ? testResults : null);

            matcher.appendReplacement(sb, Matcher.quoteReplacement(taskHtml));
            tasks.add(new TaskRenderInfoDTO(taskName, testIds, testStatus, hasFeedback ? successCount : null, hasFeedback ? failCount : null));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String buildTaskHtml(String taskName, List<Long> testIds, @Nullable String testStatus, int successCount, int total,
            @Nullable Map<Long, TestFeedbackDetail> testResults) {
        String testIdsStr = testIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String statusClass = testStatus != null ? " artemis-task-" + testStatus : "";
        String statusAttr = testStatus != null ? " data-test-status=\"" + testStatus + "\"" : "";

        StringBuilder html = new StringBuilder();
        html.append("<span class=\"artemis-task").append(statusClass).append("\" data-task-name=\"").append(escapeHtmlAttribute(taskName)).append("\" data-test-ids=\"")
                .append(testIdsStr).append("\"").append(statusAttr);

        if (testResults != null) {
            html.append(" data-feedback=\"").append(buildFeedbackJson(testIds, testResults)).append("\"");
        }

        html.append(">");
        if (testStatus != null) {
            String iconClass = "success".equals(testStatus) ? "fa-check-circle artemis-icon-success" : "fa-times-circle artemis-icon-fail";
            html.append("<i class=\"fa ").append(iconClass).append("\"></i> ");
        }
        html.append(escapeHtml(taskName));
        if (testResults != null && !testIds.isEmpty()) {
            html.append(" <span class=\"artemis-task-stats\">").append(successCount).append(" von ").append(total).append(" Tests bestanden</span>");
        }
        html.append("</span><br>");
        return html.toString();
    }

    private static String buildFeedbackJson(List<Long> testIds, Map<Long, TestFeedbackDetail> testResults) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Long testId : testIds) {
            TestFeedbackDetail detail = testResults.get(testId);
            if (detail != null) {
                if (!first) {
                    json.append(",");
                }
                json.append("{&quot;name&quot;:&quot;").append(escapeHtmlAttribute(detail.testName())).append("&quot;,&quot;passed&quot;:").append(detail.passed());
                if (detail.message() != null && !detail.message().isBlank()) {
                    json.append(",&quot;message&quot;:&quot;").append(escapeHtmlAttribute(detail.message())).append("&quot;");
                }
                json.append("}");
                first = false;
            }
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Computes the aggregate test status for a task.
     * Mirrors the Angular client's testStatusForTask logic:
     * FAIL (any failed) > not-executed (any missing) > SUCCESS (all passed).
     *
     * @return "success", "fail", "not-executed", or null if no results
     */
    private static @Nullable String computeTaskTestStatus(List<Long> testIds, @Nullable Map<Long, TestFeedbackDetail> testResults) {
        if (testResults == null || testIds.isEmpty()) {
            return null;
        }

        boolean anyFailed = false;
        boolean anyNotExecuted = false;

        for (Long testId : testIds) {
            TestFeedbackDetail detail = testResults.get(testId);
            if (detail == null) {
                anyNotExecuted = true;
            }
            else if (!detail.passed()) {
                anyFailed = true;
            }
        }

        if (anyFailed) {
            return "fail";
        }
        if (anyNotExecuted) {
            return "not-executed";
        }
        return "success";
    }

    /**
     * Counts successful and failed tests for a task.
     *
     * @return int[2]: [successCount, failCount]
     */
    private static int[] countTestResults(List<Long> testIds, @Nullable Map<Long, TestFeedbackDetail> testResults) {
        if (testResults == null) {
            return new int[] { 0, 0 };
        }
        int success = 0;
        int fail = 0;
        for (Long testId : testIds) {
            TestFeedbackDetail detail = testResults.get(testId);
            if (detail != null && detail.passed()) {
                success++;
            }
            else if (detail != null && !detail.passed()) {
                fail++;
            }
        }
        return new int[] { success, fail };
    }

    /**
     * Rewrites relative URLs to absolute and sanitizes HTML.
     * Used for the GraalJS path where URL rewriting can't happen during rendering.
     */
    private String rewriteUrlsAndSanitize(String html) {
        Document dirty = Jsoup.parseBodyFragment(html);
        dirty.select("a[href^=/]").forEach(el -> el.attr("href", serverUrl + el.attr("href")));
        dirty.select("img[src^=/]").forEach(el -> el.attr("src", serverUrl + el.attr("src")));
        Cleaner cleaner = new Cleaner(HTML_SAFELIST);
        Document clean = cleaner.clean(dirty);
        return clean.body().html();
    }

    /**
     * Sanitizes HTML using the shared safelist.
     * Used for the CommonMark fallback path.
     */
    private String sanitizeHtml(String html) {
        return Jsoup.clean(html, HTML_SAFELIST);
    }

    private static Safelist buildSafelist() {
        Safelist safelist = Safelist.relaxed();

        // Custom elements for tasks and diagrams
        safelist.addAttributes("div", "class", "data-diagram-id", "data-svg-url");
        safelist.addAttributes("span", "class", "data-task-name", "data-test-ids", "data-test-status", "data-feedback", "data-svg-index");
        safelist.addAttributes("code", "class");
        safelist.addAttributes("pre", "class");

        // KaTeX HTML output: spans with class, style (sizing/positioning), and aria-hidden.
        // TRUST BOUNDARY: allowing style on span enables CSS injection (e.g. position:fixed overlays)
        // from user-authored HTML (markdown-it runs with html:true). This is accepted because:
        // 1. Problem statements are authored by trusted instructors/editors, not students
        // 2. CSS cannot execute JavaScript in modern browsers (no expression(), no -moz-binding)
        // 3. jsoup does not support CSS property whitelisting — it's all-or-nothing for style
        // 4. KaTeX requires inline styles for layout (strut heights, spacing, etc.)
        // If this trust model changes (e.g. student-authored content), style must be restricted.
        safelist.addAttributes("span", "style", "aria-hidden");

        // GitHub alerts: class on p and div (div already has class above)
        safelist.addAttributes("p", "class");

        // FontAwesome icons for task status
        safelist.addAttributes("i", "class");

        // MathML tags for KaTeX accessibility
        safelist.addTags("math", "semantics", "annotation", "mrow", "mi", "mo", "mn", "ms", "mtext", "msup", "msub", "msubsup", "mfrac", "msqrt", "mroot", "mover", "munder",
                "munderover", "mspace", "mtable", "mtr", "mtd", "menclose", "mpadded", "mphantom", "mstyle", "merror");

        // MathML attributes
        safelist.addAttributes("math", "xmlns", "display");
        safelist.addAttributes("annotation", "encoding");
        safelist.addAttributes("mo", "fence", "stretchy", "symmetric", "lspace", "rspace", "minsize", "maxsize", "separator", "form", "movablelimits");
        safelist.addAttributes("mi", "mathvariant");
        safelist.addAttributes("mspace", "width");
        safelist.addAttributes("mfrac", "linethickness");
        safelist.addAttributes("mtd", "columnalign");
        safelist.addAttributes("mover", "accent");
        safelist.addAttributes("munder", "accentunder");
        safelist.addAttributes("mpadded", "width", "height", "depth", "lspace", "voffset");
        safelist.addAttributes("mstyle", "displaystyle", "scriptlevel");

        return safelist;
    }

    private static String escapeHtmlAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Computes a SHA-256 hash of the given input string.
     *
     * @param input the string to hash
     * @return the hex-encoded SHA-256 hash
     */
    public static String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
