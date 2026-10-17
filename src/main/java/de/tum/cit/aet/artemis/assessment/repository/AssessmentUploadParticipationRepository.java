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
     * Resolves every participation of one exercise for building a manual-assessment upload template, using the same identifier (login or team short name) as the upload validation.
     * <p>
     * <b>Precondition:</b> {@code exerciseId} identifies a persisted exercise.
     * <p>
     * <b>Postcondition:</b> returns one DTO per student or team participation of the exercise, ordered by participation id.
     *
     * @param exerciseId the exercise whose participations are exported into the template
     * @return the participation id and login or team short name of every participation of the exercise
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
                AND (student.id IS NOT NULL OR team.id IS NOT NULL)
            ORDER BY p.id
            """)
    List<AssessmentUploadParticipationDTO> findAllForAssessmentUploadTemplate(@Param("exerciseId") final long exerciseId);

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
     * Loads the participations needed during assessment-upload storage: the participant reference used by result lifecycle callbacks, and each participation's latest submission
     * with the results and feedback the storage path inspects to find the manual assessment it overwrites.
     * <p>
     * The submission is loaded here rather than by a second, submission-rooted query. {@code Submission.participation} is an eager to-one, and no transaction spans two
     * repository calls, so a separate query would re-read every participation one select at a time — together with the whole eager exercise graph hanging off it. Selecting the
     * participation means the submission's own reference to it resolves within this query instead, and a batch import stays independent of its size.
     * <p>
     * The fetched {@code submissions} collection is deliberately partial: it holds the latest submission alone, never the participation's full history. Nothing in the upload
     * reads it back — the caller takes that one submission and the upload writes results and new submissions, never the collection — but a future caller must not mistake it for
     * the complete set.
     * <p>
     * <b>Preconditions:</b> {@code exerciseId} identifies a persisted exercise and {@code participationIds} is non-{@code null}, non-empty, and contains persisted ids.
     * <p>
     * <b>Postcondition:</b> every matching participation is returned with its student or team reference initialized, and with its latest submission — if it has one — and that
     * submission's results and their feedback initialized.
     *
     * @param exerciseId       target exercise id
     * @param participationIds participations included in the upload
     * @return matching participations, each with the participant reference and its latest submission initialized
     */
    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.student
                LEFT JOIN FETCH p.team
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks
            WHERE p.exercise.id = :exerciseId
                AND p.id IN :participationIds
                AND (
                    s.id IS NULL
                    OR s.id = (
                        SELECT MAX(s2.id)
                        FROM Submission s2
                        WHERE s2.participation.id = p.id
                    )
                )
            """)
    List<StudentParticipation> findAllForAssessmentUpload(@Param("exerciseId") final long exerciseId, @Param("participationIds") final Collection<Long> participationIds);
}
