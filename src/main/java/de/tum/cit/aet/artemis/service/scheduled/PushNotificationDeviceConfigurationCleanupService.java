package de.tum.cit.aet.artemis.service.scheduled;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.PushNotificationDeviceConfigurationRepository;
import tech.jhipster.config.JHipsterConstants;

@Service
@Profile(PROFILE_SCHEDULING)
public class PushNotificationDeviceConfigurationCleanupService {

    private final PushNotificationDeviceConfigurationRepository deviceConfigurationRepository;

    private final Environment env;

    public PushNotificationDeviceConfigurationCleanupService(PushNotificationDeviceConfigurationRepository deviceConfigurationRepository, Environment env) {
        this.deviceConfigurationRepository = deviceConfigurationRepository;
        this.env = env;
    }

    /**
     * cleans up old push notification device configurations (expired expiration_date) from the database at 3:00:00 am in the night in form of a repeating "cron"
     * job.
     */
    @Scheduled(cron = "0 0 3 * * *") // execute this every night at 3:00:00 am
    public void cleanup() {
        final Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (!activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            // only execute this on production server, i.e. when the prod profile is active
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }

        performCleanup();
    }

    // Visible for testing only because we cannot call the cron job directly.
    public void performCleanup() {
        deviceConfigurationRepository.deleteExpiredDeviceConfigurations();
    }
}
