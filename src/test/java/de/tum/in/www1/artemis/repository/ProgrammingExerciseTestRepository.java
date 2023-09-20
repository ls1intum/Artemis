package de.tum.in.www1.artemis.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

@Repository
public interface ProgrammingExerciseTestRepository extends JpaRepository<ProgrammingExercise, Long> {

    @Query("""
            select p from ProgrammingExercise p
            left join fetch p.studentParticipations
            left join fetch p.attachments
            left join fetch p.categories
            left join fetch p.templateParticipation
            left join fetch p.solutionParticipation
            left join fetch p.exampleSubmissions
            left join fetch p.exerciseHints eh
            left join fetch eh.solutionEntries
            left join fetch p.tutorParticipations
            left join fetch p.posts
            left join fetch p.testCases tc
            left join fetch tc.solutionEntries
            left join fetch p.staticCodeAnalysisCategories
            left join fetch p.auxiliaryRepositories
            left join fetch p.tasks t
            left join fetch t.testCases
            left join fetch t.exerciseHints
            where p.id = :#{#exerciseId}
            """)
    ProgrammingExercise findOneWithEagerEverything(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT pe FROM ProgrammingExercise pe LEFT JOIN FETCH pe.templateParticipation LEFT JOIN FETCH pe.solutionParticipation")
    List<ProgrammingExercise> findAllWithEagerTemplateAndSolutionParticipations();

    /**
     * Returns the programming exercises that have a buildAndTestStudentSubmissionsAfterDueDate higher than the provided date.
     * This can't be done as a standard query as the property contains the word 'And'.
     *
     * @param dateTime ZonedDatetime object.
     * @return List<ProgrammingExercise> (can be empty)
     */
    @Query("SELECT pe FROM ProgrammingExercise pe WHERE pe.buildAndTestStudentSubmissionsAfterDueDate > :#{#dateTime}")
    List<ProgrammingExercise> findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

    /**
     * Returns the list of programming exercises with a buildAndTestStudentSubmissionsAfterDueDate in the future.
     *
     * @return List<ProgrammingExercise>
     */
    default List<ProgrammingExercise> findAllWithBuildAndTestAfterDueDateInFuture() {
        return findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(ZonedDateTime.now());
    }
}
