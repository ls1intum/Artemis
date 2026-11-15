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
 * Repository for loading and updating results during manual assessment uploads.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface AssessmentUploadResultRepository extends ArtemisJpaRepository<Result, Long> {

    /**
     * Finds the participations that own one of the given results and whose result carries a complaint. Overwriting such a result would change the assessment a student is
     * complaining about while the complaint is still open, so the upload rejects these participations instead. The query is scoped to the exact results an upload would overwrite
     * (the manual results on each participation's latest submission, see {@code overwrittenResultIds}); a complaint on a superseded submission's result — which the upload leaves
     * untouched — is therefore intentionally not reported.
     * <p>
     * <b>Precondition:</b> {@code resultIds} is non-{@code null}, non-empty, and contains persisted result ids.
     * <p>
     * <b>Postcondition:</b> read-only; every returned id is the participation of a supplied result that has a complaint.
     *
     * @param resultIds ids of the results that would be overwritten
     * @return ids of participations that must not be overwritten because a complaint exists on a result being overwritten
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
