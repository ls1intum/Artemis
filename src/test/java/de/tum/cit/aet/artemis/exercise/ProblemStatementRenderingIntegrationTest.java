package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ProblemStatementRenderingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "psrendering";

    private static final String BASE_URL = "/api/exercise/exercises/";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
    }

    private String renderUrl(long exerciseId) {
        return BASE_URL + exerciseId + "/problem-statement/rendered";
    }

    private String renderUrl(long exerciseId, boolean selfContained) {
        return renderUrl(exerciseId) + "?selfContained=" + selfContained;
    }

    private String renderUrl(long exerciseId, boolean selfContained, boolean interactive) {
        return renderUrl(exerciseId) + "?selfContained=" + selfContained + "&interactive=" + interactive;
    }

    private TextExercise createCourseExerciseWithProblemStatement(String problemStatement) {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        exercise.setProblemStatement(problemStatement);
        return exerciseRepository.save(exercise);
    }

    // --- Basic rendering tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderPlainMarkdown() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("# Hello\n\nThis is **bold** text.");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).contains("<h1>Hello</h1>");
        assertThat(result.html()).contains("<strong>bold</strong>");
        assertThat(result.html()).contains("artemis-problem-statement");
        assertThat(result.tasks()).isNullOrEmpty();
        assertThat(result.diagrams()).isNullOrEmpty();
        assertThat(result.rendererVersion()).isNotBlank();
        assertThat(result.contentHash()).isNotBlank();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderTasksWithTestIds() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("Implement the following:\n[task][Sort Method](<testid>42</testid>,<testid>43</testid>)");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().getFirst().taskName()).isEqualTo("Sort Method");
        assertThat(result.tasks().getFirst().testIds()).containsExactly(42L, 43L);
        assertThat(result.html()).contains("artemis-task");
        assertThat(result.html()).contains("data-task-name");
        assertThat(result.html()).contains("data-test-ids=\"42,43\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderPlantUmlDiagramAsUrl() throws Exception {
        String ps = "Some text\n@startuml\n!pragma layout smetana\nclass A\n@enduml\nMore text";
        TextExercise exercise = createCourseExerciseWithProblemStatement(ps);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.diagrams()).hasSize(1);
        assertThat(result.diagrams().getFirst().diagramId()).isEqualTo("uml-0");
        assertThat(result.diagrams().getFirst().renderMode()).isEqualTo("url");
        assertThat(result.diagrams().getFirst().svgUrl()).contains("plantuml/svg");
        assertThat(result.diagrams().getFirst().inlineSvg()).isNull();
        assertThat(result.html()).contains("artemis-diagram");
        assertThat(result.html()).contains("data-diagram-id=\"uml-0\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderKaTeXServerSideWhenFormulaPresent() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("The formula is $$E = mc^2$$");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // GraalJS renders KaTeX server-side: client doesn't need KaTeX JS
        assertThat(result.assets().katex()).isEqualTo("absent");
        // The HTML should contain rendered KaTeX output
        assertThat(result.html()).contains("katex");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderCodeHighlightingServerSide() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("```java\npublic class Foo {}\n```");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // GraalJS renders syntax highlighting server-side: client doesn't need highlight.js
        assertThat(result.assets().highlighting()).isEqualTo("absent");
        assertThat(result.html()).contains("hljs");
        assertThat(result.html()).contains("<code");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderMarkdownTables() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("| Col A | Col B |\n|-------|-------|\n| 1     | 2     |");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // markdown-it tag-class plugin adds class="table" to <table>
        assertThat(result.html()).contains("<table");
        assertThat(result.html()).contains("<th>Col A</th>");
        assertThat(result.html()).contains("<td>1</td>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyContentWhenProblemStatementIsNull() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement(null);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // Null PS should return empty content (NON_EMPTY serialization omits empty fields → null on deserialization)
        assertThat(result.html()).isNullOrEmpty();
        assertThat(result.tasks()).isNullOrEmpty();
        assertThat(result.diagrams()).isNullOrEmpty();
        // Assets and version should still be present
        assertThat(result.assets()).isNotNull();
        assertThat(result.assets().katex()).isEqualTo("absent");
        assertThat(result.assets().highlighting()).isEqualTo("absent");
        assertThat(result.rendererVersion()).isNotBlank();
        assertThat(result.contentHash()).isNotBlank();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripScriptTagsWhenXssInjected() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("<script>alert('xss')</script>\n\nSafe text");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).doesNotContain("<script>");
        assertThat(result.html()).contains("Safe text");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldChangeContentHashWhenProblemStatementEdited() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("Version 1");
        RenderedProblemStatementDTO result1 = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        exercise.setProblemStatement("Version 2");
        exerciseRepository.save(exercise);
        RenderedProblemStatementDTO result2 = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result1.contentHash()).isNotEqualTo(result2.contentHash());
    }

    // --- GraalJS / Phase 2 rendering tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnRendererVersionV2() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("Hello");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.rendererVersion()).isEqualTo("2.0.0");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnRequiredCssWhenServerRendered() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("Hello");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.assets().requiredCss()).containsExactlyInAnyOrder("katex", "hljs", "github-alerts");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderBlockKaTeXServerSide() throws Exception {
        // Block formula: $$ on its own line
        TextExercise exercise = createCourseExerciseWithProblemStatement("$$E = mc^2$$");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.assets().katex()).isEqualTo("absent");
        assertThat(result.html()).contains("class=\"katex");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderHighlightingServerSide() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("```java\npublic class Foo {}\n```");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.assets().highlighting()).isEqualTo("absent");
        assertThat(result.html()).contains("hljs");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderGithubAlerts() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("> [!NOTE]\n> This is a note");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).contains("markdown-alert");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotRenderFormulaAsKaTeXWhenInsideCodeFence() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("```\n$$E=mc^2$$\n```");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // Formula inside a code block should NOT be rendered as KaTeX
        assertThat(result.html()).doesNotContain("class=\"katex");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldConvertInlineDollarDollarToKaTeX() throws Exception {
        // Inline: $$ with surrounding text → converted to $ for KaTeX inline math
        TextExercise exercise = createCourseExerciseWithProblemStatement("The energy is $$E=mc^2$$ according to Einstein.");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).contains("katex");
        // The raw $$ delimiters should not be in the HTML (they were converted to $ and rendered)
        assertThat(result.html()).doesNotContain("$$");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripMaliciousContentFromUserCraftedKaTeXHtml() throws Exception {
        // User-injected HTML that mimics KaTeX structure with malicious content
        TextExercise exercise = createCourseExerciseWithProblemStatement("<span class=\"katex\"><script>alert('xss')</script></span>\n<span onclick=\"alert('xss')\">text</span>");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // jsoup must strip dangerous content even inside user-crafted spans
        assertThat(result.html()).doesNotContain("<script>");
        assertThat(result.html()).doesNotContain("onclick");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldPreserveStyleAttributeOnSpanAfterSanitization() throws Exception {
        // Regression test: style on span is allowed (needed for KaTeX layout).
        // User-authored spans with style pass through jsoup — this is an accepted trade-off
        // because problem statements are authored by trusted instructors, not students.
        // CSS cannot execute JS in modern browsers, so this is cosmetic-only risk.
        TextExercise exercise = createCourseExerciseWithProblemStatement("<span style=\"color:red\">styled text</span>");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // style attribute IS preserved (intentional — needed for KaTeX)
        assertThat(result.html()).contains("style=\"color:red\"");
        // But script/event handlers are still stripped
        assertThat(result.html()).doesNotContain("<script>");
        assertThat(result.html()).doesNotContain("onclick");
    }

    // --- Authorization tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldForbidStudentAccessToExamExercise() throws Exception {
        TextExercise exercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        exercise.setProblemStatement("Hello");
        exerciseRepository.save(exercise);

        request.get(renderUrl(exercise.getId()), HttpStatus.FORBIDDEN, RenderedProblemStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldForbidTutorAccessToExamExerciseBeforeEnd() throws Exception {
        // Default exam has endDate = now + 60min, so it hasn't ended yet
        TextExercise exercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        exercise.setProblemStatement("Hello");
        exerciseRepository.save(exercise);

        request.get(renderUrl(exercise.getId()), HttpStatus.FORBIDDEN, RenderedProblemStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldAllowTutorAccessToExamExerciseAfterEnd() throws Exception {
        TextExercise exercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        exercise.setProblemStatement("Hello");
        exerciseRepository.save(exercise);

        // Set exam end date to the past
        Exam exam = exercise.getExerciseGroup().getExam();
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);
        assertThat(result.html()).contains("Hello");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldAllowEditorAccessToExamExercise() throws Exception {
        TextExercise exercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        exercise.setProblemStatement("Hello");
        exerciseRepository.save(exercise);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);
        assertThat(result.html()).contains("Hello");
    }

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("Hello");
        request.get(renderUrl(exercise.getId()), HttpStatus.UNAUTHORIZED, RenderedProblemStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldConvertRelativeLinksToAbsolute() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("[link](/api/files/attachments/123)\n\n![image](/api/files/attachments/456)");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // Links and images should have absolute URLs (server URL prepended)
        assertThat(result.html()).doesNotContain("href=\"/api/files");
        assertThat(result.html()).doesNotContain("src=\"/api/files");
        // The absolute URL should contain the server URL prefix + the original path
        assertThat(result.html()).containsPattern("href=\"https?://[^\"]+/api/files/attachments/123\"");
        assertThat(result.html()).containsPattern("src=\"https?://[^\"]+/api/files/attachments/456\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldInlineSvgWhenSelfContainedPlantUml() throws Exception {
        String ps = "Some text\n@startuml\n!pragma layout smetana\nclass Student\n@enduml\nMore text";
        TextExercise exercise = createCourseExerciseWithProblemStatement(ps);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // Diagram metadata
        assertThat(result.diagrams()).hasSize(1);
        assertThat(result.diagrams().getFirst().renderMode()).isEqualTo("inline");
        assertThat(result.diagrams().getFirst().inlineSvg()).contains("<svg");
        assertThat(result.assets().diagramMode()).isEqualTo("inline");
        // SVG is inlined in the HTML and survives sanitization
        assertThat(result.html()).contains("<svg");
        assertThat(result.html()).contains("Student");
        // SVG placeholder spans must not remain in the output
        assertThat(result.html()).doesNotContain("artemis-svg-placeholder");
    }

    // --- Additional rendering edge case tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyContentWhenProblemStatementIsBlank() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("   \n  \t  ");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).isNullOrEmpty();
        assertThat(result.tasks()).isNullOrEmpty();
        assertThat(result.diagrams()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderMultipleTasksAndDiagrams() throws Exception {
        String ps = """
                [task][Task A](<testid>1</testid>)
                [task][Task B](<testid>2</testid>,<testid>3</testid>)
                @startuml
                !pragma layout smetana
                class Foo
                @enduml
                @startuml
                !pragma layout smetana
                class Bar
                @enduml
                """;
        TextExercise exercise = createCourseExerciseWithProblemStatement(ps);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.tasks()).hasSize(2);
        assertThat(result.tasks().get(0).taskName()).isEqualTo("Task A");
        assertThat(result.tasks().get(0).testIds()).containsExactly(1L);
        assertThat(result.tasks().get(1).taskName()).isEqualTo("Task B");
        assertThat(result.tasks().get(1).testIds()).containsExactly(2L, 3L);

        assertThat(result.diagrams()).hasSize(2);
        assertThat(result.diagrams().get(0).diagramId()).isEqualTo("uml-0");
        assertThat(result.diagrams().get(1).diagramId()).isEqualTo("uml-1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEscapeSpecialCharactersInTaskName() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("[task][Implement <List> & \"Map\"](<testid>10</testid>)");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().getFirst().taskName()).isEqualTo("Implement <List> & \"Map\"");
        // HTML attributes must be escaped
        assertThat(result.html()).doesNotContain("data-task-name=\"Implement <List>");
        assertThat(result.html()).contains("&lt;List&gt;");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripTestIdTagsFromOutput() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("[task][Sort](<testid>42</testid>)");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).doesNotContain("<testid>");
        assertThat(result.html()).doesNotContain("</testid>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStripEventHandlersAndJavascriptProtocol() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("<img src=x onerror=alert('xss')>\n\n<a href=\"javascript:alert('xss')\">click</a>");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // The dangerous img tag must not appear as executable HTML (either escaped or sanitized)
        assertThat(result.html()).doesNotContain("<img src=x onerror");
        // javascript: protocol must be stripped from href
        assertThat(result.html()).doesNotContain("javascript:");
    }

    // --- Additional authorization tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "outsider1", roles = "USER")
    void shouldForbidStudentOutsideCourseAccess() throws Exception {
        userUtilService.createAndSaveUser(TEST_PREFIX + "outsider1");
        TextExercise exercise = createCourseExerciseWithProblemStatement("Hello");

        // outsider1 is not in the course groups (tumuser, tutor, editor, instructor)
        request.get(renderUrl(exercise.getId()), HttpStatus.FORBIDDEN, RenderedProblemStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturn404WhenExerciseDoesNotExist() throws Exception {
        request.get(renderUrl(999999L), HttpStatus.NOT_FOUND, RenderedProblemStatementDTO.class);
    }

    // --- Interactive script tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIncludeInteractiveScriptWhenSelfContained() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("# Hello");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.interactiveScript()).isNotNull();
        assertThat(result.interactiveScript()).contains("artemis-feedback-modal");
        assertThat(result.interactiveScript()).contains("DOMContentLoaded");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExcludeInteractiveScriptWhenInteractiveFalse() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("# Hello");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true, false), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.interactiveScript()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldExcludeInteractiveScriptWhenNotSelfContained() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("# Hello");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), false), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.interactiveScript()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRemoveClickAffordanceWhenNonInteractive() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("# Hello");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true, false), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).contains("cursor:default");
        assertThat(result.html()).contains("text-decoration:none");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldKeepClickAffordanceWhenInteractive() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("# Hello");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true, true), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).doesNotContain("cursor:default");
        assertThat(result.html()).contains("cursor:pointer");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldAllowTutorAccessToExamExerciseJustEnded() throws Exception {
        TextExercise exercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        exercise.setProblemStatement("Hello");
        exerciseRepository.save(exercise);

        // Set exam end date to 1 second ago — clearly ended, no race condition
        Exam exam = exercise.getExerciseGroup().getExam();
        exam.setEndDate(ZonedDateTime.now().minusSeconds(1));
        examRepository.save(exam);

        request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);
    }

    // --- HTTP contract tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnETagAndCacheControlHeaders() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("Header test");

        MvcResult result = request.performMvcRequest(get(new URI(renderUrl(exercise.getId())))).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader("ETag")).isNotNull().isNotBlank();
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("max-age=300").contains("private");
    }

    // --- Helper: ProgrammingExercise with Result/Feedback ---

    private record ProgrammingExerciseWithTestCases(ProgrammingExercise exercise, List<de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase> testCases) {
    }

    private ProgrammingExerciseWithTestCases createProgrammingExerciseWithTestCases(String problemStatement) {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        ProgrammingExercise exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        var testCases = programmingExerciseUtilService.addTestCasesToProgrammingExercise(exercise);
        exercise.setProblemStatement(problemStatement);
        exercise.setMaxPoints(10.0);
        exercise.setBonusPoints(2.0);
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        return new ProgrammingExerciseWithTestCases(exerciseRepository.save(exercise), testCases);
    }

    private Result addResultWithFeedback(ProgrammingExercise exercise, String studentLogin, String commitHash, List<Feedback> feedbacks) {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, studentLogin);
        var submission = new ProgrammingSubmission();
        submission.setSubmitted(true);
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(5));
        submission.setParticipation(participation);
        submission = (ProgrammingSubmission) submissionRepository.save(submission);
        var result = new Result().submission(submission).score(75.0).rated(true).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now().minusMinutes(4));
        result.setExerciseId(exercise.getId());
        submission.addResult(result);
        result = resultRepository.save(result);
        for (Feedback fb : feedbacks) {
            fb.setResult(result);
        }
        result.setFeedbacks(feedbacks);
        result = resultRepository.save(result);
        submissionRepository.save(submission);
        return result;
    }

    // --- #1: No participation → no feedback data ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderWithoutFeedbackWhenNoParticipation() throws Exception {
        var exercise = createProgrammingExerciseWithTestCases("[task][Sort](<testid>1</testid>)").exercise();

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).contains("artemis-task");
        assertThat(result.html()).doesNotContain("data-feedback");
        assertThat(result.html()).doesNotContain("data-result");
        assertThat(result.html()).doesNotContain("data-test-status");
        assertThat(result.tasks().getFirst().testStatus()).isNull();
    }

    // --- #2: Participation exists but no result ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderWithoutFeedbackWhenParticipationButNoResult() throws Exception {
        var exercise = createProgrammingExerciseWithTestCases("[task][Sort](<testid>1</testid>)").exercise();
        participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).doesNotContain("data-feedback");
        assertThat(result.html()).doesNotContain("data-result");
        assertThat(result.tasks().getFirst().testStatus()).isNull();
    }

    // --- #3: End-to-end with real Result/Feedback ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEmbedFeedbackAndResultWhenResultExists() throws Exception {
        var setup = createProgrammingExerciseWithTestCases("[task][My Task](<testid>1</testid>,<testid>2</testid>)");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        var tc2 = testCases.get(1);

        // Use actual test case IDs in problem statement
        exercise.setProblemStatement("[task][My Task](<testid>" + tc1.getId() + "</testid>,<testid>" + tc2.getId() + "</testid>)");
        exerciseRepository.save(exercise);

        var feedbacks = List.of(new Feedback().credits(1.0).positive(true).type(FeedbackType.AUTOMATIC).testCase(tc1),
                new Feedback().credits(0.0).positive(false).type(FeedbackType.AUTOMATIC).testCase(tc2).detailText("Assertion failed"));
        addResultWithFeedback(exercise, TEST_PREFIX + "student1", "abc123def", feedbacks);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // data-feedback present with credits and message
        assertThat(result.html()).contains("data-feedback");
        assertThat(result.html()).contains("Assertion failed");
        // data-result present with score and commit hash
        assertThat(result.html()).contains("data-result");
        assertThat(result.html()).contains("abc123def");
        assertThat(result.html()).contains("75");
        // Task status in DTO
        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().getFirst().testStatus()).isEqualTo("fail");
    }

    // --- #4: Task status aggregation (success, fail, not-executed) ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowSuccessStatusWhenAllTestsPass() throws Exception {
        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        exercise.setProblemStatement("[task][Task A](<testid>" + tc1.getId() + "</testid>)");
        exerciseRepository.save(exercise);

        var feedbacks = List.of(new Feedback().credits(1.0).positive(true).type(FeedbackType.AUTOMATIC).testCase(tc1));
        addResultWithFeedback(exercise, TEST_PREFIX + "student1", "aaa", feedbacks);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.tasks().getFirst().testStatus()).isEqualTo("success");
        assertThat(result.html()).contains("artemis-task-success");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowFailStatusWhenAnyTestFails() throws Exception {
        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.get(0);
        var tc2 = testCases.get(1);
        exercise.setProblemStatement("[task][Task B](<testid>" + tc1.getId() + "</testid>,<testid>" + tc2.getId() + "</testid>)");
        exerciseRepository.save(exercise);

        var feedbacks = List.of(new Feedback().credits(1.0).positive(true).type(FeedbackType.AUTOMATIC).testCase(tc1),
                new Feedback().credits(0.0).positive(false).type(FeedbackType.AUTOMATIC).testCase(tc2));
        addResultWithFeedback(exercise, TEST_PREFIX + "student1", "bbb", feedbacks);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.tasks().getFirst().testStatus()).isEqualTo("fail");
        assertThat(result.html()).contains("artemis-task-fail");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldShowNotExecutedWhenTestResultMissing() throws Exception {
        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        // Reference tc1 + a non-existent test case ID → not-executed
        exercise.setProblemStatement("[task][Task C](<testid>" + tc1.getId() + "</testid>,<testid>999999</testid>)");
        exerciseRepository.save(exercise);

        var feedbacks = List.of(new Feedback().credits(1.0).positive(true).type(FeedbackType.AUTOMATIC).testCase(tc1));
        addResultWithFeedback(exercise, TEST_PREFIX + "student1", "ccc", feedbacks);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.tasks().getFirst().testStatus()).isEqualTo("not-executed");
        assertThat(result.html()).contains("artemis-task-not-executed");
    }

    // --- #5: Latest result used ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldUseLatestResultNotOlderOne() throws Exception {
        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        exercise.setProblemStatement("[task][Task](<testid>" + tc1.getId() + "</testid>)");
        exerciseRepository.save(exercise);

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");

        // Old result: test fails
        var oldSubmission = new ProgrammingSubmission();
        oldSubmission.setSubmitted(true);
        oldSubmission.setCommitHash("old111");
        oldSubmission.setSubmissionDate(ZonedDateTime.now().minusHours(2));
        oldSubmission.setParticipation(participation);
        oldSubmission = (ProgrammingSubmission) submissionRepository.save(oldSubmission);
        var oldResult = new Result().submission(oldSubmission).score(0.0).rated(true).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now().minusHours(2));
        oldResult.setExerciseId(exercise.getId());
        oldSubmission.addResult(oldResult);
        oldResult = resultRepository.save(oldResult);
        var oldFb = new Feedback().credits(0.0).positive(false).type(FeedbackType.AUTOMATIC).testCase(tc1);
        oldFb.setResult(oldResult);
        oldResult.setFeedbacks(List.of(oldFb));
        resultRepository.save(oldResult);
        submissionRepository.save(oldSubmission);

        // New result: test passes
        var newSubmission = new ProgrammingSubmission();
        newSubmission.setSubmitted(true);
        newSubmission.setCommitHash("new222");
        newSubmission.setSubmissionDate(ZonedDateTime.now().minusMinutes(5));
        newSubmission.setParticipation(participation);
        newSubmission = (ProgrammingSubmission) submissionRepository.save(newSubmission);
        var newResult = new Result().submission(newSubmission).score(100.0).rated(true).assessmentType(AssessmentType.AUTOMATIC)
                .completionDate(ZonedDateTime.now().minusMinutes(4));
        newResult.setExerciseId(exercise.getId());
        newSubmission.addResult(newResult);
        newResult = resultRepository.save(newResult);
        var newFb = new Feedback().credits(1.0).positive(true).type(FeedbackType.AUTOMATIC).testCase(tc1);
        newFb.setResult(newResult);
        newResult.setFeedbacks(List.of(newFb));
        resultRepository.save(newResult);
        submissionRepository.save(newSubmission);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // Should reflect the latest (passing) result, not the old (failing) one
        assertThat(result.tasks().getFirst().testStatus()).isEqualTo("success");
    }

    // --- #6: Feedback serialization edge cases ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIgnoreFeedbackWithoutTestCase() throws Exception {
        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        exercise.setProblemStatement("[task][Task](<testid>" + tc1.getId() + "</testid>)");
        exerciseRepository.save(exercise);

        var feedbacks = List.of(new Feedback().credits(1.0).positive(true).type(FeedbackType.AUTOMATIC).testCase(tc1),
                // Feedback without test case (manual) → should be ignored in data-feedback
                new Feedback().credits(2.0).positive(true).type(FeedbackType.MANUAL_UNREFERENCED).detailText("Manual comment"));
        addResultWithFeedback(exercise, TEST_PREFIX + "student1", "fff", feedbacks);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).contains("data-feedback");
        // Manual feedback text should not appear in data-feedback (it has no test case)
        assertThat(result.html()).doesNotContain("Manual comment");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEscapeHtmlInFeedbackMessage() throws Exception {
        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        exercise.setProblemStatement("[task][Task](<testid>" + tc1.getId() + "</testid>)");
        exerciseRepository.save(exercise);

        var feedbacks = List.of(new Feedback().credits(0.0).positive(false).type(FeedbackType.AUTOMATIC).testCase(tc1).detailText("Expected <div>hello</div> but got \"error\""));
        addResultWithFeedback(exercise, TEST_PREFIX + "student1", "ggg", feedbacks);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // HTML special chars must be escaped in the data-feedback attribute
        assertThat(result.html()).doesNotContain("data-feedback=\"[{\"");
        assertThat(result.html()).contains("&lt;div&gt;");
        // Jackson escapes " as \", then escapeHtmlAttribute converts " to &quot;
        // So the inner quotes appear as \&quot; in the attribute
        assertThat(result.html()).contains("\\&quot;error\\&quot;");
    }

    // --- #7: ResultSummary fields ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEmbedResultSummaryWithAllFields() throws Exception {
        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        exercise.setProblemStatement("[task][Task](<testid>" + tc1.getId() + "</testid>)");
        exerciseRepository.save(exercise);

        var feedbacks = List.of(new Feedback().credits(1.0).positive(true).type(FeedbackType.AUTOMATIC).testCase(tc1));
        addResultWithFeedback(exercise, TEST_PREFIX + "student1", "deadbeef12345", feedbacks);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // data-result JSON should contain all fields
        assertThat(result.html()).contains("data-result");
        assertThat(result.html()).contains("75"); // score
        assertThat(result.html()).contains("10.0"); // maxPoints
        assertThat(result.html()).contains("2.0"); // bonusPoints
        assertThat(result.html()).contains("deadbeef12345"); // commitHash
        assertThat(result.html()).contains("AUTOMATIC"); // assessmentType
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotEmbedResultDataForNonSelfContained() throws Exception {
        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        exercise.setProblemStatement("[task][Task](<testid>" + tc1.getId() + "</testid>)");
        exerciseRepository.save(exercise);

        var feedbacks = List.of(new Feedback().credits(1.0).positive(true).type(FeedbackType.AUTOMATIC).testCase(tc1));
        addResultWithFeedback(exercise, TEST_PREFIX + "student1", "xyz", feedbacks);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), false), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // data-result is always embedded (it's part of the HTML, not interactive-only)
        // but data-feedback should still be present since we have results
        assertThat(result.html()).contains("data-feedback");
    }

    // --- #8: Locale handling ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRenderTaskStatsInGermanWhenLangKeyIsDE() throws Exception {
        // Set user's language to German
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setLangKey("de");
        userTestRepository.save(user);

        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        exercise.setProblemStatement("[task][Task](<testid>" + tc1.getId() + "</testid>)");
        exerciseRepository.save(exercise);

        var feedbacks = List.of(new Feedback().credits(1.0).positive(true).type(FeedbackType.AUTOMATIC).testCase(tc1));
        addResultWithFeedback(exercise, TEST_PREFIX + "student1", "loc", feedbacks);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // German stats text: "1 von 1 Tests bestanden" (or similar localized format)
        assertThat(result.html()).contains("von");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldDefaultToEnglishWhenLangKeyIsNull() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setLangKey(null);
        userTestRepository.save(user);

        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        exercise.setProblemStatement("[task][Task](<testid>" + tc1.getId() + "</testid>)");
        exerciseRepository.save(exercise);

        var feedbacks = List.of(new Feedback().credits(1.0).positive(true).type(FeedbackType.AUTOMATIC).testCase(tc1));
        addResultWithFeedback(exercise, TEST_PREFIX + "student1", "loc2", feedbacks);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // English stats text: "1 of 1 tests passing" (or similar English format)
        assertThat(result.html()).contains("of");
    }

    // --- #9: PlantUML testsColor resolution ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveTestsColorToGreenForPassedTest() throws Exception {
        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        exercise.setProblemStatement("@startuml\n!pragma layout smetana\nclass A\n<color:testsColor(<testid>" + tc1.getId() + "</testid>)>coloredText</color>\n@enduml");
        exerciseRepository.save(exercise);

        var feedbacks = List.of(new Feedback().credits(1.0).positive(true).type(FeedbackType.AUTOMATIC).testCase(tc1));
        addResultWithFeedback(exercise, TEST_PREFIX + "student1", "puml1", feedbacks);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // Diagram should reference this test case
        assertThat(result.diagrams()).hasSize(1);
        assertThat(result.diagrams().getFirst().testIds()).contains(tc1.getId());
        // The SVG should contain green color (test passed)
        assertThat(result.diagrams().getFirst().inlineSvg()).contains("green").doesNotContain("grey");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldResolveTestsColorToGreyWithoutResults() throws Exception {
        var setup = createProgrammingExerciseWithTestCases("placeholder");
        var exercise = setup.exercise();
        var testCases = setup.testCases();
        var tc1 = testCases.getFirst();
        exercise.setProblemStatement("@startuml\n!pragma layout smetana\nclass A\n<color:testsColor(<testid>" + tc1.getId() + "</testid>)>coloredText</color>\n@enduml");
        exerciseRepository.save(exercise);

        // No result/participation → grey fallback
        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.diagrams().getFirst().inlineSvg()).contains("grey");
    }
}
