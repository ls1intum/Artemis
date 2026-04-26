package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SvgSanitizer}.
 */
class SvgSanitizerTest {

    private static final String SVG_WRAPPER = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">%s</svg>";

    private static String wrapSvg(String content) {
        return SVG_WRAPPER.formatted(content);
    }

    // --- Disallowed elements ---

    @Test
    void shouldRemoveScriptElement() {
        String result = SvgSanitizer.sanitize(wrapSvg("<rect width=\"10\" height=\"10\"/><script>alert('xss')</script>"));
        assertThat(result).isNotNull().doesNotContain("<script").doesNotContain("alert").contains("<rect");
    }

    @Test
    void shouldRemoveForeignObjectElement() {
        String result = SvgSanitizer.sanitize(wrapSvg("<foreignObject><div xmlns=\"http://www.w3.org/1999/xhtml\">evil</div></foreignObject>"));
        assertThat(result).isNotNull().doesNotContain("foreignObject").doesNotContain("evil");
    }

    @Test
    void shouldRemoveUseAndImageElements() {
        String result = SvgSanitizer.sanitize(wrapSvg("<use href=\"#malicious\"/><image href=\"http://evil.com/img.png\"/><rect width=\"5\" height=\"5\"/>"));
        assertThat(result).isNotNull().doesNotContain("<use").doesNotContain("<image").contains("<rect");
    }

    @Test
    void shouldRemoveSmilAnimationElements() {
        String result = SvgSanitizer.sanitize(wrapSvg("""
                <rect width="10" height="10">
                    <animate attributeName="fill" to="red" dur="1s"/>
                    <set attributeName="opacity" to="0"/>
                    <animateTransform attributeName="transform" type="rotate" values="0;360" dur="2s"/>
                    <animateMotion path="M0,0 L100,100" dur="3s"/>
                </rect>"""));
        assertThat(result).isNotNull().contains("<rect").doesNotContain("<animate").doesNotContain("<set").doesNotContain("animateTransform").doesNotContain("animateMotion");
    }

    @Test
    void shouldRemoveSetThatMutatesHrefToJavascript() {
        String result = SvgSanitizer.sanitize(wrapSvg("<a href=\"#safe\"><text>click</text><set attributeName=\"href\" to=\"javascript:alert(1)\" begin=\"0s\"/></a>"));
        assertThat(result).isNotNull().doesNotContain("<set").doesNotContain("javascript:");
    }

    // --- Event handlers ---

    @Test
    void shouldRemoveEventHandlerAttributes() {
        String result = SvgSanitizer.sanitize(wrapSvg("<rect width=\"10\" height=\"10\" onload=\"alert('xss')\" onclick=\"evil()\"/>"));
        assertThat(result).isNotNull().doesNotContain("onload").doesNotContain("onclick").contains("<rect");
    }

    @Test
    void shouldRemoveUppercaseEventHandlerAttributes() {
        String result = SvgSanitizer.sanitize(wrapSvg("<rect width=\"10\" height=\"10\" ONCLICK=\"evil()\"/>"));
        assertThat(result).isNotNull().doesNotContain("ONCLICK").doesNotContain("onclick").doesNotContain("evil()");
    }

    // --- Href scheme allowlist on <a> ---

    @Test
    void shouldStripDangerousSchemesOnAnchorHref() {
        String[] dangerous = { "javascript:alert(1)", "data:text/html,<script>alert(1)</script>", "vbscript:msgbox(1)", "blob:http://evil/x", "file:///etc/passwd",
                "ftp://evil.com/" };
        for (String scheme : dangerous) {
            String result = SvgSanitizer.sanitize(wrapSvg("<a href=\"" + scheme + "\"><text>click</text></a>"));
            assertThat(result).as("sanitized output must not contain dangerous scheme %s", scheme).isNotNull();
            // The attribute must be stripped; rendering of the <a> itself may stay.
            assertThat(result).doesNotContain(scheme);
        }
    }

    @Test
    void shouldPreserveAllowedSchemesOnAnchorHref() {
        String result = SvgSanitizer.sanitize(
                wrapSvg("<a href=\"https://example.com/\"><text>A</text></a>" + "<a href=\"http://example.com/\"><text>B</text></a><a href=\"mailto:x@y.com\"><text>C</text></a>"));
        assertThat(result).isNotNull().contains("https://example.com/").contains("http://example.com/").contains("mailto:x@y.com");
    }

    @Test
    void shouldPreserveLocalFragmentAndRelativeHrefOnAnchor() {
        String result = SvgSanitizer.sanitize(wrapSvg("<a href=\"#anchor\"><text>A</text></a><a href=\"./relative/path\"><text>B</text></a>"));
        assertThat(result).isNotNull().contains("href=\"#anchor\"").contains("href=\"./relative/path\"");
    }

    @Test
    void shouldStripXlinkHrefOnNonAnchorUnlessLocalFragment() {
        String result = SvgSanitizer.sanitize(wrapSvg("<text xlink:href=\"http://evil.com/\">click</text>"));
        assertThat(result).isNotNull().doesNotContain("evil.com");
    }

