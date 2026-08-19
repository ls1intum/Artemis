package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.exercise.dto.ProblemStatementRenderRequestDTO;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.ResultSummaryInputDTO;
import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;
import de.tum.cit.aet.artemis.exercise.service.ProblemStatementRenderingConfiguration;
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
        assertThat(result.rendererVersion()).isEqualTo("1.3.0");
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
    void shouldEmitOneFeedbackEntryPerTestWhenATaskRepeatsAReference() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testSort", false, "Array index out of bounds", 0.0));
        String repeated = String.join(",", Collections.nCopies(500, "<testid>1</testid>"));
        var body = new ProblemStatementRenderRequestDTO("[task][Sort Method](" + repeated + ")", testResults, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // The dialog shows one row per test however often the task names it, so a repeated reference used to add
        // nothing but bytes: with the longest message the request DTO permits, this shape produced a response
        // thousands of times the size of the request.
        assertThat(StringUtils.countOccurrencesOf(result.html(), "Array index out of bounds")).isEqualTo(1);
        assertThat(result.html()).contains("data-feedback");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEmitOneFeedbackEntryPerTestWhenManySeparateTasksNameIt() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testSort", false, "Array index out of bounds", 0.0));
        // Separate markers rather than one marker repeating a reference: deduplicating within a task would leave this
        // shape untouched, and it fits thousands of markers into the permitted request size.
        String markdown = "[task][T](<testid>1</testid>)\n".repeat(1000);
        var body = new ProblemStatementRenderRequestDTO(markdown, testResults, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(StringUtils.countOccurrencesOf(result.html(), "Array index out of bounds")).isEqualTo(1);
        // Every task still offers the feedback; it names the test rather than carrying a copy of it.
        assertThat(StringUtils.countOccurrencesOf(result.html(), "data-feedback=\"1\"")).isEqualTo(1000);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCarryTheFeedbackPayloadOnTheContainer() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(7L, "testEdge", false, "boom", 0.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Edge](<testid>7</testid>)", testResults, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // The container holds the entries keyed by test id; the task names the id it can show.
        assertThat(result.html()).contains("<div class=\"artemis-problem-statement\" data-feedback=\"{&quot;7&quot;:{&quot;name&quot;:&quot;testEdge&quot;");
        assertThat(result.html()).contains("data-test-ids=\"7\"");
        assertThat(result.html()).contains("data-feedback=\"7\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLeaveUnclosedDiagramMarkersAsWritten() throws Exception {
        // No `@enduml` follows, so nothing is a diagram and every marker stays in the prose. Asserted because the
        // scan that establishes this used to run once per marker over the whole remaining input.
        String markdown = "@startuml\n".repeat(2000);
        var body = new ProblemStatementRenderRequestDTO(markdown, null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("artemis-diagram");
        assertThat(result.html()).contains("@startuml");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStillRenderADiagramThatFollowsAnUnclosedMarker() throws Exception {
        // The scan stops at the first opening marker without a closing one. This pins that a diagram *before* such a
        // marker is still rendered, and that the trailing text is preserved.
        var body = new ProblemStatementRenderRequestDTO("@startuml\nAlice -> Bob\n@enduml\n\ntrailing @startuml never closed", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-diagram");
        assertThat(result.html()).contains("trailing");
        assertThat(result.html()).contains("never closed");
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
    void shouldTreatOutOfRangeTestIdAsUnresolvedInsteadOfFailing() throws Exception {
        // Problem statements are author-controlled: a digit sequence that does not fit into a long must render as an
        // unresolved reference, not blow up the request with an internal server error.
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](<testid>1</testid>,<testid>999999999999999999999999</testid>)", testResults, null, "en", false, false, false,
                null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-not-executed");
        assertThat(result.html()).contains("data-test-ids=\"1\"");
        assertThat(result.html()).doesNotContain("999999999999999999999999");
    }

    // --- "All tests passed" without per-test feedback ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowSuccessWhenAllTestsPassedWithoutFeedback() throws Exception {
        // A successful result carrying no feedback at all: the client cannot map any test, so it only sends the flag.
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](<testid>1</testid>,testBubbleSort())", null, null, "en", false, false, false, null, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-success");
        assertThat(result.html()).contains("data-test-status=\"success\"");
        assertThat(result.html()).contains("artemis-icon-success");
        // The counts must follow the status: the name-only reference cannot resolve without results, so counting
        // resolved ids would claim "1 of 2" and mark the rest as not executed under a green icon.
        assertThat(result.html()).contains("data-authored-count=\"2\"");
        assertThat(result.html()).contains("data-not-executed-count=\"0\"");
        assertThat(result.html()).contains("2 of 2 tests passed");
        assertThat(result.html()).doesNotContain("No results");
        assertThat(result.html()).doesNotContain("data-feedback");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldKeepNoTestsWhenAllTestsPassedAndTaskHasNoRefs() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][Empty]()", null, null, "en", false, false, false, null, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-no-tests");
        assertThat(result.html()).contains("No tests");
        assertThat(result.html()).doesNotContain("artemis-task-success");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLetPassingTestResultsWinOverAllTestsPassed() throws Exception {
        // Contradictory request: the flag plus actual results. The results decide, so the unresolvable second
        // reference keeps the task at "not executed" instead of turning it green.
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](<testid>1</testid>,<testid>999</testid>)", testResults, null, "en", false, false, false, null, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-not-executed");
        assertThat(result.html()).doesNotContain("artemis-task-success");
        assertThat(result.html()).contains("data-not-executed-count=\"1\"");
        assertThat(result.html()).contains("1 of 2 tests passed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLetAFailingTestResultWinOverAllTestsPassed() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", false, "boom", 0.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](<testid>1</testid>)", testResults, null, "en", false, false, false, null, true);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-fail");
        assertThat(result.html()).doesNotContain("artemis-task-success");
        assertThat(result.html()).contains("0 of 1 tests passed");
        assertThat(result.html()).contains("data-not-executed-count=\"0\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIgnoreAllTestsPassedForAnEmptyResultList() throws Exception {
        // An empty list is "a result exists but maps no test case", which must stay not executed. Sent as raw JSON
        // because the DTO serializes with NON_EMPTY, which would drop an empty list before it reaches the server.
        String rawBody = """
                {"markdown":"[task][Sort](<testid>1</testid>)","testResults":[],"locale":"en","darkMode":false,"includeJs":false,"includeCss":false,"allTestsPassed":true}""";

        var mvcResult = request.performMvcRequest(post(new URI(POST_URL)).contentType(MediaType.APPLICATION_JSON).content(rawBody)).andExpect(status().isOk()).andReturn();
        var result = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), RenderedProblemStatementDTO.class);

        assertThat(result.html()).contains("artemis-task-not-executed");
        assertThat(result.html()).doesNotContain("artemis-task-success");
        assertThat(result.html()).contains("data-not-executed-count=\"1\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNoResultWhenAllTestsPassedIsFalse() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](<testid>1</testid>)", null, null, "en", false, false, false, null, false);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-no-result");
        assertThat(result.html()).contains("No results");
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
    void shouldShowNoTestsForSeparatorOnlyRefsWithTestResults() throws Exception {
        // A list of separators names no test, so the task must not be reported as passing just because results exist.
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Sort](,)", testResults, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-no-tests");
        assertThat(result.html()).contains("data-test-status=\"no-tests\"");
        assertThat(result.html()).doesNotContain("artemis-task-success");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNoTestsForSeparatorAndWhitespaceOnlyRefsWithTestResults() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0));
        var body = new ProblemStatementRenderRequestDTO("[task][Sort]( , , )", testResults, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("artemis-task-no-tests");
        assertThat(result.html()).doesNotContain("artemis-task-success");
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
    void shouldLeaveMarkersInsideNonBacktickCodeBlocksAlone() throws Exception {
        // CommonMark knows three more code constructs than the backtick fence: the tilde fence, the indented block,
        // and a multi-backtick inline span. A marker inside any of them is content the reader wants written out, not
        // something to expand, and the legacy pipeline leaves all of them alone.
        String markdown = "~~~\n[task][Tilde](testA)\n@startuml\nclass A\n@enduml\n~~~\n\n    [task][Indented](testB)\n\n``[task][Inline](testC)``\n";
        var body = new ProblemStatementRenderRequestDTO(markdown, null, null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // `data-task-name` rather than the class: the embedded stylesheet mentions `artemis-task` in its own
        // rules, so a class check would match the CSS instead of generated markup.
        assertThat(result.html()).doesNotContain("data-task-name").doesNotContain("<svg");
        assertThat(result.html()).contains("[task][Tilde](testA)").contains("[task][Indented](testB)").contains("[task][Inline](testC)");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldDecideAnAlertOnTheFirstInlineBlockOfTheQuote() throws Exception {
        // The client picks the first `inline` token of the blockquote, which markdown-it emits for a heading just as
        // for a paragraph. Looking only at the first paragraph disagreed in both directions, so the toggle would have
        // changed authored content: the heading below holds no marker and must keep the quote a quote.
        String headingFirst = "> # Intro\n> [!NOTE]\n> Body\n";
        var headingFirstBody = new ProblemStatementRenderRequestDTO(headingFirst, null, null, "en", false, false, true, null);

        RenderedProblemStatementDTO quoted = request.postWithResponseBody(POST_URL, headingFirstBody, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // Matched on the opening tag, not the bare class name: the embedded stylesheet carries `.markdown-alert`
        // rules of its own, so a bare check would find the CSS instead of generated markup.
        assertThat(quoted.html()).contains("<blockquote>").doesNotContain("<div class=\"markdown-alert");

        // ... and the mirror image: the marker sits in the heading, which is the first inline token, so it does apply.
        String markerInHeading = "> # [!NOTE]\n> Body\n";
        var markerInHeadingBody = new ProblemStatementRenderRequestDTO(markerInHeading, null, null, "en", false, false, true, null);

        RenderedProblemStatementDTO alerted = request.postWithResponseBody(POST_URL, markerInHeadingBody, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(alerted.html()).contains("<div class=\"markdown-alert markdown-alert-note\">").doesNotContain("<blockquote>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReadTheAlertMarkerAsAuthoredRatherThanFlattened() throws Exception {
        // The client matches its regex against markdown-it's raw inline content, so a marker wrapped in emphasis
        // starts with `**` there and never matches. Flattening the AST first would drop those delimiters and turn the
        // quote into an alert, which is the toggle changing authored content.
        String emphasised = "> **[!NOTE]**\n> Body\n";
        var emphasisedBody = new ProblemStatementRenderRequestDTO(emphasised, null, null, "en", false, false, true, null);

        RenderedProblemStatementDTO quoted = request.postWithResponseBody(POST_URL, emphasisedBody, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(quoted.html()).contains("<blockquote>").doesNotContain("<div class=\"markdown-alert");

        // The same reading keeps a custom title's delimiters, which the client carries through verbatim as well.
        String formattedTitle = "> [!WARNING] **Read** this\n> Body\n";
        var formattedTitleBody = new ProblemStatementRenderRequestDTO(formattedTitle, null, null, "en", false, false, true, null);

        RenderedProblemStatementDTO alerted = request.postWithResponseBody(POST_URL, formattedTitleBody, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(alerted.html()).contains("<div class=\"markdown-alert markdown-alert-warning\">").contains("**Read** this");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderMarkupInACustomAlertTitle() throws Exception {
        // The client interpolates the title into the alert markup and lets its sanitizer decide, so an inline element
        // in a title is an element there. Escaping it here would show the tags to the reader instead.
        String markdown = "> [!WARNING] <em>Read</em> this\n> Body\n";
        var body = new ProblemStatementRenderRequestDTO(markdown, null, null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<em>Read</em> this").doesNotContain("&lt;em&gt;");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripDisallowedMarkupFromACustomAlertTitle() throws Exception {
        // The title takes the same route as every other authored fragment: through the safelist. This is what makes
        // rendering it raw safe, and it is the server-side counterpart of the client's sanitizer.
        String markdown = "> [!NOTE] <script>alert(1)</script>Careful\n> Body\n";
        var body = new ProblemStatementRenderRequestDTO(markdown, null, null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("<script>alert(1)</script>").contains("Careful");
    }

    @Test
    void shouldRefuseANegativeTestResultLimit() {
        // The limit is compared with `>`, so a negative one rejects even an empty list and the endpoint answers 422 to
        // every request that carries test results at all. Failing while the configuration binds surfaces the typo.
        var configuration = new ProblemStatementRenderingConfiguration();
        configuration.setMaxTestResults(-1);

        assertThatIllegalArgumentException().isThrownBy(configuration::rejectNegativeMaxTestResults).withMessageContaining("must not be negative");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldColorPlantUmlGreyForNullPassed() throws Exception {
        // The layout pragma matches the neighbouring colour tests: it pins PlantUML to its built-in engine so the
        // assertion below depends on the resolved colour alone and not on a Graphviz installation.
        String markdown = "@startuml\n!pragma layout smetana\nclass A #testsColor(<testid>1</testid>)\n@enduml";
        var feedback = new TestFeedbackInputDTO(1L, "testA", null, null, null);
        var body = new ProblemStatementRenderRequestDTO(markdown, List.of(feedback), null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // Asserting only that an <svg> exists would pass for green as well, which is the very thing a null verdict
        // must not produce. PlantUML resolves grey to #808080 and green to #008000.
        assertThat(result.html()).contains("<svg").contains("fill=\"#808080\"").doesNotContain("fill=\"#008000\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldColorPlantUmlGreenWhenAllTestsPassedWithoutFeedback() throws Exception {
        // Covers the wiring from the request through the renderer into PlantUmlTaskColorResolver: without the flag
        // reaching the diagram pass, the task markers of an all-passed submission turn green next to a grey diagram.
        // PlantUML resolves the color names into their HTML values, so green is #008000 and grey is #808080.
        String markdown = "@startuml\n!pragma layout smetana\nclass A #testsColor(testBubbleSort())\n@enduml";
        var flagged = new ProblemStatementRenderRequestDTO(markdown, null, null, "en", false, false, true, null, true);
        var unflagged = new ProblemStatementRenderRequestDTO(markdown, null, null, "en", false, false, true, null);

        RenderedProblemStatementDTO greenResult = request.postWithResponseBody(POST_URL, flagged, RenderedProblemStatementDTO.class, HttpStatus.OK);
        RenderedProblemStatementDTO greyResult = request.postWithResponseBody(POST_URL, unflagged, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(greenResult.html()).contains("<svg").contains("fill=\"#008000\"").doesNotContain("fill=\"#808080\"");
        // The identical request without the flag stays grey, which is what makes the assertion above about the flag.
        assertThat(greyResult.html()).contains("<svg").contains("fill=\"#808080\"").doesNotContain("fill=\"#008000\"");
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
        assertThat(result.html()).contains("/assets/katex/katex.min.css");
        assertThat(result.html()).contains("/assets/katex/katex.min.js");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderPathologicalMarkdownQuickly() throws Exception {
        // The markdown patterns used to backtrack quadratically on a long single line: 100 KB took between eight and
        // twenty-eight seconds depending on how many dollar signs it held, and an unclosed $$ raised a StackOverflowError.
        // The request size limit is 100 KB, so these are the worst inputs the endpoint accepts.
        // Each of these hit a different part: the first four the inline-formula check, the fifth the display-math body, the
        // two alternating ones the display-math body again (one dollar sign per repetition used to recurse) together with
        // the placeholder substitution, which cost a pass over the document per formula, and the last two the task syntax,
        // whose list previously recursed once per comma and once per parenthesis pair.
        String[] pathological = { "a".repeat(90_000), "a".repeat(90_000) + "$$", "a".repeat(45_000) + "$$" + "b".repeat(45_000), "$".repeat(90_000), "$$" + "a".repeat(90_000),
                "$$" + "a$".repeat(30_000), "$$" + "a$".repeat(30_000) + "$$", "[task][n](" + "a,".repeat(20_000), "[task][n](" + "a(),".repeat(20_000) };

        for (String markdown : pathological) {
            var body = new ProblemStatementRenderRequestDTO(markdown, null, null, "en", false, false, false, null);
            long startedAt = System.nanoTime();
            request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

            // The limit sits between the two: an order of magnitude below the regression it has to catch, and an order of
            // magnitude above the slowest of these inputs today (about 170 ms for the alternating dollars, under 10 ms for
            // every other one), so a slow CI machine does not turn it red.
            assertThat(elapsedMillis).as("rendering pathological markdown must not cost seconds of CPU").isLessThan(2_000);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderADisplayFormulaThatEscapesManyDollarSigns() throws Exception {
        // A legitimate formula, not crafted input: the body escapes a dollar sign per term. The display-math pattern
        // recursed once per escaped dollar, so a few thousand of them ended the request with a StackOverflowError rather
        // than a rendered formula.
        String formula = "a$".repeat(5_000) + "b";
        var body = new ProblemStatementRenderRequestDTO("$$" + formula + "$$", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-display-mode=\"true\"");
        assertThat(result.html()).contains(formula);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLeaveTheFormulaPlaceholderEmptyWithJavaScript() throws Exception {
        // With KaTeX in the document it overwrites the element as soon as it runs, so putting the source inside would only
        // show raw LaTeX until then. The source is the fallback for a document without script, not an addition to both.
        var body = new ProblemStatementRenderRequestDTO("Area is $$\\int_0^1 x\\,dx$$ today", null, null, "en", false, true, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-formula=\"\\int_0^1 x\\,dx\"");
        assertThat(result.html()).contains("></span>");
        assertThat(result.html()).doesNotContain("\\,dx</span>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotShipKatexWhenTheCallerAsksForNoJavaScript() throws Exception {
        // KaTeX is JavaScript, so includeJs=false has to exclude it too. Before, the scripts were emitted whenever the
        // statement contained math, whatever the caller asked for.
        var body = new ProblemStatementRenderRequestDTO("Display:\n$$\\int_0^1 x\\,dx$$", null, null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("<script");
        assertThat(result.html()).doesNotContain("katex.min.js");
        // The stylesheet is a separate switch and stays, so the formula is still styled if the caller wants CSS.
        assertThat(result.html()).contains("/assets/katex/katex.min.css");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldKeepTheFormulaSourceVisibleWithoutJavaScript() throws Exception {
        // Without a script nothing replaces the placeholder, so its own text is all the reader gets. An empty span showed
        // nothing at all; the source is at least readable.
        var body = new ProblemStatementRenderRequestDTO("Area is $$\\int_0^1 x\\,dx$$ today", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("class=\"katex-formula\"");
        assertThat(result.html()).doesNotContain("></span>");
        assertThat(result.html()).contains("\\int_0^1 x\\,dx</span>");
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
        // The rendered document is loaded in a WebView that does not authenticate for the asset requests, so this path has
        // to stay outside the security rules.
        //
        // Only that is asserted here, not that the file exists: KaTeX now comes from the client's own copy, which the
        // Angular build copies out of node_modules, and server tests run with the client build skipped. So 404 ("not built
        // in this run") is an acceptable outcome while 401 or 403 ("behind authentication") is not.
        int status = request.performMvcRequest(get("/assets/katex/katex.min.css")).andReturn().getResponse().getStatus();

        assertThat(status).as("the KaTeX assets must not be behind authentication").isIn(HttpStatus.OK.value(), HttpStatus.NOT_FOUND.value());
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSilenceTheGlobalAlertOnARejectedRender() throws Exception {
        var testResults = List.of(new TestFeedbackInputDTO(1L, "testA", true, null, 1.0), new TestFeedbackInputDTO(1L, "testB", false, null, 0.0));
        var body = new ProblemStatementRenderRequestDTO("[task][T](<testid>1</testid>)", testResults, null, "en", false, true, null, null);

        // Sent through MockMvc directly: the shared request helpers discard the body of a non-2xx response, and the
        // flag under test lives exactly there.
        var mvcResult = request.performMvcRequest(post(new URI(POST_URL)).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        // The renderer puts a message above the statement it could not replace, so the client's ErrorHandlerInterceptor
        // must not raise a second, global one for the same failure. AlertService reads exactly this flag.
        assertThat(objectMapper.readTree(mvcResult.getResponse().getContentAsString()).path("skipAlert").asBoolean()).isTrue();
    }

    // --- Test result count cap ---

    /**
     * The effective value of {@code artemis.problem-statement-rendering.max-test-results} under the shipped
     * configuration. Asserted against the default rather than a value injected via {@code @TestPropertySource},
     * because the latter forks the Spring test context (see {@code SpringContextConfigurationArchitectureTest}).
     * <p>
     * The two tests below bracket the limit, so they fail for any effective value other than this constant. They do
     * not cover that the limit is configurable: the field default in {@code ProblemStatementRenderingConfiguration} is
     * 1000 as well, so a mistyped property key would keep the same behaviour and stay unnoticed here.
     */
    private static final int MAX_TEST_RESULTS = 1000;

    private static List<TestFeedbackInputDTO> testFeedbacks(int count) {
        List<TestFeedbackInputDTO> feedbacks = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            feedbacks.add(new TestFeedbackInputDTO((long) i, "test" + i, true, null, null));
        }
        return feedbacks;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldAcceptExactlyTheConfiguredTestResultLimit() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][A](<testid>1</testid>)", testFeedbacks(MAX_TEST_RESULTS), null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-test-status=\"success\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectOneTestResultAboveTheConfiguredLimit() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][A](<testid>1</testid>)", testFeedbacks(MAX_TEST_RESULTS + 1), null, "en", false, false, true, null);

        request.postWithoutResponseBody(POST_URL, body, HttpStatus.UNPROCESSABLE_CONTENT);
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
        // line must still render using the authored count: results were supplied, just not for this task's tests.
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
        // The stats denominator must be the authored count (2), not the resolved count (1). This is the
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
        // read "5" (authored), not "3" (resolved). A task with unresolvable refs must not look fully accounted for.
        assertThat(result.html()).contains("data-authored-count=\"5\"");
        assertThat(result.html()).contains("data-not-executed-count=\"2\"");
        assertThat(result.html()).contains("3 of 5 tests passed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveBothEntriesOfADuplicateNameByTestId() throws Exception {
        // test_name has no uniqueness constraint in the database, so two distinct test ids sharing a display name
        // must not break rendering. Referencing each by its unambiguous <testid> must still resolve correctly.
        var first = new TestFeedbackInputDTO(1L, "sameName()", true, null, null);
        var second = new TestFeedbackInputDTO(2L, "sameName()", false, null, null);
        var body = new ProblemStatementRenderRequestDTO("[task][A](<testid>1</testid>),[task][B](<testid>2</testid>)", List.of(first, second), null, "en", false, false, true,
                null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-test-ids=\"1\"");
        assertThat(result.html()).contains("data-test-ids=\"2\"");
        var taskAStatus = result.html().substring(result.html().indexOf("data-test-ids=\"1\""));
        assertThat(taskAStatus).contains("data-test-status=\"success\"");
        var taskBStatus = result.html().substring(result.html().indexOf("data-test-ids=\"2\""));
        assertThat(taskBStatus).contains("data-test-status=\"fail\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldTreatAmbiguousTestNameAsNotExecutedInsteadOfRejecting() throws Exception {
        // A reference by the shared name alone cannot pick between the two test ids, so it must resolve as
        // not-executed rather than silently choosing one of them or failing the whole request.
        var first = new TestFeedbackInputDTO(1L, "sameName()", true, null, null);
        var second = new TestFeedbackInputDTO(2L, "sameName()", false, null, null);
        var body = new ProblemStatementRenderRequestDTO("[task][Dup](sameName())", List.of(first, second), null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-test-status=\"not-executed\"");
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

    // --- Autolinking ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLinkifyBareUrlInProse() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("Check out https://artemis.tum.de for details.", null, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // The response has already passed through the service's Jsoup safelist by this point, so a surviving
        // <a href> also confirms the safelist keeps the link the autolink extension produces.
        assertThat(result.html()).contains("<a href=\"https://artemis.tum.de\">https://artemis.tum.de</a>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotLinkifyUrlInFencedCodeBlock() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("```\nhttps://artemis.tum.de\n```", null, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("<a href");
        assertThat(result.html()).contains("<pre><code>https://artemis.tum.de");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotLinkifyUrlInInlineCode() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("`https://artemis.tum.de`", null, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("<a href");
        assertThat(result.html()).contains("<code>https://artemis.tum.de</code>");
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

        assertThat(result1.rendererVersion()).isEqualTo("1.3.0");
        assertThat(result2.rendererVersion()).isEqualTo(result1.rendererVersion());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldChangeContentHashWhenRendererVersionDiffers() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("# Hello\n\nThis is **bold** text.", null, null, "en", false, true, null, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // f0d658433ea3ce51e37b7e76c21dc7739d18dd56c168ecb1cc35dcd40cc00634 is the content hash this exact request
        // produced under renderer version "1.0.0" (captured before the bump to "1.1.0" that added this test, and
        // still distinct under "1.3.0", which moved the feedback payload onto the container). The
        // renderer version is folded into the content hash precisely so that a stale client-cached rendering does
        // not survive a semantic change to the renderer; this pins that a version bump actually changes the hash
        // for byte-for-byte identical input, rather than only changing the reported version string. If this
        // assertion ever fails because the version was bumped again, that is expected: replace the pinned hash
        // with the new value captured under the previous version.
        assertThat(result.contentHash()).isNotEqualTo("f0d658433ea3ce51e37b7e76c21dc7739d18dd56c168ecb1cc35dcd40cc00634");
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderNoResultWhenTestResultsAreNull() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][A](<testid>1</testid>)", null, null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-test-status=\"no-result\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderNotExecutedWhenTestResultsAreEmptyList() throws Exception {
        // Hand-built JSON posted via the plainString overload, not a ProblemStatementRenderRequestDTO instance: the DTO
        // carries @JsonInclude(NON_EMPTY), so serializing an empty testResults list through the object-based overload
        // would silently drop the field, making "no result" and "result, nothing mappable" indistinguishable on the wire.
        String requestBody = """
                {
                  "markdown": "[task][A](<testid>1</testid>)",
                  "testResults": [],
                  "locale": "en",
                  "darkMode": false,
                  "includeJs": false,
                  "includeCss": true
                }
                """;

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, requestBody, true, RenderedProblemStatementDTO.class, HttpStatus.OK, null, null, null);

        assertThat(result.html()).contains("data-test-status=\"not-executed\"");
        assertThat(result.html()).doesNotContain("data-test-status=\"no-result\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldAcceptMoreThanOneHundredTestResults() throws Exception {
        List<TestFeedbackInputDTO> many = new ArrayList<>();
        for (int i = 1; i <= 150; i++) {
            many.add(new TestFeedbackInputDTO((long) i, "test" + i, true, null, null));
        }
        var body = new ProblemStatementRenderRequestDTO("[task][A](<testid>1</testid>)", many, null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-test-status=\"success\"");
    }

    // --- GitHub-style alerts ---

    @ParameterizedTest
    @CsvSource({ "NOTE, note, Note, octicon-info", "TIP, tip, Tip, octicon-light-bulb", "IMPORTANT, important, Important, octicon-report",
            "WARNING, warning, Warning, octicon-alert", "CAUTION, caution, Caution, octicon-stop" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderEveryGitHubAlertType(String marker, String type, String defaultTitle, String iconClass) throws Exception {
        var body = new ProblemStatementRenderRequestDTO("> [!" + marker + "]\n> Body text.", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<div class=\"markdown-alert markdown-alert-" + type + "\">");
        assertThat(result.html()).contains("<p class=\"markdown-alert-title\">");
        assertThat(result.html()).contains("<svg class=\"octicon " + iconClass + " mr-2\"");
        assertThat(result.html()).contains(">" + defaultTitle + "</p>");
        assertThat(result.html()).contains("<p>Body text.</p>");
        // The blockquote the alert was parsed from must be gone, otherwise the client would style it twice.
        assertThat(result.html()).doesNotContain("<blockquote>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRecognizeAlertMarkerCaseInsensitively() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("> [!warning]\n> Body.", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<div class=\"markdown-alert markdown-alert-warning\">");
        assertThat(result.html()).contains(">Warning</p>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldUseCustomAlertTitleWhenSupplied() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("> [!TIP] Read this first\n> Body.", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains(">Read this first</p>");
        assertThat(result.html()).doesNotContain(">Tip</p>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEscapeCustomAlertTitle() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("> [!NOTE] Rules & limits\n> Body.", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains(">Rules &amp; limits</p>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotLocalizeTheDefaultAlertTitle() throws Exception {
        // The client always falls back to the capitalized English type, so a localized server title would create a
        // divergence instead of closing one.
        var body = new ProblemStatementRenderRequestDTO("> [!NOTE]\n> Body.", null, null, "de", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains(">Note</p>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldTreatBackslashEscapedAlertMarkerAsAnAlert() throws Exception {
        // Matches the client: its marker regex allows an optional leading backslash, so `\[!NOTE]` is an alert too.
        // CommonMark resolves the escape to a literal "[" before the server pattern runs, which gets there by itself.
        var body = new ProblemStatementRenderRequestDTO("> \\[!NOTE]\n> Body.", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<div class=\"markdown-alert markdown-alert-note\">");
        assertThat(result.html()).doesNotContain("[!NOTE]");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldKeepOcticonSvgAfterSanitization() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("> [!NOTE]\n> Body.", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        // The jsoup safelist drops SVG, so the icon is injected after sanitization. Both halves are asserted: the
        // real markup arrives, and no placeholder is left behind because a type failed to match.
        assertThat(result.html()).contains("<svg class=\"octicon octicon-info mr-2\" viewBox=\"0 0 16 16\"").contains("<path d=\"M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Z");
        assertThat(result.html()).doesNotContain("data-alert-type=");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotRenderAlertForUnknownMarker() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("> [!HINT]\n> Body.", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("markdown-alert");
        assertThat(result.html()).contains("<blockquote>").contains("[!HINT]");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotRenderAlertWhenMarkerIsNotOnTheFirstLine() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("> Intro line.\n> [!NOTE]\n> Body.", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("markdown-alert");
        assertThat(result.html()).contains("<blockquote>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderAlertConsistingOfTheMarkerLineOnly() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("> [!CAUTION]", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<div class=\"markdown-alert markdown-alert-caution\">");
        assertThat(result.html()).contains(">Caution</p>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderNestedAlertMarkerWithoutFailing() throws Exception {
        // Degenerate input: only the outermost blockquote of a nesting is considered, mirroring the client, which
        // skips from a blockquote_open to the first blockquote_close. The inner quote survives inside the alert.
        var body = new ProblemStatementRenderRequestDTO("> > [!NOTE]\n> > Body.", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<div class=\"markdown-alert markdown-alert-note\">");
        assertThat(result.html()).contains("<blockquote>");
        assertThat(result.html()).contains("<p>Body.</p>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldKeepEveryBlockOfAnAlertInsideTheAlertContainer() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("> [!IMPORTANT]\n> First.\n>\n> Second.", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("<p>First.</p>");
        assertThat(result.html()).contains("<p>Second.</p>");
        // Both paragraphs must sit before the alert closes; the container ends right before the problem-statement div.
        assertThat(result.html()).contains("<p>Second.</p>\n</div>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotTurnAPlainBlockquoteIntoAnAlert() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("> Just a quote.", null, null, "en", false, false, false, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).doesNotContain("markdown-alert");
        assertThat(result.html()).contains("<blockquote>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIncludeAlertStylingInEmbeddedCss() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("> [!NOTE]\n> Body.", null, null, "en", true, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains(".markdown-alert-note");
        assertThat(result.html()).contains(".artemis-problem-statement--dark .markdown-alert-note");
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
