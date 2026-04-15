package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.exercise.dto.ProblemStatementRenderRequestDTO;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.ResultSummaryInputDTO;
import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ProblemStatementRenderingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "psrendering";

    private static final String POST_URL = "/api/exercise/problem-statement/render";

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

        assertThat(result.html()).isNullOrEmpty();
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

        assertThat(result.html()).doesNotContain("katex");
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
    void shouldRejectDuplicateTestIds() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0), new TestFeedbackInputDTO(1L, "testB", false, null, 0.0));
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", testResults, null, "en", false, true, null);

        request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.BAD_REQUEST);
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
