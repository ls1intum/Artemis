package de.tum.cit.aet.artemis.sharing.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.sharing.config.SharingEnabled;

/**
 * Health indicator that shows the status of the Sharing Platform connector.
 */
@Component
@Conditional(SharingEnabled.class)
@Lazy
public class SharingHealthIndicator implements HealthIndicator {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final DateTimeFormatter TIME_STAMP_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm:ss");

    private final SharingConnectorService sharingConnectorService;

    public SharingHealthIndicator(SharingConnectorService sharingConnectorService) {
        this.sharingConnectorService = sharingConnectorService;
    }

    /** Reports connection freshness and the ten most recent connector events, newest first. */
    @Override
    public Health health() {
        var snapshot = sharingConnectorService.getHealthSnapshot();
        Health.Builder health;
        if (snapshot.lastConnect() == null) {
            health = Health.status(ConnectorHealth.DEGRADED);
        }
        else if (snapshot.lastConnect().isBefore(Instant.now().minus(11, ChronoUnit.MINUTES))) {
            health = Health.unknown();
        }
        else {
            health = Health.up();
        }

        var entries = snapshot.entries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            String timestamp = TIME_STAMP_FORMATTER.format(entry.timestamp().atZone(UTC));
            health.withDetail("%3d: %s".formatted(i + 1, timestamp), entry.message());
        }
        return health.build();
    }
}
