package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
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

    @Query("select distinct c from Exercise e join e.categories c where e.id = :#{#exerciseId}")
    Set<String> findAllCategories(@Param("exerciseId") Long exerciseId);

    /**
     * calculates the average score and the participation rate of students for each given individual course exercise
     * by using the last result (rated or not)
     * @param exerciseIds - exercise ids to count the statistics for
     * @return <code>Object[]</code> where each index corresponds to the column from the db (0 refers to exerciseId and so on)
     */
    @Query("""
            SELECT
            e.id,
            AVG(r.score),
            Count(Distinct p.student.id),
            (SELECT count(distinct u.id)
            FROM User u
            WHERE
            e.course.studentGroupName member of u.groups
            AND e.course.teachingAssistantGroupName not member of u.groups
            AND e.course.instructorGroupName not member of u.groups
            )
            FROM Exercise e JOIN e.studentParticipations p JOIN p.submissions s JOIN s.results r
            WHERE e.id IN :exerciseIds
            AND e.course.studentGroupName member of p.student.groups
            AND e.course.teachingAssistantGroupName not member of p.student.groups
            AND e.course.instructorGroupName not member of p.student.groups
            AND r.score IS NOT NULL
            AND
            s.id = (
                SELECT max(s2.id)
                FROM Submission s2 JOIN s2.results r2
                WHERE s2.participation.id = s.participation.id
                AND r2.score IS NOT NULL
                )
            GROUP BY e.id
            """)
    List<Object[]> calculateExerciseStatisticsForIndividualCourseExercises(@Param("exerciseIds") List<Long> exerciseIds);

    /**
     * calculates the average score and the participation rate of students for each given team course exercise
     * by using the last result (rated or not)
     * @param exerciseIds - exercise ids to count the statistics for
     * @return <code>Object[]</code> where each index corresponds to the column from the db (0 refers to exerciseId and so on)
     */
    @Query("""
            SELECT
            e.id,
            AVG(r.score),
            Count(Distinct p.team.id),
            (SELECT count(distinct t.id)
             FROM Team t JOIN t.students st2
             WHERE st2.id IN (
                 SELECT DISTINCT u.id
                FROM User u
                WHERE
                e.course.studentGroupName member of u.groups
                AND e.course.teachingAssistantGroupName not member of u.groups
                AND e.course.instructorGroupName not member of u.groups
             )
            )
            FROM Exercise e JOIN e.studentParticipations p JOIN p.submissions s JOIN s.results r JOIN p.team.students st
            WHERE e.id IN :exerciseIds
            AND r.score IS NOT NULL
            AND
            st.id IN (
                 SELECT DISTINCT u.id
                FROM User u
                WHERE
                e.course.studentGroupName member of u.groups
                AND e.course.teachingAssistantGroupName not member of u.groups
                AND e.course.instructorGroupName not member of u.groups
             )
             AND
            s.id = (
                SELECT max(s2.id)
                FROM Submission s2 JOIN s2.results r2
                WHERE s2.participation.id = s.participation.id
                AND r2.score IS NOT NULL
                )
            GROUP BY e.id
            """)
    List<Object[]> calculateExerciseStatisticsForTeamCourseExercises(@Param("exerciseIds") List<Long> exerciseIds);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.student", "studentParticipations.submissions" })
    Optional<Exercise> findWithEagerStudentParticipationsStudentAndSubmissionsById(Long exerciseId);

    /**
     * Fetches the details of the exercises for a course
     *
     * @param courseId - course to get the statistics for
     * @return <code>Object[]</code> where each index corresponds to the column from the db (0 refers to the exercise and so on)
     */
    @Query("""
            SELECT
            e.id as id,
            e.title as title,
            TYPE(e) as type,
            e.releaseDate as releaseDate,
            e.dueDate as dueDate,
            e.assessmentDueDate as assessmentDueDate,
            e.mode as mode
            FROM Exercise e
            WHERE e.course.id = :courseId
            """)
    List<Map<String, Object>> getExercisesForCourseManagementOverview(@Param("courseId") Long courseId);

    /**
     * Fetches the statistics of the exercises for a course
     *
     * @param courseId - course to get the statistics for
     * @return <code>Object[]</code> where each index corresponds to the column from the db (0 refers to the exercise and so on)
     */
    @Query("""
            SELECT
            e.id as id,
            e.maxPoints as maxPoints,
            e.mode as mode,
            (SELECT AVG(r.score)
            FROM e.studentParticipations p JOIN p.submissions s JOIN s.results r
            WHERE e.course.id = :courseId
                AND e.course.studentGroupName member of p.student.groups
                AND s.id = (
                    SELECT max(s2.id)
                    FROM Submission s2 JOIN s2.results r2
                    WHERE s2.participation.id = s.participation.id
                    AND r2.score IS NOT NULL
                )
            GROUP BY e.id
            ) as averageScore,
            (SELECT COUNT(DISTINCT p.student.id)
            FROM e.studentParticipations p JOIN p.submissions s
            WHERE e.course.id = :courseId AND e.course.studentGroupName member of p.student.groups
            GROUP BY e.id
            ) as participations
            FROM Exercise e
            WHERE e.course.id = :courseId
            """)
    List<Map<String, Object>> getStatisticsForCourseManagementOverview(@Param("courseId") Long courseId);
}
