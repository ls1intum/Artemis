package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.fail;

import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import com.opencsv.CSVReader;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;

/**
 * Service responsible for initializing the database with specific testdata related to grading for use in integration tests.
 */
@Service
public class GradingScaleUtilService {

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    /**
     * Creates a set of three grade steps. The second grade step is valid or invalid, depending on the {@code valid} parameter.
     *
     * @param gradingScale the grading scale to which the grade steps belong
     * @param valid        whether the second grade step is valid (i.e. the upper bound of the second grade step is equal to the lower bound of the third grade step)
     * @return a set of grade steps
     */
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

    /**
     * Creates a grading scale with the given parameters.
     *
     * @param gradeStepCount        The number of grade steps to generate
     * @param intervals             The intervals to use for the grade steps. The length of this array must be gradeStepCount + 1.
     * @param lowerBoundInclusivity Whether the lower bound of the first grade step should be inclusive.
     * @param firstPassingIndex     The index of the first passing grade step.
     * @param gradeNames            The names of the grade steps.
     * @param course                The course to which the grading scale belongs.
     * @param presentationsNumber   The number of presentations for the course.
     * @param presentationsWeight   The weight of the presentations for the course.
     * @return The generated grading scale.
     */
    public GradingScale generateGradingScale(int gradeStepCount, double[] intervals, boolean lowerBoundInclusivity, int firstPassingIndex, Optional<String[]> gradeNames,
            Course course, Integer presentationsNumber, Double presentationsWeight) {
        GradingScale gradingScale = generateGradingScale(gradeStepCount, intervals, lowerBoundInclusivity, firstPassingIndex, gradeNames);
        gradingScale.setCourse(course);
        gradingScale.setPresentationsNumber(presentationsNumber);
        gradingScale.setPresentationsWeight(presentationsWeight);
        return gradingScale;
    }

    /**
     * Creates and saves a grading scale with the given parameters.
     *
     * @param gradeStepCount        The number of grade steps to generate.
     * @param intervals             The intervals to use for the grade steps. The length of this array must be gradeStepCount + 1.
     * @param lowerBoundInclusivity Whether the lower bound of the first grade step should be inclusive.
     * @param firstPassingIndex     The index of the first passing grade step.
     * @param gradeNames            The names of the grade steps.
     * @param exam                  The exam to which the grading scale belongs.
     * @return The generated and saved grading scale.
     */
    public GradingScale generateAndSaveGradingScale(int gradeStepCount, double[] intervals, boolean lowerBoundInclusivity, int firstPassingIndex, Optional<String[]> gradeNames,
            Exam exam) {
        GradingScale gradingScale = generateGradingScale(gradeStepCount, intervals, lowerBoundInclusivity, firstPassingIndex, gradeNames);
        gradingScale.setExam(exam);
        return gradingScaleRepository.save(gradingScale);
    }

    /**
     * Creates a grading scale with the given parameters. Fails if {@code gradeStepCount + 1} does not equal {@code intervals.length} or if {@code firstPassingIndex} is not in [0;
     * gradeStepCount).
     *
     * @param gradeStepCount        The number of grade steps to generate.
     * @param intervals             The intervals to use for the grade steps. The length of this array must be gradeStepCount + 1.
     * @param lowerBoundInclusivity Whether the lower bound of the first grade step should be inclusive.
     * @param firstPassingIndex     The index of the first passing grade step.
     * @param gradeNames            The names of the grade steps.
     * @return The generated grading scale.
     */
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

    /**
     * Creates a grading scale with a sticky grade step. Fails if {@code firstPassingIndex} is not in [0; gradeStepCount).
     *
     * @param intervalSizes         The sizes of the intervals to use for the grade steps.
     * @param gradeNames            The names of the grade steps.
     * @param lowerBoundInclusivity Whether the lower bound of the first grade step should be inclusive.
     * @param firstPassingIndex     The index of the first passing grade step.
     * @return The generated grading scale.
     */
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

    /**
     * Creates a list of String arrays containing the percentages[0], whether the student has submitted[1], and their grade[2] given the path to a csv file.
     *
     * @param path The path to the csv file.
     * @return The list of String arrays.
     * @throws Exception if an error occurs while reading the csv file.
     */
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
