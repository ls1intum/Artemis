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
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismComparisonStatusDTO;

public class PlagiarismIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    private static Course course;

    private static TextExercise textExercise;

    private static TextPlagiarismResult textPlagiarismResult;

    private static PlagiarismComparison<TextSubmissionElement> plagiarismComparison;

    private static PlagiarismSubmission<TextSubmissionElement> plagiarismSubmissionA;

    private static PlagiarismSubmission<TextSubmissionElement> plagiarismSubmissionB;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 1, 1, 1);
        course = database.addCourseWithOneFinishedTextExercise();
        textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        plagiarismComparison = new PlagiarismComparison<>();
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setStatus(PlagiarismStatus.NONE);
        plagiarismSubmissionA = new PlagiarismSubmission<>();
        plagiarismSubmissionA.setStudentLogin("student1");
        plagiarismSubmissionB = new PlagiarismSubmission<>();
        plagiarismSubmissionB.setStudentLogin("student2");
        plagiarismComparison.setSubmissionA(plagiarismSubmissionA);
        plagiarismComparison.setSubmissionB(plagiarismSubmissionB);
        plagiarismComparison = plagiarismComparisonRepository.save(plagiarismComparison);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testUpdatePlagiarismComparisonStatus_forbidden_student() throws Exception {
        request.put("/api/courses/1/plagiarism-comparisons/1/status", new PlagiarismComparisonStatusDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testUpdatePlagiarismComparisonStatus_forbidden_tutor() throws Exception {
        request.put("/api/courses/1/plagiarism-comparisons/1/status", new PlagiarismComparisonStatusDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testUpdatePlagiarismComparisonStatus() throws Exception {
        var plagiarismComparisonStatusDTO = new PlagiarismComparisonStatusDTO();
        plagiarismComparisonStatusDTO.setStatus(PlagiarismStatus.CONFIRMED);

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/status", plagiarismComparisonStatusDTO, HttpStatus.OK);
        var updatedComparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison.getId());
        assertThat(updatedComparison.getStatus()).as("should update plagiarism comparison status").isEqualTo(PlagiarismStatus.CONFIRMED);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPlagiarismComparisonsForSplitView_student() throws Exception {
        var comparison = request.get("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/for-split-view", HttpStatus.OK,
                plagiarismComparison.getClass());
        assertThat(comparison.getPlagiarismResult()).isEqualTo(textPlagiarismResult);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testGetPlagiarismComparisonsForSplitView_editor() throws Exception {
        var comparison = request.get("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/for-split-view", HttpStatus.OK,
                plagiarismComparison.getClass());
        assertThat(comparison.getPlagiarismResult()).isEqualTo(textPlagiarismResult);
    }
}
