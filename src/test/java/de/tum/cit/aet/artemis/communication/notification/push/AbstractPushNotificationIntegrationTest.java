package de.tum.cit.aet.artemis.communication.notification.push;

import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.repository.NotificationSettingRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

public abstract class AbstractPushNotificationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    protected NotificationSettingRepository notificationSettingRepository;

    @Autowired
    protected UserUtilService userUtilService;

    @Autowired
    protected UserTestRepository userRepository;
}
