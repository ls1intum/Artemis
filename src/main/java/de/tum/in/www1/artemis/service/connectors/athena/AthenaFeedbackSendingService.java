package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.service.dto.athena.TextExerciseDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextFeedbackDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextSubmissionDTO;

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

    private final TextBlockRepository textBlockRepository;

    /**
     * Creates a new service to send feedback to the Athena service
     *
     * @param textBlockRepository Needed to get start and end indexes of feedbacks
     * @param athenaRestTemplate  The rest template to use for sending requests to Athena
     */
    public AthenaFeedbackSendingService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, TextBlockRepository textBlockRepository) {
        connector = new AthenaConnector<>(athenaRestTemplate, ResponseDTO.class);
        this.textBlockRepository = textBlockRepository;
    }

    private static class RequestDTO {

        public TextExerciseDTO exercise;

        public TextSubmissionDTO submission;

        public List<TextFeedbackDTO> feedbacks;

        /**
         * Connect feedback and text block to find the correct start and end indexes for transfer when constructing the DTO:
         */
        RequestDTO(@NotNull TextExercise exercise, @NotNull TextSubmission submission, @NotNull List<Feedback> feedbacks, TextBlockRepository textBlockRepository) {
            this.exercise = TextExerciseDTO.of(exercise);
            this.submission = TextSubmissionDTO.of(exercise.getId(), submission);
            this.feedbacks = feedbacks.stream().map(feedback -> {
                // Give the DTO the text block the feedback is referring to.
                // => It will figure out start and end index of the feedback in the text
                TextBlock feedbackTextBlock = null;
                if (feedback.getReference() != null) {
                    feedbackTextBlock = textBlockRepository.findById(feedback.getReference()).orElse(null);
                }
                return TextFeedbackDTO.of(exercise.getId(), submission.getId(), feedback, feedbackTextBlock);
            }).toList();
        }
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
        if (!exercise.isFeedbackSuggestionsEnabled()) {
            throw new IllegalArgumentException("The exercise does not have feedback suggestions enabled.");
        }

        log.debug("Start Athena Feedback Sending Service for Text Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        if (feedbacks.isEmpty()) {
            log.debug("No feedback given for submission #{}.", submission.getId());
            return;
        }

        log.info("Calling Athena with given feedback.");

        try {
            final RequestDTO request = new RequestDTO(exercise, submission, feedbacks, textBlockRepository);
            // TODO: make module selection dynamic (based on exercise)
            ResponseDTO response = connector.invokeWithRetry(athenaUrl + "/modules/text/module_text_cofee/feedbacks", request, maxRetries);
            log.info("Athena responded to feedback: {}", response.data);
        }
        catch (NetworkingException networkingException) {
            log.error("Error while calling Athena", networkingException);
        }
    }

}
