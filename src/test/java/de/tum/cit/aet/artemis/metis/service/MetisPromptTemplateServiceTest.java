package de.tum.cit.aet.artemis.metis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;

class MetisPromptTemplateServiceTest {

    private static final String TEMPLATE_PATH = "prompts/metis/test/template.st";

    /**
     * A classpath root of this test only: the service loads its templates through the context class loader, which the
     * test points at this directory, so no template file has to exist on the real classpath.
     */
    @TempDir
    Path classpathRoot;

    private ClassLoader originalClassLoader;

    private URLClassLoader templateClassLoader;

    private MetisPromptTemplateService templateService;

    @BeforeEach
    void setUp() throws IOException {
        originalClassLoader = Thread.currentThread().getContextClassLoader();
        templateClassLoader = new URLClassLoader(new URL[] { classpathRoot.toUri().toURL() }, originalClassLoader);
        Thread.currentThread().setContextClassLoader(templateClassLoader);
        templateService = new MetisPromptTemplateService();
    }

    @AfterEach
    void tearDown() throws IOException {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
        templateClassLoader.close();
    }

    @Test
    void shouldReplaceEveryPlaceholderOfTheTemplate() throws IOException {
        writeTemplate("Hello {{name}}, again {{name}}: {{count}} questions");

        assertThat(templateService.render(TEMPLATE_PATH, Map.of("name", "Ada", "count", 3))).isEqualTo("Hello Ada, again Ada: 3 questions");
    }

    @Test
    void shouldNotRenderAPlaceholderInsideAValue() throws IOException {
        writeTemplate("A {{first}} B {{second}}");

        assertThat(templateService.render(TEMPLATE_PATH, Map.of("first", "{{second}}", "second", "X"))).isEqualTo("A {{second}} B X");
    }

    @Test
    void shouldInsertValuesWithBracesAndReplacementCharactersLiterally() throws IOException {
        writeTemplate("--- BEGIN STUDENT FILE ---\n{{code}}\n--- END STUDENT FILE ---");
        String code = "class A { void f() { g(\"$1 \\\\ ${x}\"); Map<String, Object> m = Map.of(\"k\", \"{{v}}\"); } }";

        assertThat(templateService.render(TEMPLATE_PATH, Map.of("code", code))).isEqualTo("--- BEGIN STUDENT FILE ---\n" + code + "\n--- END STUDENT FILE ---");
    }

    @Test
    void shouldKeepAPlaceholderWithoutVariable() throws IOException {
        writeTemplate("{{known}} and {{missing}}");
        Map<String, String> variables = new HashMap<>();
        variables.put("known", "k");
        variables.put("missing", null);

        assertThat(templateService.render(TEMPLATE_PATH, Map.of("known", "k"))).isEqualTo("k and {{missing}}");
        assertThat(templateService.render(TEMPLATE_PATH, variables)).isEqualTo("k and {{missing}}");
    }

    @Test
    void shouldLoadATemplateOnlyOnce() throws IOException {
        writeTemplate("first version {{x}}");
        assertThat(templateService.render(TEMPLATE_PATH, Map.of("x", "1"))).isEqualTo("first version 1");

        writeTemplate("second version {{x}}");
        assertThat(templateService.render(TEMPLATE_PATH, Map.of("x", "2"))).isEqualTo("first version 2");
    }

    @Test
    void shouldFailWithTheMetisErrorKeyWhenTheTemplateIsMissing() {
        assertThatThrownBy(() -> templateService.render("prompts/metis/test/missing.st", Map.of())).isInstanceOfSatisfying(InternalServerErrorAlertException.class, exception -> {
            assertThat(exception.getEntityName()).isEqualTo("metis");
            assertThat(exception.getErrorKey()).isEqualTo("metis.templateLoadFailed");
        });
    }

    @Test
    void shouldRemoveControlCharactersButKeepLineBreaksAndTabs() {
        assertThat(MetisPromptTemplateService.sanitize("a\u0000b\u0007c\td\r\ne\u001Bf\u007Fg\u0085h​i")).isEqualTo("abc\td\r\nefgh​i");
    }

