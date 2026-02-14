package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.dto.GradingScaleDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradingScaleRequestDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.util.GradingScaleUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class GradingScaleIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "gradingscale";

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private GradingScaleUtilService gradingScaleUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    private GradingScale courseGradingScale;

    private GradingScale examGradingScale;

    private Set<GradeStep> gradeSteps;

    private Course course;

    private Exam exam;

    /**
     * Converts a GradingScale entity to a GradingScaleRequestDTO for testing.
     */
    private GradingScaleRequestDTO toRequestDTO(GradingScale gradingScale) {
        Set<GradingScaleRequestDTO.GradeStepDTO> gradeStepDTOs = new HashSet<>();
        if (gradingScale.getGradeSteps() != null) {
            for (GradeStep step : gradingScale.getGradeSteps()) {
                gradeStepDTOs.add(new GradingScaleRequestDTO.GradeStepDTO(step.getLowerBoundPercentage(), step.isLowerBoundInclusive(), step.getUpperBoundPercentage(),
                        step.isUpperBoundInclusive(), step.getGradeName(), step.getIsPassingGrade()));
            }
        }
        return new GradingScaleRequestDTO(gradingScale.getGradeType(), gradingScale.getBonusStrategy(), gradingScale.getPlagiarismGrade(), gradingScale.getNoParticipationGrade(),
                gradingScale.getPresentationsNumber(), gradingScale.getPresentationsWeight(), gradeStepDTOs,
                gradingScale.getCourse() != null ? gradingScale.getCourse().getMaxPoints() : null,
                gradingScale.getCourse() != null ? gradingScale.getCourse().getPresentationScore() : null,
                gradingScale.getExam() != null ? gradingScale.getExam().getExamMaxPoints() : null);
    }

    /**
     * Initialize variables
     */
    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        instructor.setGroups(Set.of("gradingscaleintegrationinstructors"));
        userTestRepository.save(instructor);

        course = courseUtilService.addEmptyCourse();
        course.setInstructorGroupName("gradingscaleintegrationinstructors");
        courseRepository.save(course);

        exam = examUtilService.addExamWithExerciseGroup(course, true);
        courseGradingScale = new GradingScale();
        courseGradingScale.setCourse(course);
        examGradingScale = new GradingScale();
        examGradingScale.setExam(exam);
        gradeSteps = new HashSet<>();
        courseGradingScale.setGradeSteps(gradeSteps);
        examGradingScale.setGradeSteps(gradeSteps);
    }

    /**
     * Test get request for non-existing grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetGradingScaleForCourseNotFound() throws Exception {
        request.get("/api/assessment/courses/" + course.getId() + "/grading-scale", HttpStatus.OK, Void.class);
    }

    /**
     * Test get request for a grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetGradingScaleForCourse() throws Exception {
        courseGradingScale.setGradeSteps(Set.of());
        gradingScaleRepository.save(courseGradingScale);

        GradingScaleDTO foundGradingScale = request.get("/api/assessment/courses/" + course.getId() + "/grading-scale", HttpStatus.OK, GradingScaleDTO.class);

        assertThat(foundGradingScale).usingRecursiveComparison().ignoringFields("id", "course", "exam").isEqualTo(GradingScaleDTO.of(courseGradingScale));
    }

    /**
     * Test get request for non-existing grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetGradingScaleForExamNotFound() throws Exception {
        request.get("/api/assessment/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.OK, Void.class);
    }

    /**
     * Test get request for a grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetGradingScaleForExam() throws Exception {
        examGradingScale.setGradeSteps(Set.of());
        gradingScaleRepository.save(examGradingScale);

        GradingScaleDTO foundGradingScale = request.get("/api/assessment/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.OK,
                GradingScaleDTO.class);

        assertThat(foundGradingScale).usingRecursiveComparison().ignoringFields("id", "course", "exam").isEqualTo(GradingScaleDTO.of(examGradingScale));
    }

    /**
     * Test post request for existing grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForCourseGradingScaleAlreadyExists() throws Exception {
        gradingScaleRepository.save(courseGradingScale);

        request.post("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale), HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post-request for a grading scale without set grade steps
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForCourseGradeStepsAreNotSet() throws Exception {
        courseGradingScale.setGradeSteps(Set.of());

        request.post("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale), HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post request for existing grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForExamGradingScaleAlreadyExists() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.post("/api/assessment/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", toRequestDTO(examGradingScale), HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post-request for a grading scale without set grade steps
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForExamGradeStepsAreNotSet() throws Exception {
        examGradingScale.setGradeSteps(Set.of());

        request.post("/api/assessment/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", toRequestDTO(examGradingScale), HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post-request with invalid grade steps
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForCourseInvalidGradeSteps() throws Exception {
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(courseGradingScale, false);
        courseGradingScale.setGradeSteps(gradeSteps);

        request.post("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale), HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post-request with invalid presentation configuration for basic presentations
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForCourseInvalidBasicPresentationConfiguration() throws Exception {
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(courseGradingScale, true);
        courseGradingScale.setGradeSteps(gradeSteps);

        // The presentationsNumber and presentationsWeight must be null.
        course.setPresentationScore(2);
        courseGradingScale.setPresentationsNumber(1);
        courseGradingScale.setPresentationsWeight(20.0);
        request.post("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale), HttpStatus.BAD_REQUEST);
        courseGradingScale.setPresentationsNumber(null);
        courseGradingScale.setPresentationsWeight(null);

        // The presentationScore must be above 0.
        course.setPresentationScore(-1);
        request.post("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale), HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post-request with invalid presentation configuration for graded presentations
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForCourseInvalidGradedPresentationConfiguration() throws Exception {
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(courseGradingScale, true);
        courseGradingScale.setGradeSteps(gradeSteps);

        // The presentationsNumber must be above 0. The presentationsWeight must be between 0 and 99.
        course.setPresentationScore(null);
        courseGradingScale.setPresentationsNumber(0);
        courseGradingScale.setPresentationsWeight(120.0);
        request.post("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale), HttpStatus.BAD_REQUEST);

        // The gradingScale must belong to a course.
        courseGradingScale.setPresentationsNumber(2);
        courseGradingScale.setPresentationsWeight(20.0);
        courseGradingScale.setCourse(null);
        request.post("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale), HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post-request with invalid presentation configuration for graded presentations
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForCourseWithChangedPresentationScore() throws Exception {
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(courseGradingScale, true);
        courseGradingScale.setGradeSteps(gradeSteps);
        course.setPresentationScore(5);

        GradingScaleDTO savedGradingScale = request.postWithResponseBody("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale),
                GradingScaleDTO.class, HttpStatus.CREATED);

        assertThat(savedGradingScale.gradeSteps().gradeSteps()).hasSameSizeAs(courseGradingScale.getGradeSteps());
        assertThat(savedGradingScale.gradeSteps().gradeSteps()).allMatch(gradeStep -> isGradeStepInSet(courseGradingScale.getGradeSteps(), gradeStep));
        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course", "gradeSteps").ignoringCollectionOrder().isEqualTo(courseGradingScale);
    }

    /**
     * Test post-request for a grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForCourse() throws Exception {
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(courseGradingScale, true);
        courseGradingScale.setGradeSteps(gradeSteps);

        GradingScaleDTO savedGradingScale = request.postWithResponseBody("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale),
                GradingScaleDTO.class, HttpStatus.CREATED);

        assertThat(savedGradingScale.gradeSteps().gradeSteps()).hasSameSizeAs(courseGradingScale.getGradeSteps());
        assertThat(savedGradingScale.gradeSteps().gradeSteps()).allMatch(gradeStep -> isGradeStepInSet(courseGradingScale.getGradeSteps(), gradeStep));
        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course", "gradeSteps").ignoringCollectionOrder().isEqualTo(courseGradingScale);
    }

    /**
     * Test post-request for a grading scale with plagiarism and no-participation special grades
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleWithSpecialGrades() throws Exception {
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(courseGradingScale, true);
        courseGradingScale.setGradeSteps(gradeSteps);
        courseGradingScale.setPlagiarismGrade("Plagiarism");
        courseGradingScale.setNoParticipationGrade("NoParticipation");
        GradingScaleDTO savedGradingScale = request.postWithResponseBody("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale),
                GradingScaleDTO.class, HttpStatus.CREATED);

        assertThat(savedGradingScale.gradeSteps().gradeSteps()).hasSameSizeAs(courseGradingScale.getGradeSteps());
        assertThat(savedGradingScale.gradeSteps().gradeSteps()).allMatch(gradeStep -> isGradeStepInSet(courseGradingScale.getGradeSteps(), gradeStep));
        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course", "gradeSteps").ignoringCollectionOrder().isEqualTo(courseGradingScale);
    }

    /**
     * Test post-request with invalid grade steps
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForExamInvalidGradeSteps() throws Exception {
        exam.setExamMaxPoints(null);
        examRepository.save(exam);
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(examGradingScale, false);
        examGradingScale.setGradeSteps(gradeSteps);

        request.post("/api/assessment/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", toRequestDTO(examGradingScale), HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post-request for a grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForExam() throws Exception {
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(examGradingScale, true);
        examGradingScale.setGradeSteps(gradeSteps);
        exam.setExamMaxPoints(null);
        examRepository.save(exam);

        GradingScaleDTO savedGradingScale = request.postWithResponseBody("/api/assessment/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale",
                toRequestDTO(examGradingScale), GradingScaleDTO.class, HttpStatus.CREATED);

        assertThat(savedGradingScale.gradeSteps().gradeSteps()).hasSameSizeAs(examGradingScale.getGradeSteps());
        assertThat(savedGradingScale.gradeSteps().gradeSteps()).allMatch(gradeStep -> isGradeStepInSet(examGradingScale.getGradeSteps(), gradeStep));
        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course", "gradeSteps").ignoringCollectionOrder().isEqualTo(examGradingScale);
    }

    /**
     * Test put request for non-existing grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForCourseGradingScaleNotFound() throws Exception {
        request.put("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale), HttpStatus.NOT_FOUND);
    }

    /**
     * Test put request with invalid grade steps
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForCourseInvalidGradeSteps() throws Exception {
        gradingScaleRepository.save(courseGradingScale);
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(courseGradingScale, false);
        courseGradingScale.setGradeSteps(gradeSteps);

        request.put("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale), HttpStatus.BAD_REQUEST);
    }

    /**
     * Test put request for a grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForCourse() throws Exception {
        gradingScaleRepository.save(courseGradingScale);
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(courseGradingScale, true);
        courseGradingScale.setGradeSteps(gradeSteps);

        GradingScaleDTO savedGradingScale = request.putWithResponseBody("/api/assessment/courses/" + course.getId() + "/grading-scale", toRequestDTO(courseGradingScale),
                GradingScaleDTO.class, HttpStatus.OK);

        assertThat(savedGradingScale.gradeSteps().gradeSteps()).hasSameSizeAs(courseGradingScale.getGradeSteps());
        assertThat(savedGradingScale.gradeSteps().gradeSteps()).allMatch(gradeStep -> isGradeStepInSet(courseGradingScale.getGradeSteps(), gradeStep));
        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course", "gradeSteps").ignoringCollectionOrder().isEqualTo(courseGradingScale);
    }

    /**
     * Test put request for non-existing grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForExamGradingScaleNotFound() throws Exception {
        request.put("/api/assessment/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", toRequestDTO(examGradingScale), HttpStatus.NOT_FOUND);
    }

    /**
     * Test put request with invalid grade steps
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForExamInvalidGradeSteps() throws Exception {
        exam.setExamMaxPoints(null);
        examRepository.save(exam);
        gradingScaleRepository.save(examGradingScale);
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(examGradingScale, false);
        examGradingScale.setGradeSteps(gradeSteps);

        request.put("/api/assessment/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", toRequestDTO(examGradingScale), HttpStatus.BAD_REQUEST);
    }

    /**
     * Test put request for a grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForExam() throws Exception {
        gradingScaleRepository.save(examGradingScale);
        gradeSteps = gradingScaleUtilService.generateGradeStepSet(examGradingScale, true);
        examGradingScale.setGradeSteps(gradeSteps);
        exam.setExamMaxPoints(null);
        examRepository.save(exam);

        GradingScaleDTO savedGradingScale = request.putWithResponseBody("/api/assessment/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale",
                toRequestDTO(examGradingScale), GradingScaleDTO.class, HttpStatus.OK);

        assertThat(savedGradingScale.gradeSteps().gradeSteps()).hasSameSizeAs(examGradingScale.getGradeSteps());
        assertThat(savedGradingScale.gradeSteps().gradeSteps()).allMatch(gradeStep -> isGradeStepInSet(examGradingScale.getGradeSteps(), gradeStep));
        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course", "gradeSteps").ignoringCollectionOrder().isEqualTo(examGradingScale);
    }

    /**
     * Test delete request for non-existing grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteGradingScaleForCourseNotFound() throws Exception {
        request.delete("/api/assessment/courses/" + course.getId() + "/grading-scale", HttpStatus.NOT_FOUND);
    }

    /**
     * Test delete request for grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteGradingScaleForCourse() throws Exception {
        gradingScaleRepository.save(courseGradingScale);

        request.delete("/api/assessment/courses/" + course.getId() + "/grading-scale", HttpStatus.OK);
    }

    /**
     * Test delete request for non-existing grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteGradingScaleForExamNotFound() throws Exception {
        request.delete("/api/assessment/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.NOT_FOUND);
    }

    /**
     * Test delete request for grading scale
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteGradingScaleForExam() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.delete("/api/assessment/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.OK);
    }

    /**
     * Test delete request for a course should delete the grading scale of that course as well
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourseDeletesGradingScale() throws Exception {
        gradingScaleRepository.save(courseGradingScale);

        request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

        Optional<GradingScale> foundGradingScale = gradingScaleRepository.findByCourseId(course.getId());
        assertThat(foundGradingScale).isEmpty();
    }

    /**
     * Test delete request for exam should delete the grading scale of that exam as well
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamDeletesGradingScale() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.delete("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);

        Optional<GradingScale> foundGradingScale = gradingScaleRepository.findByExamId(exam.getId());
        assertThat(foundGradingScale).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllGradingScalesInInstructorGroupOnPageWithAdmin() throws Exception {
        course.setTitle("abcdefghijklmnop");
        courseRepository.save(course);
        exam.setTitle("abcdefghijklmnop");
        examRepository.save(exam);

        String url = "/api/assessment/grading-scales";
        var search = pageableSearchUtilService.configureSearch("abcdefghijklmnop");
        search.setPageSize(100);
        var result = request.getSearchResult(url, HttpStatus.OK, GradingScale.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isEmpty();

        courseGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(courseGradingScale);

        result = request.getSearchResult(url, HttpStatus.OK, GradingScale.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);

        examGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(examGradingScale);

        result = request.getSearchResult(url, HttpStatus.OK, GradingScale.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllGradingScalesInInstructorGroupOnPageWithInstructor() throws Exception {

        String url = "/api/assessment/grading-scales";
        var search = pageableSearchUtilService.configureSearch("");
        search.setPageSize(100);
        search.setSortingOrder(SortingOrder.DESCENDING);
        var result = request.getSearchResult(url, HttpStatus.OK, GradingScale.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isEmpty();

        courseGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(courseGradingScale);

        result = request.getSearchResult(url, HttpStatus.OK, GradingScale.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);

        examGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(examGradingScale);

        result = request.getSearchResult(url, HttpStatus.OK, GradingScale.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(2);
    }

    /**
     * Test if a grade step is contained in a grade step set
     *
     * @param gradeSteps      the grade step set
     * @param gradeStepToTest the grade step
     * @return true if it is contained and false otherwise
     */
    private static boolean isGradeStepInSet(Set<GradeStep> gradeSteps, GradeStep gradeStepToTest) {
        for (GradeStep gradeStep : gradeSteps) {
            if (equalGradeSteps(gradeStep, gradeStepToTest)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests if two grade steps are equal by comparing all attributes but the ids and grading scales
     *
     * @param gradeStep1 the first grade step
     * @param gradeStep2 the second grade step
     * @return true if grade steps are equal and false otherwise
     */
    private static boolean equalGradeSteps(GradeStep gradeStep1, GradeStep gradeStep2) {
        return gradeStep1.getIsPassingGrade() == gradeStep2.getIsPassingGrade() && gradeStep1.isLowerBoundInclusive() == gradeStep2.isLowerBoundInclusive()
                && gradeStep1.isUpperBoundInclusive() == gradeStep2.isUpperBoundInclusive() && gradeStep1.getLowerBoundPercentage() == gradeStep2.getLowerBoundPercentage()
                && gradeStep1.getUpperBoundPercentage() == gradeStep2.getUpperBoundPercentage() && gradeStep1.getGradeName().equals(gradeStep2.getGradeName());
    }

}
