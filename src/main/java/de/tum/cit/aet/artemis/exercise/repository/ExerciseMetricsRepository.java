package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map.Entry;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.ExerciseInformationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.MapEntryLongLong;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.ResourceTimestampDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.ScoreDTO;

/**
 * Spring Data JPA repository to fetch exercise related metrics.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ExerciseMetricsRepository extends ArtemisJpaRepository<Exercise, Long> {

    /**
     * Get the exercise information for all exercises in a course.
     *
     * @param courseId the id of the course
     * @return the exercise information for all exercises in the course
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.web.rest.dto.metrics.ExerciseInformationDTO(
                e.id,
                e.shortName,
                e.title,
                COALESCE(e.startDate, e.releaseDate),
                e.dueDate,
                e.maxPoints,
                e.includedInOverallScore,
                e.difficulty,
                e.mode,
                e.class,
                CASE WHEN TYPE(e) = ProgrammingExercise THEN TREAT(e AS ProgrammingExercise).allowOnlineEditor ELSE NULL END,
                CASE WHEN TYPE(e) = ProgrammingExercise THEN TREAT(e AS ProgrammingExercise).allowOfflineIde ELSE NULL END
            )
            FROM Exercise e
            WHERE e.course.id = :courseId
            """)
    Set<ExerciseInformationDTO> findAllExerciseInformationByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT e.id AS key, c AS value
            FROM Exercise e
                JOIN e.categories c
            WHERE e.id IN :exerciseIds
            """)
    Set<Entry<Long, String>> findCategoriesByExerciseIds(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * Get the average score for a set of exercises.
     *
     * @param exerciseIds the ids of the exercises
     * @return the average score for each exercise
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.web.rest.dto.metrics.ScoreDTO(p.exercise.id, AVG(COALESCE(p.lastRatedScore, 0)))
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
            SELECT new de.tum.cit.aet.artemis.web.rest.dto.metrics.ScoreDTO(s.exercise.id, CAST(COALESCE(s.lastRatedScore, 0) AS DOUBLE))
            FROM StudentScore s
            WHERE s.exercise.id IN :exerciseIds
                AND s.user.id = :userId
            """)
    Set<ScoreDTO> findScore(@Param("exerciseIds") Set<Long> exerciseIds, @Param("userId") long userId);

    /**
     * Get the latest submission dates for a user in a set of exercises.
     * <p>
     * This query fetches the latest submission dates for the specified user in the given set of exercises.
     * It takes into account both individual and team participations.
     *
     * @param exerciseIds the ids of the exercises for which to fetch the latest submission dates
     * @param userId      the id of the user whose submission dates are being fetched
     * @return a set of ResourceTimestampDTO objects containing the exercise id and the latest submission date for the user
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.web.rest.dto.metrics.ResourceTimestampDTO(e.id, MAX(s.submissionDate)), p.id
            FROM Submission s
                LEFT JOIN StudentParticipation p ON s.participation.id = p.id
                LEFT JOIN p.exercise e
                LEFT JOIN p.team t
                LEFT JOIN t.students u
            WHERE e.id IN :exerciseIds
                AND s.submitted = TRUE
                AND (p.student.id = :userId OR u.id = :userId)
                AND p.testRun = FALSE
            GROUP BY e.id, p.id
            """)
    Set<ResourceTimestampDTO> findLatestSubmissionDatesForUser(@Param("exerciseIds") Set<Long> exerciseIds, @Param("userId") long userId);

    /**
     * Get the latest submission dates for a set of exercises.
     * <p>
     * This query fetches all latest submission dates for the specified set of exercises.
     * It considers all participations related to the exercises.
     *
     * @param exerciseIds the ids of the exercises for which to fetch the latest submission dates
     * @return a set of ResourceTimestampDTO objects containing the exercise id and the latest submission date for each exercise
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.web.rest.dto.metrics.ResourceTimestampDTO(e.id, MAX(s.submissionDate)), p.id
            FROM Submission s
                LEFT JOIN Participation p ON s.participation.id = p.id
                LEFT JOIN p.exercise e
            WHERE e.id IN :exerciseIds
                AND s.submitted = TRUE
                AND p.testRun = FALSE
            GROUP BY e.id, p.id
            """)
    Set<ResourceTimestampDTO> findLatestSubmissionDates(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * Get the ids of the completed exercises for a user in a set of exercises.
     *
     * @param userId      the id of the user
     * @param exerciseIds the ids of the exercises
     * @param minScore    the minimum score required to consider an exercise as completed, normally {@link Constants#MIN_SCORE_GREEN }
     * @return the ids of the completed exercises for the user in the exercises
     */
    @Query("""
            SELECT e.id
            FROM ParticipantScore p
                LEFT JOIN p.exercise e
                LEFT JOIN TREAT (p AS StudentScore).user u
                LEFT JOIN TREAT (p AS TeamScore).team.students s
            WHERE (u.id = :userId OR s.id = :userId)
                AND p.exercise.id IN :exerciseIds
                AND COALESCE(p.lastRatedScore, p.lastScore, 0) >= :minScore
            """)
    Set<Long> findAllCompletedExerciseIdsForUserByExerciseIds(@Param("userId") long userId, @Param("exerciseIds") Set<Long> exerciseIds, @Param("minScore") double minScore);

    /**
     * Get the ids of the teams the user is in for a set of exercises.
     *
     * @param userId      the id of the user
     * @param exerciseIds the ids of the exercises
     * @return MapEntryDTO with exercise id and team id
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.web.rest.dto.metrics.MapEntryLongLong(e.id, t.id)
            FROM Exercise e
                LEFT JOIN e.teams t
                LEFT JOIN t.students u
            WHERE e.mode = de.tum.cit.aet.artemis.exercise.domain.ExerciseMode.TEAM
                AND e.id IN :exerciseIds
                AND u.id = :userId
            """)
    Set<MapEntryLongLong> findTeamIdsForUserByExerciseIds(@Param("userId") long userId, @Param("exerciseIds") Set<Long> exerciseIds);
}
