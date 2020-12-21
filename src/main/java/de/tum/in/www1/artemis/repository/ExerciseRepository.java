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
            WITH chosen_exercises(exercise_id, course_id) AS (
                SELECT DISTINCT e.id, e.course_id
                FROM exercise e
                WHERE e.id IN :exerciseIds
            ),
                 students_in_course(student_id) -- finds id of students in exercise course that are neither TA nor instructors in the course
                     AS
                     (
                         SELECT DISTINCT ug.user_id
                         FROM user_groups ug,
                              course c
                         WHERE c.id = (
                             SELECT DISTINCT e.course_id
                             FROM chosen_exercises e
                         )
                           AND ug.`groups` = c.student_group_name
                           AND ug.user_id NOT IN ( -- user should not be teaching assistant in course
                             SELECT ug2.user_id
                             FROM user_groups ug2
                             WHERE ug2.`groups` = c.teaching_assistant_group_name
                         )
                           AND ug.user_id NOT IN ( -- user should not be instructor in course
                             SELECT ug3.user_id
                             FROM user_groups ug3
                             WHERE ug3.`groups` = c.instructor_group_name
                         )
                     ),
                 teams_of_course(team_id) AS
                     (SELECT DISTINCT ts.team_id
                      FROM team_student ts
                      WHERE ts.student_id IN (
                          SELECT s.student_id
                          FROM students_in_course s
                      )
                     ),
                 last_score_of_participating_student_or_team(exercise_id, student_id, team_id, participant_score)
                     AS (
                     SELECT DISTINCT e.exercise_id, p.student_id, p.team_id, r.score
                     FROM chosen_exercises e
                              JOIN participation p ON e.exercise_id = p.exercise_id
                              JOIN submission s ON p.id = s.participation_id
                              JOIN result r ON s.id = r.submission_id
                     WHERE (
                             p.team_id IN (SELECT tc.team_id FROM teams_of_course tc) -- participation has to be either from a student of the course or from a team
                             OR
                             p.student_id IN (SELECT sc.student_id FROM students_in_course sc)
                         )
                       AND r.score IS NOT NULL
                       AND (IF(:onlyConsiderRatedResults, r.rated = 1, TRUE))
                       AND NOT EXISTS( -- only consider the last submission (the one with the highest id)
                             SELECT *
                             FROM submission s2,
                                  result r2
                             WHERE s2.participation_id = s.participation_id
                               AND r2.submission_id = s2.id
                               AND r2.score IS NOT NULL
                               AND (IF(:onlyConsiderRatedResults, r2.rated = 1, TRUE))
                               AND s2.id > s.id
                         )
                 ),
                 last_score_of_all_chosen_exercises(exercise_id, exercise_title, exercise_mode, exercise_max_points, student_id, team_id, participant_score)
                     AS (
                     SELECT e.id, e.title, e.mode, e.max_score, ls.student_id, ls.team_id, ls.participant_score
                     FROM exercise e
                              LEFT JOIN last_score_of_participating_student_or_team ls ON e.id = ls.exercise_id
                     WHERE e.id IN (
                         SELECT ce.exercise_id
                         FROM chosen_exercises ce
                     )
                 )
            SELECT exercise_id,
                   exercise_title,
                   exercise_mode,
                   exercise_max_points,
                   avg(participant_score) as average_score,
                   (
                       IF(exercise_mode = 'INDIVIDUAL', count(DISTINCT student_id), count(DISTINCT team_id))
                       )                  AS no_of_participating_students_or_teams,
                   (
                       SELECT count(*)
                       FROM students_in_course sc
                   )                      AS no_students_in_course,
                   (SELECT count(*)
                    FROM teams_of_course tc
                   )                      AS no_teams_in_course,
                   (
                       IF(exercise_mode = 'INDIVIDUAL',
                          ROUND(CAST(count(DISTINCT student_id) AS FLOAT) / (SELECT CAST(count(*) AS FLOAT) from students_in_course), 4) * 100.0,
                          ROUND(CAST(count(DISTINCT team_id) AS FLOAT) / (SELECT CAST(count(*) AS FLOAT) from teams_of_course), 4) * 100.0)
                       )                  AS participation_rate
            FROM last_score_of_all_chosen_exercises ls
            GROUP BY exercise_id, exercise_title, exercise_mode, exercise_max_points
                    """, nativeQuery = true)
    List<Object[]> calculateExerciseStatisticsForCourseExercise(@Param("exerciseIds") List<Long> exerciseIds, @Param("onlyConsiderRatedResults") boolean onlyConsiderRatedResults);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.student", "studentParticipations.submissions" })
    Optional<Exercise> findWithEagerStudentParticipationsStudentAndSubmissionsById(Long exerciseId);
}
