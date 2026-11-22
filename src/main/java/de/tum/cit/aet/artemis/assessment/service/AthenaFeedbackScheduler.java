package de.tum.cit.aet.artemis.assessment.service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.AthenaFeedbackRequestRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/** Coordinates durable feedback reservation and best-effort notifications before exercise-specific generation. */
public final class AthenaFeedbackScheduler {

    private static final Logger log = LoggerFactory.getLogger(AthenaFeedbackScheduler.class);

    private final AthenaFeedbackRequestRepository athenaFeedbackRequestRepository;

    private final ResultWebsocketService resultWebsocketService;

    public AthenaFeedbackScheduler(AthenaFeedbackRequestRepository athenaFeedbackRequestRepository, ResultWebsocketService resultWebsocketService) {
        this.athenaFeedbackRequestRepository = athenaFeedbackRequestRepository;
        this.resultWebsocketService = resultWebsocketService;
    }

    /**
     * Reserves a request before publishing its pending result and starting the generation callback. Repository rejection
     * leaves both the response objects and the worker untouched.
     *
     * @param submission    validated submission to assess
     * @param participation participation returned to the requester
     * @param generate      exercise-specific worker receiving the durable reservation
     */
    public void schedule(Submission submission, StudentParticipation participation, Consumer<Result> generate) {
        Result pending = athenaFeedbackRequestRepository.reserveAthenaFeedbackRequest(submission);
        submission.addResult(pending);
        participation.getSubmissions().stream().filter(existing -> existing.getId().equals(submission.getId())).forEach(existing -> existing.addResult(pending));
        notifyResult(participation, pending);
        CompletableFuture.runAsync(() -> generate.accept(pending));
    }

    /**
     * Broadcasts feedback state without allowing a websocket failure to invalidate durable state or prevent generation.
     *
     * @param participation participation receiving the result
     * @param result        persisted pending or completed result
     */
    public void notifyResult(StudentParticipation participation, Result result) {
        try {
            resultWebsocketService.broadcastNewResult(participation, result);
        }
        catch (RuntimeException notificationFailure) {
            // The durable state is authoritative. A disconnected websocket must not prevent work or invalidate success.
            log.warn("Could not broadcast Athena feedback state for result {}", result.getId(), notificationFailure);
        }
    }
}
