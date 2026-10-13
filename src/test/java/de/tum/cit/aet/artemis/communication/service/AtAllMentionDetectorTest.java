package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the detection of the "@all" token. The expectation of every case is what the client displays: the token counts if the client shows it as normal text and
 * does not count if it is part of code, a quote, a link destination, a URL or hidden HTML.
 * <p>
 * Where the detector is deliberately more conservative than the client, the token does not count although the client shows it: "www." addresses (the client does not turn
 * them into links), URL schemes other than the ones of the client (e.g. "foo://") and the query behind a "mailto:" address. A missed ping is the harmless direction.
 */
class AtAllMentionDetectorTest {

    /** The maximum length of the content of a posting, see {@code Posting#getContent()}. */
    private static final int MAX_POSTING_CONTENT_LENGTH = 5000;

    @ParameterizedTest
    @ValueSource(strings = { "@all", "@ALL", "@All", "@all ", "@all, please read", "@all. Please read", "@all!", "Hello @all", "Hello (@all)", "line one\n@all",
            "[user]A B(ab)[/user] @all", "@all @all", "`code` @all", "```\ncode\n```\n@all", "~~~\ncode\n~~~\n@all", "> quoted\n\n@all", "see https://host/page and @all",
            "   @all", "text\n    @all", "text\n\t@all", "> quote\n\n@all", "`code\n\n@all`", "a ` b\n@all", "`code` and\n@all", "`` a `\n@all", "`example\n# @all",
            "# Heading @all", "- item\n\n  @all", "- ~~~\n  code\n  ~~~\n\n@all", "**@all**", "_@all_ please read", "[details](https://host/page) @all", "<b>@all</b>",
            "<code>x</code> @all", "<blockquote>q</blockquote>\n\n@all", "<p>@all please read</p>", "<p>@all</p>", "<div>\n<p>\n@all please read\n</p>\n</div>",
            "<ul><li>@all</li></ul>", "<p><code>x</code> @all</p>", "<code>x</code>\n\n@all", "<pre>x</pre>\n\n@all", "<!-- note -->\n<p>@all</p>", "<div title=\"x\">@all</div>",
            "<DIV CLASS='a'>@ALL</DIV>", "<p><b>bold</b> @all</p>", "<p>a &lt; b @all</p>", "<p>1 < 2 @all</p>", "<a href=\"https://host/@all\">link</a> @all",
            "<p title=\">\">@all</p>", "<textarea>@all</textarea>", "<p></code>@all</p>", "<style>p {}</style>\n\n@all", "<script>x</script>\n<p>@all</p>",
            "see www.example.org @all", "www.example.org @all", "WWW.example.org\n@all", "www @all", "www.@all", "see https://host/page @all", "mailto:someone@example.org @all",
            "@all www.example.org", "@all <!-- never closed", "@all <p never closed", "< @all", "<3 @all", "a <b>@all</b>", "https://host/ @all", "http://host/a(b) @all",
            "x // @all", "<pre><code>x</code></pre>\n\n@all", "<code>x</code></pre>\n\n@all", "<svg/> @all", "<svg />@all", "<math/>\n\n@all", "<svg>x</svg> @all",
            "<svg><svg/>x</svg> @all", "<div>\n<code\u000B>@all</code>", "<div>\n<code\u2003>@all</code>", "<pre><code>x</pre></code>\n\n@all",
            "<pre><code>x</pre>\n\n</code>\n\n@all", "<blockquote><code>x</blockquote></code>\n\n@all", "<pre><code><code>x</pre></code></code>\n\n@all",
            "<blockquote><pre><code>x</pre></blockquote></code>\n\n@all" })
    void testContainsAtAllMentionMatches(String content) {
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "all", "@alle", "@allow", "@all_hands", "@all1", "email@all.com", "name@all", "@@all", "@ all", "@al", "[user]A B(ab)[/user]", "@all\u00e9",
            "\u00e9@all", "> @all meeting at 5", "  > @all meeting at 5", ">> @all", "`@all`", "``@all``", "use `@all` to ping", "```\n@all\n```", "```java\nint a;\n@all\n```",
            "```\n@all", "~~~\n@all\n~~~", "https://host/@all", "[link](https://host/@all)", "[link](https://host/p?x=@all)", "https://host/?a=@all", "a=@all", "    @all",
            "\t@all", "text\n\n    @all", "> quote\n    @all", "```\ncode\n```\n    @all", "`code\n@all`", "a `b\nc @all` d", "`code\n    @all` text", "``a\nb ` @all\nc`` d",
            "> quoted text\n@all please read", "> quote\n> more\n@all", "<code>@all</code>", "text <code>@all</code> more", "<pre>@all</pre>", "<blockquote>@all</blockquote>",
            "<p><code>@all</code></p>", "- ~~~\n  @all\n  ~~~", "1. text\n\n       @all", "[details](https://host/?q=(@all))", "see https://host/?q=(@all)", "<https://host/@all>",
            "![alt @all](https://host/image.png)", "<p><code>x</code></p><pre>x</pre>\n<code>y</code><blockquote>@all</blockquote>", "<div>\n<code>\n@all\n</code>\n</div>",
            "<pre>\nline\n\n@all\n</pre>", "<blockquote>\n\n@all\n\n</blockquote>", "<blockquote>\nquote\n</blockquote>\n<p><code>@all</code></p>", "<p>x <code>@all</code></p>",
            "<style>@all</style>", "<script>@all</script>", "<script>\nvar a = '</code>';\n@all\n</script>", "<p>x</p>\n<script>@all</script>", "<noscript>@all</noscript>",
            "<template><p>@all</p></template>", "<svg><text>@all</text></svg>", "<iframe>@all</iframe>", "<title>@all</title>", "<!-- @all -->", "<!--\n@all\n-->",
            "<p><!-- @all --></p>", "<!--@all-->", "<!---->\n<code>@all</code>", "<!-- never closed\n@all", "<p title=\">@all\">x</p>", "<div title=\">@all\">\nx\n</div>",
            "<p title='@all'>x</p>", "<p data-a=@all>x</p>", "<div title=\"x\n@all\">\ny\n</div>", "<p never closed @all", "<p \"never closed @all", "<p a=\"never closed\n@all",
            "<CODE>@ALL</CODE>", "<Pre>\n@all\n</PRE>", "<code class=\"a\">@all</code>", "<code/>@all", "<p><span><code>x</code></span><code>@all</code></p>", "<?php @all ?>",
            "<!DOCTYPE @all>", "<tel:(@all)>", "<mailto:someone@example.org?subject=(@all)>", "www.example.org/?q=(@all)", "WWW.EXAMPLE.ORG/?q=(@all)",
            "see www.example.org/?q=(@all) now", "(www.example.org/?q=(@all))", "see (www.example.org/?q=(@all)", "text,www.example.org/#(@all)", "www.example.org/a,(@all)",
            "mailto:someone@example.org?subject=(@all)", "MAILTO:someone@example.org?subject=(@all)", "(mailto:someone@example.org?body=(@all))", "//example.org/?q=(@all)",
            "(//example.org/?q=(@all))", "see //example.org/#(@all)", "foo://example.org/?q=(@all)", "(https://example.org/?q=(@all))", "x(https://host/a&(@all))",
            "ftp://host/(@all)", "tel://host/(@all)", "file:///a/(@all)", "<code>a</pre> @all", "<code><pre>a</pre> @all", "<pre>\n</blockquote>\n\n@all\n</pre>",
            "<code></code\u000B>@all", "<pre>\n</pre\u000B>\n\n@all", "<blockquote>\n</blockquote\u2003>\n\n@all", "<blockquote>\n</blockquote\u2003 >\n@all",
            "<code>x</code/>@all", "<svg a=b/>@all", "<svg a=/>@all", "<div>\n<svg/ >@all", "<div>\n<svg a=\"b\"/ >@all", "```\n@all\n``", "<pre><code>x</pre>\n\n@all",
            "<pre><code>x</pre> @all", "<pre><code>x</pre><b>y</b>\n\n@all", "<pre><code>x</pre>\n\n<code>y</code>\n\n@all", "<pre><code><code>x</pre></code>\n\n@all",
            "<blockquote><pre><code>x</pre></blockquote>\n\n@all", "<blockquote><code>x</blockquote>\n\n@all", "<blockquote><code>x</blockquote>\n\n<p>y</p>\n\n@all",
            "<blockquote><code>x</blockquote>\n\n```\ncode\n```\n\n@all" })
    void testContainsAtAllMentionDoesNotMatch(String content) {
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isFalse();
    }

    @Test
    void testContainsAtAllMentionNullContent() {
        assertThat(AtAllMentionDetector.containsAtAllMention(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "a ` b @all", "``code with ` inside`` @all", "`a` `b` @all", "```\ncode\n````\n@all", "`` `` @all", "```inline``` @all" })
    void testContainsAtAllMentionCountsTokensOutsideOfClosedCode(String content) {
        // an unmatched backtick is plain text and a code span ends with a run of exactly the same length
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isTrue();
    }

    @Test
    void testContainsAtAllMentionIgnoresTokensInAFencedBlockThatIsOnlyEndedByAShorterLine() {
        // a fenced code block ends with a fence that is at least as long as the opening one, so the block is not closed and runs to the end of the posting
        assertThat(AtAllMentionDetector.containsAtAllMention("```\n@all\n``")).isFalse();
        assertThat(AtAllMentionDetector.containsAtAllMention("```\n@all\n```")).isFalse();
        // while the longer closing fence ends the block, so the token behind it counts
        assertThat(AtAllMentionDetector.containsAtAllMention("```\ncode\n````\n@all")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "www.example.org/?q=(@all) @all", "see www.example.org @all", "(www.example.org) @all", "https://host/p?x=(@all) and @all",
            "mailto:a@example.org @all", "<p>text</p>\n<pre>@all</pre>\n\n@all", "<code>@all</code> @all", "<div>\n<script>@all</script>\n</div>\n\n@all",
            "<blockquote>q</blockquote><p>@all</p>" })
    void testContainsAtAllMentionCountsAStandaloneTokenNextToUrlsAndHtml(String content) {
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contentAroundTheMaximumLength")
    void testContainsAtAllMentionHonoursTheMaximumLengthOfAPosting(String description, String content, int expectedLength, boolean expectedResult) {
        // such content is rejected when the posting is saved, so it is not parsed in the first place
        assertThat(content).hasSize(expectedLength);
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isEqualTo(expectedResult);
    }

    private static Stream<Arguments> contentAroundTheMaximumLength() {
        return Stream.of(
                Arguments.of("token at the start of a posting with the maximum length", "@all " + "x".repeat(MAX_POSTING_CONTENT_LENGTH - 5), MAX_POSTING_CONTENT_LENGTH, true),
                Arguments.of("token at the end of a posting with the maximum length", "x".repeat(MAX_POSTING_CONTENT_LENGTH - 5) + " @all", MAX_POSTING_CONTENT_LENGTH, true),
                Arguments.of("token at the start of a posting that is one character too long", "@all " + "x".repeat(MAX_POSTING_CONTENT_LENGTH - 4), MAX_POSTING_CONTENT_LENGTH + 1,
                        false),
                Arguments.of("token at the end of a posting that is one character too long", "x".repeat(MAX_POSTING_CONTENT_LENGTH - 4) + " @all", MAX_POSTING_CONTENT_LENGTH + 1,
                        false),
                Arguments.of("token in a long posting", "@all " + "x".repeat(4000), 4005, true),
                Arguments.of("token in a very long posting", "@all " + "x".repeat(5000), 5005, false));
    }

    @ParameterizedTest
    @ValueSource(strings = { "[@all](https://host/page)", "[see @all](https://host/page)", "[@all][ref]\n\n[ref]: https://host/", "[x][ref] @all\n\n[ref]: https://host/",
            "<someone@example.org> @all", "| a | b |\n|---|---|\n| @all | x |", "~~@all~~", "@all\n===", "---\n@all", "x  \n@all", "x\\\n@all", "\\@all", "x*@all*",
            "![a](x.png) @all", "x <!-- c --> @all", "line\r\n@all\r\n", "\n@all\n", "<div>\n@all <" })
    void testContainsAtAllMentionCountsTheDisplayedTextOfLinksTablesAndOtherMarkup(String content) {
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "[x](https://host/@all)", "[x][ref]\n\n[ref]: https://host/?q=@all", "<someone@example.org>", "![@all](x.png)", "\\@\\all", "@\u200ball", "\uff20all",
            "[x]: https://host/@all\n\nno token here" })
    void testContainsAtAllMentionIgnoresLinkDestinationsAndTextThatIsNotTheLiteralToken(String content) {
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { ".@all", "-@all", "#@all", "(@all)", "x:@all", "@all@all", "@all@", "@all-hands", "@all.", "@all\u200b", "\u00a0@all", "@all\t" })
    void testContainsAtAllMentionEndsAtCharactersThatDoNotBelongToAWord(String content) {
        // the token is neither preceded by a letter, digit, underscore, "@", "/" or "=", nor followed by a letter, digit or underscore
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "@all_", "@all2", "@all\u4e2d", "\u4e2d@all", "_@all", "9@all", "x@all", "/@all", "a/@all", "=@all" })
    void testContainsAtAllMentionNeedsTheTokenToStandAloneAsAWord(String content) {
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adversarialInputsOfMaximumLength")
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testContainsAtAllMentionStaysFastAndCorrectForAdversarialInputOfMaximumLength(String description, String content, boolean expectedResult) {
        // The inputs are built to make parsers slow or deep: nesting, many unmatched code spans of different lengths, unclosed markup. Every input has its own time limit, so
        // the failure names the input, and the result of every input is the one that the client displays, so a scan that gives up early or loops is not accepted either.
        assertThat(content.length()).isLessThanOrEqualTo(MAX_POSTING_CONTENT_LENGTH);
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isEqualTo(expectedResult);
    }

    private static Stream<Arguments> adversarialInputsOfMaximumLength() {
        StringBuilder backtickRunsOfGrowingLength = new StringBuilder();
        for (int length = 1; backtickRunsOfGrowingLength.length() < 4900; length++) {
            backtickRunsOfGrowingLength.append("`".repeat(length)).append(' ');
        }
        return Stream.of(
                // markdown, the token is displayed unless it is in a quote or in indented code
                Arguments.of("nested block quotes", "> ".repeat(2480) + " @all", false), Arguments.of("block quotes without spaces", ">".repeat(4990) + " @all", false),
                // the client stops the nesting at 100 levels, the server keeps parsing, and the token is displayed text of the innermost item
                Arguments.of("nested list items", "- ".repeat(2480) + " @all", true), Arguments.of("nested ordered list items", "1. ".repeat(1650) + " @all", true),
                Arguments.of("emphasis delimiters", "*".repeat(4980) + " @all", true), Arguments.of("alternating emphasis delimiters", "_*".repeat(2490) + " @all", true),
                Arguments.of("unmatched brackets", "[".repeat(2490) + " @all", true), Arguments.of("unmatched link destinations", "[](".repeat(1650) + " @all", true),
                Arguments.of("unmatched code span openers of different lengths", "`a``b```c````d".repeat(350) + " @all", true),
                Arguments.of("backtick runs of growing length", backtickRunsOfGrowingLength + " @all", true),
                Arguments.of("balanced code fences", "```\n".repeat(1240) + " @all", true),
                Arguments.of("indented code lines with a backtick", "    code `\n".repeat(440) + "    @all", false),
                // HTML, an unclosed element or tag hides the rest, stray closing tags and the like do not
                Arguments.of("unclosed code elements", "<code>".repeat(800) + " @all", false), Arguments.of("less-than signs", "<".repeat(4980) + " @all", true),
                Arguments.of("less-than signs in an HTML block", "<div>\n" + "<".repeat(4900) + " @all", true),
                Arguments.of("unclosed paragraph tags in an HTML block", "<div>\n" + "<p ".repeat(1600) + " @all", false),
                Arguments.of("unclosed paragraph tags", "<p ".repeat(1600) + " @all", false), Arguments.of("paragraph elements", "<div>\n" + "<p>".repeat(1600) + " @all", true),
                Arguments.of("paragraph elements in separate lines", "<p>\n".repeat(1200) + " @all", true),
                Arguments.of("unclosed code elements in an HTML block", "<div>\n" + "<code>".repeat(800) + " @all", false),
                Arguments.of("closing code tags without an open element", "<div>\n" + "</code>".repeat(700) + " @all", true),
                Arguments.of("unclosed code tags", "<code ".repeat(800) + " @all", true), Arguments.of("unclosed closing tags", "</code ".repeat(700) + " @all", true),
                Arguments.of("invalid closing tags", "<div>\n" + "</".repeat(2400) + " @all", false), Arguments.of("declarations", "<div>\n" + "<!".repeat(2400) + " @all", false),
                Arguments.of("processing instructions", "<div>\n" + "<?".repeat(2400) + " @all", false),
                Arguments.of("comment openers", "<div>\n" + "<!--".repeat(1200) + " @all", false),
                Arguments.of("spaced comment openers", "<div>\n" + "<!-- ".repeat(950) + " @all", false),
                Arguments.of("spaced comment openers at the start", "<!-- ".repeat(950) + " @all", false),
                Arguments.of("empty comments that end with the opener", "<div>\n" + "<!--> ".repeat(800) + " @all", true),
                Arguments.of("script openers", "<div>\n" + "<script>".repeat(600) + " @all", false),
                Arguments.of("script with incomplete closing tags", "<script>\n" + "</scrip".repeat(700) + " @all", false),
                Arguments.of("unclosed double quoted attribute values", "<div>\n" + "<a b=\"".repeat(800) + " @all", false),
                Arguments.of("unclosed single quoted attribute values", "<div>\n" + "<a b='".repeat(800) + " @all", false),
                Arguments.of("unquoted attribute values", "<div>\n" + "<a b=".repeat(950) + " @all", false),
                Arguments.of("attributes without end", "<div>\n" + "<a b=\"c\" ".repeat(450) + " @all", false),
                Arguments.of("unclosed pre elements", "<pre>\n\n".repeat(650) + " @all", false), Arguments.of("anchor elements", "<a>".repeat(1600) + " @all", true),
                Arguments.of("tag names with a dot", "<www.".repeat(900) + " @all", true), Arguments.of("self-closing svg elements", "<svg/>".repeat(800) + " @all", true),
                Arguments.of("unclosed blockquote elements", "<blockquote>".repeat(400) + " @all", false),
                Arguments.of("code elements and the closing tags of pre", "<code>".repeat(400) + "</pre>".repeat(400) + " @all", false),
                Arguments.of("code elements and the closing tags of blockquote", "<div>\n" + "<code>".repeat(300) + "</blockquote>".repeat(200) + " @all", false),
                // the HTML parser reopens a code element that the closing tag of an outer element ends, which makes the scan repeat the work of every closing tag
                Arguments.of("code elements that every pre element closes", "<pre><code>".repeat(250) + "</pre>".repeat(250) + " @all", false),
                Arguments.of("code elements that every blockquote element closes", "<blockquote><code>".repeat(150) + "</blockquote>".repeat(150) + " @all", false),
                Arguments.of("many code elements that every blockquote element closes", "<blockquote>".repeat(100) + "<code>".repeat(400) + "</blockquote>".repeat(100) + " @all",
                        false),
                // URLs, the token in a URL does not count, the token behind the URL does
                Arguments.of("www addresses", "www.".repeat(1200) + " @all", true), Arguments.of("www address with the token", "www.".repeat(1200) + "@all", false),
                Arguments.of("bracketed www addresses", "(www.a/".repeat(700) + " @all", true),
                Arguments.of("bracketed www address with the token", "(www.a/".repeat(700) + "@all", false), Arguments.of("scheme separators", "a://".repeat(1200) + " @all", true),
                Arguments.of("scheme with a long name", "a".repeat(4900) + "://x/@all", false), Arguments.of("mailto prefixes", "mailto:".repeat(700) + " @all", true),
                Arguments.of("scheme-relative URLs", "//a".repeat(1600) + " @all", true));
    }
}
