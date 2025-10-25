package de.tum.cit.aet.artemis.programming.service.sharing;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Health indicator that shows the status of the Sharing Platform connector.
 */
@Component
@Conditional(SharingEnabled.class)
@Lazy
public class SharingHealthIndicator implements HealthIndicator {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final DateTimeFormatter TIME_STAMP_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm:ss");

    @Value("${artemis.sharing.enabled:'undefined'}")
    private String sharingEnabled;

    private final Optional<SharingConnectorService> sharingConnectorService;

    public SharingHealthIndicator(Optional<SharingConnectorService> sharingConnectorService) {
        this.sharingConnectorService = sharingConnectorService;
    }

    /**
     * returns the main health status (up/down or unknown if config request from Sharing Platform is too long ago), together
     * with a list of the 10 most recent log events for the sharing connector.
     */
    @Override
    public Health health() {
        Health.Builder health;
        if ("true".equals(sharingEnabled) && sharingConnectorService.isPresent()) {

            SharingConnectorService.HealthStatusWithHistory lastHealthStati = sharingConnectorService.get().getLastHealthStati();
            if (lastHealthStati.getLastConnect() == null) {
                health = Health.down();
            }
            else if (lastHealthStati.getLastConnect().isBefore(Instant.now().minus(11, ChronoUnit.MINUTES))) {
                health = Health.unknown();
            }
            else {
                health = Health.up();
            }
            UnmodifiableList<SharingConnectorService.HealthStatus> lastStati = new UnmodifiableList<>(lastHealthStati);

            for (int i = lastStati.size() - 1; i >= 0; i--) {
                SharingConnectorService.HealthStatus hs = lastStati.get(i);
                ZonedDateTime zonedTimestamp = hs.getTimeStamp().atZone(UTC);
                String timeStamp = TIME_STAMP_FORMATTER.format(zonedTimestamp);
                health.withDetail(String.format("%3d: %s", i + 1, timeStamp), hs.getStatusMessage());
            }

        }
        else {
            health = Health.down().withDetail("Status", "Sharing seems not configured: prop \"artemis.sharing.enabled\" is " + sharingEnabled + ". Should be \"true\"");
        }
        return health.build();
    }
}
