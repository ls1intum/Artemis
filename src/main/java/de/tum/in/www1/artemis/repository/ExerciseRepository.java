package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;

/**
 * Spring Data JPA repository for the Exercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    @Query("select e from Exercise e left join fetch e.categories where e.course.id = :#{#courseId}")
    Set<Exercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @Query("select e from Exercise e where e.course.id = :#{#courseId} and e.mode = 'TEAM'")
    Set<Exercise> findAllTeamExercisesByCourseId(@Param("courseId") Long courseId);

    @Query("select e from Exercise e where e.course.testCourse = false and e.dueDate >= :#{#now} order by e.dueDate asc")
    Set<Exercise> findAllExercisesWithUpcomingDueDate(@Param("now") ZonedDateTime now);

    /**
     * Select Exercise for Course ID WHERE there does exist an LtiOutcomeUrl for the current user (-> user has started exercise once using LTI)
     * @param courseId the id of the course
     * @param login the login of the corresponding user
     * @return list of exercises
     */
    @Query("select e from Exercise e where e.course.id = :#{#courseId} and exists (select l from LtiOutcomeUrl l where e = l.exercise and l.user.login = :#{#login})")
    Set<Exercise> findByCourseIdWhereLtiOutcomeUrlExists(@Param("courseId") Long courseId, @Param("login") String login);

    @Query("select distinct c from Exercise e join e.categories c where e.course.id = :#{#courseId}")
    Set<String> findAllCategoryNames(@Param("courseId") Long courseId);

    @Query("select distinct exercise from Exercise exercise left join fetch exercise.studentParticipations where exercise.id = :#{#exerciseId}")
    Optional<Exercise> findByIdWithEagerParticipations(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "categories", "teamAssignmentConfig" })
    Optional<Exercise> findWithEagerCategoriesAndTeamAssignmentConfigById(Long exerciseId);

    @Query("select distinct exercise from Exercise exercise left join fetch exercise.exampleSubmissions examplesub left join fetch examplesub.submission exsub left join fetch exsub.results where exercise.id = :#{#exerciseId}")
    Optional<Exercise> findByIdWithEagerExampleSubmissions(@Param("exerciseId") Long exerciseId);

    @Query("select distinct exercise from Exercise exercise left join fetch exercise.exerciseHints left join fetch exercise.studentQuestions left join fetch exercise.categories where exercise.id = :#{#exerciseId}")
    Optional<Exercise> findByIdWithDetailsForStudent(@Param("exerciseId") Long exerciseId);

    @Query(value = """
            SELECT EXERCISE_ID,
                   EXERCISE_TITLE,
                   EXERCISE_MODE,
                   EXERCISE_MAX_POINTS,
                   AVG(PARTICIPANT_SCORE) AS AVERAGE_SCORE,
                   (
                       CASE
                           WHEN EXERCISE_MODE = 'INDIVIDUAL' THEN COUNT(DISTINCT STUDENT_ID)
                           ELSE COUNT(DISTINCT TEAM_ID)
                           END
                       )                  AS NO_OF_PARTICIPATING_STUDENTS_OR_TEAMS,
                   (
                       SELECT COUNT(DISTINCT STUDENT_ID)
                       FROM VIEW_STUDENTS_OF_COURSE
                       WHERE COURSE_ID = (
                           SELECT DISTINCT COURSE_ID
                           FROM EXERCISE
                           WHERE ID IN :exerciseIds
                       )
                   )                      AS NO_STUDENTS_IN_COURSE,
                   (
                       SELECT COUNT(DISTINCT TEAM_ID)
                       FROM VIEW_TEAMS_OF_COURSE
                       WHERE COURSE_ID = (
                           SELECT DISTINCT COURSE_ID
                           FROM EXERCISE
                           WHERE ID IN :exerciseIds
                       )
                   )                      AS NO_TEAMS_IN_COURSE,
                   (
                       CASE
                           WHEN EXERCISE_MODE = 'INDIVIDUAL' THEN
                                   ROUND(CAST(COUNT(DISTINCT STUDENT_ID) AS FLOAT) / ( -- NUMBER OF PARTICIPATING STUDENT IN EXERCISE
                                   SELECT CAST(COUNT(DISTINCT STUDENT_ID) AS FLOAT) -- NUMBER OF TEAMS IN COURSE
                               FROM VIEW_STUDENTS_OF_COURSE
                               WHERE COURSE_ID = (
                                   SELECT DISTINCT COURSE_ID
                                   FROM EXERCISE
                                   WHERE ID IN :exerciseIds
                               )
                                   ), 4) * 100.0
                           ELSE ROUND(CAST(COUNT(DISTINCT TEAM_ID) AS FLOAT) / ( -- NUMBER OF PARTICIPATING TEAMS IN EXERCISE
                               SELECT CAST(COUNT(DISTINCT TEAM_ID) AS FLOAT) -- NUMBER OF TEAMS IN COURSE
                               FROM VIEW_TEAMS_OF_COURSE
                               WHERE COURSE_ID = (
                                   SELECT DISTINCT COURSE_ID
                                   FROM EXERCISE
                                   WHERE ID IN :exerciseIds
                               )
                           ), 4) * 100.0
                           END
                       )                  AS PARTICIPATION_RATE
            FROM (
                    SELECT ID AS EXERCISE_ID, TITLE AS EXERCISE_TITLE, MODE AS EXERCISE_MODE, MAX_SCORE AS EXERCISE_MAX_POINTS, LAST_RESULT.STUDENT_ID, LAST_RESULT.TEAM_ID, LAST_RESULT.PARTICIPANT_SCORE
                    FROM EXERCISE
                    LEFT JOIN (
                        SELECT DISTINCT E.EXERCISE_ID,
                                        P.STUDENT_ID,
                                        P.TEAM_ID,
                                        R.SCORE AS PARTICIPANT_SCORE
                        FROM (SELECT DISTINCT ID AS EXERCISE_ID
                              FROM EXERCISE
                              WHERE ID IN :exerciseIds) AS E
                                 JOIN PARTICIPATION P ON E.EXERCISE_ID = P.EXERCISE_ID
                                 JOIN SUBMISSION S ON P.ID = S.PARTICIPATION_ID
                                 JOIN RESULT R ON S.ID = R.SUBMISSION_ID
                        WHERE (
                                    P.TEAM_ID IN ( -- ONLY COUNT PARTICIPATIONS FROM TEAMS OF THE COURSE
                                    SELECT DISTINCT TEAM_ID
                                    FROM VIEW_TEAMS_OF_COURSE
                                    WHERE COURSE_ID = (
                                        SELECT DISTINCT COURSE_ID
                                        FROM EXERCISE
                                        WHERE ID IN :exerciseIds
                                        )
                                    )
                                OR
                                    P.STUDENT_ID IN (
                                        SELECT DISTINCT STUDENT_ID -- ONLY COUNT PARTICIPATIONS FROM STUDENTS OF THE COURSE
                                        FROM VIEW_STUDENTS_OF_COURSE
                                        WHERE COURSE_ID = (
                                            SELECT DISTINCT COURSE_ID
                                            FROM EXERCISE
                                            WHERE ID IN :exerciseIds
                                            )
                                        )
                            )
                          AND R.SCORE IS NOT NULL
                          AND (CASE
                                   WHEN :onlyConsiderRatedResults THEN R.RATED = 1 -- WE EITHER CARE ONLY ABOUT RATED RESULTS OR WE CARE FOR BOTH
                                   ELSE TRUE
                            END)
                          AND NOT EXISTS( -- ONLY CONSIDER THE LAST SUBMISSION (THE ONE WITH THE HIGHEST ID)
                                SELECT *
                                FROM SUBMISSION S2,
                                     RESULT R2
                                WHERE S2.PARTICIPATION_ID = S.PARTICIPATION_ID
                                  AND R2.SUBMISSION_ID = S2.ID
                                  AND R2.SCORE IS NOT NULL
                                  AND (CASE
                                           WHEN :onlyConsiderRatedResults THEN R2.RATED = 1 -- WE EITHER CARE ONLY ABOUT RATED RESULTS OR WE CARE FOR BOTH
                                           ELSE TRUE
                                    END)
                                  AND S2.ID > S.ID
                        )
                    ) AS LAST_RESULT ON LAST_RESULT.EXERCISE_ID = ID
                    WHERE ID IN :exerciseIds
                 ) AS LAST_RESULT_FOR_ALL_GIVEN_EXERCISES
            GROUP BY EXERCISE_ID, EXERCISE_TITLE, EXERCISE_MODE, EXERCISE_MAX_POINTS
            """, nativeQuery = true)
    List<Object[]> calculateExerciseStatisticsForCourseExercise(@Param("exerciseIds") List<Long> exerciseIds, @Param("onlyConsiderRatedResults") boolean onlyConsiderRatedResults);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.student", "studentParticipations.submissions" })
    Optional<Exercise> findWithEagerStudentParticipationsStudentAndSubmissionsById(Long exerciseId);
}
