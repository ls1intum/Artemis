package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
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

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.ResultSummaryInputDTO;
import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MarkdownRelativeToAbsolutePathAttributeProvider;
import de.tum.cit.aet.artemis.programming.service.PlantUmlService;

/**
 * Stateless renderer for problem-statement markdown.
 * <p>
 * The client sends markdown plus optional test feedback, and this service returns a self-contained HTML
 * document ready for embedding. Everything that must not be treated as markdown (code, diagrams, formulas) is
 * masked out before the Artemis-specific syntax is expanded, and put back once CommonMark has run and the output
 * has passed the safelist. The numbered steps of {@link #render} are the authoritative account of that order.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProblemStatementRenderingService {

    private static final Logger log = LoggerFactory.getLogger(ProblemStatementRenderingService.class);

    /**
     * Identifies the semantics of the renderer's output, independent of the input markdown. It is folded into
     * {@link #computeContentHash} so that a change to what/how content is rendered (not just a change to the
     * input) invalidates renderings the client has already cached under the old hash. Bump this whenever a
     * change alters the HTML or interactive script the renderer emits for the same input: for example a fix to
     * task-status or diagram-colour derivation, a new or changed Markdown extension, or a change to
     * sanitization/escaping. The value itself does not need to follow strict semver; any distinct string is
     * enough to invalidate the cache.
     */
    private static final String RENDERER_VERSION = "2.0.0-mathml-spike";

    private static final int MAX_PLANTUML_DIAGRAMS = 10;

    private static final @Nullable String INTERACTIVE_JS = loadClasspathResource("problem-statement-js/interactive.js");

    private static final @Nullable String EMBEDDED_CSS = loadClasspathResource("problem-statement-css/embedded.css");

    private static final @Nullable String DARK_MODE_CSS = loadClasspathResource("problem-statement-css/dark-mode.css");

    private static final int MAX_INLINE_IMAGES = 20;

    private static final long MAX_INLINE_FILE_SIZE = 5 * 1024 * 1024;

    private static final long MAX_INLINE_TOTAL_SIZE = 10 * 1024 * 1024;

    private static final String MARKDOWN_FILE_API_PATH = "/api/core/files/markdown/";

    private static final Map<String, String> INLINE_IMAGE_MIME_TYPES = Map.of("png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "gif", "image/gif", "webp",
            "image/webp");

    private static final String CODE_BLOCK_PLACEHOLDER_PREFIX = "\u0000CODE_BLOCK_";

    private static final String CODE_BLOCK_PLACEHOLDER_SUFFIX = "\u0000";

    private static final String PLANTUML_START = "@startuml";

    private static final String PLANTUML_END = "@enduml";

    /**
     * Matches the task syntax: {@code [task][Task Name](testId1,testId2,...)}.
     * <p>
     * A test identifier is typically a {@code <testid>123</testid>} value and may carry one level of parenthesised
     * suffix (for example {@code testClass(Vehicle)}). The list is therefore written as an unrolled loop: a run of
     * non-parenthesis characters, then any number of parenthesised groups each followed by another such run. Commas
     * are absorbed by the character class rather than driving a repetition, because Java's matcher recurses per
     * repetition and an unclosed task with twenty thousand commas would overflow the stack. For the same reason the
     * parenthesised groups are bounded to a hundred. A task list with more than a hundred parenthesised entries is
     * not real content, and beyond the bound the task does not match and is rendered as written.
     * <p>
     * The list is matched loosely, so empty entries are accepted; the caller discards everything that is not a test
     * identifier.
     * <p>
     * Named groups: {@code name} (task display name), {@code tests} (comma-separated test identifiers).
     */
    private static final Pattern TASK_PATTERN = Pattern.compile("\\[task]\\[(?<name>[^\\[\\]]+)]\\((?<tests>[^()]*(?:\\([^()]*\\)[^()]*){0,100})\\)");

    /**
     * Start of the marker that stands in for a diagram between extraction and re-injection.
     * <p>
     * It is plain HTML because it has to survive {@link Jsoup#clean}, and {@code data-svg-index} is on the span
     * safelist so that it does. That is exactly why the marker alone cannot identify a diagram: an author can write
     * the same span into their markdown, it passes the safelist unchanged, and the injection pass would then hand
     * every copy the same SVG. One diagram plus a markdown body full of markers turns a 100 KB request into tens of
     * megabytes and walks straight past {@link #MAX_PLANTUML_DIAGRAMS}, which is the limit that is supposed to bound
     * this. The per-render token appended below is what makes the marker unforgeable: the author writes their
     * markdown before it exists, so a forged span simply fails to match and stays the inert empty span it looks
     * like. Same principle as the null byte in {@link #CODE_BLOCK_PLACEHOLDER_PREFIX}, which the request DTO rejects
     * in markdown for the same reason.
     */
    private static final String SVG_PLACEHOLDER_PREFIX = "<span class=\"artemis-svg-placeholder\" data-svg-index=\"";

    private static final String SVG_PLACEHOLDER_SUFFIX = "\"></span>";

    /** Bytes of randomness per render token, matching the 128 bits the frame nonce and generation use. Never stored. */
    private static final int PLACEHOLDER_TOKEN_BYTES = 16;

    private static final SecureRandom PLACEHOLDER_RANDOM = new SecureRandom();

    private static final Safelist HTML_SAFELIST = buildSafelist();

    private static final List<org.commonmark.Extension> COMMONMARK_EXTENSIONS = List.of(TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create(),
            GitHubAlertExtension.create());

    /** Source spans are on because the alert extension matches its marker against the markdown as authored. */
    private static final Parser COMMONMARK_PARSER = Parser.builder().extensions(COMMONMARK_EXTENSIONS).includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();

    /**
     * Parser used only to locate code constructs before the transformation passes run. It carries the same
     * extensions as the rendering parser so both agree on what is code, and it is the only one that needs source
     * spans, which the rendering parse would otherwise pay for without using them.
     */
    private static final Parser SPAN_PARSER = Parser.builder().extensions(COMMONMARK_EXTENSIONS).includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();

    private final PlantUmlService plantUmlService;

    private final ObjectMapper objectMapper;

    private final MessageSource messageSource;

    private final FileService fileService;

    private final String serverUrl;

    private final HtmlRenderer commonMarkRenderer;

    public ProblemStatementRenderingService(PlantUmlService plantUmlService, ObjectMapper objectMapper, MessageSource messageSource, FileService fileService,
            @Value("${server.url}") String serverUrl) {
        this.plantUmlService = plantUmlService;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
        this.fileService = fileService;
        this.serverUrl = serverUrl;
        this.commonMarkRenderer = HtmlRenderer.builder().extensions(COMMONMARK_EXTENSIONS)
                .attributeProviderFactory(ctx -> new MarkdownRelativeToAbsolutePathAttributeProvider(serverUrl)).build();
    }

    /**
     * Renders the given markdown into a self-contained HTML document.
     *
     * @param markdown       the raw problem statement markdown
     * @param testResults    client-provided test results keyed by test id, or {@code null}
     * @param resultSummary  client-provided submission summary, or {@code null}
     * @param locale         the locale for user-visible text (task stats, modal labels)
     * @param darkMode       if {@code true}, PlantUML renders in dark theme and the container carries a dark marker class
     * @param includeJs      if {@code true}, the interactive feedback modal JS is included
     * @param includeCss     if {@code true}, embedded CSS and KaTeX CSS are included
     * @param inlineImages   if {@code true}, images are embedded as Base64 data URIs; otherwise they stay as absolute URLs
     * @param allTestsPassed if {@code true}, the client reported a successful result that carries no per-test feedback,
     *                           so every test counts as passed. Only honored when {@code testResults} is {@code null}.
     * @return the rendered problem statement DTO
     */
    public RenderedProblemStatementDTO render(String markdown, @Nullable Map<Long, TestFeedbackInputDTO> testResults, @Nullable ResultSummaryInputDTO resultSummary, Locale locale,
            boolean darkMode, boolean includeJs, boolean includeCss, boolean inlineImages, boolean allTestsPassed) {

        if (markdown == null || markdown.isBlank()) {
            return new RenderedProblemStatementDTO("", computeHash(""), RENDERER_VERSION, null);
        }

        // Individual test outcomes always win: the flag is only a substitute for feedback the client does not have. A
        // request carrying both is decided by the feedback. The diagram colors apply the same predicate to the same
        // two inputs inside PlantUmlTaskColorResolver, so status, counts and colors can never disagree.
        boolean allPassed = allTestsPassed && testResults == null;

        // 1. Mask code blocks so downstream passes skip over them.
        List<String> codeBlocks = new ArrayList<>();
        String processed = maskCodeBlocks(markdown, codeBlocks);

        // 2. Extract PlantUML diagrams. The sanitized SVG is held out and re-injected after CommonMark. The token
        // ties the two halves together across the sanitizer, so only a marker this render wrote is ever replaced.
        String placeholderToken = randomPlaceholderToken();
        List<String> inlineSvgs = new ArrayList<>();
        processed = extractPlantUmlDiagrams(processed, inlineSvgs, testResults, darkMode, allTestsPassed, placeholderToken);

        // 3. Normalize math notation, then extract formulas (still while code blocks are masked).
        processed = MathFormulaExtractor.applyCompatibility(processed);
        List<MathFormulaExtractor.Formula> mathFormulas = new ArrayList<>();
        processed = MathFormulaExtractor.extract(processed, mathFormulas);

        // 4. Expand tasks.
        Set<Long> feedbackTestIds = new LinkedHashSet<>();
        processed = extractTasks(processed, testResults, locale, allPassed, feedbackTestIds);

        // 5. Strip leftover <testid>N</testid> wrappers in prose/PlantUML placeholders. Code blocks are
        // still masked, so their contents stay untouched and display as written.
        processed = TestReferenceParser.stripTestIdWrappers(processed);

        // 6. Restore masked content.
        processed = restoreCodeBlocks(processed, codeBlocks);
        // Restore formulas as token-guarded marker spans; the MathML is injected after jsoup (step 8), like the SVGs.
        processed = MathFormulaExtractor.restore(processed, mathFormulas, placeholderToken);

        // 7. CommonMark → sanitized HTML.
        String html = renderWithCommonMark(processed);

        // 7b. Process images based on requested mode.
        if (inlineImages) {
            html = inlineMarkdownImages(html);
        }

        // 8. Inject the earlier PlantUML SVGs and the GitHub-alert octicons (jsoup's HTML safelist would strip
        // any SVG, so both are injected afterwards).
        // A marker that does not carry this render's token was written by the author, not by step 2, and is left
        // exactly as it is. The token is gone from the output either way, so the content hash stays stable.
        html = IndexedPlaceholders.replaceAll(html, SVG_PLACEHOLDER_PREFIX + placeholderToken + "-", SVG_PLACEHOLDER_SUFFIX, inlineSvgs.size(), inlineSvgs::get);
        // Inject sanitized MathML (or escaped source on failure) at the formula markers, also token-guarded and post-jsoup.
        html = MathFormulaExtractor.injectMathml(html, mathFormulas, placeholderToken);
        html = GitHubAlertExtension.injectIcons(html);

        String containerClass = darkMode ? "artemis-problem-statement artemis-problem-statement--dark" : "artemis-problem-statement";
        String resultAttr = buildResultAttribute(resultSummary);
        String feedbackAttr = buildDocumentFeedbackAttribute(feedbackTestIds, testResults);
        html = "<div class=\"" + containerClass + "\"" + resultAttr + feedbackAttr + ">" + html + "</div>";

        if (includeCss) {
            StringBuilder css = new StringBuilder();
            // Formulas are native MathML now, so no KaTeX stylesheet is shipped; embedded.css styles <math> directly.
            if (EMBEDDED_CSS != null) {
                css.append("<style>").append(EMBEDDED_CSS).append("</style>");
            }
            if (darkMode && DARK_MODE_CSS != null) {
                css.append("<style>").append(DARK_MODE_CSS).append("</style>");
            }
            html = css + html;
        }

        String interactiveScript = includeJs ? buildLocalizedScript(locale) : null;
        String contentHash = computeContentHash(html, interactiveScript, inlineImages);

        String bodyClass = " class=\"artemis-ssr-body" + (darkMode ? " artemis-ssr-body--dark" : "") + "\"";
        String document = "<!DOCTYPE html><html lang=\"" + HtmlEscaper.escapeAttribute(locale.toLanguageTag()) + "\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head><body" + bodyClass + ">" + html
                + (interactiveScript != null ? "<script>" + interactiveScript + "</script>" : "") + "</body></html>";

        return new RenderedProblemStatementDTO(document, contentHash, RENDERER_VERSION, interactiveScript);
    }

    private String extractPlantUmlDiagrams(String markdown, List<String> inlineSvgs, @Nullable Map<Long, TestFeedbackInputDTO> testResults, boolean darkMode,
            boolean allTestsPassed, String placeholderToken) {
        StringBuilder sb = new StringBuilder();
        int diagramIndex = 0;
        int copiedUpTo = 0;

        while (true) {
            int start = markdown.indexOf(PLANTUML_START, copiedUpTo);
            if (start < 0) {
                break;
            }
            int endMarker = markdown.indexOf(PLANTUML_END, start + PLANTUML_START.length());
            if (endMarker < 0) {
                // No closing marker after this opening one, so no complete diagram can follow it either, and the rest
                // is left untouched. Bailing out here keeps the scan linear: searching to the end of the input once
                // per opening marker costs 2.4 seconds of CPU for 9000 unclosed `@startuml` lines, which fit well
                // within the 100 000-character request limit.
                break;
            }
            int end = endMarker + PLANTUML_END.length();
            sb.append(markdown, copiedUpTo, start);
            copiedUpTo = end;

            if (diagramIndex >= MAX_PLANTUML_DIAGRAMS) {
                sb.append("<div class=\"alert alert-warning\">Diagram limit exceeded</div>");
                continue;
            }

            String fullMatch = markdown.substring(start, end);
            String diagramId = "uml-" + diagramIndex;
            // The raw request flag, not the derived one: the resolver applies the identical predicate to the same
            // testResults, which keeps its documented contract true for every caller.
            String resolvedSource = PlantUmlTaskColorResolver.resolve(fullMatch, testResults, allTestsPassed);
            // Strip <testid> wrappers inside PlantUML: the layout engine does not understand them.
            resolvedSource = TestReferenceParser.stripTestIdWrappers(resolvedSource);

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

            sb.append("<div class=\"artemis-diagram\" data-diagram-id=\"").append(diagramId).append("\">").append(SVG_PLACEHOLDER_PREFIX).append(placeholderToken).append('-')
                    .append(diagramIndex).append(SVG_PLACEHOLDER_SUFFIX).append("</div>");
            diagramIndex++;
        }
        sb.append(markdown, copiedUpTo, markdown.length());
        return sb.toString();
    }

    private String extractTasks(String markdown, @Nullable Map<Long, TestFeedbackInputDTO> testResults, Locale locale, boolean allPassed, Set<Long> feedbackTestIds) {
        Matcher matcher = TASK_PATTERN.matcher(markdown);
        StringBuilder sb = new StringBuilder();
        // Loop-invariant: the lookup only depends on the request's test results.
        TestFeedbackLookup lookup = TestFeedbackLookup.of(testResults);

        while (matcher.find()) {
            String taskName = matcher.group("name");
            String testsStr = matcher.group("tests");

            List<String> authoredRefs = TestReferenceParser.splitTestReferences(testsStr);
            // Separators alone are not a reference: "[task][Name](,)" names no test. The parser already drops blank
            // references, so deriving the flag from the parsed list keeps a task that references nothing from being
            // reported as successful by computeTaskTestStatus, which would otherwise find nothing failed and nothing
            // unexecuted among its (empty) ids.
            boolean hasTestRefs = !authoredRefs.isEmpty();

            List<Long> testIds = new ArrayList<>();
            int unresolvedRefs = 0;
            for (String ref : authoredRefs) {
                Long authoredId = TestReferenceParser.extractTestId(ref);
                if (authoredId != null) {
                    // An authored id stays authoritative even when no feedback carries it: it then counts as not executed.
                    testIds.add(authoredId);
                    continue;
                }
                TestFeedbackInputDTO named = lookup.resolve(ref);
                if (named != null) {
                    testIds.add(named.testId());
                }
                else {
                    unresolvedRefs++;
                }
            }

            String testStatus = computeTaskTestStatus(testIds, hasTestRefs, unresolvedRefs > 0, testResults, allPassed);
            int authoredCount = authoredRefs.size();
            // The counts are computed independently of the status, so they must follow the same signal. Otherwise an
            // all-passed task would render green while reporting every one of its tests as not executed, and the
            // success count would be the number of *resolvable* ids ("1 of 3 passed") rather than all authored tests:
            // without test results a name-only reference cannot resolve at all.
            int successCount = allPassed ? authoredCount : countPassedTests(testIds, testResults);
            int notExecutedCount = allPassed ? 0 : unresolvedRefs + countNotExecutedTests(testIds, testResults);

            // Only emit data-feedback when at least one referenced test actually has feedback. Authored ids are always
            // added to testIds, so `!testIds.isEmpty()` alone would emit an empty attribute for an empty (but present)
            // result map, which the CSS reads as "this task can be opened". This gates data-feedback only: the stats
            // line below is driven by whether the task's outcome is known at all, not by whether any of *this* task's
            // tests are among the results.
            boolean hasFeedback = testResults != null && testIds.stream().anyMatch(testResults::containsKey);
            if (hasFeedback) {
                testIds.stream().filter(testResults::containsKey).forEach(feedbackTestIds::add);
            }
            String taskHtml = buildTaskHtml(taskName, testIds, testStatus, successCount, authoredCount, notExecutedCount, testResults, hasFeedback, allPassed, locale);

            matcher.appendReplacement(sb, Matcher.quoteReplacement(taskHtml));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String buildTaskHtml(String taskName, List<Long> testIds, String testStatus, int successCount, int authoredCount, int notExecutedCount,
            @Nullable Map<Long, TestFeedbackInputDTO> testResults, boolean hasFeedback, boolean allPassed, Locale locale) {
        String testIdsStr = testIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        StringBuilder html = new StringBuilder();
        html.append("<span class=\"artemis-task artemis-task-").append(testStatus).append("\" data-task-name=\"").append(HtmlEscaper.escapeAttribute(taskName))
                .append("\" data-test-ids=\"").append(testIdsStr).append("\" data-test-status=\"").append(testStatus).append("\" data-authored-count=\"").append(authoredCount)
                .append("\" data-not-executed-count=\"").append(notExecutedCount).append("\"");

        if (hasFeedback) {
            // The ids this task can show feedback for, not the feedback itself. The payload is emitted once per
            // document (see buildDocumentFeedbackAttribute): a statement may repeat the same task marker thousands of
            // times within the request limit, and carrying a copy of every message on every marker turned a 100 KB
            // request into tens of megabytes of response.
            String feedbackIds = new LinkedHashSet<>(testIds).stream().filter(testResults::containsKey).map(String::valueOf).collect(Collectors.joining(","));
            html.append(" data-feedback=\"").append(feedbackIds).append("\"");
        }

        html.append(">");
        String iconClass = switch (testStatus) {
            case "success" -> "fa-check-circle artemis-icon-success";
            case "fail" -> "fa-times-circle artemis-icon-fail";
            default -> "fa-circle artemis-icon-no-result"; // not-executed, no-result, no-tests
        };
        html.append("<i class=\"fa ").append(iconClass).append("\"></i> ");
        html.append(HtmlEscaper.escapeText(taskName));
        // An all-passed task has no test results, but it does know its outcome, so it shows the same stats line
        // ("n of n tests passed") instead of the "no result" text a missing result would otherwise produce.
        if ((testResults != null || allPassed) && authoredCount > 0) {
            String statsText = messageSource.getMessage("exercise.problemStatement.taskStats", new Object[] { successCount, authoredCount }, locale);
            html.append(" <span class=\"artemis-task-stats\">").append(HtmlEscaper.escapeText(statsText)).append("</span>");
        }
        else if ("no-result".equals(testStatus)) {
            String text = messageSource.getMessage("exercise.problemStatement.noResult", null, locale);
            html.append(" <span class=\"artemis-task-no-result-text\">").append(HtmlEscaper.escapeText(text)).append("</span>");
        }
        else if ("no-tests".equals(testStatus)) {
            String text = messageSource.getMessage("exercise.problemStatement.noTests", null, locale);
            html.append(" <span class=\"artemis-task-no-result-text\">").append(HtmlEscaper.escapeText(text)).append("</span>");
        }
        html.append("</span><br>");
        return html.toString();
    }

    /**
     * The feedback payload of the whole document, keyed by test id, as a {@code data-feedback} attribute for the
     * container element. Emitted once rather than per task: the entry for a given test is identical wherever it
     * appears, and each one may carry a 5000-character message, so repeating it per task marker let a request within
     * the size limits expand into tens of megabytes. Task elements name the ids they can show and look them up here.
     *
     * @param feedbackTestIds the ids at least one task can show feedback for, in the order they were first authored
     * @param testResults     the request's test results
     * @return the attribute including its leading space, or an empty string when no task can show feedback
     */
    private String buildDocumentFeedbackAttribute(Set<Long> feedbackTestIds, @Nullable Map<Long, TestFeedbackInputDTO> testResults) {
        if (testResults == null || feedbackTestIds.isEmpty()) {
            return "";
        }
        Map<String, Map<String, Object>> byTestId = new LinkedHashMap<>();
        for (Long testId : feedbackTestIds) {
            TestFeedbackInputDTO detail = testResults.get(testId);
            if (detail == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", detail.testName());
            entry.put("passed", detail.passed());
            if (detail.credits() != null) {
                entry.put("credits", detail.credits());
            }
            if (detail.message() != null && !detail.message().isBlank()) {
                entry.put("message", detail.message());
            }
            byTestId.put(String.valueOf(testId), entry);
        }
        if (byTestId.isEmpty()) {
            return "";
        }
        try {
            return " data-feedback=\"" + HtmlEscaper.escapeAttribute(objectMapper.writeValueAsString(byTestId)) + "\"";
        }
        catch (JsonProcessingException e) {
            log.error("Failed to serialize feedback JSON", e);
            return "";
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

    private static String computeTaskTestStatus(List<Long> testIds, boolean hasTestRefs, boolean hasUnresolvedRefs, @Nullable Map<Long, TestFeedbackInputDTO> testResults,
            boolean allPassed) {
        if (!hasTestRefs) {
            // A task without references has nothing that could have passed, so it stays "no tests" even when the
            // request declares that every test passed.
            return "no-tests";
        }
        if (testResults == null) {
            // A successful result without any feedback means every test passed; without that signal nothing is known.
            return allPassed ? "success" : "no-result";
        }
        boolean anyFailed = false;
        // Unresolved (name-only) refs cannot be matched to feedback, so they count as not executed.
        boolean anyNotExecuted = hasUnresolvedRefs;
        for (Long testId : testIds) {
            TestFeedbackInputDTO detail = testResults.get(testId);
            switch (TestFeedbackLookup.outcomeOf(detail)) {
                case FAILED -> anyFailed = true;
                case NOT_EXECUTED -> anyNotExecuted = true;
                case PASSED -> {
                }
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
            if (TestFeedbackLookup.outcomeOf(testResults.get(testId)) == TestOutcome.PASSED) {
                success++;
            }
        }
        return success;
    }

    private static int countNotExecutedTests(List<Long> testIds, @Nullable Map<Long, TestFeedbackInputDTO> testResults) {
        int notExecuted = 0;
        for (Long testId : testIds) {
            TestFeedbackInputDTO detail = testResults == null ? null : testResults.get(testId);
            if (TestFeedbackLookup.outcomeOf(detail) == TestOutcome.NOT_EXECUTED) {
                notExecuted++;
            }
        }
        return notExecuted;
    }

    private String renderWithCommonMark(String markdown) {
        Node document = COMMONMARK_PARSER.parse(markdown);
        // Applied here rather than as a parser post processor: the alert marker has to be read from the markdown the
        // author wrote, and a post processor is handed the AST alone.
        GitHubAlertExtension.applyAlerts(document, markdown);
        String html = commonMarkRenderer.render(document);
        return Jsoup.clean(html, HTML_SAFELIST);
    }

    private record ResolvedMarkdownImage(String filename, String mime, Path realPath) {
    }

    private @Nullable ResolvedMarkdownImage resolveMarkdownImageSrc(String src, String absolutePrefix, Path basePath, Path baseReal) {
        String filename;
        if (src.startsWith(absolutePrefix)) {
            filename = src.substring(absolutePrefix.length());
        }
        else if (src.startsWith(MARKDOWN_FILE_API_PATH)) {
            filename = src.substring(MARKDOWN_FILE_API_PATH.length());
        }
        else {
            return null;
        }

        try {
            filename = URLDecoder.decode(filename.split("[?#]")[0], StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException e) {
            return null;
        }

        if (!FileUtil.sanitizeFilename(filename).equals(filename) || filename.contains("..")) {
            return null;
        }

        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String mime = INLINE_IMAGE_MIME_TYPES.get(ext);
        if (mime == null) {
            return null;
        }

        Path resolved = basePath.resolve(filename);
        try {
            Path fileReal = resolved.toRealPath();
            if (!fileReal.startsWith(baseReal)) {
                return null;
            }
            return new ResolvedMarkdownImage(filename, mime, fileReal);
        }
        catch (IOException e) {
            return null;
        }
    }

    private record InlineImage(String dataUri, long rawBytes) {
    }

    private String inlineMarkdownImages(String html) {
        var doc = Jsoup.parseBodyFragment(html);
        var images = doc.select("img[src]");
        if (images.isEmpty()) {
            return html;
        }

        Path basePath = FilePathConverter.getMarkdownFilePath();
        Path baseReal;
        try {
            baseReal = basePath.toRealPath();
        }
        catch (IOException e) {
            return html;
        }

        var cache = new HashMap<String, InlineImage>();
        int inlinedCount = 0;
        long emittedBytes = 0;
        String absolutePrefix = serverUrl + MARKDOWN_FILE_API_PATH;

        for (var img : images) {
            String src = img.attr("src");
            ResolvedMarkdownImage resolved = resolveMarkdownImageSrc(src, absolutePrefix, basePath, baseReal);
            if (resolved == null) {
                continue;
            }

            InlineImage cached = cache.get(resolved.filename());
            if (cached != null) {
                if (inlinedCount >= MAX_INLINE_IMAGES || emittedBytes + cached.rawBytes() > MAX_INLINE_TOTAL_SIZE) {
                    continue;
                }
                img.attr("src", cached.dataUri());
                emittedBytes += cached.rawBytes();
                inlinedCount++;
                continue;
            }

            if (inlinedCount >= MAX_INLINE_IMAGES) {
                continue;
            }

            try {
                long size = Files.size(resolved.realPath());
                if (size > MAX_INLINE_FILE_SIZE || emittedBytes + size > MAX_INLINE_TOTAL_SIZE) {
                    continue;
                }
                byte[] bytes = fileService.getFileForPath(resolved.realPath());
                if (bytes == null) {
                    continue;
                }
                String dataUri = "data:" + resolved.mime() + ";base64," + Base64.getEncoder().encodeToString(bytes);
                img.attr("src", dataUri);
                cache.put(resolved.filename(), new InlineImage(dataUri, size));
                emittedBytes += size;
                inlinedCount++;
            }
            catch (IOException e) {
                log.warn("Could not inline markdown image {}: {}", resolved.filename(), e.getMessage());
            }
        }

        doc.outputSettings().prettyPrint(false);
        return doc.body().html();
    }

    /**
     * Replaces every code construct with an opaque placeholder so downstream passes (PlantUML, math, tasks, testid
     * stripping) skip their contents.
     * <p>
     * The spans come from CommonMark itself rather than from a regex. A regex has to enumerate the syntaxes it knows,
     * and the ones it forgets silently become expandable: a {@code [task]} inside a tilde fence, an indented block or a
     * multi-backtick inline span would be rewritten into generated markup even though the author wrote it to be
     * displayed. The parser recognizes exactly what it will later render as code, which removes that class of gap.
     */
    private static String maskCodeBlocks(String markdown, List<String> codeBlocks) {
        List<int[]> ranges = new ArrayList<>();
        SPAN_PARSER.parse(markdown).accept(new AbstractVisitor() {

            @Override
            public void visit(FencedCodeBlock fencedCodeBlock) {
                addRange(fencedCodeBlock);
            }

            @Override
            public void visit(IndentedCodeBlock indentedCodeBlock) {
                addRange(indentedCodeBlock);
            }

            @Override
            public void visit(Code code) {
                addRange(code);
            }

            private void addRange(Node node) {
                List<SourceSpan> spans = node.getSourceSpans();
                if (spans.isEmpty()) {
                    return;
                }
                SourceSpan last = spans.getLast();
                ranges.add(new int[] { spans.getFirst().getInputIndex(), last.getInputIndex() + last.getLength() });
            }
        });

        // A code span can only ever sit inside a code block, never the other way round, and the visitor reports the
        // block before descending. Sorting by start and dropping anything that begins inside the previous range keeps
        // the outermost one, so no placeholder is ever nested into another.
        ranges.sort(Comparator.comparingInt(range -> range[0]));
        StringBuilder masked = new StringBuilder();
        int copiedUpTo = 0;
        for (int[] range : ranges) {
            if (range[0] < copiedUpTo) {
                continue;
            }
            masked.append(markdown, copiedUpTo, range[0]);
            masked.append(CODE_BLOCK_PLACEHOLDER_PREFIX).append(codeBlocks.size()).append(CODE_BLOCK_PLACEHOLDER_SUFFIX);
            codeBlocks.add(markdown.substring(range[0], range[1]));
            copiedUpTo = range[1];
        }
        masked.append(markdown, copiedUpTo, markdown.length());
        return masked.toString();
    }

    private static String restoreCodeBlocks(String markdown, List<String> codeBlocks) {
        return IndexedPlaceholders.replaceAll(markdown, CODE_BLOCK_PLACEHOLDER_PREFIX, CODE_BLOCK_PLACEHOLDER_SUFFIX, codeBlocks.size(), codeBlocks::get);
    }

    private static Safelist buildSafelist() {
        Safelist safelist = Safelist.relaxed();
        // The GFM strikethrough extension renders `~~text~~` as <del>, which jsoup's relaxed safelist does not carry.
        // Without this the tag is dropped and the text renders unmarked instead of struck through.
        safelist.addTags("del");
        safelist.addAttributes("div", "class", "data-diagram-id", "data-result", "data-feedback");
        safelist.addAttributes("span", "class", "data-task-name", "data-test-ids", "data-test-status", "data-feedback", "data-svg-index", "data-formula-index",
                "data-authored-count", "data-not-executed-count", "data-alert-type");
        safelist.addAttributes("code", "class");
        safelist.addAttributes("pre", "class");
        safelist.addAttributes("p", "class");
        safelist.addAttributes("i", "class");
        return safelist;
    }

    private String computeContentHash(String html, @Nullable String interactiveScript, boolean inlineImages) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(RENDERER_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(html.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            if (interactiveScript != null) {
                digest.update(interactiveScript.getBytes(StandardCharsets.UTF_8));
            }
            digest.update((byte) 0);
            digest.update((inlineImages ? "INLINE" : "URL").getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * A fresh token for this render's diagram markers, as lowercase hex.
     * <p>
     * Hex so it needs no escaping inside the attribute it lives in, and so it cannot terminate the marker early.
     * It does not have to stay secret after the response is written; it only has to be unknowable to whoever wrote
     * the markdown, which is guaranteed because it is drawn after the request arrives.
     *
     * @return the token, without separators
     */
    private static String randomPlaceholderToken() {
        byte[] bytes = new byte[PLACEHOLDER_TOKEN_BYTES];
        PLACEHOLDER_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
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
        for (String key : List.of("feedbackTitle", "close", "score", "points", "of", "submitted", "commit", "failedTests", "passedTests", "notExecutedTests")) {
            i18n.put(key, messageSource.getMessage(prefix + key, null, key, locale));
        }
        try {
            // Escape the slash in any "</" sequence: the JSON is emitted inside a <script> element, and an unescaped
            // "</script>" in a translation would terminate it. "<\\/" is an equivalent JSON escape. The sequence holds no
            // letter, so this covers "</ScRiPt>" as well - the parser only needs the "</" to start looking for a tag name.
            String json = objectMapper.writeValueAsString(i18n).replace("</", "<\\/");
            return "var __i18n = " + json + ";\n" + INTERACTIVE_JS;
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
                case "problem-statement-css/embedded.css" -> "embedded styling is missing; rendered output will inherit the consumer's CSS only";
                case "problem-statement-css/dark-mode.css" -> "dark mode overrides are unavailable; dark-mode requests will fall back to light styling";
                default -> "asset not loaded";
            };
            log.error("Could not load classpath resource {} — consequence: {}", path, consequence, e);
            return null;
        }
    }
}
