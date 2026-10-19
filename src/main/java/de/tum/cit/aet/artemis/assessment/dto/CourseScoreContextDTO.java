package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The part of a course score calculation that is the same for every student: the settings, the exercises and the points
 * derivable from them.
 * <p>
 * It exists so that the per-student calculation cannot be handed a set of exercises and a max-points figure that were
 * derived from different exercises — the two are computed together and travel together. Building it once and reusing it
 * across students is also what keeps scoring a whole course cheap.
 *
 * @param settings              the course settings the calculation rounds and scores by
 * @param presentationConfig    the graded presentation configuration, or {@code null} when the course awards no presentation points
 * @param exercises             the exercises of the course, or of one exercise type when scores are calculated per type
 * @param calculationTime       the single instant used for every due-date decision in this calculation
 * @param maxAndReachablePoints the points derivable from {@code exercises}, {@code presentationConfig}, and
 *                                  {@code calculationTime}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseScoreContextDTO(CourseScoreSettingsDTO settings, @Nullable GradedPresentationConfigDTO presentationConfig, Set<ExerciseCourseScoreDTO> exercises,
        ZonedDateTime calculationTime, MaxAndReachablePointsDTO maxAndReachablePoints) {

    public CourseScoreContextDTO {
        exercises = Set.copyOf(exercises);
    }

    /**
     * Whether students can earn graded presentation points in this course.
     * <p>
     * Callers use this to decide whether the presentation score sum has to be fetched at all: it is an input to the
     * calculation, and fetching it for the overwhelmingly common course without graded presentations would be a query
     * per student for a value that is never read.
     *
     * @return true if the calculation will use the graded presentation score sum
     */
    public boolean usesGradedPresentations() {
        return presentationConfig != null && maxAndReachablePoints.reachablePresentationPoints() > 0.0;
    }
}
