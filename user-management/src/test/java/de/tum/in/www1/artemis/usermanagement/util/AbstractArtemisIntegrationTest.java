package de.tum.in.www1.artemis.usermanagement.util;

import de.tum.in.www1.artemis.usermanagement.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;

import de.tum.in.www1.artemis.service.FileService;
import org.springframework.context.annotation.Import;

/**
 * This test should be completely independent of any profiles or configurations (e.g. VCS, CIS)
 */
@Import(ActiveMqArtemisMockProvider.class)
public abstract class AbstractArtemisIntegrationTest implements MockDelegate {

    @Value("${server.url}")
    protected String artemisServerUrl;

    @SpyBean
    protected InstanceMessageSendService instanceMessageSendService;

    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected RequestUtilService request;

    public void resetSpyBeans() {
        Mockito.reset(instanceMessageSendService);
    }
}
