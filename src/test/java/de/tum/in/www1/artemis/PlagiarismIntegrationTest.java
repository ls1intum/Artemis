package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismComparisonStatusDTO;

class PlagiarismIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PlagiarismResultRepository plagiarismResultRepository;

    private static Course course;

    private static TextExercise textExercise;

    private static TextPlagiarismResult textPlagiarismResult;

    private static PlagiarismComparison<TextSubmissionElement> plagiarismComparison1;

    private static PlagiarismSubmission<TextSubmissionElement> plagiarismSubmissionA1;

    private static PlagiarismSubmission<TextSubmissionElement> plagiarismSubmissionB1;

    private static PlagiarismComparison<TextSubmissionElement> plagiarismComparison2;

    private static PlagiarismSubmission<TextSubmissionElement> plagiarismSubmissionA2;

    private static PlagiarismSubmission<TextSubmissionElement> plagiarismSubmissionB2;

    @BeforeEach
    void initTestCase() {
        database.addUsers(3, 1, 1, 1);
        course = database.addCourseWithOneFinishedTextExercise();
        textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        plagiarismComparison1 = new PlagiarismComparison<>();
        plagiarismComparison1.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison1.setStatus(PlagiarismStatus.CONFIRMED);
        plagiarismSubmissionA1 = new PlagiarismSubmission<>();
        plagiarismSubmissionA1.setStudentLogin("student1");
        plagiarismSubmissionB1 = new PlagiarismSubmission<>();
        plagiarismSubmissionB1.setStudentLogin("student2");
        plagiarismComparison1.setSubmissionA(plagiarismSubmissionA1);
        plagiarismComparison1.setSubmissionB(plagiarismSubmissionB1);
        plagiarismComparison1 = plagiarismComparisonRepository.save(plagiarismComparison1);
        plagiarismComparison2 = new PlagiarismComparison<>();
        plagiarismComparison2.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison2.setStatus(PlagiarismStatus.NONE);
        plagiarismSubmissionA2 = new PlagiarismSubmission<>();
        plagiarismSubmissionA2.setStudentLogin("student2");
        plagiarismSubmissionB2 = new PlagiarismSubmission<>();
        plagiarismSubmissionB2.setStudentLogin("student3");
        plagiarismComparison2.setSubmissionA(plagiarismSubmissionA2);
        plagiarismComparison2.setSubmissionB(plagiarismSubmissionB2);
        plagiarismComparison2 = plagiarismComparisonRepository.save(plagiarismComparison2);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testUpdatePlagiarismComparisonStatus_forbidden_student() throws Exception {
        request.put("/api/courses/1/plagiarism-comparisons/1/status", new PlagiarismComparisonStatusDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testUpdatePlagiarismComparisonStatus_forbidden_tutor() throws Exception {
        request.put("/api/courses/1/plagiarism-comparisons/1/status", new PlagiarismComparisonStatusDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testUpdatePlagiarismComparisonStatus() throws Exception {
        var plagiarismComparisonStatusDTOConfirmed1 = new PlagiarismComparisonStatusDTO();
        plagiarismComparisonStatusDTOConfirmed1.setStatus(PlagiarismStatus.CONFIRMED);
        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/status", plagiarismComparisonStatusDTOConfirmed1,
                HttpStatus.OK);
        var updatedComparisonConfirmed = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison1.getId());
        assertThat(updatedComparisonConfirmed.getStatus()).as("should update plagiarism comparison status").isEqualTo(PlagiarismStatus.CONFIRMED);
        Optional<PlagiarismCase> plagiarismCaseOptionalPresent = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions("student1",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalPresent.isPresent()).as("should create new plagiarism case").isTrue();

        var plagiarismComparisonStatusDTOConfirmed2 = new PlagiarismComparisonStatusDTO();
        plagiarismComparisonStatusDTOConfirmed2.setStatus(PlagiarismStatus.CONFIRMED);
        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison2.getId() + "/status", plagiarismComparisonStatusDTOConfirmed2,
                HttpStatus.OK);
        var updatedComparisonConfirmed2 = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison2.getId());
        assertThat(updatedComparisonConfirmed2.getStatus()).as("should update plagiarism comparison status").isEqualTo(PlagiarismStatus.CONFIRMED);
        Optional<PlagiarismCase> plagiarismCaseOptionalPresent2 = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions("student1",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalPresent2.isPresent()).as("should add to existing plagiarism case").isTrue();

        var plagiarismComparisonStatusDTODenied = new PlagiarismComparisonStatusDTO();
        plagiarismComparisonStatusDTODenied.setStatus(PlagiarismStatus.DENIED);
        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/status", plagiarismComparisonStatusDTODenied, HttpStatus.OK);
        var updatedComparisonDenied = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison1.getId());
        assertThat(updatedComparisonDenied.getStatus()).as("should update plagiarism comparison status").isEqualTo(PlagiarismStatus.DENIED);
        Optional<PlagiarismCase> plagiarismCaseOptionalEmpty = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions("student1", textExercise.getId());
        assertThat(plagiarismCaseOptionalEmpty.isEmpty()).as("should remove plagiarism case").isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPlagiarismComparisonsForSplitView_student() throws Exception {
        var comparison = request.get("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/for-split-view", HttpStatus.OK,
                plagiarismComparison1.getClass());
        assertThat(comparison.getSubmissionA().getStudentLogin()).as("should anonymize plagiarism comparison").isIn("Your submission", "Other submission");
        assertThat(comparison.getSubmissionB().getStudentLogin()).as("should anonymize plagiarism comparison").isIn("Your submission", "Other submission");
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testGetPlagiarismComparisonsForSplitView_editor() throws Exception {
        request.get("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/for-split-view", HttpStatus.OK,
                plagiarismComparison1.getClass());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testDeletePlagiarismComparisons_student() throws Exception {
        request.delete("/api/exercises/1/plagiarism-results/1/plagiarism-comparisons?deleteAll=false", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testDeletePlagiarismComparisons_tutor() throws Exception {
        request.delete("/api/exercises/1/plagiarism-results/1/plagiarism-comparisons?deleteAll=false", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testDeletePlagiarismComparisons_editor() throws Exception {
        request.delete("/api/exercises/1/plagiarism-results/1/plagiarism-comparisons?deleteAll=false", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeletePlagiarismComparisons_instructor() throws Exception {
        request.delete("/api/exercises/" + textExercise.getId() + "/plagiarism-results/" + textPlagiarismResult.getId() + "/plagiarism-comparisons?deleteAll=false", HttpStatus.OK);
        var result = plagiarismResultRepository.findFirstByExerciseIdOrderByLastModifiedDateDescOrNull(textExercise.getId());
        assert result != null;
        assertThat(result.getComparisons().size()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeletePlagiarismComparisons_instructor_deleteAll() throws Exception {
        request.delete("/api/exercises/" + textExercise.getId() + "/plagiarism-results/" + textPlagiarismResult.getId() + "/plagiarism-comparisons?deleteAll=true", HttpStatus.OK);
        var result = plagiarismResultRepository.findFirstByExerciseIdOrderByLastModifiedDateDescOrNull(textExercise.getId());
        assertThat(result).isNull();
    }
}
