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

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Repository for loading and replacing results during manual assessment uploads.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface AssessmentUploadResultRepository extends ArtemisJpaRepository<Result, Long> {

    /**
     * Write-locks the given result rows for the remainder of the caller's transaction. Used by the upload to lock the manual results it is about to replace before checking for
     * complaints, so that creating a complaint concurrently — which updates its result row and inserts the complaint — serializes behind the upload instead of slipping in between
     * the complaint check and the reference cleanup (which would silently delete it). The ids are locked in ascending order to give concurrent uploads a consistent lock order.
     * <p>
     * <b>Preconditions:</b> {@code resultIds} is non-{@code null}, non-empty, contains persisted result ids, and the caller has an active transaction.
     * <p>
     * <b>Postcondition:</b> the matching result rows remain write-locked until the caller's transaction completes.
     *
     * @param resultIds ids of the results to lock
     * @return the ids of the locked results in ascending order
     */
    @Query(value = """
            SELECT r.id
            FROM result r
            WHERE r.id IN (:resultIds)
            ORDER BY r.id
            FOR UPDATE
            """, nativeQuery = true)
    List<Long> lockResultsForReplacement(@Param("resultIds") final Collection<Long> resultIds);

    /**
     * Finds the participations that own one of the given results and whose result carries a complaint. Replacing such a result would delete the student's complaint and any
     * instructor response, so the upload must reject these participations instead of overwriting them. The query is scoped to the exact results an upload would delete (the manual
     * results on each participation's latest submission, see {@code replacedResultIds}); a complaint on a superseded submission's result — which the upload leaves untouched — is
     * therefore intentionally not reported.
     * <p>
     * <b>Precondition:</b> {@code resultIds} is non-{@code null}, non-empty, and contains persisted result ids.
     * <p>
     * <b>Postcondition:</b> read-only; every returned id is the participation of a supplied result that has a complaint.
     *
     * @param resultIds ids of the results that would be replaced
     * @return ids of participations that must not be overwritten because a complaint exists on a result being replaced
     */
    @Query("""
            SELECT r.submission.participation.id
            FROM Complaint c
                JOIN c.result r
            WHERE r.id IN :resultIds
            """)
    Set<Long> findParticipationIdsWithComplaintOnResults(@Param("resultIds") final Collection<Long> resultIds);

    /**
     * Loads newly imported results with the relationships required by LTI and websocket notifications in one query.
     * <p>
     * <b>Precondition:</b> {@code resultIds} is non-{@code null}, non-empty, and contains persisted result ids.
     * <p>
     * <b>Postcondition:</b> every matching result is returned with its submission, feedback, participation, team, and team students initialized.
     *
     * @param resultIds ids of the newly imported results
     * @return results with their notification relationships initialized
     */
    @Query("""
            SELECT DISTINCT r
            FROM Result r
                LEFT JOIN FETCH r.submission s
                LEFT JOIN FETCH r.feedbacks
                LEFT JOIN FETCH TREAT (s.participation AS StudentParticipation) p
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students
            WHERE r.id IN :resultIds
            """)
    List<Result> findAllWithSubmissionAndFeedbackAndTeamStudentsByIds(@Param("resultIds") final Collection<Long> resultIds);
}
