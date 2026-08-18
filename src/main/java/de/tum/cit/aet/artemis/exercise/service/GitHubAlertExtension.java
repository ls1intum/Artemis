package de.tum.cit.aet.artemis.exercise.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.Extension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Block;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;
import org.jspecify.annotations.Nullable;

/**
 * CommonMark extension that renders GitHub-style alerts, the server-side counterpart of the client's
 * {@code markdownItGitHubAlerts} plugin (app/foundation/util/markdown-github-alerts.plugin.ts).
 * <p>
 * A blockquote whose first line is {@code [!NOTE]}, {@code [!TIP]}, {@code [!IMPORTANT]}, {@code [!WARNING]} or
 * {@code [!CAUTION]} (case-insensitive, optionally followed by a custom title) becomes
 *
 * <pre>{@code
 * <div class="markdown-alert markdown-alert-note">
 *   <p class="markdown-alert-title"><svg class="octicon …"></svg>Note</p>
 *   …remaining blockquote content…
 * </div>
 * }</pre>
 *
 * Titles are deliberately <em>not</em> localized: the client uses the author's custom title when present and the
 * capitalized English type otherwise, so a localized server title would create a divergence rather than close one.
 * <p>
 * A {@link PostProcessor} alone cannot achieve this, because it can only rewrite the AST, not the tag the core
 * renderer emits for a {@link BlockQuote}. The blockquote is therefore replaced by a {@link GitHubAlertBlock} whose
 * own {@link NodeRenderer} emits the {@code <div>}.
 * <p>
 * The octicons cannot be emitted here either: the rendering pipeline runs its HTML through a jsoup safelist that
 * strips SVG markup. The renderer emits an icon placeholder instead, which {@link #injectIcons(String)} replaces
 * with the real SVG after sanitization, mirroring how the PlantUML diagrams are re-injected.
 */
