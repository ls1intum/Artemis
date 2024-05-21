package de.tum.in.www1.artemis.repository.metrics;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ExerciseInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ResourceTimestampDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ScoreDTO;

/**
 * Spring Data JPA repository to fetch exercise related metrics.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ExerciseMetricsRepository extends JpaRepository<Exercise, Long> {

    /**
     * Get the exercise information for all exercises in a course.
     *
     * @param courseId the id of the course
     * @return the exercise information for all exercises in the course
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.ExerciseInformationDTO(e.id, e.shortName, e.title, COALESCE(e.startDate, e.releaseDate), e.dueDate, e.maxPoints, e.difficulty, e.class)
            FROM Exercise e
            WHERE e.course.id = :courseId
            """)
    Set<ExerciseInformationDTO> findAllExerciseInformationByCourseId(@Param("courseId") long courseId);

    /**
     * Get the average score for a set of exercises.
     *
     * @param exerciseIds the ids of the exercises
     * @return the average score for each exercise
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.ScoreDTO(p.exercise.id, AVG(COALESCE(p.lastScore, 0)))
            FROM ParticipantScore p
            WHERE p.exercise.id IN :exerciseIds
            GROUP BY p.exercise.id
            """)
    Set<ScoreDTO> findAverageScore(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * Get the score for a user in a set of exercises.
     *
     * @param exerciseIds the ids of the exercises
     * @param userId      the id of the user
     * @return the score for the user in each exercise
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.ScoreDTO(s.exercise.id, CAST(COALESCE(s.lastRatedScore, s.lastScore, 0) AS DOUBLE))
            FROM StudentScore s
            WHERE s.exercise.id IN :exerciseIds
                AND s.user.id = :userId
            """)
    Set<ScoreDTO> findScore(@Param("exerciseIds") Set<Long> exerciseIds, @Param("userId") long userId);

    /**
     * Get the latest submission dates for a user in a set of exercises.
     *
     * @param exerciseIds the ids of the exercises
     * @param userId      the id of the user
     * @return the latest submission dates for the user in the exercises
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.ResourceTimestampDTO(e.id, s.submissionDate)
            FROM Submission s
                LEFT JOIN StudentParticipation p ON s.participation.id = p.id
                LEFT JOIN p.exercise e
                LEFT JOIN p.team t
                LEFT JOIN t.students u
            WHERE e.id IN :exerciseIds
                AND s.submissionDate = (
                    SELECT MAX(s2.submissionDate)
                    FROM Submission s2
                    WHERE s2.participation.id = s.participation.id
                        AND s2.submitted = TRUE
                )
                AND (p.student.id = :userId OR u.id = :userId)
            """)
    Set<ResourceTimestampDTO> findLatestSubmissionDatesForUser(@Param("exerciseIds") Set<Long> exerciseIds, @Param("userId") long userId);

    /**
     * Get the latest submission dates for a set of exercises.
     *
     * @param exerciseIds the ids of the exercises
     * @return the latest submission dates for the exercises
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.ResourceTimestampDTO(e.id, s.submissionDate)
            FROM Submission s
                LEFT JOIN StudentParticipation p ON s.participation.id = p.id
                LEFT JOIN p.exercise e
            WHERE e.id IN :exerciseIds
                AND s.submissionDate = (
                    SELECT MAX(s2.submissionDate)
                    FROM Submission s2
                    WHERE s2.participation.id = s.participation.id
                        AND s2.submitted = TRUE
                )
            """)
    Set<ResourceTimestampDTO> findLatestSubmissionDates(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * Get the ids of the completed exercises for a user in a set of exercises.
     *
     * @param userId      the id of the user
     * @param exerciseIds the ids of the exercises
     * @return the ids of the completed exercises for the user in the exercises
     */
    @Query("""
            SELECT e.id
            FROM Exercise e
            LEFT JOIN e.studentParticipations p
            LEFT JOIN e.teams t
            LEFT JOIN t.students u
            WHERE e.id IN :exerciseIds
                AND (p.student.id = :userId OR u.id = :userId)
            """)
    Set<Long> findAllCompletedExerciseIdsForUserByExerciseIds(@Param("userId") long userId, @Param("exerciseIds") Set<Long> exerciseIds);
}
