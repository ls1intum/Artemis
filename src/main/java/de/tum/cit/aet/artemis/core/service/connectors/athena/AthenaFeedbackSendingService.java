package de.tum.cit.aet.artemis.core.service.connectors.athena;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.athena.dto.ExerciseBaseDTO;
import de.tum.cit.aet.artemis.athena.dto.FeedbackBaseDTO;
import de.tum.cit.aet.artemis.athena.dto.SubmissionBaseDTO;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * Service for publishing feedback to the Athena service for further processing
 * so that Athena can later give feedback suggestions on new submissions.
 */
@Service
@Profile("athena")
public class AthenaFeedbackSendingService {

    private static final Logger log = LoggerFactory.getLogger(AthenaFeedbackSendingService.class);

    private final AthenaConnector<RequestDTO, ResponseDTO> connector;

    private final AthenaModuleService athenaModuleService;

    private final AthenaDTOConverterService athenaDTOConverterService;

    /**
     * Creates a new service to send feedback to the Athena service
     */
    public AthenaFeedbackSendingService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, AthenaModuleService athenaModuleService,
            AthenaDTOConverterService athenaDTOConverterService) {
        connector = new AthenaConnector<>(athenaRestTemplate, ResponseDTO.class);
        this.athenaModuleService = athenaModuleService;
        this.athenaDTOConverterService = athenaDTOConverterService;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record RequestDTO(ExerciseBaseDTO exercise, SubmissionBaseDTO submission, List<FeedbackBaseDTO> feedbacks) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ResponseDTO(String data) {
    }

    /**
     * Calls the remote Athena service to submit feedback given by a tutor
     * We send the feedback asynchronously because it's not important for the user => quicker response
     *
     * @param exercise   the exercise the feedback is given for
     * @param submission the submission the feedback is given for
     * @param feedbacks  the feedback given by the tutor
     */
    @Async
    public void sendFeedback(Exercise exercise, Submission submission, List<Feedback> feedbacks) {
        sendFeedback(exercise, submission, feedbacks, 1);
    }

    /**
     * Calls the remote Athena service to submit feedback given by a tutor
     * We send the feedback asynchronously because it's not important for the user => quicker response
     *
     * @param exercise   the exercise the feedback is given for
     * @param submission the submission the feedback is given for
     * @param feedbacks  the feedback given by the tutor
     * @param maxRetries number of retries before the request will be canceled
     */
    @Async
    public void sendFeedback(Exercise exercise, Submission submission, List<Feedback> feedbacks, int maxRetries) {
        if (!exercise.areFeedbackSuggestionsEnabled()) {
            throw new IllegalArgumentException("The exercise does not have feedback suggestions enabled.");
        }

        log.debug("Start Athena Feedback Sending Service for Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        if (feedbacks.isEmpty()) {
            log.debug("No feedback given for submission #{}.", submission.getId());
            return;
        }

        log.info("Calling Athena with given feedback.");

        try {
            // Only send manual feedback from tutors to Athena
            final RequestDTO request = new RequestDTO(athenaDTOConverterService.ofExercise(exercise), athenaDTOConverterService.ofSubmission(exercise.getId(), submission),
                    feedbacks.stream().filter(Feedback::isManualFeedback).map((feedback) -> athenaDTOConverterService.ofFeedback(exercise, submission.getId(), feedback)).toList());
            ResponseDTO response = connector.invokeWithRetry(athenaModuleService.getAthenaModuleUrl(exercise) + "/feedbacks", request, maxRetries);
            log.info("Athena responded to feedback: {}", response.data);
        }
        catch (NetworkingException networkingException) {
            log.error("Error while calling Athena", networkingException);
        }
    }

}
