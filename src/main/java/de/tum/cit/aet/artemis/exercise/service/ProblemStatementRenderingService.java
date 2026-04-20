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
import de.tum.cit.aet.artemis.exercise.dto.ResultSummaryInputDTO;
import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;
import de.tum.cit.aet.artemis.programming.service.PlantUmlService;

/**
 * Stateless renderer for problem-statement markdown.
 * <p>
 * The client sends markdown plus optional test feedback, and this service returns a self-contained HTML
 * document ready for embedding. The pipeline, in order, is:
 * <ol>
 * <li>Mask fenced and inline code blocks so downstream passes do not process their contents.</li>
 * <li>Extract PlantUML diagrams, render them server-side via {@link PlantUmlService}, sanitize the SVG,
 * and replace each diagram with an opaque placeholder.</li>
 * <li>Apply math compatibility rewrites and extract math formulas to placeholders.</li>
 * <li>Expand {@code [task]} syntax into HTML task spans with feedback data.</li>
 * <li>Strip remaining {@code <testid>} wrappers from prose (code blocks are still masked, so code
 * examples keep theirs).</li>
 * <li>Restore masked code blocks; then restore math-formula placeholders.</li>
 * <li>Render CommonMark and re-inject the sanitized SVGs.</li>
 * <li>Wrap everything in a full HTML document, include KaTeX / embedded CSS / interactive JS as needed,
 * and compute a content hash.</li>
 * </ol>
 */
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

    private static final @Nullable String KATEX_AUTO_RENDER_JS = loadClasspathResource("problem-statement-js/katex-auto-render.js");

    private static final String CODE_BLOCK_PLACEHOLDER_PREFIX = "\u0000CODE_BLOCK_";

    private static final String CODE_BLOCK_PLACEHOLDER_SUFFIX = "\u0000";

    /** Fenced code blocks ({@code ```...```}) and inline code ({@code `...`}). */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```|`[^`\n]+`");

    private static final Pattern PLANTUML_PATTERN = Pattern.compile("@startuml([\\s\\S]*?)@enduml");

    /**
     * Matches the task syntax: {@code [task][Task Name](testId1,testId2,...)} where test identifiers
     * are typically {@code <testid>123</testid>} values. Each identifier may carry one level of
     * parenthesized suffix (e.g. {@code testClass(Vehicle)}).
     * <p>
     * Named groups: {@code name} (task display name), {@code tests} (comma-separated test identifiers).
     */
    private static final Pattern TASK_PATTERN = Pattern
            .compile("\\[task]\\[(?<name>[^\\[\\]]+)]\\((?<tests>(?:[^(),]+(?:\\([^()]*\\)[^(),]*)?(?:,[^(),]+(?:\\([^()]*\\)[^(),]*)?)*)?)\\)");

    private static final Pattern TESTID_PATTERN = Pattern.compile("<testid>(\\d+)</testid>");

    private static final String SVG_PLACEHOLDER_PREFIX = "<span class=\"artemis-svg-placeholder\" data-svg-index=\"";

    private static final String SVG_PLACEHOLDER_SUFFIX = "\"></span>";

    private static final Safelist HTML_SAFELIST = buildSafelist();

    private static final List<org.commonmark.Extension> COMMONMARK_EXTENSIONS = List.of(TablesExtension.create(), StrikethroughExtension.create());

    private static final Parser COMMONMARK_PARSER = Parser.builder().extensions(COMMONMARK_EXTENSIONS).build();

    private final PlantUmlService plantUmlService;

    private final ObjectMapper objectMapper;

    private final MessageSource messageSource;

    private final String serverUrl;

    private final HtmlRenderer commonMarkRenderer;

    public ProblemStatementRenderingService(PlantUmlService plantUmlService, ObjectMapper objectMapper, MessageSource messageSource, @Value("${server.url}") String serverUrl) {
        this.plantUmlService = plantUmlService;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
        this.serverUrl = serverUrl;
        this.commonMarkRenderer = HtmlRenderer.builder().extensions(COMMONMARK_EXTENSIONS)
                .attributeProviderFactory(ctx -> new MarkdownRelativeToAbsolutePathAttributeProvider(serverUrl)).build();
    }

    /**
     * Renders the given markdown into a self-contained HTML document.
     *
     * @param markdown      the raw problem statement markdown
     * @param testResults   client-provided test results keyed by test id, or {@code null}
     * @param resultSummary client-provided submission summary, or {@code null}
     * @param locale        the locale for user-visible text (task stats, modal labels)
     * @param darkMode      if {@code true}, PlantUML renders in dark theme and the container carries a dark marker class
     * @param includeJs     if {@code true}, the interactive feedback modal JS is included
     * @param includeCss    if {@code true}, embedded CSS and KaTeX CSS are included
     * @return the rendered problem statement DTO
     */
    public RenderedProblemStatementDTO render(String markdown, @Nullable Map<Long, TestFeedbackInputDTO> testResults, @Nullable ResultSummaryInputDTO resultSummary, Locale locale,
            boolean darkMode, boolean includeJs, boolean includeCss) {

        if (markdown == null || markdown.isBlank()) {
            return new RenderedProblemStatementDTO("", computeHash(""), RENDERER_VERSION, null);
        }

        // 1. Mask code blocks so downstream passes skip over them.
        List<String> codeBlocks = new ArrayList<>();
        String processed = maskCodeBlocks(markdown, codeBlocks);

        // 2. Extract PlantUML diagrams. The sanitized SVG is held out and re-injected after CommonMark.
        List<String> inlineSvgs = new ArrayList<>();
        processed = extractPlantUmlDiagrams(processed, inlineSvgs, testResults, darkMode);

        // 3. Normalize math notation, then extract formulas (still while code blocks are masked).
        processed = MathFormulaExtractor.applyCompatibility(processed);
        List<MathFormulaExtractor.Formula> mathFormulas = new ArrayList<>();
        processed = MathFormulaExtractor.extract(processed, mathFormulas);

        // 4. Expand tasks.
        processed = extractTasks(processed, testResults, locale);

        // 5. Strip leftover <testid>N</testid> wrappers in prose/PlantUML placeholders. Code blocks are
        // still masked, so their contents stay untouched and display as written.
        processed = TESTID_PATTERN.matcher(processed).replaceAll("$1");

        // 6. Restore masked content.
        processed = restoreCodeBlocks(processed, codeBlocks);
        processed = MathFormulaExtractor.restore(processed, mathFormulas);

        // 7. CommonMark → sanitized HTML.
        String html = renderWithCommonMark(processed);

        // 8. Inject the earlier PlantUML SVGs (jsoup's HTML safelist would strip them, so we inject afterwards).
        for (int i = 0; i < inlineSvgs.size(); i++) {
            html = html.replace(SVG_PLACEHOLDER_PREFIX + i + SVG_PLACEHOLDER_SUFFIX, inlineSvgs.get(i));
        }

        String containerClass = darkMode ? "artemis-problem-statement artemis-problem-statement--dark" : "artemis-problem-statement";
        String resultAttr = buildResultAttribute(resultSummary);
        html = "<div class=\"" + containerClass + "\"" + resultAttr + ">" + html + "</div>";

        if (includeCss) {
            StringBuilder css = new StringBuilder();
            if (!mathFormulas.isEmpty()) {
                css.append("<link rel=\"stylesheet\" href=\"").append(HtmlEscaper.escapeAttribute(serverUrl)).append(KATEX_BASE_PATH).append("/katex.min.css\">");
            }
            if (EMBEDDED_CSS != null) {
                css.append("<style>").append(EMBEDDED_CSS).append("</style>");
            }
            if (darkMode && DARK_MODE_CSS != null) {
                css.append("<style>").append(DARK_MODE_CSS).append("</style>");
            }
            html = css + html;
        }

        if (!mathFormulas.isEmpty()) {
            html += "<script src=\"" + HtmlEscaper.escapeAttribute(serverUrl) + KATEX_BASE_PATH + "/katex.min.js\"></script>";
            if (KATEX_AUTO_RENDER_JS != null) {
                html += "<script>" + KATEX_AUTO_RENDER_JS + "</script>";
            }
        }

        String interactiveScript = includeJs ? buildLocalizedScript(locale) : null;
        String contentHash = computeHash(html + (interactiveScript != null ? interactiveScript : ""));

        String bodyClass = darkMode ? " class=\"artemis-ssr-body--dark\"" : "";
        String document = "<!DOCTYPE html><html lang=\"" + HtmlEscaper.escapeAttribute(locale.toLanguageTag()) + "\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head><body" + bodyClass + ">" + html
                + (interactiveScript != null ? "<script>" + interactiveScript + "</script>" : "") + "</body></html>";

        return new RenderedProblemStatementDTO(document, contentHash, RENDERER_VERSION, interactiveScript);
    }

    private String extractPlantUmlDiagrams(String markdown, List<String> inlineSvgs, @Nullable Map<Long, TestFeedbackInputDTO> testResults, boolean darkMode) {
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
            String resolvedSource = PlantUmlTaskColorResolver.resolve(fullMatch, testResults);
            // Strip <testid> wrappers inside PlantUML — the layout engine does not understand them.
            resolvedSource = PlantUmlTaskColorResolver.stripTestIdWrappers(resolvedSource);

            String inlineSvg;
            try {
                String rawSvg = plantUmlService.generateSvg(resolvedSource, darkMode);
                rawSvg = rawSvg.replace("preserveAspectRatio=\"none\"", "preserveAspectRatio=\"xMidYMid meet\"");
                rawSvg = rawSvg.replaceFirst("style=\"width:\\d+px;height:\\d+px;", "style=\"");
                rawSvg = rawSvg.replace("background:#FFFFFF;", "");
                String sanitized = SvgSanitizer.sanitize(rawSvg);
                inlineSvg = sanitized != null ? sanitized : "<div class=\"alert alert-danger\">Failed to render diagram</div>";
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

    private String extractTasks(String markdown, @Nullable Map<Long, TestFeedbackInputDTO> testResults, Locale locale) {
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

    private String buildTaskHtml(String taskName, List<Long> testIds, @Nullable String testStatus, int successCount, int total,
            @Nullable Map<Long, TestFeedbackInputDTO> testResults, Locale locale) {
        String testIdsStr = testIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String statusClass = testStatus != null ? " artemis-task-" + testStatus : "";
        String statusAttr = testStatus != null ? " data-test-status=\"" + testStatus + "\"" : "";

        StringBuilder html = new StringBuilder();
        html.append("<span class=\"artemis-task").append(statusClass).append("\" data-task-name=\"").append(HtmlEscaper.escapeAttribute(taskName)).append("\" data-test-ids=\"")
                .append(testIdsStr).append("\"").append(statusAttr);

        if (testResults != null) {
            html.append(" data-feedback=\"").append(buildFeedbackJson(testIds, testResults)).append("\"");
        }

        html.append(">");
        if (testStatus != null) {
            String iconClass = "success".equals(testStatus) ? "fa-check-circle artemis-icon-success" : "fa-times-circle artemis-icon-fail";
            html.append("<i class=\"fa ").append(iconClass).append("\"></i> ");
        }
        html.append(HtmlEscaper.escapeText(taskName));
        if (testResults != null && !testIds.isEmpty()) {
            String statsText = messageSource.getMessage("exercise.problemStatement.taskStats", new Object[] { successCount, total }, locale);
            html.append(" <span class=\"artemis-task-stats\">").append(HtmlEscaper.escapeText(statsText)).append("</span>");
        }
        html.append("</span><br>");
        return html.toString();
    }

    private String buildFeedbackJson(List<Long> testIds, Map<Long, TestFeedbackInputDTO> testResults) {
        List<Map<String, Object>> feedbackList = new ArrayList<>();
        for (Long testId : testIds) {
            TestFeedbackInputDTO detail = testResults.get(testId);
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
            return HtmlEscaper.escapeAttribute(objectMapper.writeValueAsString(feedbackList));
        }
        catch (JsonProcessingException e) {
            log.error("Failed to serialize feedback JSON", e);
            return "[]";
        }
    }

    private String buildResultAttribute(@Nullable ResultSummaryInputDTO resultSummary) {
        if (resultSummary == null) {
            return "";
        }
        try {
            return " data-result=\"" + HtmlEscaper.escapeAttribute(objectMapper.writeValueAsString(resultSummary)) + "\"";
        }
        catch (JsonProcessingException e) {
            log.error("Failed to serialize result summary JSON", e);
            return "";
        }
    }

    private static @Nullable String computeTaskTestStatus(List<Long> testIds, @Nullable Map<Long, TestFeedbackInputDTO> testResults) {
        if (testResults == null || testIds.isEmpty()) {
            return null;
        }
        boolean anyFailed = false;
        boolean anyNotExecuted = false;
        for (Long testId : testIds) {
            TestFeedbackInputDTO detail = testResults.get(testId);
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

    private static int countPassedTests(List<Long> testIds, @Nullable Map<Long, TestFeedbackInputDTO> testResults) {
        if (testResults == null) {
            return 0;
        }
        int success = 0;
        for (Long testId : testIds) {
            TestFeedbackInputDTO detail = testResults.get(testId);
            if (detail != null && detail.passed()) {
                success++;
            }
        }
        return success;
    }

    private String renderWithCommonMark(String markdown) {
        String html = commonMarkRenderer.render(COMMONMARK_PARSER.parse(markdown));
        return Jsoup.clean(html, HTML_SAFELIST);
    }

    /**
     * Replaces fenced code blocks and inline code with opaque placeholders so downstream passes
     * (PlantUML, math, tasks, testid stripping) skip their contents.
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
        safelist.addAttributes("div", "class", "data-diagram-id", "data-result");
        safelist.addAttributes("span", "class", "data-task-name", "data-test-ids", "data-test-status", "data-feedback", "data-svg-index", "data-formula", "data-display-mode");
        safelist.addAttributes("code", "class");
        safelist.addAttributes("pre", "class");
        safelist.addAttributes("p", "class");
        safelist.addAttributes("i", "class");
        return safelist;
    }

    private static String computeHash(String input) {
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
            String consequence = switch (path) {
                case "problem-statement-js/interactive.js" -> "interactive feedback modal will not be injected";
                case "problem-statement-js/katex-auto-render.js" -> "client-side KaTeX auto-rendering will not run; formulas will appear as empty placeholders";
                case "problem-statement-css/embedded.css" -> "embedded styling is missing; rendered output will inherit the consumer's CSS only";
                case "problem-statement-css/dark-mode.css" -> "dark mode overrides are unavailable; dark-mode requests will fall back to light styling";
                default -> "asset not loaded";
            };
            log.error("Could not load classpath resource {} — consequence: {}", path, consequence, e);
            return null;
        }
    }
}
