package de.tum.cit.aet.artemis.assessment;

import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * Factory for creating GradingScales and related objects.
 */
public class GradingScaleFactory {

    /**
     * Generates a GradingScale instance with given arguments.
     *
     * @param course              of grading scale
     * @param presentationsNumber the number of presentations a student can give
     * @param presentationsWeight the combined weight of the presentations
     * @return a new GradingScale instance.
     */
    public static GradingScale generateGradingScaleForCourse(Course course, Integer presentationsNumber, Double presentationsWeight) {
        GradingScale gradingScale = new GradingScale();
        gradingScale.setCourse(course);
        gradingScale.setPresentationsNumber(presentationsNumber);
        gradingScale.setPresentationsWeight(presentationsWeight);
        return gradingScale;
    }

    /**
     * Generates a GradingScale instance with given arguments.
     *
     * @param course    of grading scale
     * @param gradeType grade type of the grading scale
     * @return a new GradingScale instance.
     */
    public static GradingScale generateGradingScaleForCourse(Course course, GradeType gradeType) {
        GradingScale gradingScale = new GradingScale();
        gradingScale.setCourse(course);
        gradingScale.setGradeType(gradeType);
        return gradingScale;
    }

    /**
     * Generates a GradingScale instance with given arguments.
     *
     * @param exam      of grading scale
     * @param gradeType grade type of the grading scale
     * @return a new GradingScale instance.
     */
    public static GradingScale generateGradingScaleForExam(Exam exam, GradeType gradeType) {
        GradingScale gradingScale = new GradingScale();
        gradingScale.setGradeType(gradeType);
        gradingScale.setExam(exam);
        return gradingScale;
    }
}
