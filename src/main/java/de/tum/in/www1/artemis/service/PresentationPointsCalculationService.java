package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.web.rest.dto.ScoreDTO;

/**
 * Service for calculating the presentation points for a course or student.
 */
@Service
public class PresentationPointsCalculationService {

    private final StudentParticipationRepository studentParticipationRepository;

    public PresentationPointsCalculationService(StudentParticipationRepository studentParticipationRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
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
        // return 0 if grading scale is not set
        if (gradingScale == null) {
            return 0.0;
        }

        Course course = gradingScale.getCourse();
        if (course == null) {
            return 0.0;
        }

        double presentationPointSum = studentParticipationRepository.sumPresentationScoreByStudentIdAndCourseId(course.getId(), studentId);

        return calculatePresentationPoints(gradingScale, reachablePresentationPoints, presentationPointSum);
    }

    /**
     * Adds the presentation points to the ScoreDTOs given the gradingScale, the reachable presentation points of the
     * course, and the presentationsWeight of the courses GradingScale.
     *
     * @param gradingScale                the grading scale with the presentation configuration
     * @param scoreDTOS                   the ScoreDTOs to which the presentation points should be added
     * @param reachablePresentationPoints the reachable presentation points in the given course.
     */
    public void addPresentationPointsToScoreDTOs(GradingScale gradingScale, Collection<ScoreDTO> scoreDTOS, double reachablePresentationPoints) {
        // return if grading scale is not set
        if (gradingScale == null) {
            return;
        }

        Course course = gradingScale.getCourse();
        if (course == null) {
            return;
        }

        Set<Long> studentIds = scoreDTOS.stream().map(scoreDTO -> scoreDTO.studentId).collect(toSet());
        Map<Long, Double> studentIdToPresentationPointSum = studentParticipationRepository.mapStudentIdToPresentationScoreSumByCourseIdAndStudentIds(course.getId(), studentIds);

        scoreDTOS.forEach(scoreDTO -> {
            double presentationScoreSum = studentIdToPresentationPointSum.getOrDefault(scoreDTO.studentId, 0.0);
            double presentationPoints = calculatePresentationPoints(gradingScale, reachablePresentationPoints, presentationScoreSum);
            scoreDTO.pointsAchieved += presentationPoints;
        });
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
        // return 0 if grading scale is not set
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
     * @return the reachable presentation points for the course
     */
    public double calculateReachablePresentationPoints(GradingScale gradingScale, double baseReachablePoints) {
        // return 0 if reachable points are 0
        if (baseReachablePoints <= 0.0) {
            return 0.0;
        }

        // return 0 if grading scale is not set
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
}
