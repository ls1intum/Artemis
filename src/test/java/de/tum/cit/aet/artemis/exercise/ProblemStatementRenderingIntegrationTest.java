package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.exercise.dto.ProblemStatementRenderRequest;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.ResultSummaryInput;
import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInput;
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
        var body = new ProblemStatementRenderRequest("# Hello\n\nThis is **bold** text.", null, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<h1>Hello</h1>");
        assertThat(result.html()).contains("<strong>bold</strong>");
        assertThat(result.html()).contains("artemis-problem-statement");
        assertThat(result.rendererVersion()).isEqualTo("1.0.0-stateless");
        assertThat(result.contentHash()).isNotBlank();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderMarkdownTables() throws Exception {
        var body = new ProblemStatementRenderRequest("| Col A | Col B |\n|-------|-------|\n| 1     | 2     |", null, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<table");
        assertThat(result.html()).contains("<th>Col A</th>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyForBlankMarkdown() throws Exception {
        var body = new ProblemStatementRenderRequest("   \n  \t  ", null, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).isNullOrEmpty();
        assertThat(result.tasks()).isNullOrEmpty();
        assertThat(result.diagrams()).isNullOrEmpty();
    }

    // --- XSS / Sanitization ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripScriptTags() throws Exception {
        var body = new ProblemStatementRenderRequest("<script>alert('xss')</script>\n\nSafe text", null, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("<script>");
        assertThat(result.html()).contains("Safe text");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripEventHandlers() throws Exception {
        var body = new ProblemStatementRenderRequest("<img src=x onerror=alert('xss')>\n\n<a href=\"javascript:alert('xss')\">click</a>", null, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // CommonMark escapes raw HTML as text — the dangerous attributes must not appear as actual HTML
        assertThat(result.html()).doesNotContain("<img src=x onerror");
        assertThat(result.html()).doesNotContain("href=\"javascript:");
    }

    // --- Tasks with test results ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderTasksWithTestResults() throws Exception {
        var testResults = List.of(new TestFeedbackInput(1, "testSort", true, null, 1.0), new TestFeedbackInput(2, "testEdge", false, "Array index out of bounds", 0.0));
        var body = new ProblemStatementRenderRequest("[task][Sort Method](<testid>1</testid>,<testid>2</testid>)", testResults, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().getFirst().taskName()).isEqualTo("Sort Method");
        assertThat(result.tasks().getFirst().testStatus()).isEqualTo("fail");
        assertThat(result.html()).contains("data-feedback");
        assertThat(result.html()).contains("Array index out of bounds");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowSuccessWhenAllTestsPass() throws Exception {
        var testResults = List.of(new TestFeedbackInput(1, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequest("[task][Task A](<testid>1</testid>)", testResults, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.tasks().getFirst().testStatus()).isEqualTo("success");
        assertThat(result.html()).contains("artemis-task-success");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNotExecutedWhenTestMissing() throws Exception {
        var testResults = List.of(new TestFeedbackInput(1, "testA", true, null, 1.0));
        // Task references testId 1 and 999 — 999 has no result → not-executed
        var body = new ProblemStatementRenderRequest("[task][Task](<testid>1</testid>,<testid>999</testid>)", testResults, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.tasks().getFirst().testStatus()).isEqualTo("not-executed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderWithoutFeedbackWhenNoTestResults() throws Exception {
        var body = new ProblemStatementRenderRequest("[task][Sort](<testid>1</testid>)", null, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("data-feedback");
        assertThat(result.tasks().getFirst().testStatus()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEscapeHtmlInFeedbackMessage() throws Exception {
        var testResults = List.of(new TestFeedbackInput(1, "test", false, "Expected <div>hello</div> but got \"error\"", 0.0));
        var body = new ProblemStatementRenderRequest("[task][T](<testid>1</testid>)", testResults, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("&lt;div&gt;");
    }

    // --- Result summary ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEmbedResultSummary() throws Exception {
        var testResults = List.of(new TestFeedbackInput(1, "test", true, null, 1.0));
        var resultSummary = new ResultSummaryInput(92.3, 10.0, 2.0, "deadbeef123", "2025-12-01T10:00:00Z", "AUTOMATIC");
        var body = new ProblemStatementRenderRequest("[task][T](<testid>1</testid>)", testResults, resultSummary, true, true, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-result");
        assertThat(result.html()).contains("deadbeef123");
        assertThat(result.html()).contains("92.3");
        assertThat(result.html()).contains("AUTOMATIC");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotEmbedResultWhenNull() throws Exception {
        var body = new ProblemStatementRenderRequest("[task][T](<testid>1</testid>)", null, null, true, true, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("data-result");
    }

    // --- Assets ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReportClientRenderingRequiredForKatex() throws Exception {
        var body = new ProblemStatementRenderRequest("Formula: $$E=mc^2$$", null, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.assets().katex()).isEqualTo("client-rendering-required");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReportClientRenderingRequiredForHighlighting() throws Exception {
        var body = new ProblemStatementRenderRequest("```java\nclass Foo {}\n```", null, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.assets().highlighting()).isEqualTo("client-rendering-required");
    }

    // --- Self-contained + Interactive ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIncludeInteractiveScriptWhenSelfContained() throws Exception {
        var body = new ProblemStatementRenderRequest("# Hello", null, null, true, true, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.interactiveScript()).isNotNull();
        assertThat(result.interactiveScript()).contains("artemis-feedback-modal");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExcludeInteractiveScriptWhenDisabled() throws Exception {
        var body = new ProblemStatementRenderRequest("# Hello", null, null, true, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.interactiveScript()).isNull();
        assertThat(result.html()).contains("cursor:default");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExcludeInteractiveScriptWhenNotSelfContained() throws Exception {
        var body = new ProblemStatementRenderRequest("# Hello", null, null, false, true, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.interactiveScript()).isNull();
    }

    // --- PlantUML ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldInlineSanitizedSvg() throws Exception {
        var body = new ProblemStatementRenderRequest("@startuml\n!pragma layout smetana\nclass A\n@enduml", null, null, true, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.diagrams()).hasSize(1);
        assertThat(result.diagrams().getFirst().inlineSvg()).contains("<svg");
        assertThat(result.diagrams().getFirst().inlineSvg()).doesNotContain("<script");
        assertThat(result.diagrams().getFirst().inlineSvg()).doesNotContain("onload");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnUrlModeWhenNotSelfContained() throws Exception {
        var body = new ProblemStatementRenderRequest("@startuml\n!pragma layout smetana\nclass A\n@enduml", null, null, false, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.diagrams()).hasSize(1);
        assertThat(result.diagrams().getFirst().renderMode()).isEqualTo("url");
        assertThat(result.diagrams().getFirst().inlineSvg()).isNull();
        assertThat(result.diagrams().getFirst().svgUrl()).contains("plantuml/svg");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveTestsColorInPlantUml() throws Exception {
        var testResults = List.of(new TestFeedbackInput(42, "testSort", true, null, 1.0));
        var body = new ProblemStatementRenderRequest("@startuml\n!pragma layout smetana\nclass A\n<color:testsColor(<testid>42</testid>)>colored</color>\n@enduml", testResults,
                null, true, false, "en");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.diagrams().getFirst().testIds()).contains(42L);
        assertThat(result.diagrams().getFirst().inlineSvg()).contains("green");
    }

    // --- Locale ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLocalizeTaskStats() throws Exception {
        var testResults = List.of(new TestFeedbackInput(1, "test", true, null, 1.0));
        var body = new ProblemStatementRenderRequest("[task][T](<testid>1</testid>)", testResults, null, false, false, "de");

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("von");
    }
}
