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

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 1, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "User")
    public void testUpdatePlagiarismComparisonStatus_forbidden_student() throws Exception {
        request.put("/api/courses/1/plagiarism-comparisons/1/status", new PlagiarismComparisonStatusDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testUpdatePlagiarismComparisonStatus_forbidden_tutor() throws Exception {
        request.put("/api/courses/1/plagiarism-comparisons/1/status", new PlagiarismComparisonStatusDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "Editor")
    public void testUpdatePlagiarismComparisonStatus() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setStatus(PlagiarismStatus.NONE);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var plagiarismComparisonStatusDTO = new PlagiarismComparisonStatusDTO();
        plagiarismComparisonStatusDTO.setStatus(PlagiarismStatus.CONFIRMED);

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison.getId() + "/status", plagiarismComparisonStatusDTO, HttpStatus.OK);
        var updatedComparison = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison.getId());
        assertThat(updatedComparison.getStatus()).as("should update plagiarism comparison status").isEqualTo(PlagiarismStatus.CONFIRMED);
    }

    @Test
    @WithMockUser(username = "student1", roles = "User")
    public void testGetPlagiarismComparisonsForSplitView_student() throws Exception {
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

    @Test
    @WithMockUser(username = "editor1", roles = "Editor")
    public void testGetPlagiarismComparisonsForSplitView_editor() throws Exception {
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
}
