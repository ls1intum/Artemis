package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.service.notifications.MarkdownRelativeToAbsolutePathAttributeProvider;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.programming.service.PlantUmlService;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProblemStatementRenderingService {

    private static final Logger log = LoggerFactory.getLogger(ProblemStatementRenderingService.class);

    private static final String RENDERER_VERSION = "1.0.0";

    private static final int MAX_PLANTUML_DIAGRAMS = 10;

    private static final @Nullable String INTERACTIVE_JS = loadInteractiveJs();

    // Dangerous SVG constructs that PlantUML should never generate but we strip as defense-in-depth
    private static final Pattern SVG_DANGEROUS_PATTERN = Pattern.compile("<script|</script|\\bon\\w+\\s*=|javascript:|foreignObject|<image|<use[\\s>]", Pattern.CASE_INSENSITIVE);

    // @formatter:off
    private static final String EMBEDDED_CSS = """
            <style>
            .artemis-problem-statement{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Helvetica Neue",Arial,sans-serif;line-height:1.5;color:var(--body-color,#212529)}
            .artemis-problem-statement h1,.artemis-problem-statement h2,.artemis-problem-statement h3,.artemis-problem-statement h4{font-weight:400}
            .artemis-problem-statement ol,.artemis-problem-statement ul{margin-bottom:.75em}
            .artemis-problem-statement hr{border:none;border-top:1px solid var(--border-color,#dee2e6);margin:16px 0}
            .artemis-problem-statement svg{max-width:100%;height:auto}
            .artemis-problem-statement a{color:var(--link-color,#3e8acc)}
            .artemis-problem-statement pre{background:var(--artemis-pre-background,#f5f5f5);color:var(--artemis-pre-color,#333);border:1px solid var(--artemis-pre-border,#ccc);border-radius:4px;padding:10px;font-size:13px;line-height:1.43;white-space:pre-wrap;overflow-wrap:break-word}
            .artemis-problem-statement :not(pre)>code{font-size:87.5%;color:#d63384;padding:2px 4px;background:var(--artemis-pre-background,#f5f5f5);border-radius:4px}
            .artemis-problem-statement blockquote{color:var(--markdown-preview-blockquote,#6a737d);border-left:4px solid var(--markdown-preview-blockquote-border,#dfe2e5);padding:0 1em;margin:0 0 16px}
            .artemis-problem-statement img{max-width:100%}
            .artemis-task{cursor:pointer;font-weight:600}
            i.fa.artemis-icon-success,i.fa.artemis-icon-fail{display:inline-block;width:1em;height:1em;vertical-align:-0.125em;background-size:contain;background-repeat:no-repeat}
            i.fa.artemis-icon-success{background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E%3Ccircle cx='8' cy='8' r='7.5' fill='%2328a745'/%3E%3Cpath d='M5 8l2 2 4-4' stroke='%23fff' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round' fill='none'/%3E%3C/svg%3E")}
            i.fa.artemis-icon-fail{background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E%3Ccircle cx='8' cy='8' r='7.5' fill='%23dc3545'/%3E%3Cpath d='M5.5 5.5l5 5M10.5 5.5l-5 5' stroke='%23fff' stroke-width='1.5' stroke-linecap='round' fill='none'/%3E%3C/svg%3E")}
            .artemis-task-stats{font-weight:400;font-size:.9em;margin-left:4px;text-decoration:underline}
            .artemis-task-success .artemis-task-stats{color:var(--success,#28a745)}
            .artemis-task-fail .artemis-task-stats{color:var(--danger,#dc3545)}
            .artemis-task-not-executed .artemis-task-stats{color:var(--secondary,#6c757d)}
            .markdown-alert{border-left:4px solid var(--info,#17a2b8);padding:8px 16px;margin:16px 0;border-radius:0 4px 4px 0}
            .markdown-alert-title{font-weight:600}
            table.table{border-collapse:collapse;width:100%}
            table.table th,table.table td{border:1px solid var(--border-color,#dee2e6);padding:8px}
            </style>
            """;
    // @formatter:on

    /**
     * Holds feedback detail for a single test case.
     */
    public record TestFeedbackDetail(long testId, String testName, boolean passed, @Nullable String message, @Nullable Double credits) {
    }

    /**
     * Holds result-level summary data for the interactive modal (score, submission info).
     */
    public record ResultSummary(@Nullable Double score, @Nullable Double maxPoints, @Nullable Double bonusPoints, @Nullable String commitHash, @Nullable String submissionDate,
            @Nullable String assessmentType) {
    }

    private static final Pattern PLANTUML_PATTERN = Pattern.compile("@startuml([^@]*)@enduml");

    private static final Pattern TASK_PATTERN = Pattern
            .compile("\\[task]\\[(?<name>[^\\[\\]]+)]\\((?<tests>(?:[^(),]+(?:\\([^()]*\\)[^(),]*)?(?:,[^(),]+(?:\\([^()]*\\)[^(),]*)?)*)?)\\)");

    private static final Pattern TESTID_PATTERN = Pattern.compile("<testid>(\\d+)</testid>");

    private static final Pattern TESTS_COLOR_TAG_PATTERN = Pattern.compile("<color:testsColor\\(<testid>(\\d+)</testid>\\)>(.*?)</color>");

    private static final Pattern TESTS_COLOR_ARROW_PATTERN = Pattern.compile("#testsColor\\(<testid>(\\d+)</testid>\\)");

    private static final String SVG_PLACEHOLDER_PREFIX = "<span class=\"artemis-svg-placeholder\" data-svg-index=\"";

    private static final String SVG_PLACEHOLDER_SUFFIX = "\"></span>";

    private static final Safelist HTML_SAFELIST = buildSafelist();

    private final PlantUmlService plantUmlService;

    private final ObjectMapper objectMapper;

    private final MessageSource messageSource;

    private final String serverUrl;

    public ProblemStatementRenderingService(PlantUmlService plantUmlService, ObjectMapper objectMapper, MessageSource messageSource, @Value("${server.url}") String serverUrl) {
        this.plantUmlService = plantUmlService;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
        this.serverUrl = serverUrl;
    }

    /**
     * Stateless rendering: client provides all data, server returns self-contained HTML with interactive JS.
     * Zero database access. Uses CommonMark with inline PlantUML SVGs.
     *
     * @param markdown      the raw problem statement markdown
     * @param testResults   client-provided test results, or null
     * @param resultSummary client-provided result summary (score, commit hash, etc.), or null
     * @param locale        the locale for i18n of user-visible text
     * @param darkMode      if true, PlantUML diagrams use the Artemis dark theme
     * @param interactive   if true, includes vanilla JS for task feedback modal
     * @return the rendered problem statement DTO
     */
    public RenderedProblemStatementDTO render(String markdown, @Nullable Map<Long, TestFeedbackDetail> testResults, @Nullable ResultSummary resultSummary, Locale locale,
            boolean darkMode, boolean interactive) {

        if (markdown == null || markdown.isBlank()) {
            return new RenderedProblemStatementDTO("", computeHash(""), RENDERER_VERSION, null);
        }

        String processedMarkdown = markdown;

        // Step 1: Extract PlantUML diagrams (max 10)
        List<String> inlineSvgs = new ArrayList<>();
        processedMarkdown = extractPlantUmlDiagrams(processedMarkdown, inlineSvgs, testResults, darkMode);

        // Step 2: Extract tasks
        processedMarkdown = extractTasks(processedMarkdown, testResults, locale);

        // Step 3: CommonMark → HTML
        String html = renderWithCommonMark(processedMarkdown);

        // Step 4: Inject SVGs (after sanitization since jsoup can't handle SVG)
        for (int i = 0; i < inlineSvgs.size(); i++) {
            html = html.replace(SVG_PLACEHOLDER_PREFIX + i + SVG_PLACEHOLDER_SUFFIX, inlineSvgs.get(i));
        }

        // Step 5: Wrap in container div with result summary
        String resultAttr = buildResultAttribute(resultSummary);
        html = "<div class=\"artemis-problem-statement\"" + resultAttr + ">" + html + "</div>";

        // Step 6: Strip testid tags
        html = html.replace("<testid>", "").replace("</testid>", "");

        // Step 7: Prepend embedded CSS
        html = EMBEDDED_CSS + html;

        // Step 8: Content hash (covers HTML + JS)
        String interactiveScript = interactive ? buildLocalizedScript(locale) : null;
        String contentHash = computeHash(html + (interactiveScript != null ? interactiveScript : ""));

        return new RenderedProblemStatementDTO(html, contentHash, RENDERER_VERSION, interactiveScript);
    }

    private String extractPlantUmlDiagrams(String markdown, List<String> inlineSvgs, @Nullable Map<Long, TestFeedbackDetail> testResults, boolean darkMode) {
        Matcher matcher = PLANTUML_PATTERN.matcher(markdown);
        StringBuilder sb = new StringBuilder();
        int diagramIndex = 0;

        while (matcher.find()) {
            if (diagramIndex >= MAX_PLANTUML_DIAGRAMS) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("<div class=\"alert alert-warning\">Diagram limit exceeded</div>"));
                continue;
            }

            String fullMatch = matcher.group(0);
            String diagramId = "uml-" + diagramIndex;
            String cleanedSource = resolvePlantUmlTestColors(fullMatch, testResults);

            String inlineSvg = null;
            try {
                String rawSvg = plantUmlService.generateSvg(cleanedSource, darkMode);
                rawSvg = rawSvg.replace("preserveAspectRatio=\"none\"", "preserveAspectRatio=\"xMidYMid meet\"");
                rawSvg = rawSvg.replaceFirst("style=\"width:\\d+px;height:\\d+px;", "style=\"");
                rawSvg = rawSvg.replace("background:#FFFFFF;", "");
                inlineSvg = rejectDangerousSvg(rawSvg);
            }
            catch (Exception e) {
                log.error("Failed to generate inline SVG for diagram {} in stateless render", diagramId, e);
                inlineSvg = "<div class=\"alert alert-danger\">Failed to render diagram</div>";
            }
            inlineSvgs.add(inlineSvg);

            String replacement = "<div class=\"artemis-diagram\" data-diagram-id=\"" + diagramId + "\">" + SVG_PLACEHOLDER_PREFIX + diagramIndex + SVG_PLACEHOLDER_SUFFIX
                    + "</div>";

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            diagramIndex++;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Defense-in-depth: reject SVG if it contains dangerous constructs that PlantUML should never generate.
     * Falls back to a safe error message instead of inlining the suspect SVG.
     */
    private static String rejectDangerousSvg(String svg) {
        if (SVG_DANGEROUS_PATTERN.matcher(svg).find()) {
            log.warn("PlantUML SVG contained dangerous construct — falling back to error placeholder");
            return "<div class=\"alert alert-danger\">SVG rejected for security reasons</div>";
        }
        return svg;
    }

    private static String resolvePlantUmlTestColors(String source, @Nullable Map<Long, TestFeedbackDetail> testResults) {
        String resolved = TESTS_COLOR_TAG_PATTERN.matcher(source).replaceAll(match -> {
            String color = resolveTestColor(match.group(1), testResults);
            return Matcher.quoteReplacement("<color:" + color + ">" + match.group(2) + "</color>");
        });
        resolved = TESTS_COLOR_ARROW_PATTERN.matcher(resolved).replaceAll(match -> {
            String color = resolveTestColor(match.group(1), testResults);
            return Matcher.quoteReplacement("#" + color);
        });
        return resolved;
    }

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

    private String extractTasks(String markdown, @Nullable Map<Long, TestFeedbackDetail> testResults, Locale locale) {
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
            int total = testIds.size();

            boolean hasFeedback = testResults != null && !testIds.isEmpty();
            String taskHtml = buildTaskHtml(taskName, testIds, testStatus, successCount, total, hasFeedback ? testResults : null, locale);

            matcher.appendReplacement(sb, Matcher.quoteReplacement(taskHtml));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String buildTaskHtml(String taskName, List<Long> testIds, @Nullable String testStatus, int successCount, int total, @Nullable Map<Long, TestFeedbackDetail> testResults,
            Locale locale) {
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
            String statsText = messageSource.getMessage("exercise.problemStatement.taskStats", new Object[] { successCount, total }, locale);
            html.append(" <span class=\"artemis-task-stats\">").append(escapeHtml(statsText)).append("</span>");
        }
        html.append("</span><br>");
        return html.toString();
    }

    private String buildFeedbackJson(List<Long> testIds, Map<Long, TestFeedbackDetail> testResults) {
        List<Map<String, Object>> feedbackList = new ArrayList<>();
        for (Long testId : testIds) {
            TestFeedbackDetail detail = testResults.get(testId);
            if (detail != null) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", detail.testName());
                entry.put("passed", detail.passed());
                if (detail.credits() != null) {
                    entry.put("credits", detail.credits());
                }
                if (detail.message() != null && !detail.message().isBlank()) {
                    entry.put("message", detail.message());
                }
                feedbackList.add(entry);
            }
        }
        try {
            return escapeHtmlAttribute(objectMapper.writeValueAsString(feedbackList));
        }
        catch (JsonProcessingException e) {
            log.error("Failed to serialize feedback JSON", e);
            return "[]";
        }
    }

    private String buildResultAttribute(@Nullable ResultSummary resultSummary) {
        if (resultSummary == null) {
            return "";
        }
        try {
            return " data-result=\"" + escapeHtmlAttribute(objectMapper.writeValueAsString(resultSummary)) + "\"";
        }
        catch (JsonProcessingException e) {
            log.error("Failed to serialize result summary JSON", e);
            return "";
        }
    }

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

    private String renderWithCommonMark(String markdown) {
        var extensions = List.of(TablesExtension.create(), StrikethroughExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).attributeProviderFactory(ctx -> new MarkdownRelativeToAbsolutePathAttributeProvider(serverUrl))
                .build();
        String html = renderer.render(parser.parse(markdown));
        return Jsoup.clean(html, HTML_SAFELIST);
    }

    private static Safelist buildSafelist() {
        Safelist safelist = Safelist.relaxed();
        safelist.addAttributes("div", "class", "data-diagram-id", "data-svg-url", "data-result");
        safelist.addAttributes("span", "class", "data-task-name", "data-test-ids", "data-test-status", "data-feedback", "data-svg-index");
        safelist.addAttributes("code", "class");
        safelist.addAttributes("pre", "class");
        safelist.addAttributes("p", "class");
        safelist.addAttributes("i", "class");
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

    private @Nullable String buildLocalizedScript(Locale locale) {
        if (INTERACTIVE_JS == null) {
            return null;
        }
        String prefix = "exercise.problemStatement.modal.";
        Map<String, String> i18n = new LinkedHashMap<>();
        for (String key : List.of("feedbackTitle", "close", "score", "points", "of", "submitted", "commit", "failedTests", "passedTests")) {
            i18n.put(key, messageSource.getMessage(prefix + key, null, key, locale));
        }
        try {
            return "var __i18n = " + objectMapper.writeValueAsString(i18n) + ";\n" + INTERACTIVE_JS;
        }
        catch (JsonProcessingException e) {
            log.error("Failed to serialize i18n JSON for interactive script", e);
            return INTERACTIVE_JS;
        }
    }

    private static @Nullable String loadInteractiveJs() {
        try (InputStream is = new ClassPathResource("problem-statement-js/interactive.js").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            log.warn("Could not load interactive JS resource for problem statement rendering");
            return null;
        }
    }
}