    @Test
    void shouldStripControlCharsFromHrefBeforeSchemeCheck() {
        // Tab and newline between 'java' and 'script:' would otherwise bypass a literal substring check.
        String tabbed = "java\tscript:alert(1)";
        String newlined = "java\nscript:alert(1)";
        assertThat(SvgSanitizer.sanitize(wrapSvg("<a href=\"" + tabbed + "\"><text>A</text></a>"))).isNotNull().doesNotContain("alert").doesNotContain("javascript:");
        assertThat(SvgSanitizer.sanitize(wrapSvg("<a href=\"" + newlined + "\"><text>A</text></a>"))).isNotNull().doesNotContain("alert").doesNotContain("javascript:");
    }

    // --- Presentation attributes / URLs ---

    @Test
    void shouldStripExternalUrlFromPresentationAttributes() {
        String result = SvgSanitizer.sanitize(wrapSvg("<rect width=\"10\" height=\"10\" fill=\"url(http://evil.com/gradient)\"/>"));
        assertThat(result).isNotNull().doesNotContain("evil.com");
    }

    @Test
    void shouldPreserveLocalFragmentRefInPresentationAttributes() {
        String result = SvgSanitizer.sanitize(wrapSvg("<defs><linearGradient id=\"grad1\"/></defs><rect width=\"10\" height=\"10\" fill=\"url(#grad1)\"/>"));
        assertThat(result).isNotNull().contains("url(#grad1)").contains("linearGradient");
    }

    @Test
    void shouldPreserveLocalFragmentRefsInClipPathAndFill() {
        String result = SvgSanitizer.sanitize(wrapSvg("<defs><clipPath id=\"clip1\"><rect width=\"10\" height=\"10\"/></clipPath></defs>"
                + "<g clip-path=\"url(#clip1)\"><rect width=\"5\" height=\"5\" fill=\"url(#grad)\"/></g>"));
        assertThat(result).isNotNull().contains("url(#clip1)").contains("url(#grad)").contains("clipPath");
    }

    // --- CSS ---

    @Test
    void shouldStripDangerousCssInStyleAttribute() {
        String result = SvgSanitizer.sanitize(wrapSvg("<rect width=\"10\" height=\"10\" style=\"background:url(http://evil.com/track.png)\"/>"));
        assertThat(result).isNotNull().doesNotContain("evil.com");
    }

    @Test
    void shouldStripDangerousCssInStyleElement() {
        String result = SvgSanitizer.sanitize(wrapSvg("<style>@import url(http://evil.com/styles.css);</style><rect width=\"10\" height=\"10\"/>"));
        assertThat(result).isNotNull().doesNotContain("@import").doesNotContain("evil.com").contains("<rect");
    }

    @Test
    void shouldRejectCssCommentObfuscatedExpression() {
        // ex/**/pression(...) would bypass a naive `expression(` match if comments are not stripped first.
        String result = SvgSanitizer.sanitize(wrapSvg("<style>rect { width: ex/**/pression(alert(1)); }</style><rect width=\"10\" height=\"10\"/>"));
        assertThat(result).isNotNull().doesNotContain("pression").doesNotContain("alert").contains("<rect");
    }

    @Test
    void shouldRejectCssEscapeSequences() {
        String result = SvgSanitizer.sanitize(wrapSvg("<style>rect { width: \\65 xpression(alert(1)); }</style><rect width=\"10\" height=\"10\"/>"));
        assertThat(result).isNotNull().doesNotContain("xpression").doesNotContain("alert").contains("<rect");
    }

    // --- Structural ---

    @Test
    void shouldRemoveProcessingInstructions() {
        String result = SvgSanitizer.sanitize("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + wrapSvg("<rect width=\"10\" height=\"10\"/>"));
        assertThat(result).isNotNull().doesNotContain("<?xml").contains("<rect");
    }

    @Test
    void shouldPreserveLegitPlantUmlSvg() {
        String plantUmlSvg = """
                <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 200 150" preserveAspectRatio="xMidYMid meet">
                <style>svg.uml{font-family:Arial;font-size:14px;}</style>
                <defs>
                <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#FEF9E7"/><stop offset="100%" stop-color="#FDEBD0"/></linearGradient>
                </defs>
                <g>
                <rect x="10" y="10" width="180" height="40" rx="5" ry="5" fill="url(#bg)" stroke="#A93226" stroke-width="1.5"/>
                <text x="100" y="35" text-anchor="middle" font-size="14" font-family="Arial" fill="#333333">MyClass</text>
                <line x1="10" y1="50" x2="190" y2="50" stroke="#A93226" stroke-width="1"/>
                </g>
                </svg>""";
        String result = SvgSanitizer.sanitize(plantUmlSvg);
        assertThat(result).isNotNull().contains("<svg").contains("<rect").contains("<text").contains("<line").contains("linearGradient").contains("url(#bg)").contains("MyClass")
                .contains("stop-color");
    }
}
