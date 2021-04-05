package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.GradeStepRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;

public class GradeStepIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private GradeStepRepository gradeStepRepository;

    private Course course;

    private Exam exam;

    private GradingScale courseGradingScale;

    private GradingScale examGradingScale;

    private Set<GradeStep> gradeSteps;

    @BeforeEach
    public void init() {
        course = database.addEmptyCourse();
        exam = database.addExamWithExerciseGroup(course, true);
        courseGradingScale = new GradingScale();
        examGradingScale = new GradingScale();
        gradeSteps = new HashSet<>();
        course.setGradingScale(courseGradingScale);
        courseGradingScale.setCourse(course);
        exam.setGradingScale(examGradingScale);
        examGradingScale.setExam(exam);
        courseGradingScale.setGradeSteps(gradeSteps);
        examGradingScale.setGradeSteps(gradeSteps);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllGradeStepsForCourse_NoGradingScaleExists() throws Exception {
        request.getList("/api/courses/" + course.getId() + "/grading-scale/grade-steps", HttpStatus.NOT_FOUND, GradeStep.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllGradeStepsForCourse() throws Exception {
        GradeStep gradeStep1 = new GradeStep();
        GradeStep gradeStep2 = new GradeStep();
        gradeStep1.setGradeName("Name1");
        gradeStep2.setGradeName("Name2");
        gradeStep1.setLowerBoundPercentage(0);
        gradeStep1.setUpperBoundPercentage(60);
        gradeStep2.setLowerBoundPercentage(60);
        gradeStep2.setUpperBoundPercentage(100);
        gradeStep1.setGradingScale(courseGradingScale);
        gradeStep2.setGradingScale(courseGradingScale);
        gradeSteps = Set.of(gradeStep1, gradeStep2);
        courseGradingScale.setGradeSteps(gradeSteps);
        gradingScaleRepository.save(courseGradingScale);

        List<GradeStep> foundGradeSteps = request.getList("/api/courses/" + course.getId() + "/grading-scale/grade-steps", HttpStatus.OK, GradeStep.class);

        assertThat(foundGradeSteps).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(List.of(gradeStep1, gradeStep2));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllGradeStepsForExam_NoGradingScaleExists() throws Exception {
        request.getList("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/grade-steps", HttpStatus.NOT_FOUND, GradeStep.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllGradeStepsForExam() throws Exception {
        GradeStep gradeStep1 = new GradeStep();
        GradeStep gradeStep2 = new GradeStep();
        gradeStep1.setGradeName("Name1");
        gradeStep2.setGradeName("Name2");
        gradeStep1.setLowerBoundPercentage(0);
        gradeStep1.setUpperBoundPercentage(60);
        gradeStep2.setLowerBoundPercentage(60);
        gradeStep2.setUpperBoundPercentage(100);
        gradeStep1.setGradingScale(examGradingScale);
        gradeStep2.setGradingScale(examGradingScale);
        gradeSteps = Set.of(gradeStep1, gradeStep2);
        examGradingScale.setGradeSteps(gradeSteps);
        gradingScaleRepository.save(examGradingScale);

        List<GradeStep> foundGradeSteps = request.getList("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/grade-steps", HttpStatus.OK,
                GradeStep.class);

        assertThat(foundGradeSteps).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(List.of(gradeStep1, gradeStep2));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradeStepByIdForCourse_NoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale/grade-step/1", HttpStatus.NOT_FOUND, GradeStep.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradeStepByIdForCourse() throws Exception {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradeName("Name");
        gradeStep.setLowerBoundPercentage(0);
        gradeStep.setUpperBoundPercentage(60);
        gradeStep.setGradingScale(courseGradingScale);
        gradeSteps = Set.of(gradeStep);
        courseGradingScale.setGradeSteps(gradeSteps);
        gradingScaleRepository.save(courseGradingScale);
        Long gradeStepId = gradeStepRepository.findAll().get(0).getId();

        GradeStep foundGradeStep = request.get("/api/courses/" + course.getId() + "/grading-scale/grade-step/" + gradeStepId, HttpStatus.OK, GradeStep.class);

        assertThat(foundGradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(gradeStep);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradeStepByIdForExam_NoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale/grade-step/1", HttpStatus.NOT_FOUND, GradeStep.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradeStepByIdForExam() throws Exception {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradeName("Name");
        gradeStep.setLowerBoundPercentage(0);
        gradeStep.setUpperBoundPercentage(60);
        gradeStep.setGradingScale(examGradingScale);
        gradeSteps = Set.of(gradeStep);
        examGradingScale.setGradeSteps(gradeSteps);
        gradingScaleRepository.save(examGradingScale);
        Long gradeStepId = gradeStepRepository.findAll().get(0).getId();

        GradeStep foundGradeStep = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/grade-step/" + gradeStepId, HttpStatus.OK,
                GradeStep.class);

        assertThat(foundGradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(gradeStep);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradeStepByPercentageForCourse_NoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.NOT_FOUND, GradeStep.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradeStepByPercentageForCourse() throws Exception {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradeName("Name");
        gradeStep.setLowerBoundPercentage(60);
        gradeStep.setUpperBoundPercentage(100);
        gradeStep.setGradingScale(courseGradingScale);
        gradeSteps = Set.of(gradeStep);
        courseGradingScale.setGradeSteps(gradeSteps);
        gradingScaleRepository.save(courseGradingScale);

        GradeStep foundGradeStep = request.get("/api/courses/" + course.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.OK, GradeStep.class);

        assertThat(foundGradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(gradeStep);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradeStepByPercentageForExam_NoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.NOT_FOUND, GradeStep.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradeStepByPercentageForExam() throws Exception {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradeName("Name");
        gradeStep.setLowerBoundPercentage(60);
        gradeStep.setUpperBoundPercentage(100);
        gradeStep.setGradingScale(examGradingScale);
        gradeSteps = Set.of(gradeStep);
        examGradingScale.setGradeSteps(gradeSteps);
        gradingScaleRepository.save(examGradingScale);

        GradeStep foundGradeStep = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.OK,
                GradeStep.class);

        assertThat(foundGradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(gradeStep);
    }

}
