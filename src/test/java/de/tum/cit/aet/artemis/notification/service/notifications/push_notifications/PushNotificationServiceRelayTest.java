package de.tum.cit.aet.artemis.notification.service.notifications.push_notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.notification.repository.PushNotificationDeviceConfigurationRepository;

class PushNotificationServiceRelayTest {

    @Test
    @SuppressWarnings("unchecked")
    void sendRelayRequest_postsJsonBodyToRelayServer() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        Executor directExecutor = Runnable::run;
        var service = new FirebasePushNotificationService(mock(PushNotificationDeviceConfigurationRepository.class), restTemplate, directExecutor);

        service.sendRelayRequest("{\"a\":1}", "http://hermes.test");

        ArgumentCaptor<HttpEntity<String>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("http://hermes.test" + service.getRelayPath()), entity.capture(), eq(String.class));
        assertThat(entity.getValue().getBody()).isEqualTo("{\"a\":1}");
        assertThat(entity.getValue().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }
}
