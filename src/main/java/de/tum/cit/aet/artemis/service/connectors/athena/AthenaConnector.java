package de.tum.cit.aet.artemis.service.connectors.athena;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.exception.NetworkingException;
import de.tum.cit.aet.artemis.service.util.TimeLogUtil;

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

    private static final Logger log = LoggerFactory.getLogger(AthenaConnector.class);

    private final RestTemplate restTemplate;

    private final Class<ResponseType> genericResponseType;

    AthenaConnector(RestTemplate restTemplate, Class<ResponseType> genericResponseType) {
        this.restTemplate = restTemplate;
        this.genericResponseType = genericResponseType;
    }

    /**
     * Invoke Athena with a network call.
     *
     * @param url           Athena api endpoint
     * @param requestObject request body as POJO
     * @return response body from Athena
     * @throws NetworkingException exception in case of unsuccessful responses or responses without a body.
     */
    private ResponseType invoke(@NotNull String url, @NotNull RequestType requestObject) throws NetworkingException {
        long start = System.nanoTime();
        log.debug("Calling Athena.");

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        final HttpEntity<RequestType> httpRequestEntity = new HttpEntity<>(requestObject, headers);

        ResponseEntity<ResponseType> response;
        try {
            response = restTemplate.postForEntity(url, httpRequestEntity, genericResponseType);
        }
        catch (ResourceAccessException e) {
            log.error("Athena did not respond successfully in time", e);
            throw new NetworkingException("An Error occurred while calling Athena. Check Remote Logs for debugging information.", e);
        }

        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            log.error("Athena responded with an error: {}", response);
            throw new NetworkingException("An Error occurred while calling Athena. Check Remote Logs for debugging information.");
        }

        final ResponseType responseBody = response.getBody();
        if (responseBody == null) {
            throw new NetworkingException("An Error occurred while calling Athena (response is null).");
        }
        log.debug("Finished remote call in {}", TimeLogUtil.formatDurationFrom(start));

        return responseBody;
    }

    /**
     * Invoke Athena with a network call, but retry the request n times in case of an unsuccessful request.
     *
     * @param url           Athena api endpoint
     * @param requestObject request body as POJO
     * @param maxRetries    how many times to retry in case of an unsuccessful request.
     * @return response body from Athena
     * @throws NetworkingException exception in case of unsuccessful responses or responses without a body.
     */
    ResponseType invokeWithRetry(@NotNull String url, @NotNull RequestType requestObject, int maxRetries) throws NetworkingException {
        for (int retries = 0;; retries++) {
            try {
                return invoke(url, requestObject);
            }
            catch (NetworkingException | ResourceAccessException error) {
                if (retries >= maxRetries) {
                    throw new NetworkingException("An error occurred while calling Athena: " + error.getMessage(), error);
                }
            }
        }
    }
}
