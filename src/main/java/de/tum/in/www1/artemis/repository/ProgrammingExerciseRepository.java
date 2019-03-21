package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


/**
 * Spring Data JPA repository for the ProgrammingExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingExerciseRepository extends JpaRepository<ProgrammingExercise, Long> {

    @Query("select pe from ProgrammingExercise pe left join fetch pe.templateParticipation left join fetch pe.solutionParticipation where pe.course.id = :#{#courseId}")
    List<ProgrammingExercise> findByCourseId(@Param("courseId") Long courseId);

    //Override to automatically fetch the templateParticipation and solutionParticipation
    @Query("select distinct pe from ProgrammingExercise pe left join fetch pe.templateParticipation left join fetch pe.solutionParticipation where pe.id = :#{#exerciseId}")
    Optional<ProgrammingExercise> findById(@Param("exerciseId") Long exerciseId);

    @Query("select distinct pe from ProgrammingExercise as pe left join fetch pe.participations")
    List<ProgrammingExercise> findAllWithEagerParticipations();

    ProgrammingExercise findOneByTemplateParticipationId(Long templateParticipationId);

    ProgrammingExercise findOneBySolutionParticipationId(Long solutionParticipationId);
}
