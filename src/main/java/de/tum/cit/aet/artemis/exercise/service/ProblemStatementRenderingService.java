package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final String KATEX_BASE_PATH = "/webjars/katex/dist";

    private static final int MAX_PLANTUML_DIAGRAMS = 10;

    private static final @Nullable String INTERACTIVE_JS = loadClasspathResource("problem-statement-js/interactive.js");

    private static final @Nullable String EMBEDDED_CSS = loadClasspathResource("problem-statement-css/embedded.css");

    private static final @Nullable String DARK_MODE_CSS = loadClasspathResource("problem-statement-css/dark-mode.css");

    // Dangerous SVG constructs that PlantUML should never generate but we strip as defense-in-depth
    private static final Pattern SVG_DANGEROUS_PATTERN = Pattern.compile("<script|</script|\\bon\\w+\\s*=|javascript:|foreignObject|<image|<use[\\s>]", Pattern.CASE_INSENSITIVE);

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

    private static final @Nullable String KATEX_AUTO_RENDER_JS = loadClasspathResource("problem-statement-js/katex-auto-render.js");

    /** Matches a line where $$...$$ appears with other text before or after (inline formula convention). */
    private static final Pattern INLINE_FORMULA_LINE = Pattern.compile(".+\\$\\$[^$]+\\$\\$|\\$\\$[^$]+\\$\\$.+");

    /** Display math: $$...$$ on its own line (after formula compatibility transform). */
    private static final Pattern DISPLAY_MATH_PATTERN = Pattern.compile("^\\$\\$([\\s\\S]+?)\\$\\$$", Pattern.MULTILINE);

    /** Inline math: $...$, but not inside other $ signs. */
    private static final Pattern INLINE_MATH_PATTERN = Pattern.compile("(?<!\\$)\\$(?!\\$)(.+?)(?<!\\$)\\$(?!\\$)");

    private static final String MATH_PLACEHOLDER_PREFIX = "\u0000MATH_";

    private static final String MATH_PLACEHOLDER_SUFFIX = "\u0000";

    private static final String CODE_BLOCK_PLACEHOLDER_PREFIX = "\u0000CODE_BLOCK_";

    private static final String CODE_BLOCK_PLACEHOLDER_SUFFIX = "\u0000";

    /** Fenced code blocks (```...```) and inline code (`...`). */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```|`[^`\n]+`");

    private static final Pattern PLANTUML_PATTERN = Pattern.compile("@startuml([\\s\\S]*?)@enduml");

    private static final Pattern TASK_PATTERN = Pattern
            .compile("\\[task]\\[(?<name>[^\\[\\]]+)]\\((?<tests>(?:[^(),]+(?:\\([^()]*\\)[^(),]*)?(?:,[^(),]+(?:\\([^()]*\\)[^(),]*)?)*)?)\\)");

    private static final Pattern TESTID_PATTERN = Pattern.compile("<testid>(\\d+)</testid>");

    // Inner capture for testsColor(): matches <testid>123</testid> or test names like testClass[Vehicle] or testMoveCar(IOTester)
    // Aligned with the Angular client regex: /testsColor\((\s*[^()\s]+(\([^()]*\))?)\)/g
    private static final String TESTS_COLOR_INNER = "(\\s*[^()\\s]+(?:\\([^()]*\\))?)";

    private static final Pattern TESTS_COLOR_TAG_PATTERN = Pattern.compile("<color:testsColor\\(" + TESTS_COLOR_INNER + "\\)>(.*?)</color>");

    private static final Pattern TESTS_COLOR_ARROW_PATTERN = Pattern.compile("#testsColor\\(" + TESTS_COLOR_INNER + "\\)");

    private static final Pattern TESTS_COLOR_TEXT_PATTERN = Pattern.compile("#text:testsColor\\(" + TESTS_COLOR_INNER + "\\)");

    private static final String SVG_PLACEHOLDER_PREFIX = "<span class=\"artemis-svg-placeholder\" data-svg-index=\"";

    private static final String SVG_PLACEHOLDER_SUFFIX = "\"></span>";

    private static final Safelist HTML_SAFELIST = buildSafelist();

    private static final List<org.commonmark.Extension> COMMONMARK_EXTENSIONS = List.of(TablesExtension.create(), StrikethroughExtension.create());

    private static final Parser COMMONMARK_PARSER = Parser.builder().extensions(COMMONMARK_EXTENSIONS).build();

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
     * @param includeJs     if true, includes vanilla JS for task feedback modal
     * @param includeCss    if true, prepends embedded CSS to the HTML output
     * @return the rendered problem statement DTO
     */
    public RenderedProblemStatementDTO render(String markdown, @Nullable Map<Long, TestFeedbackDetail> testResults, @Nullable ResultSummary resultSummary, Locale locale,
            boolean darkMode, boolean includeJs, boolean includeCss) {

        if (markdown == null || markdown.isBlank()) {
            return new RenderedProblemStatementDTO("", computeHash(""), RENDERER_VERSION, null);
        }

        String processedMarkdown = markdown;

        // Mask code blocks to prevent task/PlantUML extraction inside them
        List<String> codeBlocks = new ArrayList<>();
        processedMarkdown = maskCodeBlocks(processedMarkdown, codeBlocks);

        // Extract PlantUML diagrams (max 10)
        List<String> inlineSvgs = new ArrayList<>();
        processedMarkdown = extractPlantUmlDiagrams(processedMarkdown, inlineSvgs, testResults, darkMode);

        // Apply formula compatibility ($$inline$$ with surrounding text → $inline$)
        processedMarkdown = applyFormulaCompatibility(processedMarkdown);

        // Extract LaTeX formulas to protect them from CommonMark parsing
        List<MathFormula> mathFormulas = new ArrayList<>();
        processedMarkdown = extractMathFormulas(processedMarkdown, mathFormulas);

        // Extract tasks
        processedMarkdown = extractTasks(processedMarkdown, testResults, locale);

        // Restore masked code blocks and LaTeX formula placeholders before CommonMark
        processedMarkdown = restoreCodeBlocks(processedMarkdown, codeBlocks);
        processedMarkdown = restoreMathFormulas(processedMarkdown, mathFormulas);

        // CommonMark → HTML
        String html = renderWithCommonMark(processedMarkdown);

        // Inject SVGs (after sanitization since jsoup can't handle SVG)
        for (int i = 0; i < inlineSvgs.size(); i++) {
            html = html.replace(SVG_PLACEHOLDER_PREFIX + i + SVG_PLACEHOLDER_SUFFIX, inlineSvgs.get(i));
        }

        // Wrap in container div with result summary
        String resultAttr = buildResultAttribute(resultSummary);
        html = "<div class=\"artemis-problem-statement\"" + resultAttr + ">" + html + "</div>";

        // Strip testid tags
        html = html.replace("<testid>", "").replace("</testid>", "");

        // Prepend embedded CSS (+ dark mode overrides if needed) and KaTeX stylesheet
        if (includeCss) {
            String css = "";
            if (!mathFormulas.isEmpty()) {
                css += "<link rel=\"stylesheet\" href=\"" + serverUrl + KATEX_BASE_PATH + "/katex.min.css\">";
            }
            if (EMBEDDED_CSS != null) {
                css += "<style>" + EMBEDDED_CSS + "</style>";
            }
            if (darkMode && DARK_MODE_CSS != null) {
                css += "<style>" + DARK_MODE_CSS + "</style>";
            }
            html = css + html;
        }

        // Append KaTeX script tags if formulas were found
        if (!mathFormulas.isEmpty()) {
            html += "<script src=\"" + serverUrl + KATEX_BASE_PATH + "/katex.min.js\"></script>";
            if (KATEX_AUTO_RENDER_JS != null) {
                html += "<script>" + KATEX_AUTO_RENDER_JS + "</script>";
            }
        }

        // Content hash (covers HTML + JS)
        String interactiveScript = includeJs ? buildLocalizedScript(locale) : null;
        String contentHash = computeHash(html + (interactiveScript != null ? interactiveScript : ""));

        // Wrap in full HTML document
        String document = "<!DOCTYPE html><html lang=\"" + locale.toLanguageTag() + "\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head><body>" + html
                + (interactiveScript != null ? "<script>" + interactiveScript + "</script>" : "") + "</body></html>";

        return new RenderedProblemStatementDTO(document, contentHash, RENDERER_VERSION, interactiveScript);
    }

    private String extractPlantUmlDiagrams(String markdown, List<String> inlineSvgs, @Nullable Map<Long, TestFeedbackDetail> testResults, boolean darkMode) {
        // Build name-based lookup map once for all diagrams
        Map<String, TestFeedbackDetail> testResultsByName = buildTestResultsByName(testResults);

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
            String cleanedSource = resolvePlantUmlTestColors(fullMatch, testResults, testResultsByName);

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

    private static Map<String, TestFeedbackDetail> buildTestResultsByName(@Nullable Map<Long, TestFeedbackDetail> testResults) {
        if (testResults == null) {
            return Map.of();
        }
        Map<String, TestFeedbackDetail> byName = new HashMap<>();
        for (TestFeedbackDetail detail : testResults.values()) {
            byName.put(detail.testName(), detail);
        }
        return byName;
    }

    private static String resolvePlantUmlTestColors(String source, @Nullable Map<Long, TestFeedbackDetail> testResults, Map<String, TestFeedbackDetail> byName) {

        // <color:testsColor(...)>text</color>
        String resolved = TESTS_COLOR_TAG_PATTERN.matcher(source).replaceAll(match -> {
            String color = resolveTestColor(match.group(1).trim(), testResults, byName);
            return Matcher.quoteReplacement("<color:" + color + ">" + match.group(2) + "</color>");
        });
        // #text:testsColor(...) — must be checked before #testsColor(...) to avoid partial match
        resolved = TESTS_COLOR_TEXT_PATTERN.matcher(resolved).replaceAll(match -> {
            String color = resolveTestColor(match.group(1).trim(), testResults, byName);
            return Matcher.quoteReplacement("#text:" + color);
        });
        // #testsColor(...)
        resolved = TESTS_COLOR_ARROW_PATTERN.matcher(resolved).replaceAll(match -> {
            String color = resolveTestColor(match.group(1).trim(), testResults, byName);
            return Matcher.quoteReplacement("#" + color);
        });
        return resolved;
    }

    private static String resolveTestColor(String testRef, @Nullable Map<Long, TestFeedbackDetail> testResults, Map<String, TestFeedbackDetail> testResultsByName) {
        if (testResults == null) {
            return "grey";
        }
        // Try <testid>ID</testid> format
        Matcher idMatcher = TESTID_PATTERN.matcher(testRef);
        if (idMatcher.matches()) {
            long testId = Long.parseLong(idMatcher.group(1));
            TestFeedbackDetail detail = testResults.get(testId);
            return detail == null ? "grey" : detail.passed() ? "green" : "red";
        }
        // Fall back to name-based lookup
        TestFeedbackDetail detail = testResultsByName.get(testRef);
        return detail == null ? "grey" : detail.passed() ? "green" : "red";
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
            int successCount = countPassedTests(testIds, testResults);
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

    private static int countPassedTests(List<Long> testIds, @Nullable Map<Long, TestFeedbackDetail> testResults) {
        if (testResults == null) {
            return 0;
        }
        int success = 0;
        for (Long testId : testIds) {
            TestFeedbackDetail detail = testResults.get(testId);
            if (detail != null && detail.passed()) {
                success++;
            }
        }
        return success;
    }

    private record MathFormula(String latex, boolean displayMode) {
    }

    /**
     * Ports the Angular FormulaCompatibilityPlugin: if a line contains $$...$$ with surrounding text,
     * convert all $$ to $ on that line (making it inline math). Also normalizes \\begin/\\end to \begin/\end.
     */
    private static String applyFormulaCompatibility(String markdown) {
        String[] lines = markdown.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (INLINE_FORMULA_LINE.matcher(line).find()) {
                line = line.replace("$$", "$");
            }
            if (line.contains("\\\\begin") || line.contains("\\\\end")) {
                line = line.replace("\\\\begin", "\\begin").replace("\\\\end", "\\end");
            }
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Extracts LaTeX formulas from markdown and replaces them with placeholders.
     * Display math ($$...$$) is extracted first, then inline math ($...$).
     */
    private static String extractMathFormulas(String markdown, List<MathFormula> formulas) {
        // Extract display math first
        String result = DISPLAY_MATH_PATTERN.matcher(markdown).replaceAll(match -> {
            int index = formulas.size();
            formulas.add(new MathFormula(match.group(1).trim(), true));
            return Matcher.quoteReplacement(MATH_PLACEHOLDER_PREFIX + index + MATH_PLACEHOLDER_SUFFIX);
        });
        // Then extract inline math
        result = INLINE_MATH_PATTERN.matcher(result).replaceAll(match -> {
            int index = formulas.size();
            formulas.add(new MathFormula(match.group(1), false));
            return Matcher.quoteReplacement(MATH_PLACEHOLDER_PREFIX + index + MATH_PLACEHOLDER_SUFFIX);
        });
        return result;
    }

    /**
     * Restores math formula placeholders as KaTeX-renderable span elements.
     */
    private static String restoreMathFormulas(String html, List<MathFormula> formulas) {
        String result = html;
        for (int i = 0; i < formulas.size(); i++) {
            MathFormula formula = formulas.get(i);
            String span = "<span class=\"katex-formula\" data-formula=\"" + escapeHtmlAttribute(formula.latex()) + "\" data-display-mode=\"" + formula.displayMode() + "\"></span>";
            result = result.replace(MATH_PLACEHOLDER_PREFIX + i + MATH_PLACEHOLDER_SUFFIX, span);
        }
        return result;
    }

    private String renderWithCommonMark(String markdown) {
        // Parser is static (thread-safe). Renderer is per-call because it depends on the instance's serverUrl.
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(COMMONMARK_EXTENSIONS)
                .attributeProviderFactory(ctx -> new MarkdownRelativeToAbsolutePathAttributeProvider(serverUrl)).build();
        String html = renderer.render(COMMONMARK_PARSER.parse(markdown));
        return Jsoup.clean(html, HTML_SAFELIST);
    }

    /**
     * Replace fenced code blocks and inline code with placeholders so that
     * task/PlantUML extraction does not process content inside them.
     */
    private static String maskCodeBlocks(String markdown, List<String> codeBlocks) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(markdown);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            int index = codeBlocks.size();
            codeBlocks.add(matcher.group());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(CODE_BLOCK_PLACEHOLDER_PREFIX + index + CODE_BLOCK_PLACEHOLDER_SUFFIX));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String restoreCodeBlocks(String markdown, List<String> codeBlocks) {
        String result = markdown;
        for (int i = 0; i < codeBlocks.size(); i++) {
            result = result.replace(CODE_BLOCK_PLACEHOLDER_PREFIX + i + CODE_BLOCK_PLACEHOLDER_SUFFIX, codeBlocks.get(i));
        }
        return result;
    }

    private static Safelist buildSafelist() {
        Safelist safelist = Safelist.relaxed();
        safelist.addAttributes("div", "class", "data-diagram-id", "data-svg-url", "data-result");
        safelist.addAttributes("span", "class", "data-task-name", "data-test-ids", "data-test-status", "data-feedback", "data-svg-index", "data-formula", "data-display-mode");
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

    private static @Nullable String loadClasspathResource(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            log.warn("Could not load classpath resource: {}", path);
            return null;
        }
    }
}
