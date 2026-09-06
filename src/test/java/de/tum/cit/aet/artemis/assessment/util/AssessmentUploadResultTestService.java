package de.tum.cit.aet.artemis.assessment.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;

/**
 * Deletes manual results and every row that references them — feedback, long feedback text, assessment notes, complaints, complaint responses, ratings and participant scores — in
 * foreign-key-safe order. Used by tests to remove uploaded manual assessments together with their dependent rows.
 * <p>
 * All cleanup queries live on {@link ResultTestRepository}: the upload itself never deletes a result — it overwrites an existing manual assessment in place — so production code
 * needs none of them.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class AssessmentUploadResultTestService {

    @Autowired
    private ResultTestRepository resultTestRepository;

    /**
     * Bulk-deletes the given results and every dependent row in foreign-key-safe order.
     *
     * @param resultIds ids of the results to delete
     * @throws IllegalArgumentException if {@code resultIds} is {@code null}, empty, or contains a non-persisted id
     */
    public void deleteResultsByIds(final Collection<Long> resultIds) {
        if (resultIds == null || resultIds.isEmpty()) {
            throw new IllegalArgumentException("The result ids must not be null or empty");
        }
        if (resultIds.stream().anyMatch(resultId -> resultId == null || resultId <= 0)) {
            throw new IllegalArgumentException("The result ids must identify persisted results");
        }
        deleteNonCascadedResultReferences(resultIds);
        resultTestRepository.deleteLongFeedbackTextByResultIds(resultIds);
        resultTestRepository.deleteFeedbackByResultIds(resultIds);
        resultTestRepository.deleteAssessmentNotesByResultIds(resultIds);
        resultTestRepository.deleteResultsByIds(resultIds);
    }

    /**
     * Deletes all manual results of the given participations in one exercise.
     *
     * @param exerciseId       target exercise id
     * @param participationIds participations whose manual results are deleted
     * @throws IllegalArgumentException if a precondition is violated
     */
    public void deleteManualResults(final long exerciseId, final Collection<Long> participationIds) {
        if (exerciseId <= 0) {
            throw new IllegalArgumentException("The exercise id must identify a persisted exercise");
        }
        if (participationIds == null || participationIds.isEmpty()) {
            throw new IllegalArgumentException("The participation ids must not be null or empty");
        }
        if (participationIds.stream().anyMatch(participationId -> participationId == null || participationId <= 0)) {
            throw new IllegalArgumentException("The participation ids must identify persisted participations");
        }
        final List<Long> resultIds = resultTestRepository.findManualResultIds(exerciseId, participationIds);
        if (!resultIds.isEmpty()) {
            deleteResultsByIds(resultIds);
        }
    }

    /**
     * Removes the references that cannot be cascade-deleted from a result (complaint responses, complaints, ratings and participant scores) before the results themselves are
     * deleted.
     *
     * @param resultIds ids of the results whose non-cascaded references are removed
     */
    private void deleteNonCascadedResultReferences(final Collection<Long> resultIds) {
        resultTestRepository.deleteComplaintResponsesByResultIds(resultIds);
        resultTestRepository.deleteComplaintsByResultIds(resultIds);
        resultTestRepository.deleteRatingsByResultIds(resultIds);
        resultTestRepository.clearParticipantScoreLastResultByResultIds(resultIds);
        resultTestRepository.clearParticipantScoreLastRatedResultByResultIds(resultIds);
    }
}
