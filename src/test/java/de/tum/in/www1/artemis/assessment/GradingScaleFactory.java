package de.tum.in.www1.artemis.assessment;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradingScale;

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
}
