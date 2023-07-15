package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.service.dto.athena.TextExerciseDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextFeedbackDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextSubmissionDTO;

@Service
@Profile("athena")
public class AthenaFeedbackSendingService {

    private final Logger log = LoggerFactory.getLogger(AthenaFeedbackSendingService.class);

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    private final AthenaConnector<RequestDTO, ResponseDTO> connector;

    private final TextBlockRepository textBlockRepository;

    public AthenaFeedbackSendingService(TextBlockRepository textBlockRepository, @Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate) {
        connector = new AthenaConnector<>(log, athenaRestTemplate, ResponseDTO.class);
        this.textBlockRepository = textBlockRepository;
    }

    static class RequestDTO {

        public TextExerciseDTO exercise;

        public TextSubmissionDTO submission;

        public List<TextFeedbackDTO> feedbacks;

        RequestDTO(@NotNull TextExercise exercise, @NotNull TextSubmission submission, @NotNull List<Feedback> feedbacks, TextBlockRepository textBlockRepository) {
            this.exercise = TextExerciseDTO.of(exercise);
            this.submission = TextSubmissionDTO.of(exercise.getId(), submission);
            this.feedbacks = feedbacks.stream().map(feedback -> {
                // Give the DTO the text block the feedback is referring to.
                // => It will figure out start and end index of the feedback in the text
                var feedbackTextBlock = textBlockRepository.findById(feedback.getReference()).orElse(null);
                return TextFeedbackDTO.of(exercise.getId(), submission.getId(), feedback, feedbackTextBlock);
            }).toList();
        }
    }

    static class ResponseDTO {

        public String data;
    }

    /**
     * Calls the remote Athena service to submit feedback given by a tutor
     *
     * @param exercise   the exercise the feedback is given for
     * @param submission the submission the feedback is given for
     * @param feedbacks  the feedback given by the tutor
     */
    public void sendFeedback(TextExercise exercise, TextSubmission submission, List<Feedback> feedbacks) {
        sendFeedback(exercise, submission, feedbacks, 1);
    }

    /**
     * Calls the remote Athena service to submit feedback given by a tutor
     *
     * @param exercise   the exercise the feedback is given for
     * @param submission the submission the feedback is given for
     * @param feedbacks  the feedback given by the tutor
     * @param maxRetries number of retries before the request will be canceled
     */
    public void sendFeedback(TextExercise exercise, TextSubmission submission, List<Feedback> feedbacks, int maxRetries) {
        if (!exercise.isFeedbackSuggestionsEnabled()) {
            throw new IllegalArgumentException("The Exercise does not have feedback suggestions enabled.");
        }

        log.debug("Start Athena Feedback Sending Service for Text Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        if (feedbacks.isEmpty()) {
            log.info("No feedback given for submission #{}.", submission.getId());
            return;
        }

        log.info("Calling Remote Service with given feedback.");

        try {
            final RequestDTO request = new RequestDTO(exercise, submission, feedbacks, textBlockRepository);
            // TODO: make module selection dynamic (based on exercise)
            ResponseDTO response = connector.invokeWithRetry(athenaUrl + "/modules/text/module_text_cofee/feedbacks", request, maxRetries);
            log.info("Remote Service responded to feedback: {}", response.data);
        }
        catch (NetworkingError networkingError) {
            log.error("Error while calling Remote Service: {}", networkingError.getMessage());
        }
    }

}
