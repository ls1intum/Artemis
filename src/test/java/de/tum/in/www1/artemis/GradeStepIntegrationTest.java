package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.GradeStepRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.GradeDTO;
import de.tum.in.www1.artemis.web.rest.dto.GradeStepsDTO;

class GradeStepIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private GradeStepRepository gradeStepRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private UserRepository userRepository;

    private Course course;

    private Exam exam;

    private GradingScale courseGradingScale;

    private GradingScale examGradingScale;

    private Set<GradeStep> gradeSteps;

    /**
     * Initialize attributes
     */
    @BeforeEach
    void init() {
        database.addUsers(1, 0, 0, 1);

        // Student not belonging to any course
        User student = ModelFactory.generateActivatedUser("student2");
        userRepository.save(student);

        course = database.addEmptyCourse();
        exam = database.addExamWithExerciseGroup(course, true);
        courseGradingScale = new GradingScale();
        examGradingScale = new GradingScale();
        gradeSteps = new HashSet<>();
        courseGradingScale.setCourse(course);
        examGradingScale.setExam(exam);
        courseGradingScale.setGradeSteps(gradeSteps);
        examGradingScale.setGradeSteps(gradeSteps);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    /**
     * Test get request for all grade steps when no grading scale exists
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetAllGradeStepsForCourseNoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps", HttpStatus.NOT_FOUND, GradeStepsDTO.class);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    void testGetAllGradeStepsForCourseStudentNotInCourse() throws Exception {
        createGradeScale();
        request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps", HttpStatus.FORBIDDEN, GradeStepsDTO.class);
    }

    /**
     * Test get request for all grade steps
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetAllGradeStepsForCourse() throws Exception {
        createGradeScale();

        GradeStepsDTO gradeStepsDTO = request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps", HttpStatus.OK, GradeStepsDTO.class);

        assertThat(gradeStepsDTO.gradeType).isEqualTo(GradeType.GRADE);
        assertThat(gradeStepsDTO.title).isEqualTo(course.getTitle());

        assertThat(gradeStepsDTO.gradeSteps).usingRecursiveComparison().ignoringFields("gradingScale", "id").ignoringCollectionOrder().isEqualTo(gradeSteps);
    }

    private void createGradeScale() {
        GradeStep gradeStep1 = new GradeStep();
        GradeStep gradeStep2 = new GradeStep();
        gradeStep1.setGradeName("Name1");
        gradeStep2.setGradeName("Name2");
        gradeStep1.setLowerBoundPercentage(0);
        gradeStep1.setUpperBoundPercentage(60);
        gradeStep2.setLowerBoundPercentage(60);
        gradeStep2.setUpperBoundPercentage(100);
        gradeStep2.setUpperBoundInclusive(true);
        gradeStep1.setGradingScale(courseGradingScale);
        gradeStep2.setGradingScale(courseGradingScale);
        courseGradingScale.setGradeType(GradeType.GRADE);
        gradeSteps = Set.of(gradeStep1, gradeStep2);
        courseGradingScale.setGradeSteps(gradeSteps);
        gradingScaleRepository.save(courseGradingScale);
    }

    /**
     * Test get request for all grade steps when no grading scale exists
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetAllGradeStepsForExamNoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/grade-steps", HttpStatus.NOT_FOUND, GradeStepsDTO.class);
    }

    /**
     * Test get request for all grade steps as a student
     * when the exam results have not been published yet
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetAllGradeStepsForExamForbidden() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/grade-steps", HttpStatus.FORBIDDEN, GradeStepsDTO.class);
    }

    /**
     * Test get request for all grade steps
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetAllGradeStepsForExam() throws Exception {
        exam.setPublishResultsDate(ZonedDateTime.now());
        examRepository.save(exam);
        GradeStep gradeStep1 = new GradeStep();
        GradeStep gradeStep2 = new GradeStep();
        gradeStep1.setGradeName("Name1");
        gradeStep2.setGradeName("Name2");
        gradeStep1.setLowerBoundPercentage(0);
        gradeStep1.setUpperBoundPercentage(60);
        gradeStep2.setUpperBoundInclusive(true);
        gradeStep2.setLowerBoundPercentage(60);
        gradeStep2.setUpperBoundPercentage(100);
        gradeStep1.setGradingScale(examGradingScale);
        gradeStep2.setGradingScale(examGradingScale);
        gradeSteps = Set.of(gradeStep1, gradeStep2);
        examGradingScale.setGradeSteps(gradeSteps);
        examGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(examGradingScale);

        GradeStepsDTO gradeStepsDTO = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/grade-steps", HttpStatus.OK, GradeStepsDTO.class);

        assertThat(gradeStepsDTO.gradeType).isEqualTo(GradeType.BONUS);
        assertThat(gradeStepsDTO.title).isEqualTo(exam.getTitle());

        assertThat(gradeStepsDTO.gradeSteps).usingRecursiveComparison().ignoringFields("gradingScale", "id").ignoringCollectionOrder().isEqualTo(gradeSteps);
    }

    /**
     * Test get request for a single grade step when no grading scale exists
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetGradeStepByIdForCourseNoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps/1", HttpStatus.NOT_FOUND, GradeStep.class);
    }

    /**
     * Test get request for a single grade step
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetGradeStepByIdForCourse() throws Exception {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradeName("Name");
        gradeStep.setLowerBoundPercentage(0);
        gradeStep.setUpperBoundPercentage(60);
        gradeStep.setGradingScale(courseGradingScale);
        gradeSteps = Set.of(gradeStep);
        courseGradingScale.setGradeSteps(gradeSteps);
        gradingScaleRepository.save(courseGradingScale);
        Long gradeStepId = gradeStepRepository.findAll().get(0).getId();

        GradeStep foundGradeStep = request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps/" + gradeStepId, HttpStatus.OK, GradeStep.class);

        assertThat(foundGradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(gradeStep);
    }

    /**
     * Test get request for a single grade step when no grading scale exists
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetGradeStepByIdForExamNoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps/1", HttpStatus.NOT_FOUND, GradeStep.class);
    }

    /**
     * Test get request for a single grade step
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetGradeStepByIdForExam() throws Exception {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradeName("Name");
        gradeStep.setLowerBoundPercentage(0);
        gradeStep.setUpperBoundPercentage(60);
        gradeStep.setGradingScale(examGradingScale);
        gradeSteps = Set.of(gradeStep);
        examGradingScale.setGradeSteps(gradeSteps);
        gradingScaleRepository.save(examGradingScale);
        Long gradeStepId = gradeStepRepository.findAll().get(0).getId();

        GradeStep foundGradeStep = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/grade-steps/" + gradeStepId, HttpStatus.OK,
                GradeStep.class);

        assertThat(foundGradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(gradeStep);
    }

    /**
     * Test get request for a single grade step by grade percentage when no grading scale exists
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetGradeStepByPercentageForCourseNoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.OK, Void.class);
    }

    /**
     * Test get request for a single grade by grade percentage
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetGradeStepByPercentageForCourse() throws Exception {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradeName("Name");
        gradeStep.setLowerBoundPercentage(60);
        gradeStep.setUpperBoundPercentage(100);
        gradeStep.setIsPassingGrade(true);
        gradeStep.setGradingScale(courseGradingScale);
        gradeSteps = Set.of(gradeStep);
        courseGradingScale.setGradeSteps(gradeSteps);
        courseGradingScale.setGradeType(GradeType.GRADE);
        gradingScaleRepository.save(courseGradingScale);

        GradeDTO foundGrade = request.get("/api/courses/" + course.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.OK, GradeDTO.class);

        assertThat(foundGrade.gradeName).isEqualTo("Name");
        assertThat(foundGrade.gradeType).isEqualTo(GradeType.GRADE);
        assertThat(foundGrade.isPassingGrade).isTrue();
    }

    /**
     * Test get request for a single grade step by grade percentage when no grading scale exists
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetGradeStepByPercentageForExamNoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.OK, Void.class);
    }

    /**
     * Test get request for a single grade step by grade pecentage as a student
     * when the exam results have not been published yet
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetGradeStepByPercentageForExamForbidden() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.FORBIDDEN, GradeDTO.class);
    }

    /**
     * Test get request for a single grade by grade percentage
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetGradeStepByPercentageForExam() throws Exception {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradeName("Test grade");
        gradeStep.setLowerBoundPercentage(0);
        gradeStep.setUpperBoundPercentage(40);
        gradeStep.setIsPassingGrade(false);
        gradeStep.setGradingScale(examGradingScale);
        gradeSteps = Set.of(gradeStep);
        examGradingScale.setGradeSteps(gradeSteps);
        examGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(examGradingScale);
        exam.setPublishResultsDate(ZonedDateTime.now());
        examRepository.save(exam);

        GradeDTO foundGrade = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/match-grade-step?gradePercentage=35", HttpStatus.OK,
                GradeDTO.class);

        assertThat(foundGrade.gradeName).isEqualTo("Test grade");
        assertThat(foundGrade.gradeType).isEqualTo(GradeType.BONUS);
        assertThat(foundGrade.isPassingGrade).isFalse();
    }

}
