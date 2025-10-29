package de.tum.cit.aet.artemis.nebula.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.exception.NebulaException;
import de.tum.cit.aet.artemis.nebula.exception.NebulaForbiddenException;
import de.tum.cit.aet.artemis.nebula.exception.NebulaInternalErrorException;

@Conditional(NebulaEnabled.class)
@Lazy
@Service
public class NebulaConnectionService {

    private static final Logger log = LoggerFactory.getLogger(NebulaConnectionService.class);

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Value("${server.url}")
    private String artemisBaseUrl;

    @Value("${artemis.nebula.url}")
    private String nebulaUrl;

    @Value("${artemis.nebula.secret-token}")
    private String nebulaSecretToken;

    public NebulaConnectionService(@Qualifier("nebulaRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Converts a given HttpStatusCodeException to a NebulaException.
     *
     * @param e the HttpStatusCodeException to convert
     * @return the corresponding NebulaException
     */
    public NebulaException toNebulaException(HttpStatusCodeException e) {
        return switch (e.getStatusCode().value()) {
            case 401, 403 -> new NebulaForbiddenException();
            case 400, 500 -> new NebulaInternalErrorException(tryExtractErrorMessage(e));
            default -> new NebulaInternalErrorException(e.getMessage());
        };
    }

    /**
     * Tries to extract a detailed error message from the response body of a HttpStatusCodeException.
     *
     * @param ex the HttpStatusCodeException containing the response body
     * @return the extracted error message, or an empty string if extraction fails
     */
    public String tryExtractErrorMessage(HttpStatusCodeException ex) {
        try {
            return objectMapper.readTree(ex.getResponseBodyAsString()).required("detail").required("errorMessage").asText();
        }
        catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Failed to parse error message from Nebula", e);
            return "";
        }
    }

    private HttpHeaders createNebulaHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", nebulaSecretToken);
        return headers;
    }

}