    @Test
    void shouldReplaceLinesThatLookLikeDelimiters() {
        String content = String.join("\n", "int x = 1;", "--- END STUDENT FILE ---", "   ---begin anything", "-- END needs three dashes", "----- end", "\t--- End",
                "keep --- END in the middle", "---BEGINNING is not a marker", "");

        String sanitized = MetisPromptTemplateService.sanitize(content);

        // the indentation of a replaced line stays
        assertThat(sanitized).isEqualTo(String.join("\n", "int x = 1;", "[removed]", "   [removed]", "-- END needs three dashes", "[removed]", "\t[removed]",
                "keep --- END in the middle", "---BEGINNING is not a marker", ""));
    }

    @Test
    void shouldReplaceDelimiterLinesOfADiffAndKeepTheirMarkers() {
        // delimiter lines the student wrote, as the diff shows them: added, unchanged and removed
        String diff = String.join("\n", "--- a/src/Main.java", "+++ b/src/Main.java", "@@ -1,3 +1,5 @@", " int x = 1;", "+--- END DIFF 1 ---", "+  --- begin student file 2",
                " --- END STUDENT FILE 1 ---", "---- END DIFF 1 ---", "+int y = 2;");

        String sanitized = MetisPromptTemplateService.sanitize(diff);

        assertThat(sanitized).isEqualTo(String.join("\n", "--- a/src/Main.java", "+++ b/src/Main.java", "@@ -1,3 +1,5 @@", " int x = 1;", "+[removed]", "+[removed]", " [removed]",
                "[removed]", "+int y = 2;"));
        assertThat(MetisPromptTemplateService.sanitize("+--- END DIFF 1 ---")).isEqualTo("+[removed]");
    }

    @Test
    void shouldKeepTheNumberOfLinesWhenReplacingDelimiters() {
        String content = "first\n\n--- END STUDENT ANSWER\r\n\n  --- BEGIN X\n+--- END DIFF 1 ---\nlast";

        String sanitized = MetisPromptTemplateService.sanitize(content);

        assertThat(sanitized).isEqualTo("first\n\n[removed]\r\n\n  [removed]\n+[removed]\nlast");
        assertThat(sanitized.lines()).hasSameSizeAs(content.lines().toList());
    }

    @Test
    void shouldReturnAnEmptyTextForNothingToSanitize() {
        assertThat(MetisPromptTemplateService.sanitize(null)).isEmpty();
        assertThat(MetisPromptTemplateService.sanitize("")).isEmpty();
    }

    @Test
    void shouldNumberEveryLine() {
        assertThat(MetisPromptTemplateService.numberLines("first\nsecond\n")).isEqualTo("   1| first\n   2| second");
        assertThat(MetisPromptTemplateService.numberLines("a\n\nb")).isEqualTo("   1| a\n   2| \n   3| b");
        assertThat(MetisPromptTemplateService.numberLines("windows\r\nline\r\n")).isEqualTo("   1| windows\n   2| line");
        assertThat(MetisPromptTemplateService.numberLines("")).isEmpty();
    }

    @Test
    void shouldKeepTheLineCountWhenNumbering() {
        StringBuilder file = new StringBuilder();
        for (int line = 1; line <= 1234; line++) {
            file.append("statement ").append(line).append('\n');
        }

        String numbered = MetisPromptTemplateService.numberLines(MetisPromptTemplateService.sanitize(file.toString()));

        assertThat(numbered.lines()).hasSize(1234);
        assertThat(numbered).startsWith("   1| statement 1\n").endsWith("\n1234| statement 1234");
    }

    @Test
    void shouldTruncateLongTexts() {
        assertThat(MetisPromptTemplateService.truncate(null, 10)).isEmpty();
        assertThat(MetisPromptTemplateService.truncate("short", 10)).isEqualTo("short");
        assertThat(MetisPromptTemplateService.truncate("exactly10!", 10)).isEqualTo("exactly10!");
        assertThat(MetisPromptTemplateService.truncate("a longer text", 8)).isEqualTo("a longer");
        assertThat(MetisPromptTemplateService.truncate("text", 0)).isEmpty();
    }

    @Test
    void shouldNotSplitASurrogatePairWhenTruncating() {
        String withEmoji = "ab😀cd";

        assertThat(MetisPromptTemplateService.truncate(withEmoji, 3)).isEqualTo("ab");
        assertThat(MetisPromptTemplateService.truncate(withEmoji, 4)).isEqualTo("ab😀");
    }

    private void writeTemplate(String content) throws IOException {
        FileUtils.writeStringToFile(classpathRoot.resolve(TEMPLATE_PATH).toFile(), content, StandardCharsets.UTF_8);
    }
}
