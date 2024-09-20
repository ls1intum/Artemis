package de.tum.cit.aet.artemis.programming.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

@Repository
@Primary
public interface ProgrammingExerciseTestRepository extends ProgrammingExerciseRepository {

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
                LEFT JOIN FETCH p.buildConfig
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

    List<ProgrammingExercise> findAllByCourse_InstructorGroupNameIn(Set<String> groupNames);

    List<ProgrammingExercise> findAllByCourse_EditorGroupNameIn(Set<String> groupNames);

    List<ProgrammingExercise> findAllByCourse_TeachingAssistantGroupNameIn(Set<String> groupNames);

    @NotNull
    default ProgrammingExercise findByIdWithBuildConfigElseThrow(long programmingExerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithBuildConfigById(programmingExerciseId), programmingExerciseId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "templateParticipation", "solutionParticipation", "studentParticipations.team.students", "buildConfig" })
    Optional<ProgrammingExercise> findWithAllParticipationsAndBuildConfigById(long exerciseId);

    @Query("""
            SELECT DISTINCT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.templateParticipation
                LEFT JOIN FETCH pe.solutionParticipation
            WHERE pe.id = :exerciseId
            """)
    Optional<ProgrammingExercise> findWithEagerTemplateAndSolutionParticipationsById(@Param("exerciseId") long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "buildConfig" })
    Optional<ProgrammingExercise> findWithBuildConfigById(long exerciseId);

    /**
     * Fetch the programming exercise with the build config, or throw an EntityNotFoundException if it cannot be found.
     *
     * @param programmingExercise The programming exercise to fetch the build config for.
     * @return The programming exercise with the build config.
     */
    default ProgrammingExercise getProgrammingExerciseWithBuildConfigElseThrow(ProgrammingExercise programmingExercise) {
        if (programmingExercise.getBuildConfig() == null || !Hibernate.isInitialized(programmingExercise.getBuildConfig())) {
            return getValueElseThrow(findWithBuildConfigById(programmingExercise.getId()), programmingExercise.getId());
        }
        return programmingExercise;
    }
}
