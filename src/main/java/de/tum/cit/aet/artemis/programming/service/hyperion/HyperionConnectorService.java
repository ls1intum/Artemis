package de.tum.cit.aet.artemis.programming.service.hyperion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.programming.service.hyperion.dto.HyperionSolutionGenerationRequest;
import de.tum.cit.aet.artemis.programming.service.hyperion.dto.HyperionSolutionGenerationResponse;

/**
 * Service for connecting to the Hyperion AI solution generation service
 */
@Lazy
@Service
@ConditionalOnProperty(name = "artemis.hyperion.enabled", havingValue = "true")
public class HyperionConnectorService {

    private static final Logger log = LoggerFactory.getLogger(HyperionConnectorService.class);

    private final RestTemplate restTemplate;

    @Value("${artemis.hyperion.url}")
    private String hyperionUrl;

    public HyperionConnectorService(@Qualifier("hyperionRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Generates a solution repository by calling the Hyperion API
     *
     * @param request The solution generation request
     * @return The response containing the generated repository files
     * @throws HyperionConnectorException if the request fails
     */
    public HyperionSolutionGenerationResponse generateSolution(HyperionSolutionGenerationRequest request) throws HyperionConnectorException {
        log.debug("Calling Hyperion to generate solution for exercise: {}", request.problemStatement().title());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<HyperionSolutionGenerationRequest> httpEntity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<HyperionSolutionGenerationResponse> response = restTemplate.postForEntity(hyperionUrl + "/api/generate-solution", httpEntity,
                    HyperionSolutionGenerationResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new HyperionConnectorException("Hyperion service returned an unsuccessful response: " + response.getStatusCode());
            }

            log.debug("Successfully received solution from Hyperion with {} files", response.getBody().repository().files().size());

            return response.getBody();

        }
        catch (RestClientException e) {
            log.error("Failed to call Hyperion service", e);
            throw new HyperionConnectorException("Failed to connect to Hyperion service", e);
        }
    }

    /**
     * Exception thrown when communication with Hyperion fails
     */
    public static class HyperionConnectorException extends Exception {

        public HyperionConnectorException(String message) {
            super(message);
        }

        public HyperionConnectorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
