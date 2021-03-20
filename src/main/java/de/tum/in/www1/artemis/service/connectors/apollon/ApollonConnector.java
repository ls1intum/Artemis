package de.tum.in.www1.artemis.service.connectors.apollon;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.exception.NetworkingError;

/**
 * This class allows for an easy implementation of Connectors to Remote Apollon Services.
 * As parameters, this class required DTO classes to serialize and deserialize POJOs to JSON and back.
 *
 */
class ApollonConnector {

    private final Logger log;

    private final RestTemplate restTemplate;

    private final Class<ResponseDTO> genericResponseType;

    ApollonConnector(Logger log, RestTemplate restTemplate) {
        this.log = log;
        this.restTemplate = restTemplate;
        this.genericResponseType = ResponseDTO.class;
    }

    // region Request/Response DTOs
    public static class RequestDTO {

        public String diagram;

        public String callbackUrl;

        public List<TextSubmission> submissions;

        RequestDTO(@NotNull String diagram) {
            this.diagram = diagram;
        }

    }

    public static class ResponseDTO {

        public String detail;

    }
    // endregion

    /**
     * Invoke the remote service with a network call.
     *
     * @param url remote service api endpoint
     * @param requestObject request body as POJO
     * @return response body from remote service
     * @throws NetworkingError exception in case of unsuccessful responses or responses without a body.
     */
    ResponseDTO invoke(@NotNull String url, @NotNull RequestDTO requestObject) throws NetworkingError {
        long start = System.currentTimeMillis();
        log.debug("Calling Remote Artemis Service.");

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        final HttpEntity<RequestDTO> httpRequestEntity = new HttpEntity<>(requestObject, headers);

        final ResponseEntity<ResponseDTO> response = restTemplate.postForEntity(url, httpRequestEntity, genericResponseType);

        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            throw new NetworkingError("An Error occurred while calling Remote Artemis Service. Check Remote Logs for debugging information.");
        }

        final ResponseDTO responseBody = response.getBody();
        assert responseBody != null;
        log.debug("Finished remote call in " + (System.currentTimeMillis() - start) + "ms");

        return responseBody;
    }
}
