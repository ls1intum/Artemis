package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;

@Service
public class PresentationCalculationService {

    private final StudentParticipationRepository studentParticipationRepository;

    public PresentationCalculationService(StudentParticipationRepository studentParticipationRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Counts the number of presentations for a single student, referred to as the presentationScore for basic
     * presentations.
     *
     * @param course    the course for which the presentation score should be calculated
     * @param studentId the student for which the presentation score should be calculated
     * @return the basic presentation score for a single student.
     */
    public double calculateBasicPresentationScoreForStudent(Course course, long studentId) {
        double presentationCount = studentParticipationRepository.countNonZeroPresentationsByStudentIdAndCourseId(course.getId(), studentId);

        return roundScoreSpecifiedByCourseSettings(presentationCount, course);
    }

    /**
     * Calculates the points for presentations for a single student given the participation list of the student, the
     * reachable points of the course, and the presentationsWeight of the courses GradingScale.
     *
     * @param gradingScale                the grading scale with the presentation configuration
     * @param studentId                   the student for which the presentation points should be calculated
     * @param reachablePresentationPoints the reachable presentation points in the given course.
     * @return the presentation points for a single student.
     */
    public double calculatePresentationPointsForStudentId(GradingScale gradingScale, long studentId, double reachablePresentationPoints) {
        Course course = gradingScale.getCourse();
        if (course == null) {
            return 0.0;
        }

        double presentationPointSum = studentParticipationRepository.sumPresentationScoreByStudentIdAndCourseId(course.getId(), studentId);

        return calculatePresentationPoints(gradingScale, reachablePresentationPoints, presentationPointSum);
    }

    /**
     * Calculates the points for presentations given the gradingScale, the reachable presentation points of the
     * course, the presentationsNumber of the GradingScale, and the presentationScoreSum.
     *
     * @param gradingScale                the grading scale with the presentation configuration
     * @param reachablePresentationPoints the reachable presentation points in the given course.
     * @param presentationScoreSum        the sum of all presentation scores for a single student
     * @return the presentation points for a single student.
     */
    private double calculatePresentationPoints(GradingScale gradingScale, double reachablePresentationPoints, double presentationScoreSum) {
        // return 0 if grading scale is not set for graded presentations
        if (gradingScale == null) {
            return 0.0;
        }

        // return 0 if the presentationScoreSum is 0
        if (presentationScoreSum <= 0.0) {
            return 0.0;
        }

        // return 0 if the grading scale is not configured for graded presentations
        int presentationsNumber = gradingScale.getPresentationsNumber() == null ? 0 : gradingScale.getPresentationsNumber();
        Course course = gradingScale.getCourse();
        if (presentationsNumber <= 0 || course == null) {
            return 0.0;
        }

        double presentationPointAvg = presentationScoreSum / presentationsNumber;
        double presentationPoints = reachablePresentationPoints * presentationPointAvg / 100.0;

        return roundScoreSpecifiedByCourseSettings(presentationPoints, course);
    }

    /**
     * Calculates the reachable presentation points for a course.
     *
     * @param gradingScale        the grading scale for which the reachable presentation points should be calculated
     * @param baseReachablePoints the maximum points that can be received in the course without presentation points
     */
    public double calculateReachablePresentationPoints(GradingScale gradingScale, double baseReachablePoints) {
        // return 0 if reachable points are 0
        if (baseReachablePoints <= 0.0) {
            return 0.0;
        }

        // return 0 if grading scale is not set for graded presentations
        if (gradingScale == null) {
            return 0.0;
        }

        // return 0 if the grading scale is not configured for graded presentations
        double presentationsWeight = gradingScale.getPresentationsWeight() == null ? 0.0 : gradingScale.getPresentationsWeight();
        Course course = gradingScale.getCourse();
        if (presentationsWeight <= 0.0 || course == null) {
            return 0.0;
        }

        double reachablePointsWithPresentation = -baseReachablePoints / (presentationsWeight - 100.0) * 100.0;
        double reachablePresentationPoints = reachablePointsWithPresentation * presentationsWeight / 100.0;

        return roundScoreSpecifiedByCourseSettings(reachablePresentationPoints, course);
    }

    public Map<Long, Double> mapCourseIdToReachablePresentationPoints(Map<GradingScale, Double> gradingScaleToBaseReachablePoints) {
        Map<Long, Double> courseIdToReachablePresentationPoints = new HashMap<>();

        for (var entry : gradingScaleToBaseReachablePoints.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            GradingScale gradingScale = entry.getKey();
            double baseReachablePoints = entry.getValue();

            if (gradingScale.getCourse() == null) {
                continue;
            }

            double reachablePresentationPoints = calculateReachablePresentationPoints(gradingScale, baseReachablePoints);
            courseIdToReachablePresentationPoints.put(gradingScale.getCourse().getId(), reachablePresentationPoints);
        }

        return courseIdToReachablePresentationPoints;
    }

    public Map<Long, Double> mapCourseIdToPresentationPointsGivenStudentId(Map<GradingScale, Double> gradingScaleToBaseReachablePoints) {
        Map<Long, Double> courseIdToReachablePresentationPoints = new HashMap<>();

        for (var entry : gradingScaleToBaseReachablePoints.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            GradingScale gradingScale = entry.getKey();
            double baseReachablePoints = entry.getValue();

            if (gradingScale.getCourse() == null) {
                continue;
            }

            double reachablePresentationPoints = calculateReachablePresentationPoints(gradingScale, baseReachablePoints);
            courseIdToReachablePresentationPoints.put(gradingScale.getCourse().getId(), reachablePresentationPoints);
        }

        return courseIdToReachablePresentationPoints;
    }
}
