package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.service.connectors.RemoteArtemisServiceConnector.authenticationHeaderForSecret;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.exception.NetworkingError;

@Service
@Profile("automaticText")
public class TextFeedbackValidationService {

    private final Logger log = LoggerFactory.getLogger(TextFeedbackValidationService.class);

    // Region Request/Response DTOs
    private static class Request {

        public String candidate;

        public List<String> references;

        Request(TextBlock candidate, List<TextBlock> references) {
            this.candidate = candidate.getText();
            this.references = references.stream().map(textBlock -> textBlock.getText()).collect(Collectors.toList());
        }
    }

    private static class Response {

        public float confidence;

    }
    // Endregion

    @Value("${artemis.automatic-text.validation-url}")
    private String API_ENDPOINT;

    @Value("${artemis.automatic-text.secret}")
    private String API_SECRET;

    private RemoteArtemisServiceConnector<Request, Response> connector = new RemoteArtemisServiceConnector<>(log, Response.class);

    /**
     * Wrapper method for validateFeedback()
     * @param candidate Textblock which should be validated
     * @param references List of text blocks of the text cluster
     * @return double between 0-100 indicating wether the feedback is correct
     * @throws NetworkingError if request isn't successful
     */
    public double validateFeedback(TextBlock candidate, List<TextBlock> references) throws NetworkingError {
        return validateFeedback(candidate, references, 1);
    }

    /**
     * Calls the validation service to validate wether a text block is deserving of a certain automatic feedback
     * @param candidate Text block which should be validated
     * @param references List of text blocks of the text cluster
     * @param maxRetries number of retries
     * @return double between 0-100 indicating whether the feedback is correct
     * @throws NetworkingError if request isn't successful
     */

    public double validateFeedback(TextBlock candidate, List<TextBlock> references, int maxRetries) throws NetworkingError {
        log.info("Calling Remote Service to validate feedback for text block" + candidate.getId() + " with references of text cluster " + references.get(0).getCluster().getId());
        final Request request = new Request(candidate, references);

        final Response response = connector.invokeWithRetry(API_ENDPOINT, request, authenticationHeaderForSecret(API_SECRET), maxRetries);
        return response.confidence;
    }

}
