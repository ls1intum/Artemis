package de.tum.cit.aet.artemis.programming.service.sharing;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Health indicator that shows the status of the Sharing Platform connector.
 */
@Component
@Profile(Constants.PROFILE_SHARING)
@Lazy
public class SharingHealthIndicator implements HealthIndicator {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final DateTimeFormatter TIME_STAMP_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm:ss");

    protected final SharingConnectorService sharingConnectorService;

    public SharingHealthIndicator(SharingConnectorService sharingConnectorService) {
        this.sharingConnectorService = sharingConnectorService;
    }

    /**
     * Returns the main health status (up/down or unknown if config request from Sharing Platform is too long ago), together
     * with a list of the 10 most recent log events for the sharing connector.
     */
    @Override
    public Health health() {
        SharingConnectorService.HealthStatusWithHistory lastHealthStati = sharingConnectorService.getLastHealthStati();
        Health.Builder health;
        if (lastHealthStati.getLastConnect() == null) {
            health = Health.down();
        }
        else if (lastHealthStati.getLastConnect().isBefore(Instant.now().minus(11, ChronoUnit.MINUTES))) {
            health = Health.unknown();
        }
        else {
            health = Health.up();
        }

        for (int i = lastHealthStati.size() - 1; i >= 0; i--) {
            SharingConnectorService.HealthStatus hs = lastHealthStati.get(i);
            ZonedDateTime zonedTimestamp = hs.getTimeStamp().atZone(UTC);
            String timeStamp = TIME_STAMP_FORMATTER.format(zonedTimestamp);
            health.withDetail(String.format("%3d: %s", i + 1, timeStamp), hs.getStatusMessage());
        }

        return health.build();
    }
}
