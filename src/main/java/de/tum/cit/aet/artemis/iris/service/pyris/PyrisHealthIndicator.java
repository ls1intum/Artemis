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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisHealthStatusDTO;

// ...imports...

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

    @Override
    public Health health() {
        return health(false);
    }

    public Health health(boolean useCache) {
        if (useCache && cachedHealth != null && System.currentTimeMillis() - lastUpdated < CACHE_TTL) {
            return cachedHealth;
        }

        ConnectorHealth connectorHealth;
        var additionalInfo = new HashMap<String, Object>();
        additionalInfo.put(IRIS_URL_KEY, irisUrl);

        try {
            ResponseEntity<PyrisHealthStatusDTO> response = restTemplate.getForEntity(irisUrl + "/api/v1/health/", PyrisHealthStatusDTO.class);

            boolean overallUp = response.getStatusCode().is2xxSuccessful();

            PyrisHealthStatusDTO body = response.getBody();
            if (body != null && body.modules() != null) {
                flattenModulesInto(additionalInfo, body.modules());
            }

            connectorHealth = new ConnectorHealth(overallUp, additionalInfo, null);
        }
        catch (HttpStatusCodeException e) {
            try {
                var parsed = objectMapper.readValue(e.getResponseBodyAsString(), PyrisHealthStatusDTO.class);
                if (parsed != null && parsed.modules() != null) {
                    flattenModulesInto(additionalInfo, parsed.modules());
                }
            }
            catch (Exception ignore) {
                /* body may be empty or not JSON */ }
            connectorHealth = new ConnectorHealth(false, additionalInfo, null);
        }
        catch (RestClientException e) {
            String icon = iconFor(PyrisHealthStatusDTO.ServiceStatus.DOWN);
            additionalInfo.put("error", icon + " Connection to Pyris failed");
            connectorHealth = new ConnectorHealth(false, additionalInfo, null);
        }

        var newHealth = connectorHealth.asActuatorHealth();
        cachedHealth = newHealth;
        lastUpdated = System.currentTimeMillis();
        return newHealth;
    }

    private static void flattenModulesInto(Map<String, Object> target, Map<String, PyrisHealthStatusDTO.ModuleStatusDTO> modules) {
        if (modules == null)
            return;
        modules.forEach((name, m) -> target.put(name, summarizeModule(m)));
    }

    private static String summarizeModule(PyrisHealthStatusDTO.ModuleStatusDTO m) {
        String icon = iconFor(m.status());

        StringBuilder sb = new StringBuilder().append(icon).append(' ');
        if (m.error() != null && !m.error().isBlank()) {
            sb.append(m.error());
            return sb.toString();
        }
        if (m.metaData() != null && !m.metaData().isBlank())
            sb.append(m.metaData());

        return sb.toString();
    }

    private static String iconFor(PyrisHealthStatusDTO.ServiceStatus status) {
        return switch (status) {
            case UP -> GREEN_CIRCLE;
            case WARN -> YELLOW_CIRCLE;
            case DEGRADED -> ORANGE_CIRCLE;
            case DOWN -> RED_CIRCLE;
        };
    }
}
