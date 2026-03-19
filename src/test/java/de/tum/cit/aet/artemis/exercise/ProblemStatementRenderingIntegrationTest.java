package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
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

    private TextExercise createCourseExerciseWithProblemStatement(String problemStatement) {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        exercise.setProblemStatement(problemStatement);
        return exerciseRepository.save(exercise);
    }

    // --- Rendering tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenderPlainMarkdown() throws Exception {
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
    void testRenderWithTasks() throws Exception {
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
    void testRenderWithPlantUml() throws Exception {
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
    void testRenderWithPlantUmlSelfContained() throws Exception {
        String ps = "Some text\n@startuml\n!pragma layout smetana\nclass Student\n@enduml\nMore text";
        TextExercise exercise = createCourseExerciseWithProblemStatement(ps);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.diagrams()).hasSize(1);
        assertThat(result.diagrams().getFirst().renderMode()).isEqualTo("inline");
        assertThat(result.diagrams().getFirst().inlineSvg()).contains("<svg");
        assertThat(result.html()).contains("<svg");
        assertThat(result.assets().diagramMode()).isEqualTo("inline");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenderWithKaTeX() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("The formula is $$E = mc^2$$");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.assets().katex()).isEqualTo("required");
        assertThat(result.html()).contains("$$E = mc^2$$");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenderWithCodeBlocks() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("```java\npublic class Foo {}\n```");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.assets().highlighting()).isEqualTo("required");
        assertThat(result.html()).contains("<code");
        assertThat(result.html()).contains("language-java");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenderWithTables() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("| Col A | Col B |\n|-------|-------|\n| 1     | 2     |");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).contains("<table>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenderEmptyProblemStatement() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement(null);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // With @JsonInclude(NON_EMPTY), empty strings and empty lists are omitted from JSON,
        // so Jackson deserializes them as null
        assertThat(result.html()).isNullOrEmpty();
        assertThat(result.tasks()).isNullOrEmpty();
        assertThat(result.diagrams()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenderXssInjection() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("<script>alert('xss')</script>\n\nSafe text");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).doesNotContain("<script>");
        assertThat(result.html()).contains("Safe text");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testContentHashChangesOnEdit() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("Version 1");
        RenderedProblemStatementDTO result1 = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        exercise.setProblemStatement("Version 2");
        exerciseRepository.save(exercise);
        RenderedProblemStatementDTO result2 = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result1.contentHash()).isNotEqualTo(result2.contentHash());
    }

    // --- Authorization tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAuthStudentCanAccessCourseExercise() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("Hello");
        request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAuthStudentCannotAccessExamExercise() throws Exception {
        TextExercise exercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        exercise.setProblemStatement("Hello");
        exerciseRepository.save(exercise);

        request.get(renderUrl(exercise.getId()), HttpStatus.FORBIDDEN, RenderedProblemStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAuthTutorCannotAccessExamExerciseBeforeEnd() throws Exception {
        // Default exam has endDate = now + 60min, so it hasn't ended yet
        TextExercise exercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        exercise.setProblemStatement("Hello");
        exerciseRepository.save(exercise);

        request.get(renderUrl(exercise.getId()), HttpStatus.FORBIDDEN, RenderedProblemStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAuthTutorCanAccessExamExerciseAfterEnd() throws Exception {
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
    void testAuthEditorCanAccessExamExercise() throws Exception {
        TextExercise exercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        exercise.setProblemStatement("Hello");
        exerciseRepository.save(exercise);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);
        assertThat(result.html()).contains("Hello");
    }

    @Test
    void testAuthUnauthenticated() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("Hello");
        request.get(renderUrl(exercise.getId()), HttpStatus.UNAUTHORIZED, RenderedProblemStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRelativeLinksConvertedToAbsolute() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("[link](/api/files/attachments/123)\n\n![image](/api/files/attachments/456)");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).contains("http");
        assertThat(result.html()).contains("/api/files/attachments/123");
        assertThat(result.html()).contains("/api/files/attachments/456");
        // Should not contain bare relative paths as href or src
        assertThat(result.html()).doesNotContain("href=\"/api/files");
        assertThat(result.html()).doesNotContain("src=\"/api/files");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSelfContainedSvgNotStrippedBySanitizer() throws Exception {
        String ps = "@startuml\n!pragma layout smetana\nclass Student\n@enduml";
        TextExercise exercise = createCourseExerciseWithProblemStatement(ps);

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId(), true), HttpStatus.OK, RenderedProblemStatementDTO.class);

        // The inline SVG should be present in the HTML
        assertThat(result.html()).contains("<svg");
        // The SVG should contain the class name from the diagram
        assertThat(result.html()).contains("Student");
        // SVG placeholder spans must not remain in the output
        assertThat(result.html()).doesNotContain("artemis-svg-placeholder");
    }

    // --- Additional rendering edge case tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenderBlankProblemStatement() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("   \n  \t  ");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).isNullOrEmpty();
        assertThat(result.tasks()).isNullOrEmpty();
        assertThat(result.diagrams()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenderMultipleTasksAndDiagrams() throws Exception {
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
    void testRenderTaskNameWithSpecialCharacters() throws Exception {
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
    void testTestIdTagsStrippedFromOutput() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("[task][Sort](<testid>42</testid>)");

        RenderedProblemStatementDTO result = request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);

        assertThat(result.html()).doesNotContain("<testid>");
        assertThat(result.html()).doesNotContain("</testid>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testXssEventHandlerStripped() throws Exception {
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
    void testAuthStudentOutsideCourseCannotAccess() throws Exception {
        userUtilService.createAndSaveUser(TEST_PREFIX + "outsider1");
        TextExercise exercise = createCourseExerciseWithProblemStatement("Hello");

        // outsider1 is not in the course groups (tumuser, tutor, editor, instructor)
        request.get(renderUrl(exercise.getId()), HttpStatus.FORBIDDEN, RenderedProblemStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testNonExistentExerciseReturns404() throws Exception {
        request.get(renderUrl(999999L), HttpStatus.NOT_FOUND, RenderedProblemStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAuthTutorExamExerciseExactBoundary() throws Exception {
        TextExercise exercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        exercise.setProblemStatement("Hello");
        exerciseRepository.save(exercise);

        // Set exam end date to exactly now — exam has not fully ended yet
        Exam exam = exercise.getExerciseGroup().getExam();
        exam.setEndDate(ZonedDateTime.now());
        examRepository.save(exam);

        // When endDate equals now, isAfter(now()) may be false (race), but the intent is
        // that the exam must be strictly in the past. This should be forbidden or at the boundary.
        // The controller checks: latestIndividualExamEndDate.isAfter(ZonedDateTime.now())
        // If endDate == now, isAfter returns false, so access is granted.
        // This is consistent with ExerciseResource behavior.
        request.get(renderUrl(exercise.getId()), HttpStatus.OK, RenderedProblemStatementDTO.class);
    }

    // --- HTTP contract tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testResponseContainsETagAndCacheControlHeaders() throws Exception {
        TextExercise exercise = createCourseExerciseWithProblemStatement("Header test");

        MvcResult result = request.performMvcRequest(get(new URI(renderUrl(exercise.getId())))).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader("ETag")).isNotNull().isNotBlank();
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("max-age=300").contains("private");
    }
}
