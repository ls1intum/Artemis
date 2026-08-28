package de.tum.cit.aet.artemis.exercise.service;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import uk.ac.ed.ph.snuggletex.DOMOutputOptions;
import uk.ac.ed.ph.snuggletex.SessionConfiguration;
import uk.ac.ed.ph.snuggletex.SnuggleEngine;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleSession;

/**
 * SPIKE (feature/programming/ssr-mathml-spike): converts a LaTeX math formula to sanitized Presentation MathML that
 * the browser renders natively, replacing the client-side KaTeX rendering of the SSR problem statement.
 * <p>
 * Security model: LaTeX is author-controlled, so SnuggleTeX output is <em>untrusted</em>. This class is the primary
 * server boundary and never depends on the client's DOMPurify (the render endpoint can be served directly with
 * {@code includeJs=true}). It walks the SnuggleTeX DOM with a namespace-aware allowlist and serializes only elements
 * that are in the MathML namespace <em>and</em> on {@link #ALLOWED_ELEMENTS}, dropping any attribute not on
 * {@link #ALLOWED_ATTRIBUTES}. Any disallowed element fails the whole formula closed (→ {@code Optional.empty()}), so
 * the caller falls back to the escaped source. No URL/resource attribute (href, src, …) is ever on the allowlist, and
 * {@code <annotation>}/{@code <semantics>} are never emitted (annotations are turned off), so a MathML integration
 * point cannot smuggle HTML/SVG.
 * <p>
 * The MathML the browser cannot render natively is a spike finding, not a security issue: SnuggleTeX targets legacy
 * MathML, so it emits {@code <mfenced>} (dropped from MathML Core, so its delimiters do not render in Chromium/Firefox)
 * and {@code <mspace width>} (the dimension attribute is disallowed here, so spacing is approximate).
 */
public final class LatexToMathmlConverter {

    private static final Logger log = LoggerFactory.getLogger(LatexToMathmlConverter.class);

    private static final String MATHML_NAMESPACE = "http://www.w3.org/1998/Math/MathML";

    /**
     * One reusable engine. Constructing it loads the LaTeX definition maps once; those are read-only afterwards and
     * every conversion gets its own {@link SnuggleSession}, which is the documented single-threaded unit.
     */
    private static final SnuggleEngine ENGINE = new SnuggleEngine();

    // Resource limits — SnuggleTeX exposes no expansion/time budget, so the input is bounded before parsing and the
    // output is bounded after. Each breach degrades to the escaped source at the call site.
    private static final int MAX_LATEX_LENGTH = 5_000;

    private static final int MAX_BRACE_DEPTH = 100;

    private static final int MAX_MATHML_OUTPUT_LENGTH = 100_000;

    /** Explicit macro-expansion budget for the parse, so a recursive macro is bounded before it produces output. */
    private static final int MAX_MACRO_EXPANSIONS = 1_000;

    /** Macro-definition/expansion primitives are rejected: a small self-referential macro expands recursively and the depth preflight does not bound it. */
    private static final Pattern MACRO_PRIMITIVES = Pattern.compile("\\\\(def|newcommand|renewcommand|providecommand|let|csname|expandafter|newenvironment|input|include)\\b");

    /**
     * Presentation MathML only. {@code mfenced} is kept (harmless grouping) though MathML Core no longer renders it; annotation/semantics/maction/mglyph and all non-presentation
     * elements are absent by design.
     */
    private static final Set<String> ALLOWED_ELEMENTS = Set.of("math", "mrow", "mi", "mo", "mn", "ms", "mtext", "mspace", "msup", "msub", "msubsup", "mfrac", "msqrt", "mroot",
            "mstyle", "mpadded", "mphantom", "mfenced", "mtable", "mtr", "mtd", "munder", "mover", "munderover", "merror");

    /** Safe presentation attributes only. No URL/resource attributes and no length/dimension attributes (width, height, depth, lspace, voffset), which are dropped outright. */
    private static final Set<String> ALLOWED_ATTRIBUTES = Set.of("mathvariant", "display", "displaystyle", "scriptlevel", "dir", "mathsize", "mathcolor", "accent", "accentunder",
            "stretchy", "fence", "separator", "form", "largeop", "movablelimits", "symmetric", "columnalign", "rowalign", "columnspan", "rowspan", "open", "close", "notation");

    private LatexToMathmlConverter() {
    }

