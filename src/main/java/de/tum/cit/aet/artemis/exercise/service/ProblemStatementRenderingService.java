package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
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

    private static final String RENDERER_VERSION = "1.0.0";

    private static final Pattern PLANTUML_PATTERN = Pattern.compile("@startuml([^@]*)@enduml");

    private static final Pattern TASK_PATTERN = Pattern
            .compile("\\[task]\\[(?<name>[^\\[\\]]+)]\\((?<tests>(?:[^(),]+(?:\\([^()]*\\)[^(),]*)?(?:,[^(),]+(?:\\([^()]*\\)[^(),]*)?)*)?)\\)");

    private static final Pattern TESTID_PATTERN = Pattern.compile("<testid>(\\d+)</testid>");

    private static final Pattern KATEX_INLINE_PATTERN = Pattern.compile("(?<!\\\\)\\$(?!\\$)(.+?)(?<!\\\\)\\$");

    private static final Pattern KATEX_BLOCK_PATTERN = Pattern.compile("\\$\\$[\\s\\S]+?\\$\\$");

    private static final Pattern KATEX_BEGIN_PATTERN = Pattern.compile("\\\\begin\\{");

    private static final Pattern FENCED_CODE_PATTERN = Pattern.compile("```(\\w+)");

    private static final String SVG_PLACEHOLDER_PREFIX = "<span class=\"artemis-svg-placeholder\" data-svg-index=\"";

    private static final String SVG_PLACEHOLDER_SUFFIX = "\"></span>";

    private final PlantUmlService plantUmlService;

    private final String serverUrl;

    public ProblemStatementRenderingService(PlantUmlService plantUmlService, @Value("${server.url}") String serverUrl) {
        this.plantUmlService = plantUmlService;
        this.serverUrl = serverUrl;
    }

    /**
     * Renders a problem statement for the given exercise into pre-rendered HTML with structured metadata.
     *
     * @param exercise      the exercise whose problem statement should be rendered
     * @param selfContained if true, PlantUML diagrams are inlined as SVG; otherwise URLs are provided
     * @return the rendered problem statement DTO
     */
    public RenderedProblemStatementDTO render(Exercise exercise, boolean selfContained) {
        String problemStatement = exercise.getProblemStatement();
        if (problemStatement == null || problemStatement.isBlank()) {
            return new RenderedProblemStatementDTO("", computeHash(""), RENDERER_VERSION, new AssetRequirementsDTO("absent", "absent", selfContained ? "inline" : "url"), List.of(),
                    List.of());
        }

        String processedMarkdown = problemStatement;

        // Step 1: Extract and replace PlantUML blocks
        List<DiagramRenderInfoDTO> diagrams = new ArrayList<>();
        List<String> inlineSvgs = new ArrayList<>();
        processedMarkdown = extractPlantUmlDiagrams(processedMarkdown, exercise.getId(), selfContained, diagrams, inlineSvgs);

        // Step 2: Extract and replace task markers
        List<TaskRenderInfoDTO> tasks = new ArrayList<>();
        processedMarkdown = extractTasks(processedMarkdown, tasks);

        // Step 3: Detect asset requirements
        boolean needsKatex = KATEX_INLINE_PATTERN.matcher(processedMarkdown).find() || KATEX_BLOCK_PATTERN.matcher(processedMarkdown).find()
                || KATEX_BEGIN_PATTERN.matcher(processedMarkdown).find();
        boolean needsHighlighting = FENCED_CODE_PATTERN.matcher(processedMarkdown).find();
        String diagramMode = selfContained ? "inline" : "url";

        // Step 4: Render markdown to HTML
        var extensions = List.of(TablesExtension.create(), StrikethroughExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).attributeProviderFactory(ctx -> new MarkdownRelativeToAbsolutePathAttributeProvider(serverUrl))
                .build();
        String html = renderer.render(parser.parse(processedMarkdown));

        // Step 5: Sanitize HTML (without SVGs — those are injected after)
        html = sanitizeHtml(html);

        // Step 6: Inject PlantUML SVGs after sanitization.
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

        // Step 7: Wrap in container div
        html = "<div class=\"artemis-problem-statement\">" + html + "</div>";

        // Step 8: Strip any remaining testid tags
        html = html.replace("<testid>", "").replace("</testid>", "");

        // Step 9: Compute content hash and build DTO
        String contentHash = computeHash(html);
        var assets = new AssetRequirementsDTO(needsKatex ? "required" : "absent", needsHighlighting ? "required" : "absent", diagramMode);

        return new RenderedProblemStatementDTO(html, contentHash, RENDERER_VERSION, assets, tasks, diagrams);
    }

    private String extractPlantUmlDiagrams(String markdown, long exerciseId, boolean selfContained, List<DiagramRenderInfoDTO> diagrams, List<String> inlineSvgs) {
        Matcher matcher = PLANTUML_PATTERN.matcher(markdown);
        StringBuilder sb = new StringBuilder();
        int diagramIndex = 0;

        while (matcher.find()) {
            String fullMatch = matcher.group(0);
            String diagramId = "uml-" + diagramIndex;
            String sourceHash = computeHash(fullMatch);

            // Extract test references from testsColor()
            List<Long> testIds = extractTestsColorIds(fullMatch);

            // Build SVG URL
            String svgUrl = serverUrl + "/api/programming/plantuml/svg?plantuml=" + java.net.URLEncoder.encode(fullMatch, StandardCharsets.UTF_8);

            String inlineSvg = null;
            if (selfContained) {
                try {
                    inlineSvg = plantUmlService.generateSvg(fullMatch, false);
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
     * Extracts test references from testsColor() calls in PlantUML source.
     * Currently returns an empty list because testsColor() references test NAMES (strings),
     * not numeric IDs. Resolving names to database IDs requires exercise-context lookup,
     * which is deferred to phase 2.
     *
     * @param plantUmlSource the PlantUML diagram source
     * @return an empty list (diagram test ID resolution is not yet implemented)
     */
    private List<Long> extractTestsColorIds(String plantUmlSource) {
        return List.of();
    }

    private String extractTasks(String markdown, List<TaskRenderInfoDTO> tasks) {
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

            String testIdsStr = testIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
            String replacement = "<span class=\"artemis-task\" data-task-name=\"" + escapeHtmlAttribute(taskName) + "\" data-test-ids=\"" + testIdsStr + "\">"
                    + escapeHtml(taskName) + "</span>";

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            tasks.add(new TaskRenderInfoDTO(taskName, testIds));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String sanitizeHtml(String html) {
        Safelist safelist = Safelist.relaxed();
        // Allow data attributes on div and span for our custom elements
        safelist.addAttributes("div", "class", "data-diagram-id", "data-svg-url");
        safelist.addAttributes("span", "class", "data-task-name", "data-test-ids", "data-svg-index");
        // Allow class on code for syntax highlighting
        safelist.addAttributes("code", "class");
        // Allow class on pre for styling
        safelist.addAttributes("pre", "class");
        // Preserve HTML comments (used as SVG placeholders)
        // jsoup strips comments by default — we handle SVG injection via string replacement after sanitization
        return Jsoup.clean(html, safelist);
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
