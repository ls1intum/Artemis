package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.exercise.dto.ProblemStatementRenderRequestDTO;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.ResultSummaryInputDTO;
import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

class ProblemStatementRenderingIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "psrendering";

    private static final String POST_URL = "/api/exercise/problem-statement/render";

    private static final String FIXTURE_IMAGE_NAME = "test-fixture.png";

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
    }

    @AfterEach
    void cleanUpFixtureImage() throws IOException {
        Path fixturePath = FilePathConverter.getMarkdownFilePath().resolve(FIXTURE_IMAGE_NAME);
        Files.deleteIfExists(fixturePath);
    }

    // --- Basic rendering ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderPlainMarkdown() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hello\n\nThis is **bold** text.", null, null, "en", false, true, null, null);

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
        var body = new ProblemStatementRenderRequestDTO("| Col A | Col B |\n|-------|-------|\n| 1     | 2     |", null, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<table");
        assertThat(result.html()).contains("<th>Col A</th>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyForBlankMarkdown() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("   \n  \t  ", null, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).isEmpty();
    }

    // --- XSS / Sanitization ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripScriptTags() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("<script>alert('xss')</script>\n\nSafe text", null, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("alert('xss')");
        assertThat(result.html()).contains("Safe text");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripEventHandlers() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("<img src=x onerror=alert('xss')>\n\n<a href=\"javascript:alert('xss')\">click</a>", null, null, "en", false, true, null,
                null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("<img src=x onerror");
        assertThat(result.html()).doesNotContain("href=\"javascript:");
    }

    // --- Tasks with test results ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderTasksWithTestResults() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testSort", true, null, 1.0), new TestFeedbackInputDTO(2L, "testEdge", false, "Array index out of bounds", 0.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Sort Method](<testid>1</testid>,<testid>2</testid>)", testResults, null, "en", false, true, null, null);

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
        var body = new ProblemStatementRenderRequestDTO("[task][Task A](<testid>1</testid>)", testResults, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-success");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNotExecutedWhenTestMissing() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Task](<testid>1</testid>,<testid>999</testid>)", testResults, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-not-executed");
        assertThat(result.html()).contains("artemis-icon-no-result");
        assertThat(result.html()).doesNotContain("artemis-icon-fail");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNoResultWhenNoTestResults() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](<testid>1</testid>)", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("data-feedback");
        assertThat(result.html()).contains("artemis-task-no-result");
        assertThat(result.html()).contains("data-test-status=\"no-result\"");
        assertThat(result.html()).contains("artemis-icon-no-result");
        assertThat(result.html()).contains("No results");
        assertThat(result.html()).doesNotContain("artemis-icon-fail");
        assertThat(result.html()).doesNotContain("artemis-icon-success");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNoTestsWhenTaskHasNoTests() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][Empty]()", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-no-tests");
        assertThat(result.html()).contains("data-test-status=\"no-tests\"");
        assertThat(result.html()).contains("No tests");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNoTestsEvenWithTestResults() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Empty]()", testResults, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-no-tests");
        assertThat(result.html()).contains("No tests");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNoResultForNamedTestRefWithoutSubmission() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](testBubbleSort())", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-no-result");
        assertThat(result.html()).contains("data-test-status=\"no-result\"");
        assertThat(result.html()).contains("No results");
        assertThat(result.html()).doesNotContain("artemis-task-no-tests");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNotExecutedForNamedTestRefWithSubmission() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](testBubbleSort())", testResults, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-not-executed");
        assertThat(result.html()).doesNotContain("artemis-task-success");
        assertThat(result.html()).doesNotContain("artemis-icon-fail");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNotExecutedForMixedRefsWithSubmission() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](<testid>1</testid>,testB())", testResults, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-not-executed");
        assertThat(result.html()).doesNotContain("artemis-task-success");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNoTestsForWhitespaceOnlyRefs() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][Sort]( )", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-no-tests");
        assertThat(result.html()).contains("No tests");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderNullPassedAsNotExecuted() throws Exception {
        var feedback = new TestFeedbackInputDTO(1L, "testA", null, null, null);
        var body = new ProblemStatementRenderRequestDTO("[task][Task A](<testid>1</testid>)", List.of(feedback), null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-test-status=\"not-executed\"");
        assertThat(result.html()).doesNotContain("data-test-status=\"fail\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLocalizeNoResultInGerman() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](<testid>1</testid>)", null, null, "de", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("Keine Ergebnisse");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEscapeHtmlInFeedbackMessage() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "test", false, "Expected <div>hello</div> but got \"error\"", 0.0));
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", testResults, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("&lt;div&gt;");
    }

    // --- Result summary ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEmbedResultSummary() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "test", true, null, 1.0));
        var resultSummary = new ResultSummaryInputDTO(92.3, 10.0, 2.0, "deadbeef123", "2025-12-01T10:00:00Z", "AUTOMATIC");
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", testResults, resultSummary, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-result");
        assertThat(result.html()).contains("deadbeef123");
        assertThat(result.html()).contains("92.3");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotEmbedResultWhenNull() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", null, null, "en", false, false, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("data-result");
    }

    // --- PlantUML ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldInlineSvg() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("@startuml\n!pragma layout smetana\nclass A\n@enduml", null, null, "en", false, true, null, null);

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
                null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // Assert the color was resolved (not that PlantUML rendered successfully — that's timing-dependent)
        assertThat(result.html()).doesNotContain("testsColor");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveTestsColorByTestName() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testClass[Vehicle]", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("@startuml\n!pragma layout smetana\nabstract class Vehicle <<abstract>> #text:testsColor(testClass[Vehicle]) {\n}\n@enduml",
                testResults, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("testsColor");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveTestsColorArrowByTestName() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testClass[Car]", false, null, 0.0));
        var body = new ProblemStatementRenderRequestDTO("@startuml\n!pragma layout smetana\nVehicle <|-- Car #testsColor(testClass[Car])\n@enduml", testResults, null, "en", false,
                true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("testsColor");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldColorPlantUmlGreyForNullPassed() throws Exception {
        String markdown = "@startuml\nclass A #testsColor(<testid>1</testid>)\n@enduml";
        var feedback = new TestFeedbackInputDTO(1L, "testA", null, null, null);
        var body = new ProblemStatementRenderRequestDTO(markdown, List.of(feedback), null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<svg");
    }

    // --- Locale ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLocalizeTaskStats() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "test", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", testResults, null, "de", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("von");
    }

    // --- Dark mode ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderPlantUmlWithDarkTheme() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("@startuml\n!pragma layout smetana\nclass A\n@enduml", null, null, "en", true, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<svg");
        assertThat(result.html()).doesNotContain("#FFFFFF");
    }

    // --- KaTeX / LaTeX ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderInlineAndDisplayMathFormulas() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("Inline $E = mc^2$ and display:\n$$\\int_0^1 x\\,dx$$", null, null, "en", false, true, null, null);

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
        var body = new ProblemStatementRenderRequestDTO("# No math here", null, null, "en", false, true, null, null);

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
    void shouldExposeNullVerdictAndNeutralGroupLabelForInteractiveScript() throws Exception {
        var notExecuted = new TestFeedbackInputDTO(1L, "testA", null, "no run", null);
        var failed = new TestFeedbackInputDTO(2L, "testB", false, null, null);
        var body = new ProblemStatementRenderRequestDTO("[task][A](<testid>1</testid>,<testid>2</testid>)", List.of(notExecuted, failed), null, "en", false, true, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // The script distinguishes the three buckets by `passed === true / === false / neither`, so a null verdict
        // must be serialized as null rather than being coerced to false or dropped.
        assertThat(result.html()).contains("&quot;passed&quot;:null");
        assertThat(result.html()).contains("&quot;passed&quot;:false");
        assertThat(result.interactiveScript()).contains("notExecutedTests");
        assertThat(result.html()).contains("data-test-status=\"fail\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExcludeScriptWhenNotInteractive() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hello", null, null, "en", false, false, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.interactiveScript()).isNull();
        assertThat(result.html()).contains("<h1>Hello</h1>");
    }

    // --- Duplicate test ID rejection ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectDuplicateTestIdsWithProblemDetail() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0), new TestFeedbackInputDTO(1L, "testB", false, null, 0.0));
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", testResults, null, "en", false, true, null, null);

        // Body is not asserted because the shared test helper returns null for non-2xx responses.
        // The behavior under test here is that the status is 422 (i.e. validation-stage errors map to
        // Unprocessable Content rather than Bad Request), which MockMvc enforces via the expected status.
        request.postWithResponseBody(POST_URL, body, String.class, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    // --- Task reference resolution by test name ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveTaskReferenceByTestName() throws Exception {
        var feedback = new TestFeedbackInputDTO(7L, "testDoOverlap()", true, null, null);
        var body = new ProblemStatementRenderRequestDTO("[task][Overlap](testDoOverlap())", List.of(feedback), null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-test-status=\"success\"");
        assertThat(result.html()).contains("data-test-ids=\"7\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveParameterizedNameContainingComma() throws Exception {
        var feedback = new TestFeedbackInputDTO(3L, "testInsert(InsertMock, 1)", false, null, null);
        var body = new ProblemStatementRenderRequestDTO("[task][Insert](testInsert(InsertMock, 1))", List.of(feedback), null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-test-status=\"fail\"");
        assertThat(result.html()).contains("data-test-ids=\"3\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldMatchTestNamesCaseSensitivelyAndKeepParentheses() throws Exception {
        var feedback = new TestFeedbackInputDTO(4L, "testFoo()", true, null, null);
        var body = new ProblemStatementRenderRequestDTO("[task][A](TESTFOO()),[task][B](testFoo)", List.of(feedback), null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // Neither the differently-cased nor the parenthesis-stripped variant may resolve.
        assertThat(result.html()).doesNotContain("data-test-status=\"success\"");
        // Positive anchor: both tasks must still have rendered as spans with their own authored reference, so this
        // assertion cannot pass vacuously if task rendering broke entirely and emitted no spans at all.
        assertThat(result.html()).contains("data-authored-count=\"1\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveMixedIdAndNameReferences() throws Exception {
        var byId = new TestFeedbackInputDTO(1L, "testById", true, null, null);
        var byName = new TestFeedbackInputDTO(2L, "testByName()", true, null, null);
        var body = new ProblemStatementRenderRequestDTO("[task][Mixed](<testid>1</testid>,testByName())", List.of(byId, byName), null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-test-status=\"success\"");
        assertThat(result.html()).contains("data-authored-count=\"2\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderStatsTextWhenAuthoredIdHasNoFeedbackEntry() throws Exception {
        var known = new TestFeedbackInputDTO(1L, "testA", true, null, null);
        // includeCss=false so the embedded stylesheet's `[data-feedback]` selector cannot produce a false negative
        // for the "attribute absent" assertion below.
        var body = new ProblemStatementRenderRequestDTO("[task][X](<testid>99</testid>)", List.of(known), null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // The referenced id (99) carries no feedback, so data-feedback must not be emitted for it, but the stats
        // line must still render using the authored count — results were supplied, just not for this task's tests.
        assertThat(result.html()).doesNotContain("data-feedback");
        assertThat(result.html()).contains("0 of 1 tests passed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCountUnresolvedReferencesAsNotExecutedInStats() throws Exception {
        var known = new TestFeedbackInputDTO(1L, "testKnown()", true, null, null);
        var body = new ProblemStatementRenderRequestDTO("[task][Partial](testKnown(),testMissing())", List.of(known), null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-test-status=\"not-executed\"");
        assertThat(result.html()).contains("data-authored-count=\"2\"");
        assertThat(result.html()).contains("data-not-executed-count=\"1\"");
        // The stats denominator must be the authored count (2), not the resolved count (1) — this is the
        // headline behavior of this task, so assert on the rendered text, not just the data attributes.
        assertThat(result.html()).contains("1 of 2 tests passed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldUseAuthoredCountAsStatsDenominatorWithPartialResolution() throws Exception {
        var testA = new TestFeedbackInputDTO(1L, "testA()", true, null, null);
        var testB = new TestFeedbackInputDTO(2L, "testB()", true, null, null);
        var testC = new TestFeedbackInputDTO(3L, "testC()", true, null, null);
        var body = new ProblemStatementRenderRequestDTO("[task][Five](testA(),testB(),testC(),testD(),testE())", List.of(testA, testB, testC), null, "en", false, false, true,
                null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // 3 of 5 authored references resolve and pass; testD() and testE() are unresolvable. The denominator must
        // read "5" (authored), not "3" (resolved) — a task with unresolvable refs must not look fully accounted for.
        assertThat(result.html()).contains("data-authored-count=\"5\"");
        assertThat(result.html()).contains("data-not-executed-count=\"2\"");
        assertThat(result.html()).contains("3 of 5 tests passed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectDuplicateTestNames() throws Exception {
        var first = new TestFeedbackInputDTO(1L, "sameName()", true, null, null);
        var second = new TestFeedbackInputDTO(2L, "sameName()", false, null, null);
        var body = new ProblemStatementRenderRequestDTO("[task][Dup](sameName())", List.of(first, second), null, "en", false, false, true, null);

        request.postWithoutResponseBody(POST_URL, body, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    // --- PlantUML diagram limit ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLimitPlantUmlDiagrams() throws Exception {
        StringBuilder markdown = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            markdown.append("@startuml\n!pragma layout smetana\nclass C").append(i).append("\n@enduml\n\n");
        }
        var body = new ProblemStatementRenderRequestDTO(markdown.toString(), null, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("Diagram limit exceeded");
    }

    // --- Code block masking ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotProcessTaskInsideCodeBlock() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("```\n[task][Sneaky](<testid>1</testid>)\n```", null, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // Check that "Sneaky" was not processed as a task — it should appear as code, not as a task span
        assertThat(result.html()).doesNotContain("data-task-name=\"Sneaky\"");
        assertThat(result.html()).contains("<code>");
    }

    // --- CSS toggle ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExcludeCssWhenFlagIsFalse() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hello", null, null, "en", false, true, false, null);

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
        var body = new ProblemStatementRenderRequestDTO("# Hello", null, null, "en", false, true, null, null);
        request.postWithResponseBody(POST_URL, body, String.class, HttpStatus.UNAUTHORIZED);
    }

    // --- Markdown validation ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectNullByteInMarkdown() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("hello\u0000world", null, null, "en", false, true, null, null);
        request.postWithResponseBody(POST_URL, body, String.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldAcceptMarkdownAtSizeLimit() throws Exception {
        String markdown = "a".repeat(100_000);
        var body = new ProblemStatementRenderRequestDTO(markdown, null, null, "en", false, false, null, null);
        request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectMarkdownOverSizeLimit() throws Exception {
        String markdown = "a".repeat(100_001);
        var body = new ProblemStatementRenderRequestDTO(markdown, null, null, "en", false, false, null, null);
        request.postWithResponseBody(POST_URL, body, String.class, HttpStatus.BAD_REQUEST);
    }

    // --- testid preservation inside code blocks ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldPreserveTestidInsideCodeBlock() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("`<testid>42</testid>`", null, null, "en", false, false, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<code>").contains("&lt;testid&gt;42&lt;/testid&gt;");
    }

    // --- Dark mode container marker ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldAddDarkModeClassOnContainer() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hi", null, null, "en", true, false, null, null);
        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);
        assertThat(result.html()).contains("artemis-problem-statement--dark");
    }

    // --- Body layout symmetry between light and dark ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderSymmetricBodyLayoutAcrossLightAndDarkMode() throws Exception {
        var lightReq = new ProblemStatementRenderRequestDTO("# Hi", null, null, "en", false, true, null, null);
        var darkReq = new ProblemStatementRenderRequestDTO("# Hi", null, null, "en", true, true, null, null);

        RenderedProblemStatementDTO lightResult = request.postWithResponseBody(POST_URL, lightReq, RenderedProblemStatementDTO.class, HttpStatus.OK);
        RenderedProblemStatementDTO darkResult = request.postWithResponseBody(POST_URL, darkReq, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // Both modes emit the shared base class; only dark adds the --dark modifier.
        assertThat(lightResult.html()).contains("<body class=\"artemis-ssr-body\">").doesNotContain("artemis-ssr-body--dark");
        assertThat(darkResult.html()).contains("<body class=\"artemis-ssr-body artemis-ssr-body--dark\">");

        // The shared layout rule lives in embedded.css and fires in both modes. This locks the
        // invariant "light and dark differ in colors only, never in body layout".
        assertThat(lightResult.html()).contains("body.artemis-ssr-body {").contains("padding: var(--artemis-ssr-body-padding, 16px);");
        assertThat(darkResult.html()).contains("body.artemis-ssr-body {").contains("padding: var(--artemis-ssr-body-padding, 16px);");

        // Dark-mode CSS keeps a body rule for the backdrop color, but the old layout properties
        // have moved to the base rule in embedded.css.
        assertThat(darkResult.html()).contains("body.artemis-ssr-body--dark {").contains("background: var(--body-bg, #1e1e1e);");
    }

    // --- Interactive script shape ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEmitInteractiveScriptWithExpectedStructure() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hello", null, null, "en", false, true, null, null);
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
        var body1 = new ProblemStatementRenderRequestDTO("# First", null, null, "en", false, true, null, null);
        var body2 = new ProblemStatementRenderRequestDTO("# Second", null, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result1 = request.postWithResponseBody(POST_URL, body1, RenderedProblemStatementDTO.class, HttpStatus.OK);
        RenderedProblemStatementDTO result2 = request.postWithResponseBody(POST_URL, body2, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result1.rendererVersion()).isEqualTo("1.0.0");
        assertThat(result2.rendererVersion()).isEqualTo(result1.rendererVersion());
    }

    // --- Image inlining ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLeaveAbsoluteUrlsWhenNotInlining() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("![img](/api/core/files/markdown/test.png)", null, null, "en", false, false, null, false);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("/api/core/files/markdown/test.png");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldDefaultToNotInlining() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("![img](/api/core/files/markdown/test.png)", null, null, "en", false, false, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("/api/core/files/markdown/test.png");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldProduceDifferentHashesForDifferentInliningSetting() throws Exception {
        var inlineBody = new ProblemStatementRenderRequestDTO("# Hello", null, null, "en", false, false, null, true);
        var urlBody = new ProblemStatementRenderRequestDTO("# Hello", null, null, "en", false, false, null, false);

        RenderedProblemStatementDTO inlineResult = request.postWithResponseBody(POST_URL, inlineBody, RenderedProblemStatementDTO.class, HttpStatus.OK);
        RenderedProblemStatementDTO urlResult = request.postWithResponseBody(POST_URL, urlBody, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(inlineResult.contentHash()).isNotEqualTo(urlResult.contentHash());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLeaveOriginalUrlForUnresolvableImage() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("![img](/api/core/files/markdown/nonexistent.png)", null, null, "en", false, false, null, false);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("/api/core/files/markdown/nonexistent.png");
    }

    // --- Image inlining (inlineImages=true) ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldInlineMarkdownImageAsBase64DataUri() throws Exception {
        byte[] pngBytes = createMinimalPng();
        Path markdownDir = FilePathConverter.getMarkdownFilePath();
        Files.createDirectories(markdownDir);
        FileUtils.writeByteArrayToFile(markdownDir.resolve(FIXTURE_IMAGE_NAME).toFile(), pngBytes);

        var body = new ProblemStatementRenderRequestDTO("![img](/api/core/files/markdown/" + FIXTURE_IMAGE_NAME + ")", null, null, "en", false, false, null, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data:image/png;base64,");
        assertThat(result.html()).doesNotContain("/api/core/files/markdown/" + FIXTURE_IMAGE_NAME);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotInlineImageWithPathTraversal() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("![img](/api/core/files/markdown/..%2Fpasswd.png)", null, null, "en", false, false, null, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("data:image/png;base64,");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotInlineNonAllowedExtension() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("![doc](/api/core/files/markdown/readme.txt)", null, null, "en", false, false, null, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("data:image/png;base64,");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotInlineNonexistentImage() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("![img](/api/core/files/markdown/doesnotexist.png)", null, null, "en", false, false, null, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("data:image/png;base64,");
    }

    private static byte[] createMinimalPng() {
        return new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1 pixel
                0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53, // 8-bit RGB
                (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, // IDAT chunk
                0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01, (byte) 0xE2, 0x21, (byte) 0xBC, 0x33, // CRC
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, // IEND chunk
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82 };
    }
}
