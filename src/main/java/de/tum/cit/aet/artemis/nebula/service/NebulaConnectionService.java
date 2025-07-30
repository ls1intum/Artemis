package de.tum.cit.aet.artemis.nebula.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorException;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.dto.FaqConsistencyDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqConsistencyResponse;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingResponse;
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

    public NebulaConnectionService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the FAQ rewriting operation by sending a request to the Nebula service.
     *
     * @param faqRewritingDTO the data transfer object containing the necessary information for rewriting FAQs
     */
    public FaqRewritingResponse executeFaqRewriting(FaqRewritingDTO faqRewritingDTO) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<FaqRewritingDTO> request = new HttpEntity<>(faqRewritingDTO, headers);
            ResponseEntity<FaqRewritingResponse> response = restTemplate.exchange(nebulaUrl + "/faq/rewrite-faq", HttpMethod.POST, request, FaqRewritingResponse.class);
            return response.getBody();
        }
        catch (HttpStatusCodeException e) {
            throw toNebulaException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to fetch response from Nebula", e);
            throw new PyrisConnectorException("Could not fetch response from Nebula");
        }
    }

    /**
     * Executes the FAQ rewriting operation by sending a request to the Nebula service.
     *
     * @param faqConsistencyDTO the data transfer object containing the necessary information for consistency check the current FAQs
     */
    public FaqConsistencyResponse executeFaqConsistencyCheck(FaqConsistencyDTO faqConsistencyDTO) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<FaqConsistencyDTO> request = new HttpEntity<>(faqConsistencyDTO, headers);
            ResponseEntity<FaqConsistencyResponse> response = restTemplate.exchange(nebulaUrl + "/faq/check-consistency", HttpMethod.POST, request, FaqConsistencyResponse.class);
            return response.getBody();
        }
        catch (HttpStatusCodeException e) {
            throw toNebulaException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to fetch response from Nebula", e);
            throw new PyrisConnectorException("Could not fetch response from Nebula");
        }
    }

    private NebulaException toNebulaException(HttpStatusCodeException e) {
        return switch (e.getStatusCode().value()) {
            case 401, 403 -> new NebulaForbiddenException();
            case 400, 500 -> new NebulaInternalErrorException(tryExtractErrorMessage(e));
            default -> new NebulaInternalErrorException(e.getMessage());
        };
    }

    private String tryExtractErrorMessage(HttpStatusCodeException ex) {
        try {
            return objectMapper.readTree(ex.getResponseBodyAsString()).required("detail").required("errorMessage").asText();
        }
        catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Failed to parse error message from Pyris", e);
            return "";
        }
    }

}
