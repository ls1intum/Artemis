package de.tum.in.www1.artemis.service.connectors;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.exception.NetworkingError;

class RemoteArtemisServiceConnector<RequestType, ResponseType> {

    private final Logger log;

    private final RestTemplate restTemplate = new RestTemplate();

    private final Class<ResponseType> genericResponseType;

    RemoteArtemisServiceConnector(Logger log, Class<ResponseType> genericResponseType) {
        this.log = log;
        this.genericResponseType = genericResponseType;
    }

    ResponseType invoke(@NotNull String url, @NotNull RequestType requestObject) throws NetworkingError {
        return invoke(url, requestObject, null);
    }

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
        log.info("Finished remote call in " + (System.currentTimeMillis() - start) + "ms");

        return responseBody;
    }

    ResponseType invokeWithRetry(@NotNull String url, @NotNull RequestType requestObject, int maxRetries) throws NetworkingError {
        return invokeWithRetry(url, requestObject, null, maxRetries);
    }

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

    static HttpHeaders authenticationHeaderForSecret(String secret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secret);
        return headers;
    }
}
