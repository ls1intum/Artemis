package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
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

public class GradingScaleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    public void init() {
        gradingScale = new GradingScale();
        gradingScale.setId(1L);
        gradeSteps = new HashSet<>();
        course = new Course();
        exam = new Exam();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    /**
     * Test match percentage query with invalid grade percentages
     *
     * @param invalidPercentage the invalid percentage
     */
    @ParameterizedTest
    @ValueSource(doubles = { -60, -1.3, -0.0002, 100.2, 150 })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testMatchPercentageToGradeStepInvalidPercentage(double invalidPercentage) {
        BadRequestAlertException exception = assertThrows(BadRequestAlertException.class,
                () -> gradingScaleRepository.matchPercentageToGradeStep(invalidPercentage, gradingScale.getId()));

        assertThat(exception.getMessage()).isEqualTo("Grade percentages must be between 0 and 100");
        assertThat(exception.getEntityName()).isEqualTo("gradeStep");
        assertThat(exception.getErrorKey()).isEqualTo("invalidGradePercentage");
    }

    /**
     * Test saving of an invalid grading scale
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testMatchPercentageToGradeStepNoValidMapping() {
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
    public void testMatchPercentageToGradeStepValidMappingExists() {
        GradeStep expectedGradeStep = new GradeStep();
        expectedGradeStep.setIsPassingGrade(true);
        expectedGradeStep.setGradeName("Pass");
        expectedGradeStep.setLowerBoundPercentage(60);
        expectedGradeStep.setUpperBoundPercentage(90);
        expectedGradeStep.setGradingScale(gradingScale);
        gradingScale.setGradeSteps(Set.of(expectedGradeStep));
        gradingScaleRepository.save(gradingScale);
        Long gradingScaleId = gradingScaleRepository.findAll().get(0).getId();

        double percentage = 70;

        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(percentage, gradingScaleId);

        assertThat(gradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(expectedGradeStep);
    }

    /**
     * Test saving of an invalid grading scale - missing grade names
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScaleInvalidGradeStepsNoGradeName() {
        gradingScale.setCourse(course);
        GradeStep gradeStep = new GradeStep();
        gradeStep.setIsPassingGrade(true);
        gradeStep.setGradeName("");
        gradeStep.setLowerBoundPercentage(70);
        gradeStep.setUpperBoundPercentage(95);
        gradeStep.setGradingScale(gradingScale);
        gradingScale.setGradeSteps(Set.of(gradeStep));

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
    public void testSaveGradingScaleInvalidGradeStepsInvalidPercentageValues() {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setIsPassingGrade(true);
        gradeStep.setGradeName("Name");
        gradeStep.setLowerBoundPercentage(90);
        gradeStep.setUpperBoundPercentage(80);
        gradeStep.setGradingScale(gradingScale);
        gradingScale.setGradeSteps(Set.of(gradeStep));
        gradingScale.setCourse(course);

        BadRequestAlertException exception = assertThrows(BadRequestAlertException.class, () -> gradingScaleService.saveGradingScale(gradingScale));

        assertThat(exception.getMessage()).isEqualTo("Not all grade steps are following the correct format.");
        assertThat(exception.getEntityName()).isEqualTo("gradeStep");
        assertThat(exception.getErrorKey()).isEqualTo("invalidGradeStepFormat");
    }

    /**
     * Test saving of an invalid grading scale - no valid grade step mapping
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScaleInvalidGradeStepSet() {
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
    public void testSaveGradingScaleValidGradeStepSet() {
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
    public void testGetGradingScaleForCourseIfMultipleScalesAreSaved() {
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
    public void testGetGradingScaleForExamIfMultipleScalesAreSaved() {
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
}
