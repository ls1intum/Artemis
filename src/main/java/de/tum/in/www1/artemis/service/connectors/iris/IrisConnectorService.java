package de.tum.in.www1.artemis.service.connectors.iris;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisErrorResponseDTO;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisMessageResponseDTO;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisRequestDTO;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisTemplateDTO;

@Service
public class IrisConnectorService {

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Value("${artemis.iris.url}")
    private String irisUrl;

    public IrisConnectorService(@Qualifier("irisRestTemplate") RestTemplate restTemplate, MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        this.restTemplate = restTemplate;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
    }

    @Async
    public CompletableFuture<Long> updateTemplate(long templateId, String template) {
        return saveTemplate(new IrisTemplateDTO(templateId, template)).thenApplyAsync(IrisTemplateDTO::templateId);
    }

    @Async
    public CompletableFuture<Long> saveNewTemplate(String template) {
        return saveTemplate(new IrisTemplateDTO(null, template)).thenApplyAsync(IrisTemplateDTO::templateId);
    }

    @Async
    public CompletableFuture<IrisMessageResponseDTO> sendRequest(long templateId, IrisModel preferredModel, Map<String, Object> parameters) {
        var request = new IrisRequestDTO(templateId, preferredModel, parameters);
        return sendRequest(request);
    }

    private CompletableFuture<IrisTemplateDTO> saveTemplate(IrisTemplateDTO template) {
        var response = restTemplate.postForEntity(irisUrl + "/template", template, JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            return CompletableFuture.failedFuture(new IrisConnectorException(parseResponse(response, IrisErrorResponseDTO.class).errorMessage()));
        }
        return CompletableFuture.completedFuture(parseResponse(response, IrisTemplateDTO.class));
    }

    private CompletableFuture<IrisMessageResponseDTO> sendRequest(IrisRequestDTO request) {
        var response = restTemplate.postForEntity(irisUrl + "/request", request, JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            return CompletableFuture.failedFuture(new IrisConnectorException(parseResponse(response, IrisErrorResponseDTO.class).errorMessage()));
        }
        return CompletableFuture.completedFuture(parseResponse(response, IrisMessageResponseDTO.class));
    }

    private <T> T parseResponse(ResponseEntity<JsonNode> response, Class<T> clazz) {
        try {
            return objectMapper.treeToValue(response.getBody(), clazz);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
