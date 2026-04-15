package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the SVG sanitizer in {@link ProblemStatementRenderingService}.
 * Tests the package-private {@code sanitizeSvg} method directly.
 */
class SvgSanitizerTest {

    private static final String SVG_WRAPPER = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">%s</svg>";

    private static String wrapSvg(String content) {
        return SVG_WRAPPER.formatted(content);
    }

    @Test
    void shouldRemoveScriptElement() {
        String svg = wrapSvg("<rect width=\"10\" height=\"10\"/><script>alert('xss')</script>");
        String result = ProblemStatementRenderingService.sanitizeSvg(svg);
        assertThat(result).doesNotContain("<script");
        assertThat(result).doesNotContain("alert");
        assertThat(result).contains("<rect");
    }

    @Test
    void shouldRemoveForeignObjectElement() {
        String svg = wrapSvg("<foreignObject><div xmlns=\"http://www.w3.org/1999/xhtml\">evil</div></foreignObject>");
        String result = ProblemStatementRenderingService.sanitizeSvg(svg);
        assertThat(result).doesNotContain("foreignObject");
        assertThat(result).doesNotContain("evil");
    }

    @Test
    void shouldRemoveUseAndImageElements() {
        String svg = wrapSvg("<use href=\"#malicious\"/><image href=\"http://evil.com/img.png\"/><rect width=\"5\" height=\"5\"/>");
        String result = ProblemStatementRenderingService.sanitizeSvg(svg);
        assertThat(result).doesNotContain("<use");
        assertThat(result).doesNotContain("<image");
        assertThat(result).contains("<rect");
    }

    @Test
    void shouldRemoveEventHandlerAttributes() {
        String svg = wrapSvg("<rect width=\"10\" height=\"10\" onload=\"alert('xss')\" onclick=\"evil()\"/>");
        String result = ProblemStatementRenderingService.sanitizeSvg(svg);
        assertThat(result).doesNotContain("onload");
        assertThat(result).doesNotContain("onclick");
        assertThat(result).contains("<rect");
    }

    @Test
    void shouldStripDangerousHref() {
        String svg = wrapSvg("<a href=\"javascript:alert('xss')\"><text>click</text></a>");
        String result = ProblemStatementRenderingService.sanitizeSvg(svg);
        assertThat(result).doesNotContain("javascript:");
        assertThat(result).contains("<text");

        // data: scheme should also be stripped
        String svg2 = wrapSvg("<a xlink:href=\"data:text/html,<script>alert(1)</script>\"><text>click</text></a>");
        String result2 = ProblemStatementRenderingService.sanitizeSvg(svg2);
        assertThat(result2).doesNotContain("data:");
    }

    @Test
    void shouldStripDangerousCssInStyleAttribute() {
        String svg = wrapSvg("<rect width=\"10\" height=\"10\" style=\"background:url(http://evil.com/track.png)\"/>");
        String result = ProblemStatementRenderingService.sanitizeSvg(svg);
        assertThat(result).doesNotContain("evil.com");
    }

    @Test
    void shouldStripDangerousCssInStyleElement() {
        String svg = wrapSvg("<style>@import url(http://evil.com/styles.css);</style><rect width=\"10\" height=\"10\"/>");
        String result = ProblemStatementRenderingService.sanitizeSvg(svg);
        assertThat(result).doesNotContain("@import");
        assertThat(result).doesNotContain("evil.com");
        assertThat(result).contains("<rect");
    }

    @Test
    void shouldStripExternalUrlFromPresentationAttributes() {
        String svg = wrapSvg("<rect width=\"10\" height=\"10\" fill=\"url(http://evil.com/gradient)\"/>");
        String result = ProblemStatementRenderingService.sanitizeSvg(svg);
        assertThat(result).doesNotContain("evil.com");
    }

    @Test
    void shouldPreserveLocalFragmentRefInPresentationAttributes() {
        String svg = wrapSvg("<defs><linearGradient id=\"grad1\"/></defs><rect width=\"10\" height=\"10\" fill=\"url(#grad1)\"/>");
        String result = ProblemStatementRenderingService.sanitizeSvg(svg);
        assertThat(result).contains("url(#grad1)");
        assertThat(result).contains("linearGradient");
    }

    @Test
    void shouldRemoveProcessingInstructions() {
        String svg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + wrapSvg("<rect width=\"10\" height=\"10\"/>");
        String result = ProblemStatementRenderingService.sanitizeSvg(svg);
        assertThat(result).doesNotContain("<?xml");
        assertThat(result).contains("<rect");
    }

    @Test
    void shouldPreserveLocalFragmentRefs() {
        String svg = wrapSvg("<defs><clipPath id=\"clip1\"><rect width=\"10\" height=\"10\"/></clipPath></defs>"
                + "<g clip-path=\"url(#clip1)\"><rect width=\"5\" height=\"5\" fill=\"url(#grad)\"/></g>");
        String result = ProblemStatementRenderingService.sanitizeSvg(svg);
        assertThat(result).contains("url(#clip1)");
        assertThat(result).contains("url(#grad)");
        assertThat(result).contains("clipPath");
    }

    @Test
    void shouldPreserveLegitPlantUmlSvg() {
        // Simplified but representative PlantUML-generated SVG structure
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
        String result = ProblemStatementRenderingService.sanitizeSvg(plantUmlSvg);
        // All structural elements should be preserved
        assertThat(result).contains("<svg");
        assertThat(result).contains("<rect");
        assertThat(result).contains("<text");
        assertThat(result).contains("<line");
        assertThat(result).contains("linearGradient");
        assertThat(result).contains("url(#bg)");
        assertThat(result).contains("MyClass");
        assertThat(result).contains("stop-color");
    }
}
