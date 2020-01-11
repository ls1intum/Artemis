package de.tum.in.www1.artemis;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import com.atlassian.bamboo.specs.util.BambooServer;

import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ "artemis", "bamboo", "bitbucket", "jira", "automaticText" })
public abstract class AbstractSpringIntegrationTest {

    // NOTE: we prefer SpyBean over MockBean, because it is more lightweight, we can mock method, but we can also invoke actual methods during testing
    @SpyBean
    protected LtiService ltiService;

    // TODO: we should not really mock BambooService, but only the API calls to Bamboo (e.g. based on the used RestTemplate)
    @SpyBean
    protected BambooService continuousIntegrationService;

    // TODO: we should not really mock BitbucketService, but only the API calls to Bitbucket (e.g. based on the used RestTemplate)
    @SpyBean
    protected BitbucketService versionControlService;

    @SpyBean
    protected BambooServer bambooServer;

    @SpyBean
    protected GitService gitService;

    @SpyBean
    protected GroupNotificationService groupNotificationService;

    @SpyBean
    protected WebsocketMessagingService websocketMessagingService;
}
