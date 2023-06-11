package de.tum.in.www1.artemis.service.scheduled;

import static org.springframework.test.util.AssertionErrors.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceType;
import de.tum.in.www1.artemis.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

class PushNotificationDeviceConfigurationCleanupServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private PushNotificationDeviceConfigurationRepository deviceConfigurationRepository;

    @Autowired
    private PushNotificationDeviceConfigurationCleanupService cleanupService;

    @Autowired
    private DatabaseUtilService database;

    private User user;

    @BeforeEach
    void setupUser() {
        user = database.createAndSaveUser("test-user");
        deviceConfigurationRepository.deleteAll();
    }

    @Test
    void cleanupTest() {
        final PushNotificationDeviceConfiguration valid = new PushNotificationDeviceConfiguration("token1", PushNotificationDeviceType.FIREBASE,
                Date.from(Instant.now().plus(10, ChronoUnit.DAYS)), new byte[10], user);

        final PushNotificationDeviceConfiguration expired = new PushNotificationDeviceConfiguration("token2", PushNotificationDeviceType.FIREBASE,
                Date.from(Instant.now().minus(10, ChronoUnit.DAYS)), new byte[10], user);

        deviceConfigurationRepository.save(valid);
        deviceConfigurationRepository.save(expired);

        cleanupService.performCleanup();

        List<PushNotificationDeviceConfiguration> result = deviceConfigurationRepository.findByUserIn(Collections.singletonList(user), PushNotificationDeviceType.FIREBASE);

        assertEquals("The result is not correct", Collections.singletonList(valid), result);
    }
}
