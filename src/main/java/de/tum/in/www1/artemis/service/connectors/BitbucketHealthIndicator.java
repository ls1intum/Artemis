package de.tum.in.www1.artemis.service.connectors;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("bitbucket")
@Component
public class BitbucketHealthIndicator implements HealthIndicator {

    private final BitbucketService bitbucketService;

    public BitbucketHealthIndicator(BitbucketService bitbucketService) {
        this.bitbucketService = bitbucketService;
    }

    @Override
    public Health health() {
        return bitbucketService.health().asActuatorHealth();
    }
}
