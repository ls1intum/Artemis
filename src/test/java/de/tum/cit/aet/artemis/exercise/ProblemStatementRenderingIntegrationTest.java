package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.exercise.dto.ProblemStatementRenderRequestDTO;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.ResultSummaryInputDTO;
import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ProblemStatementRenderingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "psrendering";

    private static final String POST_URL = "/api/exercise/problem-statement/render";

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
    }

    // --- Basic rendering ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderPlainMarkdown() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hello\n\nThis is **bold** text.", null, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).startsWith("<!DOCTYPE html>");
        assertThat(result.html()).contains("<html lang=\"en\">");
        assertThat(result.html()).contains("<meta charset=\"UTF-8\">");
        assertThat(result.html()).contains("<h1>Hello</h1>");
        assertThat(result.html()).contains("<strong>bold</strong>");
        assertThat(result.html()).contains("artemis-problem-statement");
        assertThat(result.rendererVersion()).isEqualTo("1.0.0");
        assertThat(result.contentHash()).isNotBlank();
        assertThat(result.interactiveScript()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderMarkdownTables() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("| Col A | Col B |\n|-------|-------|\n| 1     | 2     |", null, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<table");
        assertThat(result.html()).contains("<th>Col A</th>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyForBlankMarkdown() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("   \n  \t  ", null, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).isEmpty();
    }

    // --- XSS / Sanitization ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripScriptTags() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("<script>alert('xss')</script>\n\nSafe text", null, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("alert('xss')");
        assertThat(result.html()).contains("Safe text");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripEventHandlers() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("<img src=x onerror=alert('xss')>\n\n<a href=\"javascript:alert('xss')\">click</a>", null, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("<img src=x onerror");
        assertThat(result.html()).doesNotContain("href=\"javascript:");
    }

    // --- Tasks with test results ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderTasksWithTestResults() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testSort", true, null, 1.0), new TestFeedbackInputDTO(2L, "testEdge", false, "Array index out of bounds", 0.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Sort Method](<testid>1</testid>,<testid>2</testid>)", testResults, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-task-name");
        assertThat(result.html()).contains("Sort Method");
        assertThat(result.html()).contains("artemis-task-fail");
        assertThat(result.html()).contains("data-feedback");
        assertThat(result.html()).contains("Array index out of bounds");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowSuccessWhenAllTestsPass() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Task A](<testid>1</testid>)", testResults, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-success");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNotExecutedWhenTestMissing() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Task](<testid>1</testid>,<testid>999</testid>)", testResults, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-not-executed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderWithoutFeedbackWhenNoTestResults() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](<testid>1</testid>)", null, null, "en", false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("data-feedback");
        assertThat(result.html()).doesNotContain("data-test-status");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEscapeHtmlInFeedbackMessage() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "test", false, "Expected <div>hello</div> but got \"error\"", 0.0));
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", testResults, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("&lt;div&gt;");
    }

    // --- Result summary ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEmbedResultSummary() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "test", true, null, 1.0));
        var resultSummary = new ResultSummaryInputDTO(92.3, 10.0, 2.0, "deadbeef123", "2025-12-01T10:00:00Z", "AUTOMATIC");
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", testResults, resultSummary, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-result");
        assertThat(result.html()).contains("deadbeef123");
        assertThat(result.html()).contains("92.3");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotEmbedResultWhenNull() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", null, null, "en", false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("data-result");
    }

    // --- PlantUML ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldInlineSvg() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("@startuml\n!pragma layout smetana\nclass A\n@enduml", null, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<svg");
        // Document contains legitimate <script> tags (interactive JS), so only check SVG doesn't contain scripts
        assertThat(result.html()).doesNotContain("<script>alert");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveTestsColorInPlantUml() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(42L, "testSort", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("@startuml\n!pragma layout smetana\nclass A\n<color:testsColor(<testid>42</testid>)>colored</color>\n@enduml", testResults,
                null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // Assert the color was resolved (not that PlantUML rendered successfully — that's timing-dependent)
        assertThat(result.html()).doesNotContain("testsColor");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveTestsColorByTestName() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testClass[Vehicle]", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("@startuml\n!pragma layout smetana\nabstract class Vehicle <<abstract>> #text:testsColor(testClass[Vehicle]) {\n}\n@enduml",
                testResults, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("testsColor");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveTestsColorArrowByTestName() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testClass[Car]", false, null, 0.0));
        var body = new ProblemStatementRenderRequestDTO("@startuml\n!pragma layout smetana\nVehicle <|-- Car #testsColor(testClass[Car])\n@enduml", testResults, null, "en", false,
                true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("testsColor");
    }

    // --- Locale ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLocalizeTaskStats() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "test", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", testResults, null, "de", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("von");
    }

    // --- Dark mode ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderPlantUmlWithDarkTheme() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("@startuml\n!pragma layout smetana\nclass A\n@enduml", null, null, "en", true, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<svg");
        assertThat(result.html()).doesNotContain("#FFFFFF");
    }

    // --- KaTeX / LaTeX ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderInlineAndDisplayMathFormulas() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("Inline $E = mc^2$ and display:\n$$\\int_0^1 x\\,dx$$", null, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("class=\"katex-formula\"");
        assertThat(result.html()).contains("data-formula=\"E = mc^2\"");
        assertThat(result.html()).contains("data-display-mode=\"false\"");
        assertThat(result.html()).contains("data-display-mode=\"true\"");
        assertThat(result.html()).contains("/webjars/katex/dist/katex.min.css");
        assertThat(result.html()).contains("/webjars/katex/dist/katex.min.js");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotIncludeKatexResourcesWhenNoFormulas() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# No math here", null, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // Unrelated mentions of the word "katex" in source comments are fine; only the stylesheet/script
        // loads are what this test is about.
        assertThat(result.html()).doesNotContain("katex.min.css").doesNotContain("katex.min.js").doesNotContain("katex-formula");
    }

    @Test
    void shouldServeKatexResourcesAnonymously() throws Exception {
        request.get("/webjars/katex/dist/katex.min.css", HttpStatus.OK, String.class);
    }

    // --- Interactive toggle ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExcludeScriptWhenNotInteractive() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hello", null, null, "en", false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.interactiveScript()).isNull();
        assertThat(result.html()).contains("<h1>Hello</h1>");
    }

    // --- Duplicate test ID rejection ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectDuplicateTestIdsWithProblemDetail() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0), new TestFeedbackInputDTO(1L, "testB", false, null, 0.0));
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", testResults, null, "en", false, true, null);

        // Body is not asserted because the shared test helper returns null for non-2xx responses.
        // The behavior under test here is that the status is 422 (i.e. validation-stage errors map to
        // Unprocessable Content rather than Bad Request), which MockMvc enforces via the expected status.
        request.postWithResponseBody(POST_URL, body, String.class, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    // --- PlantUML diagram limit ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLimitPlantUmlDiagrams() throws Exception {
        StringBuilder markdown = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            markdown.append("@startuml\n!pragma layout smetana\nclass C").append(i).append("\n@enduml\n\n");
        }
        var body = new ProblemStatementRenderRequestDTO(markdown.toString(), null, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("Diagram limit exceeded");
    }

    // --- Code block masking ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotProcessTaskInsideCodeBlock() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("```\n[task][Sneaky](<testid>1</testid>)\n```", null, null, "en", false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // Check that "Sneaky" was not processed as a task — it should appear as code, not as a task span
        assertThat(result.html()).doesNotContain("data-task-name=\"Sneaky\"");
        assertThat(result.html()).contains("<code>");
    }

    // --- CSS toggle ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExcludeCssWhenFlagIsFalse() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hello", null, null, "en", false, true, false);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("<style>");
        assertThat(result.html()).doesNotContain("<link rel=\"stylesheet\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldDefaultIncludeFlagsToTrueWhenOmittedInJson() throws Exception {
        // Deliberately omit includeJs and includeCss so Jackson deserializes them as null.
        String rawBody = "{\"markdown\":\"# Hello\",\"locale\":\"en\",\"darkMode\":false}";

        var mvcResult = request.performMvcRequest(post(new URI(POST_URL)).contentType(MediaType.APPLICATION_JSON).content(rawBody)).andExpect(status().isOk()).andReturn();
        var result = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), RenderedProblemStatementDTO.class);

        assertThat(result.interactiveScript()).isNotNull();
        assertThat(result.html()).contains("<style>");
    }

    // --- Authentication ---

    @Test
    void shouldRejectUnauthenticated() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hello", null, null, "en", false, true, null);
        request.postWithResponseBody(POST_URL, body, String.class, HttpStatus.UNAUTHORIZED);
    }

    // --- Markdown validation ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectNullByteInMarkdown() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("hello\u0000world", null, null, "en", false, true, null);
        request.postWithResponseBody(POST_URL, body, String.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldAcceptMarkdownAtSizeLimit() throws Exception {
        String markdown = "a".repeat(100_000);
        var body = new ProblemStatementRenderRequestDTO(markdown, null, null, "en", false, false, null);
        request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectMarkdownOverSizeLimit() throws Exception {
        String markdown = "a".repeat(100_001);
        var body = new ProblemStatementRenderRequestDTO(markdown, null, null, "en", false, false, null);
        request.postWithResponseBody(POST_URL, body, String.class, HttpStatus.BAD_REQUEST);
    }

    // --- testid preservation inside code blocks ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldPreserveTestidInsideCodeBlock() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("`<testid>42</testid>`", null, null, "en", false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<code>").contains("&lt;testid&gt;42&lt;/testid&gt;");
    }

    // --- Dark mode container marker ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldAddDarkModeClassOnContainer() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hi", null, null, "en", true, false, null);
        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);
        assertThat(result.html()).contains("artemis-problem-statement--dark");
    }

    // --- Interactive script shape ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEmitInteractiveScriptWithExpectedStructure() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hello", null, null, "en", false, true, null);
        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);
        // Verify the rewritten interactive.js is what actually shipped. These identifiers are the
        // contract between the server (class names, ids) and the rewrite — a regression in the JS
        // that renames any of them will surface here.
        assertThat(result.interactiveScript()).isNotNull().contains("artemis-feedback-modal").contains("artemis-modal").contains("artemis-modal-backdrop").contains("aria-modal")
                .contains("artemis-problem-statement--dark").contains("WeakMap").doesNotContain("setStyles").doesNotContain("isDarkBackground");
    }

    // --- Renderer version stability ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnStableRendererVersion() throws Exception {
        var body1 = new ProblemStatementRenderRequestDTO("# First", null, null, "en", false, true, null);
        var body2 = new ProblemStatementRenderRequestDTO("# Second", null, null, "en", false, true, null);

        RenderedProblemStatementDTO result1 = request.postWithResponseBody(POST_URL, body1, RenderedProblemStatementDTO.class, HttpStatus.OK);
        RenderedProblemStatementDTO result2 = request.postWithResponseBody(POST_URL, body2, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result1.rendererVersion()).isEqualTo("1.0.0");
        assertThat(result2.rendererVersion()).isEqualTo(result1.rendererVersion());
    }
}
