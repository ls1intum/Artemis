package de.tum.cit.aet.artemis.communication.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

/**
 * Finds the "@all" token of a posting, which pings every member of a group chat. The token only counts if the client displays it as normal text, so the posting is parsed as
 * markdown like the client does and the text that is displayed as code, as a quote, as a link destination or as a URL is left out.
 * <p>
 * Everything in here runs in linear time: the posting content is limited in length, but it is user controlled, so there are no
 * backtracking regular expressions and every scan for a closing token either consumes the scanned text or ends the scan of the HTML fragment.
 */
public final class AtAllMentionDetector {

    /** The maximum length of the content of a posting, see {@code Posting#getContent()}. Longer content fails the validation when the posting is saved. */
    private static final int MAX_POSTING_CONTENT_LENGTH = 5000;

    /**
     * Matches the literal token "@all" if it is neither preceded by a letter, digit, underscore, "@", "/" or "=" (so not in an email address, a URL path or a URL query value)
     * nor followed by a letter, digit or underscore (so not "@alle"). The character classes are Unicode aware, the case-insensitive match only folds ASCII letters, i.e. it does
     * not depend on the default locale.
     */
    private static final Pattern AT_ALL_MENTION_PATTERN = Pattern.compile("(?<![\\p{L}\\p{N}_@/=])@all(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE);

    /** The text of the token anywhere in the raw markdown, which has to be present for the token to be displayed. Markup such as emphasis may adjoin it there. */
    private static final Pattern AT_ALL_TEXT_PATTERN = Pattern.compile("@all", Pattern.CASE_INSENSITIVE);

    /** Parses the markdown of postings to find the text that is displayed as normal text. The parser is immutable and thread safe. */
    private static final Parser MARKDOWN_PARSER = Parser.builder().build();

    /**
     * The HTML elements whose contents the client does not display as normal text. These are the code and quote elements of the allow list of the posting content (see
     * {@code allowedHtmlTags} of the posting content part of the client) and the elements that the sanitizer of the client removes together with their contents (the
     * forbidden contents of DOMPurify), as far as the HTML parser keeps them as elements in the body of a document. All other elements that are not on the allow list are
     * removed by the sanitizer, but their contents stay.
     */
    private static final Set<String> HTML_HIDDEN_CONTENT_ELEMENTS = Set.of("code", "pre", "blockquote", "script", "style", "template", "title", "iframe", "noscript", "noembed",
            "noframes", "xmp", "audio", "video", "svg", "math");

    /** The hidden content elements whose contents are raw text for the HTML parser: tags inside of them are not tags, the element only ends at its own closing tag. */
    private static final Set<String> HTML_RAW_TEXT_ELEMENTS = Set.of("script", "style", "title", "iframe", "noscript", "noembed", "noframes", "xmp");

    /** The elements of the allow list of the client that start a new line, so the text before and after them is not displayed as one word. */
    private static final Set<String> HTML_LINE_BREAKING_ELEMENTS = Set.of("br", "hr", "p", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre");

    /** The characters that precede the start of a link in the text without ending the word that the link is a part of, see {@link #isLinkBoundary}. */
    private static final String CHARACTERS_BEFORE_NO_LINK_START = ".:/-_@";

    private AtAllMentionDetector() {
        // utility class
    }

    /**
     * Checks whether a posting contains the "@all" token, which pings every member of a group chat. Only text that is displayed as normal text counts: the token in a
     * blockquote (including its lazy continuation lines), a fenced, indented or inline code, an HTML code, pre or blockquote element, an HTML comment, script or style
     * element, an image description, a link destination or a URL does not. Quoting a message that contains "@all" or explaining the feature in code therefore does not ping
     * the group again.
     * <p>
     * The posting is parsed as markdown, like the client does, so the block and container structure (lists, headings, fences inside lists) is respected. The text of HTML
     * blocks and inline HTML counts as far as the client displays it, e.g. "&lt;p&gt;@all please read&lt;/p&gt;" pings the group.
     * <p>
     * The URLs that the client turns into links are the ones with a scheme (the client enables the automatic linking of markdown-it, whose links are those with a scheme
     * separator, "//" and "mailto:"), and "www." addresses are left out as well, so the token in the query of such an address does not count. A token that stands next to
     * a URL, e.g. in "see www.example.org @all", counts.
     *
     * @param postingContent content of the posting, may be null
     * @return true if the content contains the "@all" token outside of quotes, code and URLs
     */
    public static boolean containsAtAllMention(String postingContent) {
        // most postings do not contain the text at all, they are not parsed, and content that exceeds the length of a posting is rejected when it is saved
        if (postingContent == null || postingContent.length() > MAX_POSTING_CONTENT_LENGTH || !AT_ALL_TEXT_PATTERN.matcher(postingContent).find()) {
            return false;
        }
        var textCollector = new DisplayedTextCollector();
        MARKDOWN_PARSER.parse(postingContent).accept(textCollector);
        return AT_ALL_MENTION_PATTERN.matcher(textCollector.getTextWithoutUrls()).find();
    }

    /**
     * Finds the start of the first URL in a word of the text, which are the URLs that the client turns into links and the "www." addresses.
     *
     * @param text the text
     * @param from the start of the word (inclusive)
     * @param to   the end of the word (exclusive)
     * @return the index at which the URL starts, or -1 if the word does not contain a URL
     */
    private static int findUrlStart(CharSequence text, int from, int to) {
        for (int i = from; i < to; i++) {
            char current = text.charAt(i);
            if (current == ':' && i + 2 < to && text.charAt(i + 1) == '/' && text.charAt(i + 2) == '/') {
                // the scheme precedes the separator
                int schemeStart = i;
                while (schemeStart > from && isSchemeCharacter(text.charAt(schemeStart - 1))) {
                    schemeStart--;
                }
                return schemeStart;
            }
            if (isLinkBoundary(text, from, i)) {
                if (regionMatchesIgnoreCase(text, i, to, "www.") && i + 4 < to && Character.isLetterOrDigit(text.charAt(i + 4))) {
                    return i;
                }
                if (regionMatchesIgnoreCase(text, i, to, "mailto:")) {
                    return i;
                }
                if (current == '/' && i + 2 < to && text.charAt(i + 1) == '/' && Character.isLetterOrDigit(text.charAt(i + 2))) {
                    // a link without a scheme, which the client turns into a link
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isSchemeCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '+' || character == '-' || character == '.';
    }

    /**
     * A URL can start at the beginning of a word and after a character that does not belong to a word, e.g. after a bracket or a comma, but not inside of a word.
     */
    private static boolean isLinkBoundary(CharSequence text, int wordStart, int index) {
        if (index == wordStart) {
            return true;
        }
        char previous = text.charAt(index - 1);
        return !Character.isLetterOrDigit(previous) && CHARACTERS_BEFORE_NO_LINK_START.indexOf(previous) < 0;
    }

    private static boolean regionMatchesIgnoreCase(CharSequence text, int index, int end, String prefix) {
        if (index + prefix.length() > end) {
            return false;
        }
        for (int offset = 0; offset < prefix.length(); offset++) {
            if (Character.toLowerCase(text.charAt(index + offset)) != prefix.charAt(offset)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiLetter(char character) {
        return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
    }

    /**
     * Collects the text of a parsed posting that is displayed as normal text and leaves out code, quotes, images, autolinks and the contents of HTML elements that are not
     * displayed as normal text. The parts that are left out are replaced by a space or a line break, so that the text around them is not joined into a token.
     */
    private static final class DisplayedTextCollector extends AbstractVisitor {

        private final StringBuilder text = new StringBuilder();

        /** The number of HTML elements with hidden contents (see {@link #HTML_HIDDEN_CONTENT_ELEMENTS}) that are open at the current position. */
        private int openHiddenHtmlElements = 0;

        /** The name of the open HTML raw text element, e.g. "script", whose contents are not parsed as HTML, or null. */
        private String openRawTextElement = null;

        @Override
        public void visit(Text textNode) {
            appendDisplayedText(textNode.getLiteral());
        }

        @Override
        public void visit(Code code) {
            text.append(' ');
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            text.append('\n');
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock) {
            text.append('\n');
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            text.append('\n');
        }

        @Override
        public void visit(HtmlBlock htmlBlock) {
            text.append('\n');
            scanHtml(htmlBlock.getLiteral());
            text.append('\n');
        }

        @Override
        public void visit(HtmlInline htmlInline) {
            scanHtml(htmlInline.getLiteral());
        }

        @Override
        public void visit(Image image) {
            text.append(' ');
        }

        @Override
        public void visit(Link link) {
            // an autolink such as <https://host/page> or <tel:(@all)> displays its destination as the text of the link, which is a URL and not text of the posting
            if (link.getFirstChild() instanceof Text linkText && link.getFirstChild() == link.getLastChild() && link.getDestination() != null
                    && (link.getDestination().equals(linkText.getLiteral()) || link.getDestination().equals("mailto:" + linkText.getLiteral()))) {
                text.append(' ');
                return;
            }
            visitChildren(link);
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            text.append('\n');
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            text.append('\n');
        }

        @Override
        public void visit(Paragraph paragraph) {
            visitChildren(paragraph);
            text.append('\n');
        }

        @Override
        public void visit(Heading heading) {
            visitChildren(heading);
            text.append('\n');
        }

        private void appendDisplayedText(CharSequence displayedText) {
            if (openHiddenHtmlElements == 0) {
                text.append(displayedText);
            }
        }

        /**
         * Adds the text of a fragment of HTML, which is an HTML block or a single tag of inline HTML, and tracks the HTML elements with hidden contents that it opens and closes.
         * The elements stay open across nodes, as the HTML of the client does: the contents of a "blockquote" element that is opened in one HTML block are still hidden in the
         * markdown that follows it.
         * <p>
         * This is a scanner for the structure of HTML (tags, comments and the like), not a parser. If a tag or a comment is not closed, the rest of the fragment belongs to it and
         * is not displayed, like an HTML parser treats the end of the input in a tag. Every scan for the end of a construct either continues behind it or ends the scan, so the
         * time is linear in the length of the fragment.
         *
         * @param html the HTML fragment
         */
        private void scanHtml(String html) {
            int position = 0;
            while (position < html.length()) {
                if (openRawTextElement != null) {
                    int closingTag = indexOfClosingTag(html, openRawTextElement, position);
                    if (closingTag < 0) {
                        // the rest is the content of the raw text element, which is not displayed
                        return;
                    }
                    openRawTextElement = null;
                    position = scanMarkup(html, closingTag);
                    continue;
                }
                int tagStart = html.indexOf('<', position);
                if (tagStart < 0) {
                    appendDisplayedText(html.substring(position));
                    return;
                }
                appendDisplayedText(html.substring(position, tagStart));
                position = scanMarkup(html, tagStart);
            }
        }

        /**
         * Handles the construct that starts with the "&lt;" at the given index.
         *
         * @return the index behind the construct, which is the length of the HTML if the construct is not closed
         */
        private int scanMarkup(String html, int start) {
            int next = start + 1;
            if (next >= html.length()) {
                appendDisplayedText("<");
                return html.length();
            }
            char first = html.charAt(next);
            if (first == '!' && html.startsWith("!--", next)) {
                // a comment ends with "-->", which may start inside of the opening "<!--" as in "<!-->"
                int end = html.indexOf("-->", start + 2);
                return end < 0 ? html.length() : end + 3;
            }
            if (first == '!' || first == '?' || (first == '/' && !(next + 1 < html.length() && isAsciiLetter(html.charAt(next + 1))))) {
                // a declaration, a processing instruction or an invalid closing tag is a comment up to the next ">"
                int end = html.indexOf('>', next);
                return end < 0 ? html.length() : end + 1;
            }
            if (first == '/' || isAsciiLetter(first)) {
                return scanTag(html, start, first == '/');
            }
            // a "<" that does not start markup is displayed as it is
            appendDisplayedText("<");
            return next;
        }

        /**
         * Handles the opening or closing tag that starts at the given index. A "/" before the closing ">" is ignored for the HTML elements that have contents, like the HTML
         * parser of the browser does, so a "code" element that is written as "&lt;code/&gt;" is open.
         *
         * @return the index behind the tag, which is the length of the HTML if the tag is not closed
         */
        private int scanTag(String html, int start, boolean closing) {
            int nameStart = closing ? start + 2 : start + 1;
            int index = nameStart;
            while (index < html.length() && !Character.isWhitespace(html.charAt(index)) && html.charAt(index) != '/' && html.charAt(index) != '>') {
                index++;
            }
            String name = html.substring(nameStart, index).toLowerCase(Locale.ROOT);
            // the attributes, whose quoted values may contain a ">"
            boolean afterEquals = false;
            while (index < html.length()) {
                char current = html.charAt(index);
                if (current == '>') {
                    handleTag(name, closing);
                    appendLineBreakAfter(name);
                    return index + 1;
                }
                if (afterEquals && (current == '"' || current == '\'')) {
                    int closingQuote = html.indexOf(current, index + 1);
                    if (closingQuote < 0) {
                        return html.length();
                    }
                    index = closingQuote + 1;
                    afterEquals = false;
                    continue;
                }
                if (current == '=') {
                    afterEquals = true;
                }
                else if (!Character.isWhitespace(current)) {
                    afterEquals = false;
                }
                index++;
            }
            return html.length();
        }

        private void handleTag(String name, boolean closing) {
            if (!HTML_HIDDEN_CONTENT_ELEMENTS.contains(name)) {
                return;
            }
            if (closing) {
                openHiddenHtmlElements = Math.max(0, openHiddenHtmlElements - 1);
            }
            else {
                openHiddenHtmlElements++;
                if (HTML_RAW_TEXT_ELEMENTS.contains(name)) {
                    openRawTextElement = name;
                }
            }
        }

        private void appendLineBreakAfter(String name) {
            if (HTML_LINE_BREAKING_ELEMENTS.contains(name)) {
                text.append('\n');
            }
        }

        /**
         * Finds the closing tag of a raw text element, e.g. "&lt;/script&gt;". Every candidate is passed once, so the time is linear.
         *
         * @return the index of the "&lt;" of the closing tag, or -1 if there is none
         */
        private static int indexOfClosingTag(String html, String elementName, int from) {
            int candidate = html.indexOf("</", from);
            while (candidate >= 0) {
                int nameEnd = candidate + 2 + elementName.length();
                if (html.regionMatches(true, candidate + 2, elementName, 0, elementName.length())
                        && (nameEnd == html.length() || Character.isWhitespace(html.charAt(nameEnd)) || html.charAt(nameEnd) == '/' || html.charAt(nameEnd) == '>')) {
                    return candidate;
                }
                candidate = html.indexOf("</", candidate + 2);
            }
            return -1;
        }

        /**
         * The collected text without the URLs, so a token in a URL (for example in a query value) does not count. A URL is a word, or the end of a word, that starts with a
         * scheme and "://", with "//", with "mailto:" or with "www.". The text before the URL in the same word is kept, and so is the text after the word.
         *
         * @return the collected text
         */
        String getTextWithoutUrls() {
            StringBuilder result = new StringBuilder(text.length());
            int wordStart = 0;
            for (int i = 0; i <= text.length(); i++) {
                if (i == text.length() || Character.isWhitespace(text.charAt(i))) {
                    int urlStart = findUrlStart(text, wordStart, i);
                    result.append(text, wordStart, urlStart < 0 ? i : urlStart);
                    if (i < text.length()) {
                        result.append(text.charAt(i));
                    }
                    wordStart = i + 1;
                }
            }
            return result.toString();
        }
    }
}
