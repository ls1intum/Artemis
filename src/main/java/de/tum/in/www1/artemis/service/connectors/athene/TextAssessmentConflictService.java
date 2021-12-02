package de.tum.in.www1.artemis.service.connectors.athene;

import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_ATHENE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.service.dto.FeedbackConflictResponseDTO;
import de.tum.in.www1.artemis.service.dto.TextFeedbackConflictRequestDTO;

@Service
@Profile(SPRING_PROFILE_ATHENE)
public class TextAssessmentConflictService {

    private final Logger log = LoggerFactory.getLogger(TextAssessmentConflictService.class);

    @Value("${artemis.athene.url}")
    private String atheneUrl;

    private final AtheneConnector<Request, Response> connector;

    public TextAssessmentConflictService(@Qualifier("atheneRestTemplate") RestTemplate atheneRestTemplate) {
        connector = new AtheneConnector<>(log, atheneRestTemplate, Response.class);
    }

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
        final Response response = connector.invokeWithRetry(atheneUrl + "/feedback_consistency", request, maxRetries);

        return response.feedbackInconsistencies;
    }
}
