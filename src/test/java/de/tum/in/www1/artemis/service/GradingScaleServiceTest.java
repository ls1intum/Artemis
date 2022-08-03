package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradeType;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class GradingScaleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private GradingScaleService gradingScaleService;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private ExamRepository examRepository;

    private GradingScale gradingScale;

    private Set<GradeStep> gradeSteps;

    private Exam exam;

    private Course course;

    /**
     * Initialize attributes
     */
    @BeforeEach
    void init() {
        gradingScale = new GradingScale();
        gradingScale.setId(1L);
        gradeSteps = new HashSet<>();
        course = database.addEmptyCourse();
        exam = database.addExam(course);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    /**
     * Test match percentage query with invalid grade percentages
     *
     * @param invalidPercentage the invalid percentage
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(doubles = { -60, -1.3, -0.0002 })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testMatchPercentageToGradeStepInvalidPercentage(double invalidPercentage) {
        BadRequestAlertException exception = assertThrows(BadRequestAlertException.class,
                () -> gradingScaleRepository.matchPercentageToGradeStep(invalidPercentage, gradingScale.getId()));

        assertThat(exception.getMessage()).isEqualTo("Grade percentages must be greater than 0");
        assertThat(exception.getEntityName()).isEqualTo("gradeStep");
        assertThat(exception.getErrorKey()).isEqualTo("invalidGradePercentage");
    }

    /**
     * Test saving of an invalid grading scale
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testMatchPercentageToGradeStepNoValidMapping() {
        gradeSteps = database.generateGradeStepSet(gradingScale, false);
        gradingScale.setGradeSteps(gradeSteps);
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        double percentage = 85;

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> gradingScaleRepository.matchPercentageToGradeStep(percentage, id));

        assertThat(exception.getMessage()).isEqualTo("No grade step in selected grading scale matches given percentage");
    }

    /**
     * Test mapping of a valid grade percentage to a grade step
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testMatchPercentageToGradeStepValidMappingExists() {
        GradeStep expectedGradeStep = createCustomGradeStep("Pass", 60, 90);
        gradingScaleRepository.save(gradingScale);
        Long gradingScaleId = gradingScaleRepository.findAll().get(0).getId();

        double percentage = 70;

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(percentage, gradingScaleId);

        assertThat(gradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(expectedGradeStep);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(doubles = { 125, 160 })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testMatchPercentageToGradeStepWithBonusPoints(double bonusPercentage) {
        GradeStep expectedGradeStep = createCustomGradeStep("🐧", 100, 150);
        gradingScaleRepository.save(gradingScale);
        Long gradingScaleId = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(bonusPercentage, gradingScaleId);

        assertThat(gradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(expectedGradeStep);
    }

    /**
     * Test saving of an invalid grading scale - missing grade names
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleInvalidGradeStepsNoGradeName() {
        createCustomGradeStep("", 70, 95);

        BadRequestAlertException exception = assertThrows(BadRequestAlertException.class, () -> gradingScaleService.saveGradingScale(gradingScale));

        assertThat(exception.getEntityName()).isEqualTo("gradeStep");
        assertThat(exception.getErrorKey()).isEqualTo("invalidGradeStepFormat");
        assertThat(exception.getMessage()).isEqualTo("Not all grade steps are following the correct format.");
    }

    /**
     * Test saving of an invalid grading scale - non-matching percentage bounds
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleInvalidGradeStepsInvalidPercentageValues() {
        createCustomGradeStep("Name", 90, 80);

        BadRequestAlertException exception = assertThrows(BadRequestAlertException.class, () -> gradingScaleService.saveGradingScale(gradingScale));

        assertThat(exception.getMessage()).isEqualTo("Not all grade steps are following the correct format.");
        assertThat(exception.getEntityName()).isEqualTo("gradeStep");
        assertThat(exception.getErrorKey()).isEqualTo("invalidGradeStepFormat");
    }

    private GradeStep createCustomGradeStep(String gradeName, double lowerBound, double upperBound) {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setIsPassingGrade(true);
        gradeStep.setGradeName(gradeName);
        gradeStep.setLowerBoundPercentage(lowerBound);
        gradeStep.setUpperBoundPercentage(upperBound);
        gradeStep.setGradingScale(gradingScale);
        gradingScale.setGradeSteps(Set.of(gradeStep));
        gradingScale.setCourse(course);
        return gradeStep;
    }

    /**
     * Test saving of an invalid grading scale - no valid grade step mapping
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleInvalidGradeStepSet() {
        gradeSteps = database.generateGradeStepSet(gradingScale, false);
        gradingScale.setGradeSteps(gradeSteps);
        gradingScale.setExam(exam);

        BadRequestAlertException exception = assertThrows(BadRequestAlertException.class, () -> gradingScaleService.saveGradingScale(gradingScale));

        assertThat(exception.getMessage()).isEqualTo("Grade step set can't match to a valid grading scale.");
        assertThat(exception.getEntityName()).isEqualTo("gradeStep");
        assertThat(exception.getErrorKey()).isEqualTo("invalidGradeStepAdjacency");
    }

    /**
     * Test saving of a valid grading scale
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveGradingScaleValidGradeStepSet() {
        gradeSteps = database.generateGradeStepSet(gradingScale, true);
        gradingScale.setGradeSteps(gradeSteps);
        course = database.addEmptyCourse();
        exam = database.addExam(course);
        exam.setMaxPoints(null);
        gradingScale.setExam(exam);
        examRepository.save(exam);

        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(gradingScale);

        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("exam", "course", "gradeSteps", "id").isEqualTo(gradingScale);
        assertThat(savedGradingScale.getGradeSteps()).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(gradingScale.getGradeSteps());
    }

    /**
     * Test fetching a grading scale for course if more than one has been saved to the database
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetGradingScaleForCourseIfMultipleScalesAreSaved() {
        Course course = database.addEmptyCourse();
        GradingScale gradingScale1 = new GradingScale();
        gradingScale1.setCourse(course);
        gradingScale1.setGradeType(GradeType.GRADE);
        GradingScale gradingScale2 = new GradingScale();
        gradingScale2.setCourse(course);
        gradingScale2.setGradeType(GradeType.BONUS);
        gradingScaleRepository.save(gradingScale1);
        gradingScaleRepository.save(gradingScale2);

        assertThat(gradingScaleRepository.findByCourseIdOrElseThrow(course.getId())).isEqualTo(gradingScale1);
    }

    /**
     * Test fetching a grading scale for exam if more than one has been saved to the database
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetGradingScaleForExamIfMultipleScalesAreSaved() {
        Course course = database.addEmptyCourse();
        Exam exam = database.addExam(course);
        GradingScale gradingScale1 = new GradingScale();
        gradingScale1.setExam(exam);
        gradingScale1.setGradeType(GradeType.BONUS);
        GradingScale gradingScale2 = new GradingScale();
        gradingScale2.setExam(exam);
        gradingScale2.setGradeType(GradeType.GRADE);
        gradingScaleRepository.save(gradingScale1);
        gradingScaleRepository.save(gradingScale2);

        assertThat(gradingScaleRepository.findByExamIdOrElseThrow(exam.getId())).isEqualTo(gradingScale1);
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors1() {
        GradingScale gradingScale = database.generateGradingScale(3, new double[] { 0, 40.005, 80, 100 }, true, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(40, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(39.99, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step0");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(39.999, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors2() {
        GradingScale gradingScale = database.generateGradingScale(3, new double[] { 0, 40, 63.9901, 100 }, false, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(64, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(64.005, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step2");
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors3() {
        GradingScale gradingScale = database.generateGradingScale(2, new double[] { 0, 50.010101, 100 }, true, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(50, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step0");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(50.009, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors4() {
        double boundary = 60 + 1d / 7d;
        GradingScale gradingScale = database.generateGradingScale(2, new double[] { 0, boundary, 100 }, true, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(60.142857, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(60.1322, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step0");
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors5() {
        double boundary = 33 + 1d / 3d;
        GradingScale gradingScale = database.generateGradingScale(2, new double[] { 0, boundary, 100 }, true, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(33.33, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(33, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step0");
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors6() {
        double boundary = 55 + 2d / 3d;
        GradingScale gradingScale = database.generateGradingScale(2, new double[] { 0, boundary, 100 }, false, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(55.67, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step0");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(55.679, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors7() {
        double boundary = 70 + 1d / 6d;
        GradingScale gradingScale = database.generateGradingScale(2, new double[] { 0, boundary, 100 }, true, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(70.16, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(70.15, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step0");
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors8() {
        double boundary = 45 + 5d / 6d;
        GradingScale gradingScale = database.generateGradingScale(2, new double[] { 0, boundary, 100 }, true, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(45.83, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(45.825, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors9() {
        double boundary = 50 + 1d / 9d;
        GradingScale gradingScale = database.generateGradingScale(2, new double[] { 0, boundary, 100 }, true, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(50.11, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(50.1, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step0");
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors10() {
        double boundary = 35 + 1d / 11d;
        GradingScale gradingScale = database.generateGradingScale(2, new double[] { 0, boundary, 100 }, true, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(35.09, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(35.089, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors11() {
        double boundary = 25 + 1d / 12d;
        GradingScale gradingScale = database.generateGradingScale(2, new double[] { 0, boundary, 100 }, true, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(25.08, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(25, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step0");
    }

    /**
     * Test grade step matching for rounding errors
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeStepMatchingForRoundingErrors12() {
        double boundary = 42 + 1d / 13d;
        GradingScale gradingScale = database.generateGradingScale(2, new double[] { 0, boundary, 100 }, true, 1, Optional.empty());
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(42.07, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step1");

        gradeStep = gradingScaleRepository.matchPercentageToGradeStep(42.06, id);

        assertThat(gradeStep.getGradeName()).isEqualTo("Step0");
    }

    /**
     * Test grade steps matching against realistic exam results from a csv file
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGradeMappingWithRealExamResults() throws Exception {
        double[] gradeBoundaries = { 0, 28.3, 34.2, 40, 45.8, 51.7, 57.5, 63.3, 69.2, 75, 80.8, 86.7, 92.5, 100 };
        String[] gradeNames = { "5.0", "4.7", "4.3", "4.0", "3.7", "3.3", "3.0", "2.7", "2.3", "2.0", "1.7", "1.3", "1.0" };
        GradingScale gradingScale = database.generateGradingScale(13, gradeBoundaries, true, 3, Optional.of(gradeNames));
        gradingScaleRepository.save(gradingScale);
        Long id = gradingScaleRepository.findAll().get(0).getId();

        List<String[]> results = database.loadPercentagesAndGrades("test-data/student-grades/grades.csv");

        assertThat(results).isNotEmpty();
        for (String[] result : results) {
            if ("yes".equals(result[1])) {
                double percentage = Double.parseDouble(result[0].substring(0, result[0].length() - 1));
                GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(percentage, id);
                assertThat(gradeStep.getGradeName()).isEqualTo(result[2]);
            }
            else {
                if (!result[2].isBlank()) {
                    assertThat(result[2]).isEqualTo("X");
                }
            }
        }
    }
}
