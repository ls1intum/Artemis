package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.exception.NetworkingException;

/**
 * Service for publishing feedback to the Athena service for further processing
 * so that Athena can later give feedback suggestions on new submissions.
 */
@Service
@Profile("athena")
public class AthenaFeedbackSendingService {

    private final Logger log = LoggerFactory.getLogger(AthenaFeedbackSendingService.class);

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    private final AthenaConnector<RequestDTO, ResponseDTO> connector;

    private final AthenaDTOConverter athenaDTOConverter;

    /**
     * Creates a new service to send feedback to the Athena service
     */
    public AthenaFeedbackSendingService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, AthenaDTOConverter athenaDTOConverter) {
        connector = new AthenaConnector<>(athenaRestTemplate, ResponseDTO.class);
        this.athenaDTOConverter = athenaDTOConverter;
    }

    private record RequestDTO(Object exercise, Object submission, List<Object> feedbacks) {
    }

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
    public void sendFeedback(TextExercise exercise, TextSubmission submission, List<Feedback> feedbacks) {
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
    public void sendFeedback(TextExercise exercise, TextSubmission submission, List<Feedback> feedbacks, int maxRetries) {
        if (!exercise.getFeedbackSuggestionsEnabled()) {
            throw new IllegalArgumentException("The exercise does not have feedback suggestions enabled.");
        }

        log.debug("Start Athena Feedback Sending Service for Text Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        if (feedbacks.isEmpty()) {
            log.debug("No feedback given for submission #{}.", submission.getId());
            return;
        }

        log.info("Calling Athena with given feedback.");

        try {
            final RequestDTO request = new RequestDTO(athenaDTOConverter.ofExercise(exercise), athenaDTOConverter.ofSubmission(exercise.getId(), submission),
                    feedbacks.stream().map((feedback) -> athenaDTOConverter.ofFeedback(exercise.getId(), submission.getId(), feedback)).toList());
            // TODO: make module selection dynamic (based on exercise)
            ResponseDTO response = connector.invokeWithRetry(athenaUrl + "/modules/text/module_text_cofee/feedbacks", request, maxRetries);
            log.info("Athena responded to feedback: {}", response.data);
        }
        catch (NetworkingException networkingException) {
            log.error("Error while calling Athena", networkingException);
        }
    }

}
