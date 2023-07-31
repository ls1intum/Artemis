package de.tum.in.www1.artemis.service.connectors.athena;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.exception.NetworkingError;

/**
 * Connector to Athena, a remote Artemis service that can create semi-automatic feedback suggestions for tutors.
 * <p>
 * The connector will be used to
 * - Send submissions to Athena
 * - Send existing feedback to Athena
 * - Request the next suggested submission to assess from Athena
 * - Receive feedback suggestions from Athena
 * <p>
 * As parameters, this class required DTO classes to serialize and deserialize POJOs to JSON and back.
 *
 * @param <RequestType>  DTO class, describing the body of the network request.
 * @param <ResponseType> DTO class, describing the body of the network response.
 */
class AthenaConnector<RequestType, ResponseType> {

    private final Logger log;

    private final RestTemplate restTemplate;

    private final Class<ResponseType> genericResponseType;

    AthenaConnector(Logger log, RestTemplate restTemplate, Class<ResponseType> genericResponseType) {
        this.log = log;
        this.restTemplate = restTemplate;
        this.genericResponseType = genericResponseType;
    }

    /**
     * Invoke Athena with a network call.
     *
     * @param url           Athena api endpoint
     * @param requestObject request body as POJO
     * @return response body from Athena
     * @throws NetworkingError exception in case of unsuccessful responses or responses without a body.
     */
    private ResponseType invoke(@NotNull String url, @NotNull RequestType requestObject) throws NetworkingError {
        long start = System.currentTimeMillis();
        log.debug("Calling Athena.");

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        final HttpEntity<RequestType> httpRequestEntity = new HttpEntity<>(requestObject, headers);

        final ResponseEntity<ResponseType> response = restTemplate.postForEntity(url, httpRequestEntity, genericResponseType);

        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            throw new NetworkingError("An Error occurred while calling Remote Artemis Service. Check Remote Logs for debugging information.");
        }

        final ResponseType responseBody = response.getBody();
        if (responseBody == null) {
            throw new NetworkingError("An Error occurred while calling Athena (response is null).");
        }
        log.debug("Finished remote call in {}ms", System.currentTimeMillis() - start);

        return responseBody;
    }

    /**
     * Invoke the Athena with a network call, but retry the request n times in case of an unsuccessful request.
     *
     * @param url           Athena api endpoint
     * @param requestObject request body as POJO
     * @param maxRetries    how many times to retry in case of an unsuccessful request.
     * @return response body from Athena
     * @throws NetworkingError exception in case of unsuccessful responses or responses without a body.
     */
    ResponseType invokeWithRetry(@NotNull String url, @NotNull RequestType requestObject, int maxRetries) throws NetworkingError {
        for (int retries = 0;; retries++) {
            try {
                return invoke(url, requestObject);
            }
            catch (NetworkingError | ResourceAccessException error) {
                if (retries >= maxRetries) {
                    throw new NetworkingError("An error occurred while calling Athena: " + error.getMessage(), error);
                }
            }
        }
    }
}
