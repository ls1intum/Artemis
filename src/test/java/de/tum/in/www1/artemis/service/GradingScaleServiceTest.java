package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class GradingScaleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private GradingScaleService gradingScaleService;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    private GradingScale gradingScale;

    private Set<GradeStep> gradeSteps;

    @BeforeEach
    public void init() {
        gradingScale = new GradingScale();
        gradingScale.setId(1L);
        gradeSteps = new HashSet<>();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @ParameterizedTest
    @ValueSource(doubles = { -60, -1.3, -0.0002, 100.1, 150 })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testMatchPercentageToGradeStep_InvalidPercentage(double invalidPercentage) {
        BadRequestAlertException exception = assertThrows(BadRequestAlertException.class, () -> {
            gradingScaleService.matchPercentageToGradeStep(invalidPercentage, gradingScale.getId());
        });

        assertThat(exception.getMessage()).isEqualTo("Grade percentages must be between 0 and 100");
        assertThat(exception.getEntityName()).isEqualTo("gradeStep");
        assertThat(exception.getErrorKey()).isEqualTo("invalidGradePercentage");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testMatchPercentageToGradeStep_NoValidMapping() {
        gradeSteps = generateGradeStepSet(false);
        gradingScale.setGradeSteps(gradeSteps);
        gradingScaleRepository.save(gradingScale);

        double percentage = 85;

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
            gradingScaleService.matchPercentageToGradeStep(percentage, gradingScale.getId());
        });

        assertThat(exception.getMessage()).isEqualTo("No grade step in selected grading scale matches given percentage");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testMatchPercentageToGradeStep_ValidMappingExists() {
        GradeStep expectedGradeStep = new GradeStep();
        expectedGradeStep.setPassingGrade(true);
        expectedGradeStep.setGradeName("Pass");
        expectedGradeStep.setLowerBoundPercentage(60);
        expectedGradeStep.setUpperBoundPercentage(90);
        expectedGradeStep.setGradingScale(gradingScale);
        gradingScale.setGradeSteps(Set.of(expectedGradeStep));
        gradingScaleRepository.save(gradingScale);
        Long gradingScaleId = gradingScaleRepository.findAll().get(0).getId();

        double percentage = 70;

        GradeStep gradeStep = gradingScaleService.matchPercentageToGradeStep(percentage, gradingScaleId);

        assertThat(gradeStep).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(expectedGradeStep);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScale_InvalidGradeSteps_NoGradeName() {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setPassingGrade(true);
        gradeStep.setGradeName("");
        gradeStep.setLowerBoundPercentage(90);
        gradeStep.setUpperBoundPercentage(100);
        gradeStep.setGradingScale(gradingScale);
        gradingScale.setGradeSteps(Set.of(gradeStep));

        BadRequestAlertException exception = assertThrows(BadRequestAlertException.class, () -> {
            gradingScaleService.saveGradingScale(gradingScale);
        });

        assertThat(exception.getMessage()).isEqualTo("Not all grade steps are following the correct format.");
        assertThat(exception.getEntityName()).isEqualTo("gradeStep");
        assertThat(exception.getErrorKey()).isEqualTo("invalidFormat");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScale_InvalidGradeSteps_InvalidPercentageValues() {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setPassingGrade(true);
        gradeStep.setGradeName("Name");
        gradeStep.setLowerBoundPercentage(90);
        gradeStep.setUpperBoundPercentage(80);
        gradeStep.setGradingScale(gradingScale);
        gradingScale.setGradeSteps(Set.of(gradeStep));

        BadRequestAlertException exception = assertThrows(BadRequestAlertException.class, () -> {
            gradingScaleService.saveGradingScale(gradingScale);
        });

        assertThat(exception.getMessage()).isEqualTo("Not all grade steps are following the correct format.");
        assertThat(exception.getEntityName()).isEqualTo("gradeStep");
        assertThat(exception.getErrorKey()).isEqualTo("invalidFormat");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScale_InvalidGradeStepSet() {
        gradeSteps = generateGradeStepSet(false);
        gradingScale.setGradeSteps(gradeSteps);

        BadRequestAlertException exception = assertThrows(BadRequestAlertException.class, () -> {
            gradingScaleService.saveGradingScale(gradingScale);
        });

        assertThat(exception.getMessage()).isEqualTo("Grade step set can't match to a valid grading scale.");
        assertThat(exception.getEntityName()).isEqualTo("gradeStep");
        assertThat(exception.getErrorKey()).isEqualTo("invalidFormat");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveGradingScale_ValidGradeStepSet() {
        gradeSteps = generateGradeStepSet(true);
        gradingScale.setGradeSteps(gradeSteps);

        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(gradingScale);

        assertThat(savedGradingScale).usingRecursiveComparison().ignoringFields("exam", "course", "gradeSteps", "id").isEqualTo(gradingScale);
        assertThat(savedGradingScale.getGradeSteps()).usingRecursiveComparison().ignoringFields("gradingScale", "id").isEqualTo(gradingScale.getGradeSteps());
    }

    @NotNull
    private Set<GradeStep> generateGradeStepSet(boolean valid) {
        GradeStep gradeStep1 = new GradeStep();
        GradeStep gradeStep2 = new GradeStep();
        GradeStep gradeStep3 = new GradeStep();

        gradeStep1.setGradingScale(gradingScale);
        gradeStep2.setGradingScale(gradingScale);
        gradeStep3.setGradingScale(gradingScale);

        gradeStep1.setId(1L);
        gradeStep1.setPassingGrade(false);
        gradeStep1.setGradeName("Fail");
        gradeStep1.setLowerBoundPercentage(0);
        gradeStep1.setUpperBoundPercentage(60);

        gradeStep2.setId(2L);
        gradeStep2.setPassingGrade(true);
        gradeStep2.setGradeName("Pass");
        gradeStep2.setLowerBoundPercentage(60);
        if (valid) {
            gradeStep2.setUpperBoundPercentage(90);
        }
        else {
            gradeStep2.setUpperBoundPercentage(80);
        }

        gradeStep3.setId(3L);
        gradeStep3.setPassingGrade(true);
        gradeStep3.setGradeName("Excellent");
        gradeStep3.setLowerBoundPercentage(90);
        gradeStep3.setUpperBoundPercentage(100);
        gradeStep3.setUpperBoundInclusive(true);

        return Set.of(gradeStep1, gradeStep2, gradeStep3);
    }

}
