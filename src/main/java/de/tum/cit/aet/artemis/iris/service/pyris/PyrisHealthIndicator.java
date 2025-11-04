package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisHealthStatusDTO;

@Component
@Lazy
@Profile(PROFILE_IRIS)
public class PyrisHealthIndicator implements HealthIndicator {

    private static final String GREEN_CIRCLE = "\uD83D\uDFE2"; // 🟢

    private static final String YELLOW_CIRCLE = "\uD83D\uDFE1"; // 🟡

    private static final String ORANGE_CIRCLE = "\uD83D\uDFE0"; // 🟠

    private static final String RED_CIRCLE = "\uD83D\uDD34"; // 🔴

    @Value("${artemis.iris.health-ttl:30000}")
    private int CACHE_TTL;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String IRIS_URL_KEY = "url";

    @Value("${artemis.iris.url}")
    private URI irisUrl;

    private long lastUpdated = 0;

    private Health cachedHealth = null;

    public PyrisHealthIndicator(@Qualifier("shortTimeoutPyrisRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Reports the current health of the Pyris connector.
     * <p>
     * Called by Spring Boot Actuator; delegates to {@link #health(boolean)} with caching disabled.
     * </p>
     *
     * @return the connector health
     */
    @Override
    public Health health() {
        return health(false);
    }

    /**
     * Computes or returns a cached health state.
     *
     * @param useCache if {@code true}, return a cached value when still within the TTL
     * @return Actuator {@link Health} describing the Pyris connector status
     */
    public Health health(boolean useCache) {
        if (useCache && cachedHealth != null && System.currentTimeMillis() - lastUpdated < CACHE_TTL) {
            return cachedHealth;
        }

        ConnectorHealth connectorHealth;
        URI healthUri = UriComponentsBuilder.fromUri(irisUrl).path("/api/v1/health/").build(true).toUri();
        var additionalInfo = new HashMap<String, Object>();
        additionalInfo.put(IRIS_URL_KEY, irisUrl);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(healthUri, String.class);
            String json = response.getBody();

            if (json == null || json.isBlank() || "null".equalsIgnoreCase(json.trim())) {
                connectorHealth = fail(additionalInfo, "Empty response from Pyris");
            }
            else {
                try {
                    PyrisHealthStatusDTO body = objectMapper.readValue(json, PyrisHealthStatusDTO.class);
                    flattenModulesInto(additionalInfo, body.modules());
                    connectorHealth = new ConnectorHealth(body.isHealthy(), additionalInfo, null);
                }
                catch (JsonProcessingException e) {
                    connectorHealth = fail(additionalInfo, "Incorrect format from Pyris");
                }
            }
        }
        catch (ResourceAccessException e) {
            connectorHealth = fail(additionalInfo, "Connection to Pyris timed out");
        }
        catch (RestClientException e) {
            connectorHealth = fail(additionalInfo, "Connection to Pyris failed");
        }

        var newHealth = connectorHealth.asActuatorHealth();
        cachedHealth = newHealth;
        lastUpdated = System.currentTimeMillis();
        return newHealth;
    }

    private static void flattenModulesInto(Map<String, Object> target, Map<String, PyrisHealthStatusDTO.ModuleStatusDTO> modules) {
        if (modules == null) {
            return;
        }
        modules.forEach((name, m) -> {
            if (m == null) {
                target.put(name, iconFor(null) + " No data");
            }
            else {
                target.put(name, summarizeModule(m));
            }
        });
    }

    private static String summarizeModule(PyrisHealthStatusDTO.ModuleStatusDTO module) {
        String icon = iconFor(module.status());

        StringBuilder sb = new StringBuilder().append(icon).append(' ');
        if (module.error() != null && !module.error().isBlank()) {
            sb.append(module.error());
            return sb.toString();
        }
        if (module.metaData() != null && !module.metaData().isBlank()) {
            sb.append(module.metaData());
        }
        return sb.toString();
    }

    private static String iconFor(PyrisHealthStatusDTO.ServiceStatus status) {
        if (status == null) {
            return RED_CIRCLE;
        }
        return switch (status) {
            case UP -> GREEN_CIRCLE;
            case WARN -> YELLOW_CIRCLE;
            case DEGRADED -> ORANGE_CIRCLE;
            case DOWN -> RED_CIRCLE;
        };
    }

    private static ConnectorHealth fail(Map<String, Object> info, String message) {
        info.put("error", iconFor(PyrisHealthStatusDTO.ServiceStatus.DOWN) + " " + message);
        return new ConnectorHealth(false, info, null);
    }
}
