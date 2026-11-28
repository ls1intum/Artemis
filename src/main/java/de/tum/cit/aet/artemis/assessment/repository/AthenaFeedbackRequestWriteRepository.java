package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.text.domain.TextBlock;

/** Atomic reservation and terminal writes for student-requested Athena feedback. */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface AthenaFeedbackRequestWriteRepository {

    /**
     * Reserves a durable pending result, serializing duplicate requests and the successful-request quota per participation.
     *
     * @param submission the validated submission snapshot for the request
     * @return the persisted pending result with its request id and lease deadline
     */
    @Transactional
    Result reserveAthenaFeedbackRequest(Submission submission);

    /**
     * Stores feedback and text blocks only while this exact reservation is still live.
     *
     * @param request    the reservation owned by the completing worker
     * @param score      the computed score in percent
     * @param feedbacks  the generated feedback to persist atomically with the result
     * @param textBlocks the text blocks associated with the generated feedback, or an empty set for modeling
     * @return whether this live reservation was completed
     */
    @Transactional
    boolean completeAthenaFeedbackRequest(Result request, double score, Collection<Feedback> feedbacks, Set<TextBlock> textBlocks);

    /**
     * Persists a failed reservation without changing an already completed or superseded request.
     *
     * @param request the reservation owned by the failing worker
     * @return whether the reservation transitioned to failed
     */
    @Transactional
    boolean failAthenaFeedbackRequest(Result request);
}
