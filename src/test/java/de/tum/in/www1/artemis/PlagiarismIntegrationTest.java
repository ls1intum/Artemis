package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismCaseDTO;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismComparisonStatusDTO;
import de.tum.in.www1.artemis.web.rest.plagiarism.PlagiarismResource;

public class PlagiarismIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    private static final String INSTRUCTOR_STATEMENT_A = "instructor Statement A";

    private static final String INSTRUCTOR_STATEMENT_B = "instructor Statement B";

    @BeforeEach
    public void initTestCase() {
        database.addUsers(3, 1, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    /**
     * Checks the method updatePlagiarismComparisonFinalStatus as student
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updatePlagiarismComparisonFinalStatus_student() throws Exception {
        request.put("/api/courses/1/plagiarism-comparisons/1/final-status/student1", new PlagiarismComparisonStatusDTO(), HttpStatus.FORBIDDEN);
    }

    /**
     * Checks the method updatePlagiarismComparisonFinalStatus as editor
     */
    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void updatePlagiarismComparisonFinalStatus_editor() throws Exception {
        request.put("/api/courses/1/plagiarism-comparisons/1/final-status/student1", new PlagiarismComparisonStatusDTO(), HttpStatus.FORBIDDEN);
    }

    /**
     * Checks the method updatePlagiarismComparisonFinalStatus as instructor
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updatePlagiarismComparisonFinalStatusForStudentA() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        plagiarismComparison.setInstructorStatementA(INSTRUCTOR_STATEMENT_A);
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setStatusA(PlagiarismStatus.NONE);
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var plagiarismComparisonStatus = new PlagiarismComparisonStatusDTO();
        plagiarismComparisonStatus.setStatus(PlagiarismStatus.CONFIRMED);

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/final-status/student1", plagiarismComparisonStatus,
                HttpStatus.OK);
        var updatedPlagiarismComparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison.getId());
        assertThat(updatedPlagiarismComparison.getStatusA()).as("should update status for studentA").isEqualTo(PlagiarismStatus.CONFIRMED);
    }

    /**
     * Checks the method updatePlagiarismComparisonFinalStatus for second (B) user
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updatePlagiarismComparisonFinalStatusForStudentB() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        PlagiarismSubmission<TextSubmissionElement> submissionB = new PlagiarismSubmission<>();
        submissionB.setStudentLogin("student2");
        plagiarismComparison.setInstructorStatementB(INSTRUCTOR_STATEMENT_B);
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setStatusB(PlagiarismStatus.NONE);
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparison.setSubmissionB(submissionB);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var plagiarismComparisonStatus = new PlagiarismComparisonStatusDTO();
        plagiarismComparisonStatus.setStatus(PlagiarismStatus.CONFIRMED);

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/final-status/student2", plagiarismComparisonStatus,
                HttpStatus.OK);
        var updatedPlagiarismComparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison.getId());
        assertThat(updatedPlagiarismComparison.getStatusB()).as("should update status for studentB").isEqualTo(PlagiarismStatus.CONFIRMED);
    }

    /**
     * Checks the method updatePlagiarismComparisonFinalStatus for unknown student
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updatePlagiarismComparisonFinalStatusForUnknownStudent() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        PlagiarismSubmission<TextSubmissionElement> submissionB = new PlagiarismSubmission<>();
        submissionB.setStudentLogin("student2");
        plagiarismComparison.setInstructorStatementB(INSTRUCTOR_STATEMENT_B);
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setStatusB(PlagiarismStatus.NONE);
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparison.setSubmissionB(submissionB);
        plagiarismComparisonRepository.save(plagiarismComparison);

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/final-status/student42", new PlagiarismComparisonStatusDTO(),
                HttpStatus.NOT_FOUND);
    }

    /**
     * Checks the method getPlagiarismComparisonsForCourse as student
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getPlagiarismComparisonsForCourse_student() throws Exception {
        request.getList("/api/courses/" + 1L + "/plagiarism-cases", HttpStatus.FORBIDDEN, PlagiarismCaseDTO.class);
    }

    /**
     * Checks the method getPlagiarismComparisonsForCourse as tutor
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void getPlagiarismComparisonsForCourse_tutor() throws Exception {
        request.getList("/api/courses/" + 1L + "/plagiarism-cases", HttpStatus.FORBIDDEN, PlagiarismCaseDTO.class);
    }

    /**
     * Checks the method getPlagiarismComparisonsForCourse as instructor
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getPlagiarismComparisonsForCourse_instructor() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison1 = new PlagiarismComparison<>();
        plagiarismComparison1.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison1.setStatus(PlagiarismStatus.CONFIRMED);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison2 = new PlagiarismComparison<>();
        plagiarismComparison2.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison2.setStatus(PlagiarismStatus.CONFIRMED);
        plagiarismComparisonRepository.save(plagiarismComparison1);
        plagiarismComparisonRepository.save(plagiarismComparison2);

        var comparisons = request.getList("/api/courses/" + course.getId() + "/plagiarism-cases", HttpStatus.OK, plagiarismComparison1.getClass());
        assertThat(comparisons).hasSize(2);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void getPlagiarismComparisonsForEditor() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison1 = new PlagiarismComparison<>();
        plagiarismComparison1.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison1.setStatus(PlagiarismStatus.CONFIRMED);
        var savedComparison = plagiarismComparisonRepository.save(plagiarismComparison1);

        var comparison = request.get("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + savedComparison.getId() + "/for-split-view", HttpStatus.OK,
                plagiarismComparison1.getClass());
        assertThat(comparison.getPlagiarismResult()).isEqualTo(textPlagiarismResult);
    }

    /**
     * Checks the method updatePlagiarismComparisonStudentStatement for student A
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void saveStudentStatementForStudentA() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setInstructorStatementA(INSTRUCTOR_STATEMENT_A);
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var statement = new PlagiarismResource.PlagiarismStatementDTO();
        statement.statement = "test statement";

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/student-statement", statement, HttpStatus.OK);
        var comparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison.getId());
        assertThat(comparison.getStudentStatementA()).as("should update student statement").isEqualTo("test statement");
    }

    /**
     * Checks the method updatePlagiarismComparisonStudentStatement for student B
     */
    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void saveStudentStatementForStudentB() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setInstructorStatementB(INSTRUCTOR_STATEMENT_B);
        PlagiarismSubmission<TextSubmissionElement> submissionB = new PlagiarismSubmission<>();
        submissionB.setStudentLogin("student2");
        plagiarismComparison.setSubmissionB(submissionB);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var statement = new PlagiarismResource.PlagiarismStatementDTO();
        statement.statement = "test statement";

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/student-statement", statement, HttpStatus.OK);
        var comparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison.getId());
        assertThat(comparison.getStudentStatementB()).as("should update student statement").isEqualTo("test statement");
    }

    /**
     * Checks the method updatePlagiarismComparisonStudentStatement for unknown student
     */
    @Test
    @WithMockUser(username = "student3", roles = "USER")
    public void saveStudentStatementForUnknownStudent() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        submissionA.setStudentLogin("student1");
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparison.setInstructorStatementA(INSTRUCTOR_STATEMENT_A);
        PlagiarismSubmission<TextSubmissionElement> submissionB = new PlagiarismSubmission<>();
        submissionB.setStudentLogin("student2");
        plagiarismComparison.setSubmissionB(submissionB);
        plagiarismComparison.setInstructorStatementB(INSTRUCTOR_STATEMENT_B);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var statement = new PlagiarismResource.PlagiarismStatementDTO();
        statement.statement = "test statement";

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/student-statement", statement, HttpStatus.FORBIDDEN);
    }

    /**
     * Checks the method updatePlagiarismComparisonInstructorStatement for student A
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void saveInstructorStatementForStudentA() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var statement = new PlagiarismResource.PlagiarismStatementDTO();
        statement.statement = "test statement";

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/instructor-statement/student1", statement, HttpStatus.OK);
        var comparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison.getId());
        assertThat(comparison.getInstructorStatementA()).as("should update instructor statement").isEqualTo("test statement");
    }

    /**
     * Checks the method updatePlagiarismComparisonInstructorStatement for student B
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void saveInstructorStatementForStudentB() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        PlagiarismSubmission<TextSubmissionElement> submissionB = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        submissionB.setStudentLogin("student2");
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparison.setSubmissionB(submissionB);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var statement = new PlagiarismResource.PlagiarismStatementDTO();
        statement.statement = "test statement";

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/instructor-statement/student2", statement, HttpStatus.OK);
        var comparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison.getId());
        assertThat(comparison.getInstructorStatementB()).as("should update instructor statement").isEqualTo("test statement");
    }

    /**
     * Checks the method updatePlagiarismComparisonInstructorStatement for unknown student
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void saveInstructorStatementForUnknownStudent() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        PlagiarismSubmission<TextSubmissionElement> submissionB = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        submissionB.setStudentLogin("student2");
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparison.setSubmissionB(submissionB);
        plagiarismComparisonRepository.save(plagiarismComparison);

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/instructor-statement/student42",
                new PlagiarismResource.PlagiarismStatementDTO(), HttpStatus.NOT_FOUND);
    }
}
