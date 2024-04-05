package de.tum.in.www1.artemis.plagiarism;

import static de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.plagiarism.PlagiarismComparisonStatusDTO;

class PlagiarismIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "plagiarismintegration";

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PlagiarismResultRepository plagiarismResultRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course course;

    private TextExercise textExercise;

    private TextPlagiarismResult textPlagiarismResult;

    private PlagiarismComparison<TextSubmissionElement> plagiarismComparison1;

    private PlagiarismComparison<TextSubmissionElement> plagiarismComparison2;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 1, 1);
        course = textExerciseUtilService.addCourseWithOneFinishedTextExercise();
        textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textPlagiarismResult = textExerciseUtilService.createTextPlagiarismResultForExercise(textExercise);
        var textSubmission = ParticipationFactory.generateTextSubmission("", Language.GERMAN, true);

        var submission1 = participationUtilService.addSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        var submission2 = participationUtilService.addSubmission(textExercise, textSubmission, TEST_PREFIX + "student2");
        var submission3 = participationUtilService.addSubmission(textExercise, textSubmission, TEST_PREFIX + "student3");
        plagiarismComparison1 = new PlagiarismComparison<>();
        plagiarismComparison1.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison1.setStatus(CONFIRMED);
        var plagiarismSubmissionA1 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionA1.setStudentLogin(TEST_PREFIX + "student1");
        plagiarismSubmissionA1.setSubmissionId(submission1.getId());
        var plagiarismSubmissionB1 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionB1.setStudentLogin(TEST_PREFIX + "student2");
        plagiarismSubmissionB1.setSubmissionId(submission2.getId());
        plagiarismComparison1.setSubmissionA(plagiarismSubmissionA1);
        plagiarismComparison1.setSubmissionB(plagiarismSubmissionB1);
        plagiarismComparison1 = plagiarismComparisonRepository.save(plagiarismComparison1);

        plagiarismComparison2 = new PlagiarismComparison<>();
        plagiarismComparison2.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison2.setStatus(NONE);
        var plagiarismSubmissionA2 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionA2.setStudentLogin(TEST_PREFIX + "student2");
        plagiarismSubmissionA2.setSubmissionId(submission2.getId());
        var plagiarismSubmissionB2 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionB2.setStudentLogin(TEST_PREFIX + "student3");
        plagiarismSubmissionB2.setSubmissionId(submission3.getId());
        plagiarismComparison2.setSubmissionA(plagiarismSubmissionA2);
        plagiarismComparison2.setSubmissionB(plagiarismSubmissionB2);
        plagiarismComparison2 = plagiarismComparisonRepository.save(plagiarismComparison2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdatePlagiarismComparisonStatus_forbidden_student() throws Exception {
        request.put("/api/courses/1/plagiarism-comparisons/1/status", new PlagiarismComparisonStatusDTO(NONE), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdatePlagiarismComparisonStatus_forbidden_tutor() throws Exception {
        request.put("/api/courses/1/plagiarism-comparisons/1/status", new PlagiarismComparisonStatusDTO(NONE), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdatePlagiarismComparisonStatus_badRequest_teamExercise() throws Exception {
        textExercise.setMode(ExerciseMode.TEAM);
        textExercise = textExerciseRepository.save(textExercise);

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/status", new PlagiarismComparisonStatusDTO(NONE),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdatePlagiarismComparisonStatus() throws Exception {
        var plagiarismComparisonStatusDTOConfirmed1 = new PlagiarismComparisonStatusDTO(CONFIRMED);
        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/status", plagiarismComparisonStatusDTOConfirmed1,
                HttpStatus.OK);
        var updatedComparisonConfirmed = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison1.getId());
        assertThat(updatedComparisonConfirmed.getStatus()).as("should update plagiarism comparison status").isEqualTo(CONFIRMED);
        Optional<PlagiarismCase> plagiarismCaseOptionalPresent = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(TEST_PREFIX + "student1",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalPresent).as("should create new plagiarism case").isPresent();

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison2.getId() + "/status", new PlagiarismComparisonStatusDTO(CONFIRMED),
                HttpStatus.OK);
        var updatedComparisonConfirmed2 = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison2.getId());
        assertThat(updatedComparisonConfirmed2.getStatus()).as("should update plagiarism comparison status").isEqualTo(CONFIRMED);
        Optional<PlagiarismCase> plagiarismCaseOptionalPresent2 = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(TEST_PREFIX + "student1",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalPresent2).as("should add to existing plagiarism case").isPresent();

        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/status", new PlagiarismComparisonStatusDTO(DENIED),
                HttpStatus.OK);
        var updatedComparisonDenied = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison1.getId());
        assertThat(updatedComparisonDenied.getStatus()).as("should update plagiarism comparison status").isEqualTo(DENIED);

        Optional<PlagiarismCase> plagiarismCaseOptionalEmpty1 = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(TEST_PREFIX + "student1",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalEmpty1).as("should remove plagiarism case for student 1").isEmpty();

        Optional<PlagiarismCase> plagiarismCaseOptionalEmpty2 = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(TEST_PREFIX + "student2",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalEmpty2).as("should NOT remove plagiarism case for student2").isPresent();

        Optional<PlagiarismCase> plagiarismCaseOptionalEmpty3 = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(TEST_PREFIX + "student3",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalEmpty3).as("should NOT remove plagiarism case for student 3").isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismComparisonsForSplitView_student() throws Exception {
        var comparison = request.get("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/for-split-view", HttpStatus.OK,
                plagiarismComparison1.getClass());
        assertThat(comparison.getSubmissionA().getStudentLogin()).as("should anonymize plagiarism comparison").isIn("Your submission", "Other submission");
        assertThat(comparison.getSubmissionB().getStudentLogin()).as("should anonymize plagiarism comparison").isIn("Your submission", "Other submission");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void testGetPlagiarismComparisonsForSplitView_student_forbidden() throws Exception {
        request.get("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/for-split-view", HttpStatus.FORBIDDEN,
                plagiarismComparison1.getClass());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetPlagiarismComparisonsForSplitView_editor() throws Exception {
        PlagiarismComparison<?> comparison = request.get("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/for-split-view",
                HttpStatus.OK, plagiarismComparison1.getClass());
        assertThat(comparison).isEqualTo(plagiarismComparison1);
        assertThat(comparison.getPlagiarismResult()).isNull();
        assertThat(comparison.getSubmissionA()).isEqualTo(plagiarismComparison1.getSubmissionA());
        assertThat(comparison.getSubmissionB()).isEqualTo(plagiarismComparison1.getSubmissionB());
        assertThat(comparison.getSimilarity()).isEqualTo(plagiarismComparison1.getSimilarity());
        assertThat(comparison.getStatus()).isEqualTo(plagiarismComparison1.getStatus());
        assertThat(comparison.getMatches()).isEqualTo(plagiarismComparison1.getMatches());

        // Important: make sure those additional information is hidden
        assertThat(comparison.getSubmissionA().getPlagiarismCase()).isNull();
        assertThat(comparison.getSubmissionB().getPlagiarismCase()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeletePlagiarismComparisons_student() throws Exception {
        request.delete("/api/exercises/1/plagiarism-results/1/plagiarism-comparisons?deleteAll=false", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeletePlagiarismComparisons_tutor() throws Exception {
        request.delete("/api/exercises/1/plagiarism-results/1/plagiarism-comparisons?deleteAll=false", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testDeletePlagiarismComparisons_editor() throws Exception {
        request.delete("/api/exercises/1/plagiarism-results/1/plagiarism-comparisons?deleteAll=false", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeletePlagiarismComparisons_instructor() throws Exception {
        request.delete("/api/exercises/" + textExercise.getId() + "/plagiarism-results/" + textPlagiarismResult.getId() + "/plagiarism-comparisons?deleteAll=false", HttpStatus.OK);
        var result = plagiarismResultRepository.findFirstByExerciseIdOrderByLastModifiedDateDescOrNull(textExercise.getId());
        assertThat(result).isNotNull();
        assertThat(result.getComparisons()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeletePlagiarismComparisons_instructor_deleteAll() throws Exception {
        request.delete("/api/exercises/" + textExercise.getId() + "/plagiarism-results/" + textPlagiarismResult.getId() + "/plagiarism-comparisons?deleteAll=true", HttpStatus.OK);
        var result = plagiarismResultRepository.findFirstByExerciseIdOrderByLastModifiedDateDescOrNull(textExercise.getId());
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNumberOfPlagiarismResultsForExercise_instructor_correct() throws Exception {
        var results = request.get("/api/exercises/" + textExercise.getId() + "/potential-plagiarism-count", HttpStatus.OK, Long.class);
        assertThat(results).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testNumberOfPlagiarismResultsForExercise_tutor_forbidden() throws Exception {
        request.get("/api/exercises/" + textExercise.getId() + "/potential-plagiarism-count", HttpStatus.FORBIDDEN, Long.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNumberOfPlagiarismResultsForExercise_instructorNotInCourse_forbidden() throws Exception {
        courseUtilService.updateCourseGroups("abc", course, "");
        request.get("/api/exercises/" + textExercise.getId() + "/potential-plagiarism-count", HttpStatus.FORBIDDEN, Long.class);
        courseUtilService.updateCourseGroups(TEST_PREFIX, course, "");
    }
}
