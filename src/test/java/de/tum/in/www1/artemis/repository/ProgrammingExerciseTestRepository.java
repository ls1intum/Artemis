package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

@Repository
public interface ProgrammingExerciseTestRepository extends JpaRepository<ProgrammingExercise, Long> {

    @Query("""
            SELECT p
            FROM ProgrammingExercise p
                LEFT JOIN FETCH p.studentParticipations
                LEFT JOIN FETCH p.attachments
                LEFT JOIN FETCH p.categories
                LEFT JOIN FETCH p.templateParticipation
                LEFT JOIN FETCH p.solutionParticipation
                LEFT JOIN FETCH p.exampleSubmissions
                LEFT JOIN FETCH p.exerciseHints eh
                LEFT JOIN FETCH eh.solutionEntries
                LEFT JOIN FETCH p.tutorParticipations
                LEFT JOIN FETCH p.posts
                LEFT JOIN FETCH p.testCases tc
                LEFT JOIN FETCH tc.solutionEntries
                LEFT JOIN FETCH p.staticCodeAnalysisCategories
                LEFT JOIN FETCH p.auxiliaryRepositories
                LEFT JOIN FETCH p.tasks t
                LEFT JOIN FETCH t.testCases
                LEFT JOIN FETCH t.exerciseHints
                LEFT JOIN FETCH p.plagiarismDetectionConfig
            WHERE p.id = :exerciseId
            """)
    ProgrammingExercise findOneWithEagerEverything(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.templateParticipation
                LEFT JOIN FETCH pe.solutionParticipation
            """)
    List<ProgrammingExercise> findAllWithEagerTemplateAndSolutionParticipations();

    /**
     * Returns the programming exercises that have a buildAndTestStudentSubmissionsAfterDueDate higher than the provided date.
     * This can't be done as a standard query as the property contains the word 'And'.
     *
     * @param dateTime ZonedDatetime object.
     * @return List<ProgrammingExercise> (can be empty)
     */
    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
            WHERE pe.buildAndTestStudentSubmissionsAfterDueDate > :dateTime
            """)
    List<ProgrammingExercise> findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

    /**
     * Returns the list of programming exercises with a buildAndTestStudentSubmissionsAfterDueDate in the future.
     *
     * @return List<ProgrammingExercise>
     */
    default List<ProgrammingExercise> findAllWithBuildAndTestAfterDueDateInFuture() {
        return findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(ZonedDateTime.now());
    }

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation" })
    List<ProgrammingExercise> findAllWithTemplateAndSolutionParticipationByIdIn(Set<Long> exerciseIds);
}
