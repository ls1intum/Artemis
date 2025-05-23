package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationApiType;
import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceType;
import de.tum.cit.aet.artemis.communication.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PushNotificationDeviceConfigurationCleanupServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private PushNotificationDeviceConfigurationRepository deviceConfigurationRepository;

    @Autowired
    private PushNotificationDeviceConfigurationCleanupService cleanupService;

    @Autowired
    private UserUtilService userUtilService;

    private User user;

    @BeforeEach
    void setupUser() {
        user = userUtilService.createAndSaveUser("test-user");
        deviceConfigurationRepository.deleteAll();
    }

    @Test
    void cleanupTest() {
        final PushNotificationDeviceConfiguration valid = new PushNotificationDeviceConfiguration("token1", PushNotificationDeviceType.FIREBASE,
                Date.from(Instant.now().plus(10, ChronoUnit.DAYS)), new byte[10], user, PushNotificationApiType.DEFAULT, "1.0.0");

        final PushNotificationDeviceConfiguration expired = new PushNotificationDeviceConfiguration("token2", PushNotificationDeviceType.FIREBASE,
                Date.from(Instant.now().minus(10, ChronoUnit.DAYS)), new byte[10], user, PushNotificationApiType.DEFAULT, "1.0.0");

        deviceConfigurationRepository.save(valid);
        deviceConfigurationRepository.save(expired);

        cleanupService.performCleanup();

        List<PushNotificationDeviceConfiguration> result = deviceConfigurationRepository.findByUserIn(Set.of(user), PushNotificationDeviceType.FIREBASE);

        assertThat(result).contains(valid);
        assertThat(result).doesNotContain(expired);
    }
}
