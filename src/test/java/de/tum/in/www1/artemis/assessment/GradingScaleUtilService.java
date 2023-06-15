package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.fail;

import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import com.opencsv.CSVReader;

import de.tum.in.www1.artemis.domain.*;

/**
 * Service responsible for initializing the database with specific testdata related to grading for use in integration tests.
 */
@Service
public class GradingScaleUtilService {

    @NotNull
    public Set<GradeStep> generateGradeStepSet(GradingScale gradingScale, boolean valid) {
        GradeStep gradeStep1 = new GradeStep();
        GradeStep gradeStep2 = new GradeStep();
        GradeStep gradeStep3 = new GradeStep();

        gradeStep1.setGradingScale(gradingScale);
        gradeStep2.setGradingScale(gradingScale);
        gradeStep3.setGradingScale(gradingScale);

        gradeStep1.setIsPassingGrade(false);
        gradeStep1.setGradeName("Fail");
        gradeStep1.setLowerBoundPercentage(0);
        gradeStep1.setUpperBoundPercentage(60);

        gradeStep2.setIsPassingGrade(true);
        gradeStep2.setGradeName("Pass");
        gradeStep2.setLowerBoundPercentage(60);
        if (valid) {
            gradeStep2.setUpperBoundPercentage(90);
        }
        else {
            gradeStep2.setUpperBoundPercentage(80);
        }

        gradeStep3.setIsPassingGrade(true);
        gradeStep3.setGradeName("Excellent");
        gradeStep3.setLowerBoundPercentage(90);
        gradeStep3.setUpperBoundPercentage(100);
        gradeStep3.setUpperBoundInclusive(true);

        return Set.of(gradeStep1, gradeStep2, gradeStep3);
    }

    public GradingScale generateGradingScale(int gradeStepCount, double[] intervals, boolean lowerBoundInclusivity, int firstPassingIndex, Optional<String[]> gradeNames,
            Course course, Integer presentationsNumber, Double presentationsWeight) {
        GradingScale gradingScale = generateGradingScale(gradeStepCount, intervals, lowerBoundInclusivity, firstPassingIndex, gradeNames);
        gradingScale.setCourse(course);
        gradingScale.setPresentationsNumber(presentationsNumber);
        gradingScale.setPresentationsWeight(presentationsWeight);
        return gradingScale;
    }

    public GradingScale generateGradingScale(int gradeStepCount, double[] intervals, boolean lowerBoundInclusivity, int firstPassingIndex, Optional<String[]> gradeNames) {
        if (gradeStepCount != intervals.length - 1 || firstPassingIndex >= gradeStepCount || firstPassingIndex < 0) {
            fail("Invalid grading scale parameters");
        }
        GradingScale gradingScale = new GradingScale();
        Set<GradeStep> gradeSteps = new HashSet<>();
        for (int i = 0; i < gradeStepCount; i++) {
            GradeStep gradeStep = new GradeStep();
            gradeStep.setLowerBoundPercentage(intervals[i]);
            gradeStep.setUpperBoundPercentage(intervals[i + 1]);
            gradeStep.setLowerBoundInclusive(i == 0 || lowerBoundInclusivity);
            gradeStep.setUpperBoundInclusive(i + 1 == gradeStepCount || !lowerBoundInclusivity);
            gradeStep.setIsPassingGrade(i >= firstPassingIndex);
            gradeStep.setGradeName(gradeNames.isPresent() ? gradeNames.get()[i] : "Step" + i);
            gradeStep.setGradingScale(gradingScale);
            gradeSteps.add(gradeStep);
        }
        gradingScale.setGradeSteps(gradeSteps);
        gradingScale.setGradeType(GradeType.GRADE);
        return gradingScale;
    }

    public GradingScale generateGradingScaleWithStickyStep(double[] intervalSizes, Optional<String[]> gradeNames, boolean lowerBoundInclusivity, int firstPassingIndex) {
        // This method has a different signature from the one above to define intervals from sizes to be consistent with
        // the instructor UI at interval-grading-system.component.ts and client tests at bonus.service.spec.ts.

        int gradeStepCount = intervalSizes.length;
        if (firstPassingIndex >= gradeStepCount || firstPassingIndex < 0) {
            fail("Invalid grading scale parameters");
        }
        GradingScale gradingScale = new GradingScale();
        Set<GradeStep> gradeSteps = new HashSet<>();
        double currentLowerBoundPercentage = 0.0;
        for (int i = 0; i < gradeStepCount; i++) {
            GradeStep gradeStep = new GradeStep();
            gradeStep.setLowerBoundPercentage(currentLowerBoundPercentage);
            currentLowerBoundPercentage += intervalSizes[i];
            gradeStep.setUpperBoundPercentage(currentLowerBoundPercentage);
            gradeStep.setLowerBoundInclusive(i == 0 || lowerBoundInclusivity);
            gradeStep.setUpperBoundInclusive(i + 1 == gradeStepCount || !lowerBoundInclusivity);

            // Ensure 100 percent is not a part of the sticky grade step.
            if (i == gradeStepCount - 2) {
                gradeStep.setUpperBoundInclusive(true);

            }
            else if (i == gradeStepCount - 1) {
                gradeStep.setLowerBoundInclusive(false);
                gradeStep.setUpperBoundInclusive(true);
            }

            gradeStep.setIsPassingGrade(i >= firstPassingIndex);
            gradeStep.setGradeName(gradeNames.isPresent() ? gradeNames.get()[i] : "Step" + i);
            gradeStep.setGradingScale(gradingScale);
            gradeSteps.add(gradeStep);
        }
        gradingScale.setGradeSteps(gradeSteps);
        gradingScale.setGradeType(GradeType.GRADE);
        return gradingScale;
    }

    public List<String[]> loadPercentagesAndGrades(String path) throws Exception {
        try (CSVReader reader = new CSVReader(new FileReader(ResourceUtils.getFile("classpath:" + path), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            // delete first row with column headers
            rows.remove(0);
            List<String[]> percentagesAndGrades = new ArrayList<>();
            // copy only percentages, whether the student has submitted, and their grade
            rows.forEach(row -> percentagesAndGrades.add(new String[] { row[2], row[3], row[4] }));
            return percentagesAndGrades;
        }
    }
}
