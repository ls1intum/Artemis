package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

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
    }
}
