package de.tum.in.www1.artemis.service.connectors.iris;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisStatusDTO;

@Component
@Profile("iris")
public class IrisHealthIndicator implements HealthIndicator {

    private final RestTemplate shortTimeoutRestTemplate;

    private final ObjectMapper objectMapper;

    @Value("${artemis.iris.url}")
    private URI irisUrl;

    public IrisHealthIndicator(@Qualifier("shortTimeoutIrisRestTemplate") RestTemplate shortTimeoutRestTemplate, MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
    }

    /**
     * Ping Iris at /health and check if the service is available and what its status is.
     */
    @Override
    public Health health() {
        ConnectorHealth health;
        try {
            var response = shortTimeoutRestTemplate.getForEntity(irisUrl + "/api/v1/health", JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
                health = new ConnectorHealth(false);
                return health.asActuatorHealth();
            }
            IrisStatusDTO[] status = (IrisStatusDTO[]) parseResponse(response.getBody(), IrisStatusDTO.class.arrayType());
            var isUp = status != null && Arrays.stream(status).anyMatch(s -> s.status() == IrisStatusDTO.ModelStatus.UP);
            Map<String, Object> additionalInfo = Map.of("url", irisUrl, "modelStatuses", status);
            health = new ConnectorHealth(isUp, additionalInfo);
        }
        catch (Exception emAll) {
            health = new ConnectorHealth(emAll);
            health.setUp(false);
            health.setAdditionalInfo(Map.of("url", irisUrl));
        }

        return health.asActuatorHealth();
    }

    private <T> T parseResponse(JsonNode response, Class<T> clazz) {
        try {
            return objectMapper.treeToValue(response, clazz);
        }
        catch (JsonProcessingException e) {
            return null;
        }
    }
}
