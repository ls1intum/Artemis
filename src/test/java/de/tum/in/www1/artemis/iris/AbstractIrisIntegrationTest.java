package de.tum.in.www1.artemis.iris;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.IrisGPT3_5RequestMockProvider;
import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.service.iris.IrisWebsocketService;

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

    /**
     * Wait for the iris message to be processed by Iris, the LLM mock and the websocket service.
     *
     * @throws InterruptedException if the thread is interrupted
     */
    protected void waitForIrisMessageToBeProcessed() throws InterruptedException {
        Thread.sleep(500);
    }

    /**
     * Verify that the message was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     * @param message   the content of the message
     */
    protected void verifyMessageWasSentOverWebsocket(String user, Long sessionId, String message) {
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.IRIS_MESSAGE
                        && websocketDTO.getMessage().getContent().size() == 1 && Objects.equals(websocketDTO.getMessage().getContent().get(0).getTextContent(), message)));
    }

    /**
     * Verify that the message was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     * @param message   the message
     */
    protected void verifyMessageWasSentOverWebsocket(String user, Long sessionId, IrisMessage message) {
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.IRIS_MESSAGE
                        && websocketDTO.getMessage().getContent().size() == 1 && Objects.equals(websocketDTO.getMessage(), message)));
    }

    /**
     * Verify that an error was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyErrorWasSentOverWebsocket(String user, Long sessionId) {
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.ERROR));
    }

    /**
     * Verify that nothing was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyNothingWasSentOverWebsocket(String user, Long sessionId) {
        verify(websocketMessagingService, times(0)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId), any());
    }

    /**
     * Verify that nothing else was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyNothingElseWasSentOverWebsocket(String user, Long sessionId) {
        verifyNoMoreInteractions(websocketMessagingService);
    }

    /**
     * Verify that an error was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyNoErrorWasSentOverWebsocket(String user, Long sessionId) {
        verify(websocketMessagingService, times(0)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.ERROR));
    }
}
