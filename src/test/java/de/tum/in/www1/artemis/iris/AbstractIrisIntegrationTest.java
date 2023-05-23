package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.IrisGPT3_5RequestMockProvider;
import de.tum.in.www1.artemis.domain.iris.IrisMessage;

@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "bamboo", "bitbucket", "jira", "ldap", "scheduling", "athene", "apollon", "iris-gpt3_5" })
class AbstractIrisIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    @Qualifier("irisGPT3_5RequestMockProvider")
    protected IrisGPT3_5RequestMockProvider gpt35RequestMockProvider;

    @BeforeEach
    void setup() {
        gpt35RequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        gpt35RequestMockProvider.reset();
    }

    protected void verifyMessageWasSentOverWebsocket(String user, Long sessionId, String message) throws InterruptedException {
        Thread.sleep(1000);
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId), ArgumentMatchers.assertArg(object -> {
            if (object instanceof IrisMessage irisMessage) {
                var contents = irisMessage.getContent();
                assertThat(contents).hasSize(1);
                var irisMessageContent = contents.get(0);
                assertThat(irisMessageContent.getTextContent()).isEqualTo(message);
            }
        }));
    }
}
