package de.tum.in.www1.artemis.service.connectors.apollon;

import java.io.IOException;
import java.io.InputStream;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

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

        public String model;

        RequestDTO(@NotNull String diagram) {
            this.model = diagram;
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
    InputStream invoke(@NotNull String url, @NotNull RequestDTO requestObject) throws NetworkingError {
        long start = System.currentTimeMillis();
        log.debug("Calling Remote Artemis Service.");
        System.out.println("url");
        System.out.println(url);
        System.out.println("requestObject");
        System.out.println(requestObject.model);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        final HttpEntity<RequestDTO> httpRequestEntity = new HttpEntity<>(requestObject, headers);
        System.out.println("requestBody");
        System.out.println(httpRequestEntity.getBody().model);
        final ResponseEntity<Resource> response = restTemplate.exchange(url, HttpMethod.POST, httpRequestEntity, Resource.class);

        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            throw new NetworkingError("An Error occurred while calling Remote Artemis Service. Check Remote Logs for debugging information.");
        }
        InputStream responseInputStream;
        assert response.getBody() != null;
        try {
            responseInputStream = response.getBody().getInputStream();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.debug("Finished remote call in " + (System.currentTimeMillis() - start) + "ms");

        return responseInputStream;
    }
}
