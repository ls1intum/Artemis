package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.service.connectors.RemoteArtemisServiceConnector.authenticationHeaderForSecret;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.service.dto.FeedbackConflictResponseDTO;
import de.tum.in.www1.artemis.service.dto.TextFeedbackConflictRequestDTO;

@Service
@Profile("automaticText")
// TODO: why is there a AutomaticTextAssessmentConflictService and a TextAssessmentConflictService
public class TextAssessmentConflictService {

    private final Logger log = LoggerFactory.getLogger(TextAssessmentConflictService.class);

    @Value("${artemis.automatic-text.feedback-consistency-url}")
    private String API_ENDPOINT;

    @Value("${artemis.automatic-text.secret}")
    private String API_SECRET;

    private final RemoteArtemisServiceConnector<Request, Response> connector = new RemoteArtemisServiceConnector<>(log, Response.class);

    // region Request/Response DTOs
    private static class Request {

        public List<TextFeedbackConflictRequestDTO> feedbackWithTextBlock;

        public Long exerciseId;

        Request(List<TextFeedbackConflictRequestDTO> textFeedbackConflictRequestDTOS, long exerciseId) {
            this.feedbackWithTextBlock = textFeedbackConflictRequestDTOS;
            this.exerciseId = exerciseId;
        }
    }

    private static class Response {

        public List<FeedbackConflictResponseDTO> feedbackInconsistencies;
    }
    // endregion

    /**
     * Calls the remote feedback consistency service to check consistencies between feedback for an automatically assessed text exercise.
     *
     * @param textFeedbackConflictRequestDTOS list of request objects
     * @param exerciseId exercise id that feedback belong to
     * @param maxRetries number of retries before the request will be canceled
     * @return A list of FeedbackConflictResponseDTO objects
     * @throws NetworkingError if the request isn't successful
     */
    public List<FeedbackConflictResponseDTO> checkFeedbackConsistencies(List<TextFeedbackConflictRequestDTO> textFeedbackConflictRequestDTOS, long exerciseId, int maxRetries)
            throws NetworkingError {
        log.info("Calling Remote Service to check feedback consistencies.");
        final Request request = new Request(textFeedbackConflictRequestDTOS, exerciseId);
        final Response response = connector.invokeWithRetry(API_ENDPOINT, request, authenticationHeaderForSecret(API_SECRET), maxRetries);

        return response.feedbackInconsistencies;
    }
}