public final class GitHubAlertExtension implements Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

    private static final String CLASS_PREFIX = "markdown-alert";

    /**
     * The alert marker at the start of a blockquote, including the client's optional leading backslash. The marker is
     * matched against the markdown as authored, so the escape in {@code \[!NOTE]} is still present here, exactly as
     * it is in the inline content the client matches against; both spellings produce an alert on both sides.
     */
    private static final Pattern ALERT_PATTERN = Pattern.compile("^\\\\?\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]([^\\n\\r]*)", Pattern.CASE_INSENSITIVE);

    private static final String ICON_PLACEHOLDER_PREFIX = "<span class=\"" + CLASS_PREFIX + "-icon\" data-alert-type=\"";

    private static final String ICON_PLACEHOLDER_SUFFIX = "\"></span>";

    /** The fixed octicon per alert type, byte-identical to the client's {@code ALERT_ICONS}. */
    private static final Map<String, String> ALERT_ICONS = Map.of("note",
            "<svg class=\"octicon octicon-info mr-2\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" height=\"16\" aria-hidden=\"true\"><path d=\"M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm8-6.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13ZM6.5 7.75A.75.75 0 0 1 7.25 7h1a.75.75 0 0 1 .75.75v2.75h.25a.75.75 0 0 1 0 1.5h-2a.75.75 0 0 1 0-1.5h.25v-2h-.25a.75.75 0 0 1-.75-.75ZM8 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z\"></path></svg>",
            "tip",
            "<svg class=\"octicon octicon-light-bulb mr-2\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" height=\"16\" aria-hidden=\"true\"><path d=\"M8 1.5c-2.363 0-4 1.69-4 3.75 0 .984.424 1.625.984 2.304l.214.253c.223.264.47.556.673.848.284.411.537.896.621 1.49a.75.75 0 0 1-1.484.211c-.04-.282-.163-.547-.37-.847a8.456 8.456 0 0 0-.542-.68c-.084-.1-.173-.205-.268-.32C3.201 7.75 2.5 6.766 2.5 5.25 2.5 2.31 4.863 0 8 0s5.5 2.31 5.5 5.25c0 1.516-.701 2.5-1.328 3.259-.095.115-.184.22-.268.319-.207.245-.383.453-.541.681-.208.3-.33.565-.37.847a.751.751 0 0 1-1.485-.212c.084-.593.337-1.078.621-1.489.203-.292.45-.584.673-.848.075-.088.147-.173.213-.253.561-.679.985-1.32.985-2.304 0-2.06-1.637-3.75-4-3.75ZM5.75 12h4.5a.75.75 0 0 1 0 1.5h-4.5a.75.75 0 0 1 0-1.5ZM6 15.25a.75.75 0 0 1 .75-.75h2.5a.75.75 0 0 1 0 1.5h-2.5a.75.75 0 0 1-.75-.75Z\"></path></svg>",
            "important",
            "<svg class=\"octicon octicon-report mr-2\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" height=\"16\" aria-hidden=\"true\"><path d=\"M0 1.75C0 .784.784 0 1.75 0h12.5C15.216 0 16 .784 16 1.75v9.5A1.75 1.75 0 0 1 14.25 13H8.06l-2.573 2.573A1.458 1.458 0 0 1 3 14.543V13H1.75A1.75 1.75 0 0 1 0 11.25Zm1.75-.25a.25.25 0 0 0-.25.25v9.5c0 .138.112.25.25.25h2a.75.75 0 0 1 .75.75v2.19l2.72-2.72a.749.749 0 0 1 .53-.22h6.5a.25.25 0 0 0 .25-.25v-9.5a.25.25 0 0 0-.25-.25Zm7 2.25v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 9a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z\"></path></svg>",
            "warning",
            "<svg class=\"octicon octicon-alert mr-2\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" height=\"16\" aria-hidden=\"true\"><path d=\"M6.457 1.047c.659-1.234 2.427-1.234 3.086 0l6.082 11.378A1.75 1.75 0 0 1 14.082 15H1.918a1.75 1.75 0 0 1-1.543-2.575Zm1.763.707a.25.25 0 0 0-.44 0L1.698 13.132a.25.25 0 0 0 .22.368h12.164a.25.25 0 0 0 .22-.368Zm.53 3.996v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 11a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z\"></path></svg>",
            "caution",
            "<svg class=\"octicon octicon-stop mr-2\" viewBox=\"0 0 16 16\" version=\"1.1\" width=\"16\" height=\"16\" aria-hidden=\"true\"><path d=\"M4.47.22A.749.749 0 0 1 5 0h6c.199 0 .389.079.53.22l4.25 4.25c.141.14.22.331.22.53v6a.749.749 0 0 1-.22.53l-4.25 4.25A.749.749 0 0 1 11 16H5a.749.749 0 0 1-.53-.22L.22 11.53A.749.749 0 0 1 0 11V5c0-.199.079-.389.22-.53Zm.84 1.28L1.5 5.31v5.38l3.81 3.81h5.38l3.81-3.81V5.31L10.69 1.5ZM8 4a.75.75 0 0 1 .75.75v3.5a.75.75 0 0 1-1.5 0v-3.5A.75.75 0 0 1 8 4Zm0 8a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z\"></path></svg>");

    private GitHubAlertExtension() {
    }

    /**
     * @return an extension instance to register on both the CommonMark parser and the HTML renderer
     */
    public static Extension create() {
        return new GitHubAlertExtension();
    }

    @Override
    public void extend(Parser.Builder parserBuilder) {
        // No post processor: a post processor only ever sees the AST, and the marker has to be matched against the
        // markdown the author wrote (see AlertVisitor). Alerts are applied by applyAlerts(...) instead, which the
        // caller invokes with the source it just parsed.
    }

    /**
     * Turns every alert blockquote in the parsed document into a {@link GitHubAlertBlock}.
     *
     * @param document the parsed document, from a parser built with {@code IncludeSourceSpans.BLOCKS_AND_INLINES}
     * @param source   the markdown that document was parsed from, used to read the marker line as authored
     */
    public static void applyAlerts(Node document, String source) {
        document.accept(new AlertVisitor(source));
    }

    @Override
    public void extend(HtmlRenderer.Builder rendererBuilder) {
        rendererBuilder.nodeRendererFactory(AlertNodeRenderer::new);
    }

    /**
     * Replaces every alert icon placeholder with its octicon SVG.
     * <p>
     * Must run <em>after</em> the HTML has been sanitized: the jsoup safelist does not allow SVG elements, so an
     * icon emitted during rendering would be stripped. The placeholder is a plain {@code <span>} with a class and
     * a {@code data-alert-type} attribute, both of which the safelist keeps.
     *
     * @param html the sanitized HTML
     * @return the HTML with the octicons inlined
     */
    public static String injectIcons(String html) {
        if (!html.contains(ICON_PLACEHOLDER_PREFIX)) {
            return html;
        }
        String result = html;
        for (Map.Entry<String, String> icon : ALERT_ICONS.entrySet()) {
            result = result.replace(ICON_PLACEHOLDER_PREFIX + icon.getKey() + ICON_PLACEHOLDER_SUFFIX, icon.getValue());
        }
        return result;
    }

    /** The block that takes the place of an alert blockquote and carries the resolved type and title. */
    private static final class GitHubAlertBlock extends CustomBlock {

        private final String type;

        private final String title;

        private GitHubAlertBlock(String type, String title) {
            this.type = type;
            this.title = title;
        }
    }

    private static final class AlertVisitor extends AbstractVisitor {

        private final String source;

        private AlertVisitor(String source) {
            this.source = source;
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            // Deliberately no visitChildren: the client scans the flat token stream and skips from a
            // blockquote_open to the first blockquote_close, so only the outermost blockquote of a nesting is ever
            // considered. `> > [!NOTE]` therefore turns the OUTER quote into the alert and keeps the inner one as
            // a blockquote inside it, and a nested marker under a non-matching outer quote stays a blockquote.
            Block markerBlock = firstInlineHost(blockQuote);
            if (markerBlock == null) {
                return;
            }
            List<Node> markerLine = firstLineNodes(markerBlock);
            // Matched against the line as authored, not against the flattened inline text. The client applies its
            // regex to markdown-it's raw inline content, so `> **[!NOTE]**` starts with `**` there and stays a
            // blockquote; flattening first would hide those delimiters and turn it into an alert. The same reading
            // keeps a custom title's delimiters, which the client also carries through verbatim.
            String authoredLine = authoredFirstLine(markerLine, source);
            if (authoredLine == null) {
                return;
            }
            Matcher matcher = ALERT_PATTERN.matcher(authoredLine);
            if (!matcher.lookingAt()) {
                return;
            }
            String type = matcher.group(1).toLowerCase(Locale.ROOT);
            String customTitle = matcher.group(2).trim();

            // The title group is greedy up to the end of the line, so the match always covers the whole first
            // line. Dropping that line and the line break behind it is the AST equivalent of the client slicing
            // the matched prefix off the inline token and trimming the leading newline.
            markerLine.forEach(Node::unlink);
            Node lineBreak = markerBlock.getFirstChild();
            if (lineBreak instanceof SoftLineBreak || lineBreak instanceof HardLineBreak) {
                lineBreak.unlink();
            }

            GitHubAlertBlock alert = new GitHubAlertBlock(type, customTitle.isEmpty() ? capitalize(type) : customTitle);
            Node child = blockQuote.getFirstChild();
            while (child != null) {
                // appendChild unlinks the node from the blockquote, so the successor has to be read first.
                Node next = child.getNext();
                alert.appendChild(child);
                child = next;
            }
            blockQuote.insertBefore(alert);
            blockQuote.unlink();
        }
    }

    private static final class AlertNodeRenderer implements NodeRenderer {

        private final HtmlNodeRendererContext context;

        private AlertNodeRenderer(HtmlNodeRendererContext context) {
            this.context = context;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(GitHubAlertBlock.class);
        }

        @Override
        public void render(Node node) {
            GitHubAlertBlock alert = (GitHubAlertBlock) node;
            HtmlWriter html = context.getWriter();

            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("class", CLASS_PREFIX + " " + CLASS_PREFIX + "-" + alert.type);

            html.line();
            html.tag("div", context.extendAttributes(alert, "div", attributes));
            // The title paragraph is synthetic and has no node of its own, so no attribute provider applies to it.
            html.tag("p", Map.of("class", CLASS_PREFIX + "-title"));
            html.raw(ICON_PLACEHOLDER_PREFIX + alert.type + ICON_PLACEHOLDER_SUFFIX);
            html.text(alert.title);
            html.tag("/p");
            html.line();
            Node child = alert.getFirstChild();
            while (child != null) {
                Node next = child.getNext();
                context.render(child);
                child = next;
            }
            html.tag("/div");
            html.line();
        }
    }

    /**
     * The marker line exactly as the author wrote it, or {@code null} when the parser reported no span for it.
     * <p>
     * Spanned across the inline nodes rather than taken from the block's own first line: a block span covers the whole
     * source line including its opening marker, so a heading would arrive here as {@code # [!NOTE]} and never match,
     * while the client sees only the inline content the heading holds.
     */
    private static @Nullable String authoredFirstLine(List<Node> markerLine, String source) {
        if (markerLine.isEmpty()) {
            return null;
        }
        List<SourceSpan> firstSpans = markerLine.getFirst().getSourceSpans();
        List<SourceSpan> lastSpans = markerLine.getLast().getSourceSpans();
        if (firstSpans.isEmpty() || lastSpans.isEmpty()) {
            return null;
        }
        SourceSpan start = firstSpans.getFirst();
        SourceSpan end = lastSpans.getLast();
        return source.substring(start.getInputIndex(), end.getInputIndex() + end.getLength());
    }

    /**
     * The first block inside the quote that carries inline content, in document order.
     * <p>
     * This mirrors the client, which takes the first {@code inline} token of the blockquote. markdown-it emits such a
     * token for every block holding inline content, a heading as much as a paragraph, so looking only for the first
     * paragraph would disagree in both directions: {@code > # Intro} followed by a marker line would become an alert
     * here while staying a blockquote there, and a marker written as {@code > # [!NOTE]} would become an alert there
     * while staying a blockquote here. The toggle would then change authored content.
     * <p>
     * A block holds inline content exactly when its first child is not itself a block, which is what separates a
     * paragraph or heading from a nested quote or list, and what leaves childless blocks such as a fenced code block
     * out, matching the non-{@code inline} tokens the client skips.
     */
    private static @Nullable Block firstInlineHost(Node node) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Block block && block.getFirstChild() != null && !(block.getFirstChild() instanceof Block)) {
                return block;
            }
            Block nested = firstInlineHost(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    /** The block's inline children up to, but excluding, its first line break. */
    private static List<Node> firstLineNodes(Block block) {
        List<Node> nodes = new ArrayList<>();
        for (Node child = block.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                break;
            }
            nodes.add(child);
        }
        return nodes;
    }

    private static String capitalize(String type) {
        return type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1);
    }
}
