package de.tum.cit.aet.artemis.core.service.connectors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the JHipster Registry (Eureka) service discovery.
 * Reports the connection status to the Eureka server and information about registered instances.
 */
@Component
@Lazy
@ConditionalOnProperty(value = "eureka.client.enabled", havingValue = "true")
public class JHipsterRegistryHealthIndicator implements HealthIndicator {

    private final DiscoveryClient discoveryClient;

    private final Optional<Registration> registration;

    public JHipsterRegistryHealthIndicator(DiscoveryClient discoveryClient, Optional<Registration> registration) {
        this.discoveryClient = discoveryClient;
        this.registration = registration;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();

        try {
            // Get all known services from Eureka
            List<String> services = discoveryClient.getServices();
            details.put("knownServices", services.size());

            // Check if this instance is registered
            if (registration.isPresent()) {
                Registration reg = registration.get();
                String serviceId = reg.getServiceId();
                details.put("serviceId", serviceId);
                details.put("instanceId", reg.getInstanceId());
                details.put("host", reg.getHost());
                details.put("port", reg.getPort());

                // Get all instances of our service
                List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
                details.put("registeredInstances", instances.size());

                // Check if our instance is among the registered ones
                String ourInstanceId = reg.getInstanceId();
                boolean isRegistered = instances.stream().anyMatch(instance -> ourInstanceId.equals(instance.getInstanceId()));
                details.put("thisInstanceRegistered", isRegistered);
            }
            else {
                details.put("registration", "not available");
            }

            // If we got here without exceptions, the connection is working
            ConnectorHealth health = new ConnectorHealth(true, details);
            return health.asActuatorHealth();
        }
        catch (Exception e) {
            details.put("error", e.getMessage());
            ConnectorHealth health = new ConnectorHealth(e);
            return health.asActuatorHealth();
        }
    }
}
