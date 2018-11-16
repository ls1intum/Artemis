package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


/**
 * Spring Data JPA repository for the Result entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {

    List<Result> findByParticipationIdOrderByCompletionDateDesc(Long participationId);

    List<Result> findByParticipationIdAndRatedOrderByCompletionDateDesc(Long participationId, boolean rated);

    List<Result> findByParticipationExerciseIdOrderByCompletionDateAsc(Long exerciseId);

    @Query("select r from Result r where r.completionDate = (select max(rr.completionDate) from Result rr where rr.participation.exercise.id = :exerciseId and rr.participation.student.id = r.participation.student.id) and r.participation.exercise.id = :exerciseId order by r.completionDate asc")
    List<Result> findLatestResultsForExercise(@Param("exerciseId") Long exerciseId);

    /*
    String sql = "select alldata.id, alldata.first_name, alldata.last_name, alldata.login, alldata.email, alldata.studentID,alldata.exerciseTitle, alldata.exerciseID, alldata.score,alldata.max_score, alldata.completion_date, alldata.due_date, alldata.discriminator, alldata.rated from (select studentID ,exerciseID, max(completion_date) as max_date
      from (select alldata.id, alldata.first_name, alldata.last_name, alldata.login, alldata.email, alldata.studentID,alldata.exerciseTitle, alldata.exerciseID, alldata.score,alldata.max_score, alldata.completion_date, alldata.due_date, alldata.discriminator, alldata.rated,alldata.jhi_successful from (select studentID ,exerciseID, max(completion_date) as max_date
from (SELECT
        `p`.`id` AS `id`,
        `j`.`id` AS `studentID`,
        `j`.`first_name` AS `first_name`,
        `j`.`last_name` AS `last_name`,
        `j`.`login` AS `login`,
        `j`.`email` AS `email`,
        `e`.`title` AS `exerciseTitle`,
        `e`.`due_date` AS `due_date`,
        `e`.`discriminator` AS `discriminator`,
        `e`.`id` AS `exerciseID`,
        `e`.`max_score` AS `max_score`,
        `r`.`completion_date` AS `completion_date`,
        `r`.`score` AS `score`,
        `r`.`jhi_successful`,
        `r`.`rated` AS `rated`
    FROM
        (((`result` `r`
        JOIN `exercise` `e`)
        JOIN `participation` `p`)
        JOIN `jhi_user` `j`)
    WHERE
        ((`r`.`participation_id` = `p`.`id`)
            AND (`p`.`exercise_id` = `e`.`id`)
            AND (`j`.`id` = `p`.`student_id`)
            AND (`e`.`course_id` = 18)
            AND (((`r`.`rated` = 1)
            AND ISNULL(`e`.`due_date`))
            OR (ISNULL(`r`.`rated`)
            AND (`r`.`completion_date` <= `e`.`due_date`))))) as alldata group by studentID, exerciseID) as t1 inner join (SELECT
        `p`.`id` AS `id`,
        `j`.`id` AS `studentID`,
        `j`.`first_name` AS `first_name`,
        `j`.`last_name` AS `last_name`,
        `j`.`login` AS `login`,
        `j`.`email` AS `email`,
        `e`.`title` AS `exerciseTitle`,
        `e`.`due_date` AS `due_date`,
        `e`.`discriminator` AS `discriminator`,
        `e`.`id` AS `exerciseID`,
        `e`.`max_score` AS `max_score`,
        `r`.`completion_date` AS `completion_date`,
        `r`.`score` AS `score`,
        `r`.`jhi_successful`,
        `r`.`rated` AS `rated`
    FROM
        (((`result` `r`
        JOIN `exercise` `e`)
        JOIN `participation` `p`)
        JOIN `jhi_user` `j`)
    WHERE
        ((`r`.`participation_id` = `p`.`id`)
            AND (`p`.`exercise_id` = `e`.`id`)
            AND (`j`.`id` = `p`.`student_id`)
            AND (`e`.`course_id` = 18)
            AND (((`r`.`rated` = 1)
            AND ISNULL(`e`.`due_date`))
            OR (ISNULL(`r`.`rated`)
            AND (`r`.`completion_date` <= `e`.`due_date`))))) as alldata on alldata.exerciseID = t1.exerciseID and alldata.completion_date = t1.max_date and alldata.studentID = t1.studentID order by studentID, exerciseID desc limit 20000")

            Query query = em.createNativeQuery(sql, User.class);
            query.setParameter(1,id);
            User user = (User) query.getSingleResult();

    */

    //native query to get data from backend. All sorts of exercises by courseID will be returned.
    //@Query exerciseQuery = em.createNativeQuery("SELECT title, id ,max_score, discriminator, due_date FROM artemis.exercise group by title order by id asc");
    //List<Object[]> exercises = exerciseQuery.getResultList();

    //@Query("select r from Result r where r.completionDate = (select min(rr.completionDate) from Result rr where rr.participation.exercise.id = :exerciseId and rr.participation.student.id = r.participation.student.id and rr.successful = true) and r.participation.exercise.id = :exerciseId and r.successful = true order by r.completionDate asc")
    //List<Result> findEarliestSuccessfulResultsForExercise(@Param("exerciseId") Long exerciseId);

    @Query("select r from Result r where r.participation.exercise.course.id = :courseId order by r.completionDate asc")
    List<Result> findAllResultsForCourse(@Param("courseId") Long courseId);

    /*
     * Custom query that counts the number of results for each participation of a particular exercise
     * @param exerciseId id of exercise for which the number of results in participations is aggregated
     * @return list of object arrays, where each object array contains two Long values, participation id (index 0) and
     * number of results for this participation (index 1)
     */
    @Query("select participation.id, count(id) from Result where participation.exercise.id = :exerciseId group by participation.id")
    List<Object[]> findSubmissionCountsForStudents(@Param("exerciseId") Long exerciseId);

    List<Result> findByParticipationExerciseIdAndSuccessfulOrderByCompletionDateAsc(Long exerciseId, boolean successful);

    Optional<Result> findFirstByParticipationIdOrderByCompletionDateDesc(Long participationId);

    Optional<Result> findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(Long participationId, boolean rated);

    Optional<Result> findDistinctBySubmissionId(Long submissionId);

    /**
     * This SQL query is used for inserting results if only one unrated result should exist per participation.
     * This prevents multiple (concurrent) inserts with the same participation_id and rated = 0.
     * It is used in ModelingSubmissionService.save(). It is needed because when saving a modeling submission
     * the first time, the create REST call could be triggered multiple times and due to threading, multiple
     * results with the same participationId and rated = 0 were inserted.
     * This query ensures thread safety for inserting unrated results.
     *
     * The query uses the logic of INSERT INTO table1 (column1) SELECT col1 FROM table2 to insert multiple rows from a table.
     * Because in this case we do not want to insert existing data, but rather specific data,the part SELECT :participationId, 0
     * specifies the fixed values. The specified values are only inserted, if there is no result with the same participationId
     * and rated = 0. The table dual is a dummy table name because we use specific values instead of values from a table.
     *
     * The parameter nativeQuery is needed because the query is not a JPQL query string, but a native SQL string.
     *
     * Query taken from https://stackoverflow.com/a/913929.
     *
     * @param participationId   the participation id for which the result should be inserted
     */
    @Modifying
    @Query(value = "insert into result (participation_id, rated) select :participationId, 0 from dual where not exists (select * from result where participation_id = :participationId and rated = 0)", nativeQuery = true)
    void insertIfNonExisting(@Param("participationId") Long participationId);
}
