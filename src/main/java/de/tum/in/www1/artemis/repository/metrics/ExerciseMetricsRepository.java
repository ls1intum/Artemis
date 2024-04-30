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
import de.tum.in.www1.artemis.web.rest.dto.metrics.SubmissionTimestampDTO;

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
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.ExerciseInformationDTO(e.id, e.shortName, e.startDate, e.dueDate)
            FROM Exercise e
            WHERE e.course.id = :courseId
            """)
    Set<ExerciseInformationDTO> findAllExerciseInformationByCourseId(long courseId);

    /**
     * Get the latest submissions for a user in a set of exercises.
     *
     * @param exerciseIds the ids of the exercises
     * @param userId      the id of the user
     * @return the latest submissions for the user in the exercises
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.ResourceTimestampDTO(e.id, s.submissionDate)
            FROM Submission s
                LEFT JOIN StudentParticipation p
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
    Set<ResourceTimestampDTO> findLatestSubmissionsForUser(@Param("exerciseIds") Set<Long> exerciseIds, @Param("userId") long userId);

    /**
     * Get the timestamps when the user started participating in the exercise.
     *
     * @param exerciseIds the ids of the exercises
     * @param userId      the id of the user
     * @return the start time of the exercises for the user
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.ResourceTimestampDTO(se.resourceId, se.timestamp)
            FROM  ScienceEvent se
            WHERE se.resourceId IN :exerciseIds
                AND se.timestamp = (
                    SELECT MIN(se2.timestamp)
                    FROM ScienceEvent se2
                        LEFT JOIN User u ON se.identity = u.login
                    WHERE se2.resourceId = se.resourceId
                        AND se2.type = de.tum.in.www1.artemis.domain.science.ScienceEventType.EXERCISE__OPEN
                        AND u.id = :userId
                )
            """)
    Set<ResourceTimestampDTO> findExerciseStartForUser(@Param("exerciseIds") Set<Long> exerciseIds, @Param("userId") long userId);

    /**
     * Get the submission timestamps for a user in a set of exercises.
     *
     * @param exerciseIds the ids of the exercises
     * @param userId      the id of the user
     * @return the submission timestamps for the user in the exercises
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.SubmissionTimestampDTO(s.id, s.submissionDate, r.score)
            FROM Submission s
                LEFT JOIN StudentParticipation p
                LEFT JOIN p.exercise e
                LEFT JOIN p.team t
                LEFT JOIN t.students u
                LEFT JOIN s.results r
            WHERE s.submitted = TRUE
                AND e.id IN :exerciseIds
                AND (p.student.id = :userId OR u.id = :userId)
                AND r.score = (
                    SELECT MAX(r2.score)
                    FROM Result r2
                    WHERE r2.submission.id = s.id
                )
            """)
    Set<SubmissionTimestampDTO> findSubmissionTimestampsForUser(@Param("exerciseIds") Set<Long> exerciseIds, @Param("userId") long userId);
}
