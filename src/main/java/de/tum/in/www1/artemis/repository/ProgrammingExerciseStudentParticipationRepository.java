package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

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

    @Query("SELECT p FROM ProgrammingExerciseStudentParticipation p LEFT JOIN FETCH p.results pr LEFT JOIN FETCH pr.feedbacks LEFT JOIN FETCH pr.submission WHERE p.id = :participationId AND (pr.id = (SELECT MAX(id) FROM p.results) OR pr.id = NULL)")
    Optional<ProgrammingExerciseStudentParticipation> findWithLatestResultAndFeedbacksAndRelatedSubmissionsById(@Param("participationId") Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "exercise" })
    List<ProgrammingExerciseStudentParticipation> findAllByBuildPlanId(String buildPlanId);

    @Query("SELECT DISTINCT p FROM ProgrammingExerciseStudentParticipation p LEFT JOIN FETCH p.results WHERE p.buildPlanId IS NOT NULL AND (p.student IS NOT NULL OR p.team IS NOT NULL)")
    List<ProgrammingExerciseStudentParticipation> findAllWithBuildPlanId();

    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndStudentLogin(Long exerciseId, String username);

    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndTeamId(Long exerciseId, Long teamId);

    List<ProgrammingExerciseStudentParticipation> findAllByExerciseId(Long exerciseId);

    /**
     * Will return the participations matching the provided participation ids, but only if they belong to the given exercise.
     *
     * @param exerciseId is used as a filter for the found participations.
     * @param participationIds the participations to retrieve.
     * @return filtered list of participations.
     */
    @Query("SELECT participation FROM ProgrammingExerciseStudentParticipation participation WHERE participation.exercise.id = :#{#exerciseId} AND participation.id IN :#{#participationIds}")
    List<ProgrammingExerciseStudentParticipation> findByExerciseIdAndParticipationIds(@Param("exerciseId") Long exerciseId,
            @Param("participationIds") Collection<Long> participationIds);

    @EntityGraph(type = LOAD, attributePaths = "student")
    Optional<ProgrammingExerciseStudentParticipation> findWithStudentById(Long participationId);
}
