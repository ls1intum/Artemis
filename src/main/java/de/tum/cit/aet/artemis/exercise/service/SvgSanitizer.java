package de.tum.cit.aet.artemis.exercise.service;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.XmlDeclaration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defense-in-depth sanitizer for server-generated PlantUML SVG.
 * <p>
 * <strong>Not a general-purpose SVG sanitizer.</strong> This class relies on PlantUML being the only
 * SVG source (we render PlantUML server-side, then feed its output through here before embedding it
 * in the rendered HTML). It denies known-dangerous patterns rather than enforcing a strict allowlist,
 * because an allowlist tight enough to block arbitrary SVG would also break legitimate PlantUML output.
 * <p>
 * Hardening applied:
 * <ul>
 * <li>Active elements are stripped ({@code script}, {@code foreignObject}, {@code use}, {@code image},
 * and SMIL animation elements like {@code animate}, {@code set}, {@code animateTransform},
 * {@code animateMotion}).</li>
 * <li>All {@code on*} event handler attributes are removed.</li>
 * <li>{@code href} / {@code xlink:href} are scheme-allowlisted on {@code <a>}; other elements only accept
 * local fragment refs.</li>
 * <li>Control characters in URI attributes are stripped before scheme checks to prevent bypasses such as
 * {@code java\tscript:}.</li>
 * <li>URL-bearing presentation attributes ({@code fill}, {@code stroke}, ...) only accept local fragment URLs.</li>
 * <li>Inline {@code style} attributes and {@code <style>} text content are checked for {@code url()} that is
 * not a local fragment, {@code expression(}, {@code @import}, {@code -moz-binding}, CSS escape sequences,
 * and comment-obfuscated variants of those tokens.</li>
 * <li>After serialization the output is re-parsed as an HTML body fragment and validated again; anything
 * dangerous surviving the parser differential causes the whole SVG to be rejected (returns {@code null}).</li>
 * </ul>
 */
public final class SvgSanitizer {

    private static final Logger log = LoggerFactory.getLogger(SvgSanitizer.class);

    /** Elements that are never safe in SVG for our threat model. */
    private static final Set<String> DENIED_ELEMENTS = Set.of("script", "foreignobject", "use", "image", "animate", "animatetransform", "animatemotion", "set");

    /** URI schemes allowed on {@code <a>} elements. All other schemes are stripped. */
    private static final Set<String> ALLOWED_HREF_SCHEMES = Set.of("http", "https", "mailto");

    /** Presentation attributes that can contain {@code url(...)} references. */
    private static final Set<String> URL_BEARING_ATTRIBUTES = Set.of("fill", "stroke", "filter", "clip-path", "mask", "marker-start", "marker-mid", "marker-end");

    /** Matches {@code url(} at any position in an attribute/CSS value. */
    private static final Pattern URL_REFERENCE_PATTERN = Pattern.compile("url\\s*\\(", Pattern.CASE_INSENSITIVE);

    /** Matches exactly one local fragment reference: {@code url(#id)}. */
    private static final Pattern LOCAL_URL_REF_PATTERN = Pattern.compile("^\\s*url\\s*\\(\\s*#[^)]+\\)\\s*$", Pattern.CASE_INSENSITIVE);

    /** Dangerous CSS patterns anywhere in a style value or {@code <style>} block. */
    private static final Pattern DANGEROUS_CSS_PATTERN = Pattern.compile("expression\\s*\\(|@import|(-moz-binding)", Pattern.CASE_INSENSITIVE);

    /** Matches {@code /* ... *}{@code /} CSS comments, non-greedy, across lines. */
    private static final Pattern CSS_COMMENT_PATTERN = Pattern.compile("/\\*[\\s\\S]*?\\*/");

    /** Extracts a URI scheme from an attribute value (letters followed by {@code :}). */
    private static final Pattern URI_SCHEME_PATTERN = Pattern.compile("^\\s*([a-zA-Z][a-zA-Z0-9+.\\-]*):");

    /** Matches control characters we strip from URI attribute values before scheme inspection. */
    private static final Pattern URI_CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");

    private SvgSanitizer() {
    }

    /**
     * Sanitizes the given SVG source. Returns {@code null} if the output fails the post-sanitization
     * HTML-reparse check, indicating a parser differential that could execute in the browser.
     *
     * @param svg the raw SVG source, typically produced by PlantUML
     * @return the sanitized SVG, or {@code null} if it could not be safely rendered
     */
    public static @Nullable String sanitize(String svg) {
        Document doc = Jsoup.parse(svg, "", org.jsoup.parser.Parser.xmlParser());
        removeNonElementNodes(doc);
        sanitizeElements(doc);
        String serialized = doc.html();
        if (failsHtmlReparseCheck(serialized)) {
            log.warn("SVG sanitizer rejected output that still contained dangerous constructs after HTML reparse");
            return null;
        }
        return serialized;
    }

    private static void removeNonElementNodes(Node parent) {
        for (Node child : new ArrayList<>(parent.childNodes())) {
            if (child instanceof XmlDeclaration || child instanceof DocumentType || child instanceof Comment) {
                child.remove();
            }
        }
    }

    private static void sanitizeElements(Element parent) {
        for (Element child : new ArrayList<>(parent.children())) {
            String tag = child.tagName().toLowerCase(Locale.ROOT);

            if (DENIED_ELEMENTS.contains(tag)) {
                log.warn("SVG sanitizer removed disallowed element: <{}>", tag);
                child.remove();
                continue;
            }

            for (Attribute attr : new ArrayList<>(child.attributes().asList())) {
                String attrKey = attr.getKey().toLowerCase(Locale.ROOT);

                if (attrKey.startsWith("on")) {
                    child.removeAttr(attr.getKey());
                    continue;
                }

                if ("href".equals(attrKey) || "xlink:href".equals(attrKey)) {
                    if (!isSafeHref(attr.getValue(), tag)) {
                        child.removeAttr(attr.getKey());
                    }
                    continue;
                }

                if (URL_BEARING_ATTRIBUTES.contains(attrKey)) {
                    String value = attr.getValue();
                    if (URL_REFERENCE_PATTERN.matcher(value).find() && !LOCAL_URL_REF_PATTERN.matcher(value).matches()) {
                        child.removeAttr(attr.getKey());
                    }
                    continue;
                }

                if ("style".equals(attrKey) && !isCssSafe(attr.getValue())) {
                    child.removeAttr(attr.getKey());
                }
            }

            // In XML parser mode, <style> content is stored as text nodes (not DataNodes), so use html() for the raw content.
            if ("style".equals(tag)) {
                String cssContent = child.html();
                if (!isCssSafe(cssContent)) {
                    child.html("");
                    log.warn("SVG sanitizer cleared dangerous CSS content from <style> element");
                }
            }

            sanitizeElements(child);
        }
    }

    private static boolean isSafeHref(String rawValue, String elementTag) {
        String value = URI_CONTROL_CHARS.matcher(rawValue == null ? "" : rawValue).replaceAll("").strip();
        if (value.isEmpty()) {
            return false;
        }

        if (value.startsWith("#")) {
            return true;
        }

        // Relative paths without a scheme are acceptable on <a>; rejected elsewhere.
        Matcher schemeMatcher = URI_SCHEME_PATTERN.matcher(value);
        if (!schemeMatcher.find()) {
            return "a".equals(elementTag);
        }

        if (!"a".equals(elementTag)) {
            // Non-<a> elements must not point outside the SVG itself.
            return false;
        }

        String scheme = schemeMatcher.group(1).toLowerCase(Locale.ROOT);
        return ALLOWED_HREF_SCHEMES.contains(scheme);
    }

    private static boolean isCssSafe(String css) {
        if (css == null || css.isEmpty()) {
            return true;
        }
        String withoutComments = CSS_COMMENT_PATTERN.matcher(css).replaceAll("");
        if (DANGEROUS_CSS_PATTERN.matcher(withoutComments).find()) {
            return false;
        }
        // PlantUML never emits CSS escape sequences; treat them as an obfuscation attempt.
        if (withoutComments.indexOf('\\') >= 0) {
            return false;
        }
        return !containsExternalUrlReference(withoutComments);
    }

    private static boolean containsExternalUrlReference(String css) {
        Matcher urlMatcher = URL_REFERENCE_PATTERN.matcher(css);
        while (urlMatcher.find()) {
            int parenOpen = css.indexOf('(', urlMatcher.start());
            int parenClose = css.indexOf(')', parenOpen);
            if (parenClose > parenOpen) {
                String urlValue = css.substring(parenOpen + 1, parenClose).strip();
                if ((urlValue.startsWith("'") && urlValue.endsWith("'")) || (urlValue.startsWith("\"") && urlValue.endsWith("\""))) {
                    urlValue = urlValue.substring(1, urlValue.length() - 1).strip();
                }
                if (!urlValue.startsWith("#")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Re-parses the sanitized SVG as an HTML body fragment (how a browser would eventually see it)
     * and checks that no dangerous constructs survived the jsoup XML→serialization→browser-HTML
     * round trip. Mutation XSS via parser differentials lives in this gap.
     */
    private static boolean failsHtmlReparseCheck(String sanitized) {
        Document reparsed = Jsoup.parseBodyFragment(sanitized);
        for (Element element : reparsed.getAllElements()) {
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            if (DENIED_ELEMENTS.contains(tag)) {
                return true;
            }
            for (Attribute attr : element.attributes().asList()) {
                String attrKey = attr.getKey().toLowerCase(Locale.ROOT);
                if (attrKey.startsWith("on")) {
                    return true;
                }
                if (("href".equals(attrKey) || "xlink:href".equals(attrKey)) && !isSafeHref(attr.getValue(), tag)) {
                    return true;
                }
                if ("style".equals(attrKey) && !isCssSafe(attr.getValue())) {
                    return true;
                }
            }
            if ("style".equals(tag) && !isCssSafe(element.html())) {
                return true;
            }
        }
        return false;
    }
}
