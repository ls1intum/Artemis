package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.dto.GradeDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradeStepsDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class GradeStepIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "gradestep";

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

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
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        // Student not belonging to any course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student2");

        course = courseUtilService.addEmptyCourse();
        exam = examUtilService.addExamWithExerciseGroup(course, true);
        courseGradingScale = new GradingScale();
        examGradingScale = new GradingScale();
        gradeSteps = new HashSet<>();
        courseGradingScale.setCourse(course);
        examGradingScale.setExam(exam);
        courseGradingScale.setGradeSteps(gradeSteps);
        examGradingScale.setGradeSteps(gradeSteps);
    }

    /**
     * Test get request for all grade steps when no grading scale exists
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllGradeStepsForCourseNoGradingScaleExists() throws Exception {
        var result = request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps", HttpStatus.OK, GradeStepsDTO.class);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetAllGradeStepsForCourseStudentNotInCourse() throws Exception {
        createGradeScale();
        request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps", HttpStatus.FORBIDDEN, GradeStepsDTO.class);
    }

    /**
     * Test get request for all grade steps
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllGradeStepsForCourse() throws Exception {
        createGradeScale();

        GradeStepsDTO gradeStepsDTO = request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps", HttpStatus.OK, GradeStepsDTO.class);

        assertThat(gradeStepsDTO.gradeType()).isEqualTo(GradeType.GRADE);
        assertThat(gradeStepsDTO.title()).isEqualTo(course.getTitle());

        assertThat(gradeStepsDTO.gradeSteps()).usingRecursiveComparison().ignoringFields("gradingScale", "id").ignoringCollectionOrder().isEqualTo(gradeSteps);
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
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllGradeStepsForExamNoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/grade-steps", HttpStatus.NOT_FOUND, GradeStepsDTO.class);
    }

    /**
     * Test get request for all grade steps as a student
     * when the exam results have not been published yet
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllGradeStepsForExamForbidden() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/grade-steps", HttpStatus.FORBIDDEN, GradeStepsDTO.class);
    }

    /**
     * Test get request for all grade steps
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllGradeStepsForExam() throws Exception {
        exam.setPublishResultsDate(ZonedDateTime.now().minusMinutes(1));
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

        assertThat(gradeStepsDTO.gradeType()).isEqualTo(GradeType.BONUS);
        assertThat(gradeStepsDTO.title()).isEqualTo(exam.getTitle());

        assertThat(gradeStepsDTO.gradeSteps()).usingRecursiveComparison().ignoringFields("gradingScale", "id").ignoringCollectionOrder().isEqualTo(gradeSteps);
    }

    /**
     * Test get request for a single grade step when no grading scale exists
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetGradeStepByIdForCourseNoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps/1", HttpStatus.NOT_FOUND, GradeStep.class);
    }

    /**
     * Test get request for a single grade step
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetGradeStepByIdForCourse() throws Exception {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradeName("Name");
        gradeStep.setLowerBoundPercentage(0);
        gradeStep.setUpperBoundPercentage(60);
        gradeStep.setGradingScale(courseGradingScale);
        gradeSteps = Set.of(gradeStep);
        courseGradingScale.setGradeSteps(gradeSteps);
        courseGradingScale = gradingScaleRepository.save(courseGradingScale);
        Long gradeStepId = courseGradingScale.getGradeSteps().stream().iterator().next().getId();

        GradeStep foundGradeStep = request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps/" + gradeStepId, HttpStatus.OK, GradeStep.class);

        assertThat(foundGradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(gradeStep);
    }

    /**
     * Test get request for a single grade step when no grading scale exists
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetGradeStepByIdForExamNoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale/grade-steps/1", HttpStatus.NOT_FOUND, GradeStep.class);
    }

    /**
     * Test get request for a single grade step
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetGradeStepByIdForExam() throws Exception {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradeName("Name");
        gradeStep.setLowerBoundPercentage(0);
        gradeStep.setUpperBoundPercentage(60);
        gradeStep.setGradingScale(examGradingScale);
        gradeSteps = Set.of(gradeStep);
        examGradingScale.setGradeSteps(gradeSteps);
        examGradingScale = gradingScaleRepository.save(examGradingScale);
        Long gradeStepId = examGradingScale.getGradeSteps().stream().iterator().next().getId();

        GradeStep foundGradeStep = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/grade-steps/" + gradeStepId, HttpStatus.OK,
                GradeStep.class);

        assertThat(foundGradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(gradeStep);
    }

    /**
     * Test get request for a single grade step by grade percentage when no grading scale exists
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetGradeStepByPercentageForCourseNoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.OK, Void.class);
    }

    /**
     * Test get request for a single grade by grade percentage
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetGradeStepByPercentageForCourse() throws Exception {
        String gradeName = "Name";
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradeName(gradeName);
        gradeStep.setLowerBoundPercentage(60);
        gradeStep.setUpperBoundPercentage(100);
        gradeStep.setIsPassingGrade(true);
        gradeStep.setGradingScale(courseGradingScale);
        gradeSteps = Set.of(gradeStep);
        courseGradingScale.setGradeSteps(gradeSteps);
        courseGradingScale.setGradeType(GradeType.GRADE);
        gradingScaleRepository.save(courseGradingScale);

        // Add student participation to course to avoid receiving no-participation special grade.
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        Long individualTextExerciseId = textExercise.getId();
        textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        participationUtilService.createParticipationSubmissionAndResult(individualTextExerciseId, student, 10.0, 10.0, 70, true);

        GradeDTO foundGrade = request.get("/api/courses/" + course.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.OK, GradeDTO.class);

        assertThat(foundGrade.gradeName()).isEqualTo(gradeName);
        assertThat(foundGrade.gradeType()).isEqualTo(GradeType.GRADE);
        assertThat(foundGrade.isPassingGrade()).isTrue();
    }

    /**
     * Test get request for a single grade for no participation special grade
     *
     * @throws Exception some error during the test
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetGradeStepForNoParticipationSpecialGradeForCourse() throws Exception {
        String noParticipationGrade = "noPart.";
        courseGradingScale.setNoParticipationGrade(noParticipationGrade);
        courseGradingScale.setGradeType(GradeType.GRADE);
        gradingScaleRepository.save(courseGradingScale);

        GradeDTO foundGrade = request.get("/api/courses/" + course.getId() + "/grading-scale/match-grade-step?gradePercentage=0", HttpStatus.OK, GradeDTO.class);

        assertThat(foundGrade.gradeName()).isEqualTo(noParticipationGrade);
        assertThat(foundGrade.gradeType()).isEqualTo(GradeType.GRADE);
        assertThat(foundGrade.isPassingGrade()).isFalse();
    }

    /**
     * Test get request for a single grade for plagiarism special grade
     *
     * @throws Exception some error during the test
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetGradeStepForPlagiarismSpecialGradeForCourse() throws Exception {
        String plagiarismGrade = "Plag.";
        courseGradingScale.setPlagiarismGrade(plagiarismGrade);
        courseGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(courseGradingScale);

        // Add student participation to course to avoid receiving no-participation special grade.
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        Long individualTextExerciseId = textExercise.getId();
        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        participationUtilService.createParticipationSubmissionAndResult(individualTextExerciseId, student, 10.0, 10.0, 50, true);

        var coursePlagiarismCase = new PlagiarismCase();
        coursePlagiarismCase.setStudent(student);
        coursePlagiarismCase.setExercise(textExercise);
        coursePlagiarismCase.setVerdict(PlagiarismVerdict.PLAGIARISM);
        plagiarismCaseRepository.save(coursePlagiarismCase);

        GradeDTO foundGrade = request.get("/api/courses/" + course.getId() + "/grading-scale/match-grade-step?gradePercentage=50", HttpStatus.OK, GradeDTO.class);

        assertThat(foundGrade.gradeName()).isEqualTo(plagiarismGrade);
        assertThat(foundGrade.gradeType()).isEqualTo(GradeType.BONUS);
        assertThat(foundGrade.isPassingGrade()).isFalse();
    }

    /**
     * Test get request for a single grade step by grade percentage when no grading scale exists
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetGradeStepByPercentageForExamNoGradingScaleExists() throws Exception {
        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.OK, Void.class);
    }

    /**
     * Test get request for a single grade step by grade pecentage as a student
     * when the exam results have not been published yet
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetGradeStepByPercentageForExamForbidden() throws Exception {
        gradingScaleRepository.save(examGradingScale);

        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/match-grade-step?gradePercentage=70", HttpStatus.FORBIDDEN, GradeDTO.class);
    }

    /**
     * Test get request for a single grade by grade percentage
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
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
        exam.setPublishResultsDate(ZonedDateTime.now().minusMinutes(1));
        examRepository.save(exam);

        GradeDTO foundGrade = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/grading-scale/match-grade-step?gradePercentage=35", HttpStatus.OK,
                GradeDTO.class);

        assertThat(foundGrade.gradeName()).isEqualTo("Test grade");
        assertThat(foundGrade.gradeType()).isEqualTo(GradeType.BONUS);
        assertThat(foundGrade.isPassingGrade()).isFalse();
    }

}
