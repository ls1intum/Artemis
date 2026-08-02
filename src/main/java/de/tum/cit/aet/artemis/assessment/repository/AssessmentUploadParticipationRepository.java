package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadParticipationDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * Repository for resolving the participation data needed by manual assessment uploads.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface AssessmentUploadParticipationRepository extends ArtemisJpaRepository<StudentParticipation, Long> {

    /**
     * Locks the target participations in a deterministic order before their manual results are replaced.
     * <p>
     * <b>Preconditions:</b> {@code exerciseId} identifies a persisted exercise, {@code participationIds} is non-{@code null}, non-empty, and contains only persisted ids, and the
     * caller has an active transaction.
     * <p>
     * <b>Postcondition:</b> returns the matching participation ids in ascending order; their database rows remain write-locked until the caller's transaction completes.
     *
     * @param exerciseId       the target exercise id
     * @param participationIds the participations included in the upload
     * @return the ids of the locked participations in ascending order
     */
    @Query(value = """
            SELECT p.id
            FROM participation p
            WHERE p.exercise_id = :exerciseId
                AND p.id IN (:participationIds)
            ORDER BY p.id
            FOR UPDATE
            """, nativeQuery = true)
    List<Long> lockAllForAssessmentUpload(@Param("exerciseId") final long exerciseId, @Param("participationIds") final Collection<Long> participationIds);

    /**
     * Resolves the minimal participant information needed for assessment-upload validation in one exercise-scoped query.
     * <p>
     * <b>Preconditions:</b> {@code exerciseId} identifies a persisted exercise and {@code participationIds} is non-{@code null}, non-empty, and contains persisted ids.
     * <p>
     * <b>Postcondition:</b> every returned DTO belongs to the supplied exercise and has an id contained in {@code participationIds}.
     *
     * @param exerciseId       the exercise to which the participations must belong
     * @param participationIds the participation ids parsed from the uploaded identifiers
     * @return matching participation ids and their login or team short name
     */
    @Query("""
            SELECT NEW de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadParticipationDTO(
                p.id,
                COALESCE(student.login, team.shortName)
            )
            FROM StudentParticipation p
                LEFT JOIN p.student student
                LEFT JOIN p.team team
            WHERE p.exercise.id = :exerciseId
                AND p.id IN :participationIds
                AND (student.id IS NOT NULL OR team.id IS NOT NULL)
            """)
    List<AssessmentUploadParticipationDTO> findAssessmentUploadParticipations(@Param("exerciseId") final long exerciseId,
            @Param("participationIds") final Collection<Long> participationIds);

    /**
     * Finds which requested participation ids exist outside the target exercise. This preserves the distinction between an unknown participation and one belonging to another
     * exercise without resolving rows individually.
     * <p>
     * <b>Preconditions:</b> {@code exerciseId} identifies a persisted exercise and {@code participationIds} is non-{@code null}, non-empty, and contains persisted ids.
     * <p>
     * <b>Postcondition:</b> every returned id occurs in {@code participationIds} and belongs to a different exercise.
     *
     * @param exerciseId       the target exercise id
     * @param participationIds ids that were not found in the target exercise
     * @return ids belonging to another exercise
     */
    @Query("""
            SELECT p.id
            FROM StudentParticipation p
                LEFT JOIN p.exercise exercise
            WHERE (exercise.id <> :exerciseId OR exercise.id IS NULL)
                AND p.id IN :participationIds
            """)
    Set<Long> findIdsOutsideExercise(@Param("exerciseId") final long exerciseId, @Param("participationIds") final Collection<Long> participationIds);

    /**
     * Loads the participations needed during assessment-upload storage together with the participant reference used by result lifecycle callbacks.
     * <p>
     * <b>Preconditions:</b> {@code exerciseId} identifies a persisted exercise and {@code participationIds} is non-{@code null}, non-empty, and contains persisted ids.
     * <p>
     * <b>Postcondition:</b> every matching participation is returned with its student or team reference initialized.
     *
     * @param exerciseId       target exercise id
     * @param participationIds participations included in the upload
     * @return matching participations with the student or team reference initialized
     */
    @Query("""
            SELECT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.student
                LEFT JOIN FETCH p.team
            WHERE p.exercise.id = :exerciseId
                AND p.id IN :participationIds
            """)
    List<StudentParticipation> findAllForAssessmentUpload(@Param("exerciseId") final long exerciseId, @Param("participationIds") final Collection<Long> participationIds);
}