    /**
     * Converts a single formula to sanitized Presentation MathML.
     *
     * @param latex       the LaTeX source (the formula body, without delimiters)
     * @param displayMode whether the formula is display (block) or inline math
     * @return the sanitized {@code <math>…</math>} string, or empty on any limit breach, conversion error, or
     *         disallowed element — in which case the caller renders the escaped source instead
     */
    public static Optional<String> toMathml(String latex, boolean displayMode) {
        if (latex.length() > MAX_LATEX_LENGTH || exceedsBraceDepth(latex) || MACRO_PRIMITIVES.matcher(latex).find()) {
            return Optional.empty();
        }
        try {
            // Bound macro expansion explicitly rather than trusting the library default, so a small recursive macro
            // cannot burn CPU/heap during the parse before any output exists.
            SessionConfiguration configuration = new SessionConfiguration();
            configuration.setExpansionLimit(MAX_MACRO_EXPANSIONS);
            SnuggleSession session = ENGINE.createSession(configuration);
            // `\[...\]` sets display="block" on the <math> element; `$...$` leaves it inline.
            String wrapped = displayMode ? "\\[" + latex + "\\]" : "$" + latex + "$";
            session.parseInput(new SnuggleInput(wrapped));
            if (!session.getErrors().isEmpty()) {
                return Optional.empty();
            }
            DOMOutputOptions options = new DOMOutputOptions();
            // No `<semantics>`/`<annotation>` wrapper: it would carry the TeX source and, more importantly, the
            // fail-closed serializer would reject the whole formula on the unknown element. The default already omits
            // it; this keeps it off explicitly.
            options.setAddingMathSourceAnnotations(false);
            NodeList nodes = session.buildDOMSubtree(options);
            Element math = singleMathRoot(nodes);
            if (math == null) {
                return Optional.empty();
            }
            StringBuilder out = new StringBuilder();
            if (!serialize(math, out, true)) {
                return Optional.empty();
            }
            return out.length() > MAX_MATHML_OUTPUT_LENGTH ? Optional.empty() : Optional.of(out.toString());
        }
        catch (IOException | RuntimeException exception) {
            // A conversion failure must never break the render; the formula degrades to its source. OutOfMemoryError
            // and other Errors are deliberately not caught here.
            log.debug("MathML conversion failed for formula, falling back to source", exception);
            return Optional.empty();
        }
    }

    /** The single {@code <math>} element among the returned nodes, ignoring whitespace text, or {@code null} when the shape is unexpected. */
    private static Element singleMathRoot(NodeList nodes) {
        Element math = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.TEXT_NODE && node.getNodeValue().isBlank()) {
                continue;
            }
            if (node.getNodeType() != Node.ELEMENT_NODE || !isMathml(node) || !"math".equals(node.getLocalName()) || math != null) {
                return null;
            }
            math = (Element) node;
        }
        return math;
    }

    /**
     * Serializes an allowlisted element and its subtree, returning false (fail closed) as soon as any element is
     * outside the MathML namespace or not on {@link #ALLOWED_ELEMENTS}.
     */
    private static boolean serialize(Element element, StringBuilder out, boolean isRoot) {
        // Abort as soon as the output budget is exceeded, rather than building the whole string first.
        if (out.length() > MAX_MATHML_OUTPUT_LENGTH || !isMathml(element) || !ALLOWED_ELEMENTS.contains(element.getLocalName())) {
            return false;
        }
        out.append('<').append(element.getLocalName());
        if (isRoot) {
            out.append(" xmlns=\"").append(MATHML_NAMESPACE).append('"');
        }
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getLocalName() != null ? attribute.getLocalName() : attribute.getNodeName();
            // Drop every attribute not explicitly allowed (never reject the formula for it): a dropped attribute
            // cannot introduce a vector. xmlns is handled above and skipped here.
            if (ALLOWED_ATTRIBUTES.contains(name)) {
                out.append(' ').append(name).append("=\"").append(escape(attribute.getNodeValue(), true)).append('"');
            }
        }
        out.append('>');
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (!serialize((Element) child, out, false)) {
                    return false;
                }
            }
            else if (child.getNodeType() == Node.TEXT_NODE) {
                out.append(escape(child.getNodeValue(), false));
            }
            // Comments, processing instructions and anything else are ignored.
        }
        out.append("</").append(element.getLocalName()).append('>');
        return true;
    }

    private static boolean isMathml(Node node) {
        return MATHML_NAMESPACE.equals(node.getNamespaceURI());
    }

    /** True when brace nesting exceeds the preflight limit, so an expensive parse is rejected before it starts. */
    private static boolean exceedsBraceDepth(String latex) {
        int depth = 0;
        for (int i = 0; i < latex.length(); i++) {
            char character = latex.charAt(i);
            if (character == '\\') {
                i++; // Skip the escaped character, so `\{` and `\}` do not count as nesting.
                continue;
            }
            if (character == '{') {
                depth++;
                if (depth > MAX_BRACE_DEPTH) {
                    return true;
                }
            }
            else if (character == '}' && depth > 0) {
                depth--;
            }
        }
        return false;
    }

    private static final Map<Character, String> ESCAPES = Map.of('&', "&amp;", '<', "&lt;", '>', "&gt;", '"', "&quot;");

    private static String escape(String value, boolean attribute) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '&' || character == '<' || character == '>' || (attribute && character == '"')) {
                result.append(ESCAPES.get(character));
            }
            else {
                result.append(character);
            }
        }
        return result.toString();
    }
}
