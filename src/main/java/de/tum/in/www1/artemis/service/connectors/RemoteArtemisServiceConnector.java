package de.tum.in.www1.artemis.service.connectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.exception.NetworkingError;

/**
 * This class allows for an easy implementation of Connectors to Remote Artemis Services (e.g. the Text Clustering System).
 * As parameters, this class required DTO classes do serialize and deserialize POJOs to JSON and back.
 *
 * @param <RequestType> DTO class, describing the body of the network request.
 * @param <ResponseType> DTO class, describing the body of the network response.
 */
class RemoteArtemisServiceConnector<RequestType, ResponseType> {

    private final Logger log;

    private final RestTemplate restTemplate = new RestTemplate();

    private final Class<ResponseType> genericResponseType;

    RemoteArtemisServiceConnector(Logger log, Class<ResponseType> genericResponseType) {
        this.log = log;
        this.genericResponseType = genericResponseType;
    }

    /**
     * Invoke the remove service with a network call.
     *
     * @param url remote service api endpoint
     * @param requestObject request body as POJO
     * @return response body from remote service
     * @throws NetworkingError exception in case of unsuccessful responses or responses without a body.
     */
    ResponseType invoke(@NotNull String url, @NotNull RequestType requestObject) throws NetworkingError {
        return invoke(url, requestObject, null);
    }

    /**
     * Invoke the remove service with a network call.
     *
     * @param url remote service api endpoint
     * @param requestObject request body as POJO
     * @param headers HTTP headers to use with network call, e.g. for authentication.
     * @return response body from remote service
     * @throws NetworkingError exception in case of unsuccessful responses or responses without a body.
     */
    ResponseType invoke(@NotNull String url, @NotNull RequestType requestObject, HttpHeaders headers) throws NetworkingError {
        long start = System.currentTimeMillis();
        log.debug("Calling Remote Artemis Service.");

        final HttpEntity<RequestType> httpRequestEntity = new HttpEntity<>(requestObject, headers);

        final ResponseEntity<ResponseType> response = restTemplate.postForEntity(url, httpRequestEntity, genericResponseType);

        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            throw new NetworkingError("An Error occurred while calling Remote Artemis Service. Check Remote Logs for debugging information.");
        }

        final ResponseType responseBody = response.getBody();
        assert responseBody != null;
        log.debug("Finished remote call in " + (System.currentTimeMillis() - start) + "ms");

        return responseBody;
    }

    /**
     * Invoke the remove service with a network call, but retry the request n times in case of an unsuccessful request.
     *
     * @param url remote service api endpoint
     * @param requestObject request body as POJO
     * @param maxRetries how many times to retry in case of an unsuccessful request.
     * @return response body from remote service
     * @throws NetworkingError exception in case of unsuccessful responses or responses without a body.
     */
    ResponseType invokeWithRetry(@NotNull String url, @NotNull RequestType requestObject, int maxRetries) throws NetworkingError {
        return invokeWithRetry(url, requestObject, null, maxRetries);
    }

    /**
     * Invoke the remove service with a network call, but retry the request n times in case of an unsuccessful request.
     *
     * @param url remote service api endpoint
     * @param requestObject request body as POJO
     * @param headers HTTP headers to use with network call, e.g. for authentication.
     * @param maxRetries how many times to retry in case of an unsuccessful request.
     * @return response body from remote service
     * @throws NetworkingError exception in case of unsuccessful responses or responses without a body.
     */
    ResponseType invokeWithRetry(@NotNull String url, @NotNull RequestType requestObject, HttpHeaders headers, int maxRetries) throws NetworkingError {
        for (int retries = 0;; retries++) {
            try {
                return invoke(url, requestObject, headers);
            }
            catch (NetworkingError error) {
                if (retries >= maxRetries) {
                    throw error;
                }
            }
        }
    }

    /**
     * Helper to generate HttpHeaders for a Bearer Token.
     *
     * @param secret Authentication Token
     * @return HttpHeaders
     */
    static HttpHeaders authenticationHeaderForSecret(String secret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secret);
        return headers;
    }
}
