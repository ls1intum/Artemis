package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;

public class GradingSystemIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

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

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradingScaleForCourseNotFound() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale", HttpStatus.NOT_FOUND, GradingScale.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradingScaleForCourse() throws Exception {
        courseGradingScale.setGradeSteps(null);
        gradingScaleRepository.save(courseGradingScale);

        GradingScale foundGradingScale = request.get("/api/courses/" + course.getId() + "/grading-scale", HttpStatus.OK, GradingScale.class);

        assertThat(foundGradingScale).usingRecursiveComparison().ignoringFields("id", "course", "exam").isEqualTo(courseGradingScale);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradingScaleForExamNotFound() throws Exception {
        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.NOT_FOUND, GradingScale.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetGradingScaleForExam() throws Exception {
        examGradingScale.setGradeSteps(null);
        gradingScaleRepository.save(examGradingScale);

        GradingScale foundGradingScale = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.OK, GradingScale.class);

        assertThat(foundGradingScale).usingRecursiveComparison().ignoringFields("id", "course", "exam").isEqualTo(examGradingScale);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScaleForCourseGradingScaleAlreadyExists() throws Exception {
        gradingScaleRepository.save(courseGradingScale);

        request.post("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScaleForCourseGradeStepsAreNotSet() throws Exception {
        courseGradingScale.setGradeSteps(null);

        request.post("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScaleForExamGradingScaleAlreadyExists() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.post("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScaleForExamGradeStepsAreNotSet() throws Exception {
        examGradingScale.setGradeSteps(null);

        request.post("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScaleForCourseInvalidGradeSteps() throws Exception {
        gradeSteps = database.generateGradeStepSet(courseGradingScale, false);
        courseGradingScale.setGradeSteps(gradeSteps);

        request.post("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScaleForCourse() throws Exception {
        gradeSteps = database.generateGradeStepSet(courseGradingScale, true);
        courseGradingScale.setGradeSteps(gradeSteps);

        GradingScale savedGradingScale = request.postWithResponseBody("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, GradingScale.class,
                HttpStatus.CREATED);

        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course").isEqualTo(courseGradingScale);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScaleForExamInvalidGradeSteps() throws Exception {
        gradeSteps = database.generateGradeStepSet(examGradingScale, false);
        examGradingScale.setGradeSteps(gradeSteps);

        request.post("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScaleForExam() throws Exception {
        gradeSteps = database.generateGradeStepSet(examGradingScale, true);
        examGradingScale.setGradeSteps(gradeSteps);

        GradingScale savedGradingScale = request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale,
                GradingScale.class, HttpStatus.CREATED);

        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course").isEqualTo(examGradingScale);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateGradingScaleForCourseGradingScaleNotFound() throws Exception {
        request.put("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateGradingScaleForCourseGradingScaleInvalidGradeSteps() throws Exception {
        gradingScaleRepository.save(courseGradingScale);
        gradeSteps = database.generateGradeStepSet(courseGradingScale, false);
        courseGradingScale.setGradeSteps(gradeSteps);

        request.put("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateGradingScaleForCourseGradingScale() throws Exception {
        gradingScaleRepository.save(courseGradingScale);
        gradeSteps = database.generateGradeStepSet(courseGradingScale, true);
        courseGradingScale.setGradeSteps(gradeSteps);

        GradingScale savedGradingScale = request.putWithResponseBody("/api/courses/" + course.getId() + "/grading-scale", courseGradingScale, GradingScale.class, HttpStatus.OK);

        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course").isEqualTo(courseGradingScale);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateGradingScaleForExamGradingScaleNotFound() throws Exception {
        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateGradingScaleForExamGradingScaleInvalidGradeSteps() throws Exception {
        gradingScaleRepository.save(examGradingScale);
        gradeSteps = database.generateGradeStepSet(examGradingScale, false);
        examGradingScale.setGradeSteps(gradeSteps);

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateGradingScaleForExamGradingScale() throws Exception {
        gradingScaleRepository.save(examGradingScale);
        gradeSteps = database.generateGradeStepSet(examGradingScale, true);
        examGradingScale.setGradeSteps(gradeSteps);

        GradingScale savedGradingScale = request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", examGradingScale,
                GradingScale.class, HttpStatus.OK);

        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("id", "exam", "course").isEqualTo(examGradingScale);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteGradingScaleForCourseNotFound() throws Exception {
        request.delete("/api/courses/" + course.getId() + "/grading-scale", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteGradingScaleForCourse() throws Exception {
        gradingScaleRepository.save(courseGradingScale);

        request.delete("/api/courses/" + course.getId() + "/grading-scale", HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteGradingScaleForExamNotFound() throws Exception {
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteGradingScaleForExam() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale", HttpStatus.OK);
    }

}
