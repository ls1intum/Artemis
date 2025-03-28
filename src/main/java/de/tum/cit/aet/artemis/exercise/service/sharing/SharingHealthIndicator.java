package de.tum.cit.aet.artemis.exercise.service.sharing;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("sharing")
public class SharingHealthIndicator implements HealthContributor, HealthIndicator {

    public static final ZoneId UTC = ZoneId.of("UTC");

    public static final DateTimeFormatter TIME_STAMP_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm:ss");

    protected final SharingConnectorService sharingConnectorService;

    public SharingHealthIndicator(SharingConnectorService sharingConnectorService) {
        super();
        this.sharingConnectorService = sharingConnectorService;
    }

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
