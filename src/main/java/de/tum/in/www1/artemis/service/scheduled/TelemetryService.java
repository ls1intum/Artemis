package de.tum.in.www1.artemis.service.scheduled;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_SCHEDULING;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Profile(PROFILE_SCHEDULING)
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    @Scheduled(cron = "0 0 3 ? * MON#1,MON#3")// execute this every night at 3:00:00 am
    public void sendTelemetry() {
        log.info("Sending telemetry information");
    }

}
