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
        var body = new ProblemStatementRenderRequest("# Hello\n\nThis is **bold** text.", null, null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

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
        var body = new ProblemStatementRenderRequest("| Col A | Col B |\n|-------|-------|\n| 1     | 2     |", null, null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<table");
        assertThat(result.html()).contains("<th>Col A</th>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyForBlankMarkdown() throws Exception {
        var body = new ProblemStatementRenderRequest("   \n  \t  ", null, null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).isNullOrEmpty();
    }

    // --- XSS / Sanitization ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripScriptTags() throws Exception {
        var body = new ProblemStatementRenderRequest("<script>alert('xss')</script>\n\nSafe text", null, null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("<script>");
        assertThat(result.html()).contains("Safe text");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripEventHandlers() throws Exception {
        var body = new ProblemStatementRenderRequest("<img src=x onerror=alert('xss')>\n\n<a href=\"javascript:alert('xss')\">click</a>", null, null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("<img src=x onerror");
        assertThat(result.html()).doesNotContain("href=\"javascript:");
    }

    // --- Tasks with test results ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderTasksWithTestResults() throws Exception {
        var testResults = List.of(new TestFeedbackInput(1L, "testSort", true, null, 1.0), new TestFeedbackInput(2L, "testEdge", false, "Array index out of bounds", 0.0));
        var body = new ProblemStatementRenderRequest("[task][Sort Method](<testid>1</testid>,<testid>2</testid>)", testResults, null, "en", false, true);

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
        var testResults = List.of(new TestFeedbackInput(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequest("[task][Task A](<testid>1</testid>)", testResults, null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-success");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNotExecutedWhenTestMissing() throws Exception {
        var testResults = List.of(new TestFeedbackInput(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequest("[task][Task](<testid>1</testid>,<testid>999</testid>)", testResults, null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-not-executed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderWithoutFeedbackWhenNoTestResults() throws Exception {
        var body = new ProblemStatementRenderRequest("[task][Sort](<testid>1</testid>)", null, null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("data-feedback");
        assertThat(result.html()).doesNotContain("data-test-status");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEscapeHtmlInFeedbackMessage() throws Exception {
        var testResults = List.of(new TestFeedbackInput(1L, "test", false, "Expected <div>hello</div> but got \"error\"", 0.0));
        var body = new ProblemStatementRenderRequest("[task][T](<testid>1</testid>)", testResults, null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("&lt;div&gt;");
    }

    // --- Result summary ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEmbedResultSummary() throws Exception {
        var testResults = List.of(new TestFeedbackInput(1L, "test", true, null, 1.0));
        var resultSummary = new ResultSummaryInput(92.3, 10.0, 2.0, "deadbeef123", "2025-12-01T10:00:00Z", "AUTOMATIC");
        var body = new ProblemStatementRenderRequest("[task][T](<testid>1</testid>)", testResults, resultSummary, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-result");
        assertThat(result.html()).contains("deadbeef123");
        assertThat(result.html()).contains("92.3");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotEmbedResultWhenNull() throws Exception {
        var body = new ProblemStatementRenderRequest("[task][T](<testid>1</testid>)", null, null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("data-result");
    }

    // --- PlantUML ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldInlineSvg() throws Exception {
        var body = new ProblemStatementRenderRequest("@startuml\n!pragma layout smetana\nclass A\n@enduml", null, null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<svg");
        assertThat(result.html()).doesNotContain("<script");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveTestsColorInPlantUml() throws Exception {
        var testResults = List.of(new TestFeedbackInput(42L, "testSort", true, null, 1.0));
        var body = new ProblemStatementRenderRequest("@startuml\n!pragma layout smetana\nclass A\n<color:testsColor(<testid>42</testid>)>colored</color>\n@enduml", testResults,
                null, "en", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("green");
    }

    // --- Locale ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLocalizeTaskStats() throws Exception {
        var testResults = List.of(new TestFeedbackInput(1L, "test", true, null, 1.0));
        var body = new ProblemStatementRenderRequest("[task][T](<testid>1</testid>)", testResults, null, "de", false, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("von");
    }

    // --- Dark mode ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderPlantUmlWithDarkTheme() throws Exception {
        var body = new ProblemStatementRenderRequest("@startuml\n!pragma layout smetana\nclass A\n@enduml", null, null, "en", true, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<svg");
        assertThat(result.html()).doesNotContain("#FFFFFF");
    }

    // --- Interactive toggle ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExcludeScriptWhenNotInteractive() throws Exception {
        var body = new ProblemStatementRenderRequest("# Hello", null, null, "en", false, false);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.interactiveScript()).isNull();
        assertThat(result.html()).contains("<h1>Hello</h1>");
    }
}
