package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingExerciseStudentParticipationRepository extends JpaRepository<ProgrammingExerciseStudentParticipation, Long> {

    @Query("""
            select p from ProgrammingExerciseStudentParticipation p
            left join fetch p.results pr
            left join fetch pr.feedbacks
            left join fetch pr.submission
            where p.id = :participationId
                and (pr.id = (select max(prr.id) from p.results prr
                    where (prr.assessmentType = 'AUTOMATIC'
                            or (prr.completionDate IS NOT NULL
                                and (p.exercise.assessmentDueDate IS NULL
                                OR p.exercise.assessmentDueDate < :#{#dateTime}))))
                    or pr.id IS NULL)
            """)
    Optional<ProgrammingExerciseStudentParticipation> findByIdWithLatestResultAndFeedbacksAndRelatedSubmissions(@Param("participationId") Long participationId,
            @Param("dateTime") ZonedDateTime dateTime);

    /**
     * Will return the participation with the provided participationId. The participation will come with all its manual results, submissions, feedbacks and assessors
     *
     * @param participationId the participation id
     * @return a participation with all its manual results.
     */
    @Query("""
            select p from ProgrammingExerciseStudentParticipation p
            left join fetch p.results pr
            left join fetch pr.feedbacks
            left join fetch pr.submission
            left join fetch pr.assessor
            where p.id = :participationId
                and pr.id in (select prr.id from p.results prr
                    where prr.assessmentType = 'MANUAL' or prr.assessmentType = 'SEMI_AUTOMATIC')
            """)
    Optional<ProgrammingExerciseStudentParticipation> findByIdWithAllManualOrSemiAutomaticResultsAndFeedbacksAndRelatedSubmissionAndAssessor(
            @Param("participationId") Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "exercise" })
    List<ProgrammingExerciseStudentParticipation> findByBuildPlanId(String buildPlanId);

    @Query("select distinct p from ProgrammingExerciseStudentParticipation p left join fetch p.results where p.buildPlanId is not null and (p.student is not null or p.team is not null)")
    List<ProgrammingExerciseStudentParticipation> findAllWithBuildPlanIdWithResults();

    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndStudentLogin(Long exerciseId, String username);

    Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndTeamId(Long exerciseId, Long teamId);

    List<ProgrammingExerciseStudentParticipation> findByExerciseId(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    List<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseId(Long exerciseId);

    /**
     * Will return the participations matching the provided participation ids, but only if they belong to the given exercise.
     *
     * @param exerciseId is used as a filter for the found participations.
     * @param participationIds the participations to retrieve.
     * @return filtered list of participations.
     */
    @Query("""
            select participation from ProgrammingExerciseStudentParticipation participation
            left join fetch participation.submissions
            where participation.exercise.id = :#{#exerciseId}
                and participation.id in :#{#participationIds}
            """)
    List<ProgrammingExerciseStudentParticipation> findWithSubmissionsByExerciseIdAndParticipationIds(@Param("exerciseId") Long exerciseId,
            @Param("participationIds") Collection<Long> participationIds);

    @Query("""
            SELECT p
            FROM ProgrammingExerciseStudentParticipation p
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.individualDueDate IS NOT null
            """)
    List<ProgrammingExerciseStudentParticipation> findWithIndividualDueDateByExerciseId(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "student")
    Optional<ProgrammingExerciseStudentParticipation> findWithStudentById(Long participationId);

    @NotNull
    default ProgrammingExerciseStudentParticipation findByIdElseThrow(Long participationId) {
        return findById(participationId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Student Participation", participationId));
    }

    default Optional<ProgrammingExerciseStudentParticipation> findStudentParticipationWithLatestResultAndFeedbacksAndRelatedSubmissions(Long participationId) {
        return findByIdWithLatestResultAndFeedbacksAndRelatedSubmissions(participationId, ZonedDateTime.now());
    }
}
