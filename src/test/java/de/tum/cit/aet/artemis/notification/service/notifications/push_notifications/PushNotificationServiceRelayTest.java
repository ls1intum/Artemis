package de.tum.cit.aet.artemis.notification.service.notifications.push_notifications;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.notification.repository.PushNotificationDeviceConfigurationRepository;

class PushNotificationServiceRelayTest {

    private static final String RELAY_URL = "http://hermes.test";

    private MockRestServiceServer relayServer;

    private FirebasePushNotificationService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        relayServer = MockRestServiceServer.bindTo(builder).build();
        Executor directExecutor = Runnable::run;
        service = new FirebasePushNotificationService(Mockito.mock(PushNotificationDeviceConfigurationRepository.class), builder.build(), directExecutor);
    }

    @Test
    void sendRelayRequest_postsJsonBodyToRelayServer() {
        relayServer.expect(requestTo(RELAY_URL + service.getRelayPath())).andExpect(method(HttpMethod.POST)).andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string("{\"a\":1}")).andRespond(withSuccess());

        service.sendRelayRequest("{\"a\":1}", RELAY_URL);

        relayServer.verify();
    }

    @Test
    void sendRelayRequest_retriesAfterServerError() {
        // The first attempt fails, the backoff waits one second, and the second attempt succeeds: exactly two requests reach the relay.
        relayServer.expect(requestTo(RELAY_URL + service.getRelayPath())).andRespond(withServerError());
        relayServer.expect(requestTo(RELAY_URL + service.getRelayPath())).andRespond(withSuccess());

        service.sendRelayRequest("{\"a\":1}", RELAY_URL);

        relayServer.verify();
    }
}
