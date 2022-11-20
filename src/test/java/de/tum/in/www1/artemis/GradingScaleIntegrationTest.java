package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradeType;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

class GradingScaleIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private ExamRepository examRepository;

    private GradingScale courseGradingScale;

    private GradingScale examGradingScale;

    private Set<GradeStep> gradeSteps;

    private Course course;

    private Exam exam;

    /**
     * Initialize variables
     */
    @BeforeEach
    void init() {
        database.addUsers(0, 0, 0, 1);
        course = database.addEmptyCourse();
        exam = database.addExamWithExerciseGroup(course, true);
        courseGradingScale = new GradingScale();
        courseGradingScale.setCourse(course);
        examGradingScale = new GradingScale();
        examGradingScale.setExam(exam);
        gradeSteps = new HashSet<>();
        courseGradingScale.setGradeSteps(gradeSteps);
        examGradingScale.setGradeSteps(gradeSteps);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    /**
     * Test get request for non-existing grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetGradingScaleForCourseNotFound() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale", HttpStatus.OK, Void.class);
    }

    /**
     * Test get request for grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetGradingScaleForCourse() throws Exception {
        courseGradingScale.setGradeSteps(Set.of());
        gradingScaleRepository.save(courseGradingScale);

        GradingScale foundGradingScale = request.get("/api/courses/" + course.getId() + "/grading-scale", HttpStatus.OK, GradingScale.class);

        assertThat(foundGradingScale).usingRecursiveComparison().ignoringFields("id", "course", "exam").isEqualTo(courseGradingScale);
    }

    /**
     * Test get request for non-existing grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetGradingScaleForExamNotFound() throws Exception {
        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.OK, Void.class);
    }

    /**
     * Test get request for grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetGradingScaleForExam() throws Exception {
        examGradingScale.setGradeSteps(Set.of());
        gradingScaleRepository.save(examGradingScale);

        GradingScale foundGradingScale = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.OK, GradingScale.class);

        assertThat(foundGradingScale).usingRecursiveComparison().ignoringFields("id", "course", "exam").isEqualTo(examGradingScale);
    }

    /**
     * Test post request for existing grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForCourseGradingScaleAlreadyExists() throws Exception {
        gradingScaleRepository.save(courseGradingScale);

        request.post("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post request for grading scale without set grade steps
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForCourseGradeStepsAreNotSet() throws Exception {
        courseGradingScale.setGradeSteps(Set.of());

        request.post("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post request for existing grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForExamGradingScaleAlreadyExists() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.post("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale, HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post request for grading scale without set grade steps
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForExamGradeStepsAreNotSet() throws Exception {
        examGradingScale.setGradeSteps(Set.of());

        request.post("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale, HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post request with invalid grade steps
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForCourseInvalidGradeSteps() throws Exception {
        gradeSteps = database.generateGradeStepSet(courseGradingScale, false);
        courseGradingScale.setGradeSteps(gradeSteps);

        request.post("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post request for grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForCourse() throws Exception {
        gradeSteps = database.generateGradeStepSet(courseGradingScale, true);
        courseGradingScale.setGradeSteps(gradeSteps);

        GradingScale savedGradingScale = request.postWithResponseBody("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, GradingScale.class,
                HttpStatus.CREATED);

        assertThat(savedGradingScale.getGradeSteps()).hasSameSizeAs(courseGradingScale.getGradeSteps());
        assertThat(savedGradingScale.getGradeSteps()).allMatch(gradeStep -> isGradeStepInSet(courseGradingScale.getGradeSteps(), gradeStep));
        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course", "gradeSteps").ignoringCollectionOrder().isEqualTo(courseGradingScale);
    }

    /**
     * Test post request with invalid grade steps
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForExamInvalidGradeSteps() throws Exception {
        exam.setMaxPoints(null);
        examRepository.save(exam);
        gradeSteps = database.generateGradeStepSet(examGradingScale, false);
        examGradingScale.setGradeSteps(gradeSteps);

        request.post("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale, HttpStatus.BAD_REQUEST);
    }

    /**
     * Test post request for grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleForExam() throws Exception {
        gradeSteps = database.generateGradeStepSet(examGradingScale, true);
        examGradingScale.setGradeSteps(gradeSteps);
        exam.setMaxPoints(null);
        examRepository.save(exam);

        GradingScale savedGradingScale = request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale,
                GradingScale.class, HttpStatus.CREATED);

        assertThat(savedGradingScale.getGradeSteps()).hasSameSizeAs(examGradingScale.getGradeSteps());
        assertThat(savedGradingScale.getGradeSteps()).allMatch(gradeStep -> isGradeStepInSet(examGradingScale.getGradeSteps(), gradeStep));
        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course", "gradeSteps").ignoringCollectionOrder().isEqualTo(examGradingScale);
    }

    /**
     * Test put request for non-existing grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForCourseGradingScaleNotFound() throws Exception {
        request.put("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, HttpStatus.NOT_FOUND);
    }

    /**
     * Test put request with invalid grade steps
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForCourseInvalidGradeSteps() throws Exception {
        gradingScaleRepository.save(courseGradingScale);
        gradeSteps = database.generateGradeStepSet(courseGradingScale, false);
        courseGradingScale.setGradeSteps(gradeSteps);

        request.put("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, HttpStatus.BAD_REQUEST);
    }

    /**
     * Test put request for grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForCourse() throws Exception {
        gradingScaleRepository.save(courseGradingScale);
        gradeSteps = database.generateGradeStepSet(courseGradingScale, true);
        courseGradingScale.setGradeSteps(gradeSteps);

        GradingScale savedGradingScale = request.putWithResponseBody("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, GradingScale.class, HttpStatus.OK);

        assertThat(savedGradingScale.getGradeSteps()).hasSameSizeAs(courseGradingScale.getGradeSteps());
        assertThat(savedGradingScale.getGradeSteps()).allMatch(gradeStep -> isGradeStepInSet(courseGradingScale.getGradeSteps(), gradeStep));
        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course", "gradeSteps").ignoringCollectionOrder().isEqualTo(courseGradingScale);
    }

    /**
     * Test put request for non-existing grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForExamGradingScaleNotFound() throws Exception {
        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale, HttpStatus.NOT_FOUND);
    }

    /**
     * Test put request with invalid grade steps
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForExamInvalidGradeSteps() throws Exception {
        exam.setMaxPoints(null);
        examRepository.save(exam);
        gradingScaleRepository.save(examGradingScale);
        gradeSteps = database.generateGradeStepSet(examGradingScale, false);
        examGradingScale.setGradeSteps(gradeSteps);

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale, HttpStatus.BAD_REQUEST);
    }

    /**
     * Test put request for grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateGradingScaleForExam() throws Exception {
        gradingScaleRepository.save(examGradingScale);
        gradeSteps = database.generateGradeStepSet(examGradingScale, true);
        examGradingScale.setGradeSteps(gradeSteps);
        exam.setMaxPoints(null);
        examRepository.save(exam);

        GradingScale savedGradingScale = request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale,
                GradingScale.class, HttpStatus.OK);

        assertThat(savedGradingScale.getGradeSteps()).hasSameSizeAs(examGradingScale.getGradeSteps());
        assertThat(savedGradingScale.getGradeSteps()).allMatch(gradeStep -> isGradeStepInSet(examGradingScale.getGradeSteps(), gradeStep));
        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course", "gradeSteps").ignoringCollectionOrder().isEqualTo(examGradingScale);
    }

    /**
     * Test delete request for non-existing grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteGradingScaleForCourseNotFound() throws Exception {
        request.delete("/api/courses/" + course.getId() + "/grading-scale", HttpStatus.NOT_FOUND);
    }

    /**
     * Test delete request for grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteGradingScaleForCourse() throws Exception {
        gradingScaleRepository.save(courseGradingScale);

        request.delete("/api/courses/" + course.getId() + "/grading-scale", HttpStatus.OK);
    }

    /**
     * Test delete request for non-existing grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteGradingScaleForExamNotFound() throws Exception {
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.NOT_FOUND);
    }

    /**
     * Test delete request for grading scale
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteGradingScaleForExam() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.OK);
    }

    /**
     * Test delete request for course should delete the grading scale of that course as well
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourseDeletesGradingScale() throws Exception {
        gradingScaleRepository.save(courseGradingScale);

        request.delete("/api/courses/" + course.getId(), HttpStatus.OK);

        Optional<GradingScale> foundGradingScale = gradingScaleRepository.findByCourseId(course.getId());
        assertThat(foundGradingScale).isEmpty();
    }

    /**
     * Test delete request for exam should delete the grading scale of that exam as well
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamDeletesGradingScale() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);

        Optional<GradingScale> foundGradingScale = gradingScaleRepository.findByExamId(exam.getId());
        assertThat(foundGradingScale).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllGradingScalesInInstructorGroupOnPageWithAdmin() throws Exception {

        String url = "/api/grading-scales?pageSize=100&page=1&sortingOrder=DESCENDING&searchTerm=&sortedColumn=ID";
        var result = request.get(url, HttpStatus.OK, SearchResultPageDTO.class);
        assertThat(result.getResultsOnPage()).isEmpty();

        courseGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(courseGradingScale);

        result = request.get(url, HttpStatus.OK, SearchResultPageDTO.class);
        assertThat(result.getResultsOnPage()).hasSize(1);

        examGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(examGradingScale);

        result = request.get(url, HttpStatus.OK, SearchResultPageDTO.class);
        assertThat(result.getResultsOnPage()).hasSize(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAllGradingScalesInInstructorGroupOnPageWithInstructor() throws Exception {

        String url = "/api/grading-scales?pageSize=100&page=1&sortingOrder=DESCENDING&searchTerm=&sortedColumn=ID";
        var result = request.get(url, HttpStatus.OK, SearchResultPageDTO.class);
        assertThat(result.getResultsOnPage()).isEmpty();

        courseGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(courseGradingScale);

        result = request.get(url, HttpStatus.OK, SearchResultPageDTO.class);
        assertThat(result.getResultsOnPage()).hasSize(1);

        examGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(examGradingScale);

        result = request.get(url, HttpStatus.OK, SearchResultPageDTO.class);
        assertThat(result.getResultsOnPage()).hasSize(2);
    }

    /**
     * Test if a grade step is contained in a grade step set
     *
     * @param gradeSteps the grade step set
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
