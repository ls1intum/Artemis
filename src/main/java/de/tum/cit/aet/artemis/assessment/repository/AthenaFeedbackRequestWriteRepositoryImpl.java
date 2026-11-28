package de.tum.cit.aet.artemis.assessment.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.text.domain.TextBlock;

/** All writes take the participation lock first; no lock or transaction spans the external Athena call. */
public class AthenaFeedbackRequestWriteRepositoryImpl implements AthenaFeedbackRequestWriteRepository {

    private final ObjectProvider<AthenaFeedbackRequestRepository> results;

    private final TextBlockRepository textBlocks;

    private final FeedbackRepository feedbackRepository;

    private final int requestLimit;

    public AthenaFeedbackRequestWriteRepositoryImpl(ObjectProvider<AthenaFeedbackRequestRepository> results, TextBlockRepository textBlocks, FeedbackRepository feedbackRepository,
            @Value("${artemis.athena.allowed-feedback-requests:10}") int requestLimit) {
        this.results = results;
        this.textBlocks = textBlocks;
        this.feedbackRepository = feedbackRepository;
        this.requestLimit = requestLimit;
    }

    @Override
    public Result reserveAthenaFeedbackRequest(Submission submission) {
        AthenaFeedbackRequestRepository repository = results.getObject();
        repository.lockParticipationForAthenaFeedback(submission.getParticipation().getId());
        var existing = repository.findAthenaFeedbackRequests(submission.getParticipation().getId());
        var now = ZonedDateTime.now();
        for (Result result : existing) {
            if (result.isSuccessful() == null) {
                if (result.getCompletionDate() != null && result.getCompletionDate().isAfter(now)) {
                    throw new BadRequestAlertException("Request has already been sent", "participation", "feedbackRequestAlreadySent", true);
                }
                // Expired work no longer owns a completion slot. A late worker sees this terminal state under the same lock.
                markFailed(result, now);
            }
            if (Boolean.TRUE.equals(result.isSuccessful()) && Objects.equals(result.getSubmission().getId(), submission.getId())) {
                throw new BadRequestAlertException("Submission already has an Athena result", "submission", "submissionAlreadyHasAthenaResult", true);
            }
        }
        if (existing.stream().filter(result -> Boolean.TRUE.equals(result.isSuccessful())).count() >= requestLimit) {
            throw new BadRequestAlertException("Maximum number of AI feedback requests reached.", "participation", "maxAthenaResultsReached", true);
        }
        Result pending = new Result();
        pending.setExerciseId(submission.getParticipation().getExercise().getId());
        pending.setSubmission(submission);
        pending.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        pending.setRated(false);
        pending.setScore(null);
        pending.setSuccessful(null);
        pending.setCompletionDate(now.plusMinutes(5));
        return repository.saveAndFlush(pending);
    }

    @Override
    public boolean completeAthenaFeedbackRequest(Result request, double score, Collection<Feedback> feedbacks, Set<TextBlock> blocks) {
        Result current = lockedRequest(request);
        if (current.isSuccessful() != null) {
            return false;
        }
        var now = ZonedDateTime.now();
        if (current.getCompletionDate() == null || !current.getCompletionDate().isAfter(now)) {
            markFailed(current, now);
            return false;
        }
        feedbacks.forEach(feedback -> feedback.setResult(current));
        var savedFeedbacks = feedbackRepository.saveAllAndFlush(feedbacks);
        current.getFeedbacks().clear();
        savedFeedbacks.forEach(current::addFeedback);
        // Flush child constraints while the request is still pending, before a successful result can schedule scores.
        textBlocks.saveAllAndFlush(blocks);
        current.setScore(score);
        current.setRated(true);
        current.setSuccessful(true);
        current.setCompletionDate(now);
        results.getObject().saveAndFlush(current);
        return true;
    }

    @Override
    public boolean failAthenaFeedbackRequest(Result request) {
        Result current = lockedRequest(request);
        if (current.isSuccessful() != null) {
            return false;
        }
        markFailed(current, ZonedDateTime.now());
        return true;
    }

    private void markFailed(Result result, ZonedDateTime completed) {
        result.setSuccessful(false);
        result.setRated(false);
        result.setScore(null);
        result.setCompletionDate(completed);
    }

    private Result lockedRequest(Result request) {
        AthenaFeedbackRequestRepository repository = results.getObject();
        long participationId = request.getSubmission().getParticipation().getId();
        repository.lockParticipationForAthenaFeedback(participationId);
        return repository.findByIdElseThrow(request.getId());
    }
}
