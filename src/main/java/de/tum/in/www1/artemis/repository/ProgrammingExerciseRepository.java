package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

/**
 * Spring Data JPA repository for the ProgrammingExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingExerciseRepository extends JpaRepository<ProgrammingExercise, Long> {

    // Does a max join on the result table for each participation by result id (the newer the result id, the newer the result). This makes sure that we only receive the latest
    // result for the template and the solution participation if they exist.
    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.templateParticipation tp left join fetch pe.solutionParticipation sp left join fetch tp.results as tpr left join fetch sp.results as spr where pe.course.id = :#{#courseId} and (tpr.id = (select max(id) from tp.results) or tpr.id = null) and (spr.id = (select max(id) from sp.results) or spr.id = null)")
    List<ProgrammingExercise> findByCourseIdWithLatestResultForParticipations(@Param("courseId") Long courseId);

    // Override to automatically fetch the templateParticipation and solutionParticipation
    @Override
    @NotNull
    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.templateParticipation left join fetch pe.solutionParticipation where pe.id = :#{#exerciseId}")
    Optional<ProgrammingExercise> findById(@Param("exerciseId") Long exerciseId);

    // TODO: Investigate - this should return the templateParticipation result including its feedbacks - but for some reason they can't be retrieved.
    // Get an a programmingExercise with template and solution participation, each with the latest result
    @EntityGraph(attributePaths = { "templateParticipation.results.feedbacks", "solutionParticipation.results.feedbacks" })
    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.templateParticipation tp left join fetch pe.solutionParticipation sp "
            + "left join fetch tp.results as tpr left join fetch sp.results as spr "
            + "where pe.id = :#{#exerciseId} and (tpr.id = (select max(id) from tp.results) or tpr.id = null) "
            + "and (spr.id = (select max(id) from sp.results) or spr.id = null)")
    Optional<ProgrammingExercise> findWithTemplateAndSolutionParticipationById(@Param("exerciseId") Long exerciseId);

    @Query("select distinct pe from ProgrammingExercise as pe left join fetch pe.participations")
    List<ProgrammingExercise> findAllWithEagerParticipations();

    ProgrammingExercise findOneByTemplateParticipationId(Long templateParticipationId);

    ProgrammingExercise findOneBySolutionParticipationId(Long solutionParticipationId);

    @EntityGraph(attributePaths = "course")
    @Query("select pe from ProgrammingExercise pe left join fetch pe.templateParticipation tp left join fetch pe.solutionParticipation sp where tp.id = :#{#participationId} or sp.id = :#{#participationId}")
    Optional<ProgrammingExercise> findOneByTemplateParticipationIdOrSolutionParticipationId(@Param("participationId") Long participationId);
}
