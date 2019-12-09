package de.tum.in.www1.artemis.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingExerciseStudentParticipationRepository extends JpaRepository<ProgrammingExerciseStudentParticipation, Long> {

    @Query("select p from ProgrammingExerciseStudentParticipation p left join fetch p.results pr left join fetch pr.feedbacks where p.id = :participationId and (pr.id = (select max(id) from p.results) or pr.id = null)")
    Optional<ProgrammingExerciseStudentParticipation> findByIdWithLatestResultAndFeedbacks(@Param("participationId") Long participationId);

    @EntityGraph(attributePaths = { "results", "exercise" })
    List<ProgrammingExerciseStudentParticipation> findByBuildPlanId(String buildPlanId);

    @Query("select distinct p from ProgrammingExerciseStudentParticipation p left join fetch p.results where p.buildPlanId is not null and p.student is not null")
    List<ProgrammingExerciseStudentParticipation> findAllWithBuildPlanId();

    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndStudentLogin(Long exerciseId, String username);

    List<ProgrammingExerciseStudentParticipation> findByExerciseId(Long exerciseId);

    /**
     * Will return the participations matching the provided participation ids, but only if they belong to the given exercise.
     *
     * @param exerciseId is used as a filter for the found participations.
     * @param participationIds the participations to retrieve.
     * @return filtered list of participations.
     */
    @Query("select participation from ProgrammingExerciseStudentParticipation participation where participation.exercise.id = :#{#exerciseId} and participation.id in :#{#participationIds}")
    List<ProgrammingExerciseStudentParticipation> findByExerciseIdAndParticipationIds(@Param("exerciseId") Long exerciseId,
            @Param("participationIds") Collection<Long> participationIds);

    @EntityGraph(attributePaths = "student")
    @Query("select distinct participation from Participation participation where participation.id = :#{#participationId}")
    Optional<ProgrammingExerciseStudentParticipation> findByIdWithStudent(@Param("participationId") Long participationId);
}
