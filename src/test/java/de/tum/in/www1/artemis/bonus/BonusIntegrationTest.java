package de.tum.in.www1.artemis.bonus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.assessment.GradingScaleFactory;
import de.tum.in.www1.artemis.assessment.GradingScaleUtilService;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.BonusExampleDTO;

class BonusIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "bonusintegration";

    @Autowired
    private BonusRepository bonusRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private GradingScaleUtilService gradingScaleUtilService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private Bonus courseBonus;

    private Bonus examBonus;

    private Course course;

    private GradingScale bonusToExamGradingScale;

    private GradingScale sourceExamGradingScale;

    private GradingScale courseGradingScale;

    private long courseId;

    private long examId;

    /**
     * Initialize variables
     */
    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.addEmptyCourse();
        course.setMaxPoints(100);
        courseRepository.saveAndFlush(course);

        // Sets the achievable points for the course to 200, even though the course's max points are still set to 100.
        Exercise exercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now().minusHours(3), ZonedDateTime.now().minusHours(2), ZonedDateTime.now().minusHours(1),
                course);
        exercise.setMaxPoints(200.0);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exerciseRepository.save(exercise);
        assertThat(course.getMaxPoints()).isEqualTo(100);

        Exam targetExam = examUtilService.addExamWithExerciseGroup(course, true);
        targetExam.setExamMaxPoints(200);
        examRepository.save(targetExam);

        Exam sourceExam = examUtilService.addExamWithExerciseGroup(course, true);
        bonusToExamGradingScale = GradingScaleFactory.generateGradingScaleForExam(targetExam, GradeType.GRADE);

        sourceExamGradingScale = GradingScaleFactory.generateGradingScaleForExam(sourceExam, GradeType.BONUS);

        courseGradingScale = GradingScaleFactory.generateGradingScaleForCourse(course, GradeType.BONUS);

        gradingScaleRepository.saveAll(List.of(bonusToExamGradingScale, sourceExamGradingScale, courseGradingScale));

        courseBonus = BonusFactory.generateBonus(BonusStrategy.GRADES_CONTINUOUS, 1.0, courseGradingScale.getId(), bonusToExamGradingScale.getId());
        bonusRepository.save(courseBonus);

        examBonus = BonusFactory.generateBonus(BonusStrategy.GRADES_CONTINUOUS, 1.0, sourceExamGradingScale.getId(), bonusToExamGradingScale.getId());

        bonusToExamGradingScale.setBonusStrategy(BonusStrategy.GRADES_CONTINUOUS);
        gradingScaleRepository.save(bonusToExamGradingScale);

        courseId = bonusToExamGradingScale.getExam().getCourse().getId();
        examId = bonusToExamGradingScale.getExam().getId();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBonusSourcesForTargetExamNotFound() throws Exception {
        bonusRepository.delete(courseBonus);

        request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus", HttpStatus.NOT_FOUND, Bonus.class);

    }

    private void assertBonusesAreEqualIgnoringId(Bonus actualBonus, Bonus expectedBonus) {
        assertThat(actualBonus).usingRecursiveComparison().ignoringFields("id", "sourceGradingScale", "bonusToGradingScale").isEqualTo(expectedBonus);
        assertThat(actualBonus.getSourceGradingScale().getId()).isEqualTo(expectedBonus.getSourceGradingScale().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBonusForTargetExam() throws Exception {

        Bonus foundBonus = request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus", HttpStatus.OK, Bonus.class);

        assertThat(foundBonus.getId()).isEqualTo(courseBonus.getId());
        assertBonusesAreEqualIgnoringId(foundBonus, courseBonus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveBonusForTargetExam() throws Exception {
        bonusRepository.delete(courseBonus);
        Exam newExam = examUtilService.addExamWithExerciseGroup(course, true);

        var newExamGradingScale = GradingScaleFactory.generateGradingScaleForExam(newExam, GradeType.BONUS);
        gradingScaleRepository.save(newExamGradingScale);

        Bonus newBonus = BonusFactory.generateBonus(BonusStrategy.GRADES_CONTINUOUS, -1.0, newExamGradingScale.getId(), bonusToExamGradingScale.getId());

        Bonus savedBonus = request.postWithResponseBody("/api/courses/" + courseId + "/exams/" + examId + "/bonus", newBonus, Bonus.class, HttpStatus.CREATED);

        assertThat(savedBonus.getId()).isGreaterThan(0);
        assertBonusesAreEqualIgnoringId(savedBonus, newBonus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveBonusForTargetExamDuplicateError() throws Exception {

        request.postWithResponseBody("/api/courses/" + courseId + "/exams/" + examId + "/bonus", examBonus, Bonus.class, HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveBonusForGradeScaleTypeError() throws Exception {
        bonusRepository.delete(courseBonus);

        courseGradingScale.setGradeType(GradeType.GRADE);
        gradingScaleRepository.save(courseGradingScale);

        Bonus newBonus = BonusFactory.generateBonus(BonusStrategy.GRADES_CONTINUOUS, -1.0, courseGradingScale.getId(), bonusToExamGradingScale.getId());

        // Source grading scale must have GradeType.BONUS.
        request.postWithResponseBody("/api/courses/" + courseId + "/exams/" + examId + "/bonus", newBonus, Bonus.class, HttpStatus.BAD_REQUEST);

        courseGradingScale.setGradeType(GradeType.BONUS);
        bonusToExamGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.saveAll(List.of(courseGradingScale, bonusToExamGradingScale));

        // BonusTo grading scale must have GradeType.GRADE.
        request.postWithResponseBody("/api/courses/" + courseId + "/exams/" + examId + "/bonus", newBonus, Bonus.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateBonusIsNotAtLeastInstructorInCourseForbidden() throws Exception {
        request.postWithResponseBody("/api/courses/" + courseId + "/exams/" + examId + "/bonus", examBonus, Bonus.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteBonusIsNotAtLeastInstructorInCourseForbidden() throws Exception {
        request.delete("/api/courses/" + courseId + "/exams/" + examId + "/bonus/" + courseBonus.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateBonusIsNotAtLeastInstructorInCourseForbidden() throws Exception {
        request.put("/api/courses/" + courseId + "/exams/" + examId + "/bonus/" + courseBonus.getId(), courseBonus, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateBonusWithMismatchingIdsInPathAndBodyConflict() throws Exception {
        request.put("/api/courses/" + courseId + "/exams/" + examId + "/bonus/" + courseBonus.getId() + 1, courseBonus, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateBonusWithoutChangingSourceGradingScale() throws Exception {

        Bonus foundBonus = request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus", HttpStatus.OK, Bonus.class);

        BonusStrategy newBonusStrategy = BonusStrategy.POINTS;
        foundBonus.setBonusStrategy(newBonusStrategy);
        foundBonus.setWeight(-foundBonus.getWeight());

        request.put("/api/courses/" + courseId + "/exams/" + examId + "/bonus/" + foundBonus.getId(), foundBonus, HttpStatus.OK);
        Bonus updatedBonus = request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus", HttpStatus.OK, Bonus.class);
        assertThat(updatedBonus.getId()).isEqualTo(foundBonus.getId());
        assertBonusesAreEqualIgnoringId(updatedBonus, foundBonus);
        assertThat(updatedBonus.getSourceGradingScale().getId()).isEqualTo(foundBonus.getSourceGradingScale().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateBonusWithChangingSourceGradingScale() throws Exception {

        Bonus foundBonus = request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus", HttpStatus.OK, Bonus.class);

        foundBonus.setBonusStrategy(BonusStrategy.POINTS);
        foundBonus.setWeight(-foundBonus.getWeight());

        assertThat(foundBonus.getSourceGradingScale().getId()).isNotEqualTo(sourceExamGradingScale.getId());
        foundBonus.setSourceGradingScale(sourceExamGradingScale);

        request.put("/api/courses/" + courseId + "/exams/" + examId + "/bonus/" + foundBonus.getId(), foundBonus, HttpStatus.OK);
        Bonus updatedBonus = request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus", HttpStatus.OK, Bonus.class);
        assertThat(updatedBonus.getId()).isEqualTo(foundBonus.getId());
        assertBonusesAreEqualIgnoringId(updatedBonus, foundBonus);
        assertThat(updatedBonus.getSourceGradingScale().getId()).isEqualTo(foundBonus.getSourceGradingScale().getId());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteBonus() throws Exception {

        Bonus foundBonus = request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus", HttpStatus.OK, Bonus.class);

        request.delete("/api/courses/" + courseId + "/exams/" + examId + "/bonus/" + foundBonus.getId(), HttpStatus.OK);
        request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus", HttpStatus.NOT_FOUND, Bonus.class);

    }

    @NotNull
    private GradingScale createBonusToGradingScale(Exam bonusToExam) {
        GradingScale bonusToGradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 40, 20, 15, 15, 10, 100 },
                Optional.of(new String[] { "5.0", "4.0", "3.0", "2.0", "1.0", "1.0" }), true, 1);

        bonusToGradingScale.setGradeType(GradeType.GRADE);
        bonusToGradingScale.setExam(bonusToExam);
        return bonusToGradingScale;
    }

    @NotNull
    private GradingScale createSourceGradingScaleWithGradeStepsForGradesBonusStrategy(Course sourceCourse) {
        GradingScale sourceGradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 30, 40, 70 }, Optional.of(new String[] { "0", "0.1", "0.2" }),
                true, 1);

        sourceGradingScale.setGradeType(GradeType.BONUS);
        sourceGradingScale.setCourse(sourceCourse);
        return sourceGradingScale;
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "admin", roles = "ADMIN")
    @EnumSource(value = BonusStrategy.class, names = { "GRADES_DISCRETE" }, mode = EnumSource.Mode.EXCLUDE)
    void testCalculateRawBonus(BonusStrategy bonusStrategy) throws Exception {
        // Calculation results should be consistent with bonus.service.spec.ts

        boolean isContinuous = bonusStrategy == BonusStrategy.GRADES_CONTINUOUS;
        double weight = isContinuous ? -1 : 1;

        Exam bonusToExam = bonusToExamGradingScale.getExam();
        Course sourceCourse = courseGradingScale.getCourse();

        bonusRepository.delete(courseBonus);
        // Line below is needed to prevent EntityNotFoundException for the Bonus instance deleted above.
        bonusToExamGradingScale = gradingScaleRepository.findWithEagerBonusFromByExamId(bonusToExam.getId()).orElseThrow();
        gradingScaleRepository.deleteAll(List.of(bonusToExamGradingScale, courseGradingScale));

        GradingScale sourceGradingScale;
        if (isContinuous) {
            sourceGradingScale = createSourceGradingScaleWithGradeStepsForGradesBonusStrategy(sourceCourse);
        }
        else {
            sourceGradingScale = createSourceGradingScaleWithGradeStepsForPointsBonusStrategy(sourceCourse);
        }

        GradingScale bonusToGradingScale = createBonusToGradingScale(bonusToExam);
        gradingScaleRepository.saveAll(List.of(bonusToGradingScale, sourceGradingScale));

        double bonusToPoints = 50;
        double sourcePoints = 100;
        String expectedExamGrade = "5.0";
        double expectedBonusGrade = 0.0;
        Double expectedFinalPoints = 50.0;
        String expectedFinalGrade = "5.0";
        boolean expectedExceedsMax = false;

        calculateFinalGradeAtServer(bonusStrategy, weight, bonusToPoints, sourcePoints, expectedExamGrade, expectedBonusGrade, expectedFinalPoints, expectedFinalGrade,
                expectedExceedsMax, sourceGradingScale.getId());

        bonusToPoints = 120;
        sourcePoints = 75;
        expectedExamGrade = "3.0";
        expectedBonusGrade = isContinuous ? 0.1 : 10.0;
        expectedFinalPoints = isContinuous ? null : 130.0;
        expectedFinalGrade = isContinuous ? "2.9" : "3.0";

        calculateFinalGradeAtServer(bonusStrategy, weight, bonusToPoints, sourcePoints, expectedExamGrade, expectedBonusGrade, expectedFinalPoints, expectedFinalGrade,
                expectedExceedsMax, sourceGradingScale.getId());

        bonusToPoints = 200;
        sourcePoints = 200;
        expectedExamGrade = "1.0";
        expectedBonusGrade = isContinuous ? 0.2 : 20.0;
        expectedFinalPoints = isContinuous ? null : 200.0;
        expectedFinalGrade = "1.0";
        expectedExceedsMax = true;

        calculateFinalGradeAtServer(bonusStrategy, weight, bonusToPoints, sourcePoints, expectedExamGrade, expectedBonusGrade, expectedFinalPoints, expectedFinalGrade,
                expectedExceedsMax, sourceGradingScale.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCalculateRawBonusWithGradesContinuousBonusStrategy_nonNumericGrades() throws Exception {
        // Calculation results should be consistent with bonus.service.spec.ts

        BonusStrategy bonusStrategy = BonusStrategy.GRADES_CONTINUOUS;
        double weight = -1;

        Exam bonusToExam = bonusToExamGradingScale.getExam();
        Course sourceCourse = courseGradingScale.getCourse();

        bonusRepository.delete(courseBonus);
        // Line below is needed to prevent EntityNotFoundException for the Bonus instance deleted above.
        bonusToExamGradingScale = gradingScaleRepository.findWithEagerBonusFromByExamId(bonusToExam.getId()).orElseThrow();
        gradingScaleRepository.deleteAll(List.of(bonusToExamGradingScale, courseGradingScale));

        GradingScale sourceGradingScale = createSourceGradingScaleWithGradeStepsForGradesBonusStrategy(sourceCourse);
        GradingScale bonusToGradingScale = createBonusToGradingScale(bonusToExam);

        GradeStep matchedBonusSourceGradeStep = sourceGradingScale.getGradeSteps().stream().filter(gradeStep -> "0.1".equals(gradeStep.getGradeName())).findFirst().orElseThrow();
        matchedBonusSourceGradeStep.setGradeName("NonNumericBonusSourceGrade");

        GradeStep matchedBonusToGradeStep = bonusToGradingScale.getGradeSteps().stream().filter(gradeStep -> "4.0".equals(gradeStep.getGradeName())).findFirst().orElseThrow();
        matchedBonusToGradeStep.setGradeName("NonNumericBonusToGrade");

        gradingScaleRepository.saveAll(List.of(bonusToGradingScale, sourceGradingScale));

        double bonusToPoints = 125;
        double sourcePoints = 150;
        String expectedExamGrade = "3.0";
        double expectedBonusGrade = 0.2;
        Double expectedFinalPoints = null;
        String expectedFinalGrade = "2.8";
        boolean expectedExceedsMax = false;

        // First assert there is a working source and bonusTo grade step combinations before checking BAD_REQUEST errors.
        calculateFinalGradeAtServer(bonusStrategy, weight, bonusToPoints, sourcePoints, expectedExamGrade, expectedBonusGrade, expectedFinalPoints, expectedFinalGrade,
                expectedExceedsMax, sourceGradingScale.getId());

        // Test getting an error due to non-numeric bonus source grade step.
        bonusToPoints = 125;
        sourcePoints = 75;
        request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus/calculate-raw?bonusStrategy=" + bonusStrategy + "&calculationSign=" + weight
                + "&sourceGradingScaleId=" + sourceGradingScale.getId() + "&bonusToPoints=" + bonusToPoints + "&sourcePoints=" + sourcePoints, HttpStatus.BAD_REQUEST,
                BonusExampleDTO.class);

        // Test getting an error due to non-numeric bonusTo grade step.
        bonusToPoints = 110;
        sourcePoints = 150;
        request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus/calculate-raw?bonusStrategy=" + bonusStrategy + "&calculationSign=" + weight
                + "&sourceGradingScaleId=" + sourceGradingScale.getId() + "&bonusToPoints=" + bonusToPoints + "&sourcePoints=" + sourcePoints, HttpStatus.BAD_REQUEST,
                BonusExampleDTO.class);

    }

    @NotNull
    private BonusExampleDTO calculateFinalGradeAtServer(BonusStrategy bonusStrategy, double weight, double bonusToPoints, double sourcePoints, String expectedExamGrade,
            double expectedBonusGrade, Double expectedFinalPoints, String expectedFinalGrade, boolean expectedExceedsMax, long sourceGradingScaleId) throws Exception {
        BonusExampleDTO bonusExample = request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus/calculate-raw?bonusStrategy=" + bonusStrategy + "&calculationSign="
                + weight + "&sourceGradingScaleId=" + sourceGradingScaleId + "&bonusToPoints=" + bonusToPoints + "&sourcePoints=" + sourcePoints, HttpStatus.OK,
                BonusExampleDTO.class);
        assertThat(bonusExample.examGrade()).isEqualTo(expectedExamGrade);
        assertThat(bonusExample.bonusGrade()).isEqualTo(expectedBonusGrade);
        assertThat(bonusExample.finalPoints()).isEqualTo(expectedFinalPoints);
        assertThat(bonusExample.finalGrade()).isEqualTo(expectedFinalGrade);
        assertThat(bonusExample.exceedsMax()).isEqualTo(expectedExceedsMax);
        return bonusExample;
    }

    @NotNull
    private GradingScale createSourceGradingScaleWithGradeStepsForPointsBonusStrategy(Course sourceCourse) {
        GradingScale sourceGradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 30, 40, 70 }, Optional.of(new String[] { "0", "10", "20" }),
                true, 1);

        sourceGradingScale.setGradeType(GradeType.BONUS);
        sourceGradingScale.setCourse(sourceCourse);
        return sourceGradingScale;
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCalculateRawBonusWithPointsBonusStrategy_nonNumericGrades() throws Exception {
        BonusStrategy bonusStrategy = BonusStrategy.POINTS;
        double weight = 1;

        Exam bonusToExam = bonusToExamGradingScale.getExam();
        Course sourceCourse = courseGradingScale.getCourse();

        bonusRepository.delete(courseBonus);
        // Line below is needed to prevent EntityNotFoundException for the Bonus instance deleted above.
        bonusToExamGradingScale = gradingScaleRepository.findWithEagerBonusFromByExamId(bonusToExam.getId()).orElseThrow();
        gradingScaleRepository.deleteAll(List.of(bonusToExamGradingScale, courseGradingScale));

        GradingScale sourceGradingScale = createSourceGradingScaleWithGradeStepsForPointsBonusStrategy(sourceCourse);
        GradingScale bonusToGradingScale = createBonusToGradingScale(bonusToExam);

        GradeStep matchedBonusSourceGradeStep = sourceGradingScale.getGradeSteps().stream().filter(gradeStep -> "10".equals(gradeStep.getGradeName())).findFirst().orElseThrow();
        matchedBonusSourceGradeStep.setGradeName("NonNumericBonusSourceGrade");

        gradingScaleRepository.saveAll(List.of(bonusToGradingScale, sourceGradingScale));

        double bonusToPoints = 120;
        double sourcePoints = 75;

        request.get("/api/courses/" + courseId + "/exams/" + examId + "/bonus/calculate-raw?bonusStrategy=" + bonusStrategy + "&calculationSign=" + weight
                + "&sourceGradingScaleId=" + sourceGradingScale.getId() + "&bonusToPoints=" + bonusToPoints + "&sourcePoints=" + sourcePoints, HttpStatus.BAD_REQUEST,
                BonusExampleDTO.class);

    }

}
