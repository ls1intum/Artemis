package de.tum.cit.aet.artemis.core.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private TelemetryService telemetryService;

    private MockRestServiceServer mockServer;

    private final ObjectMapper mapper = new ObjectMapper();

    private TelemetryService telemetryServiceSpy;

    @Value("${artemis.telemetry.destination}")
    private String destination;

    @BeforeEach
    void init() {
        telemetryServiceSpy = spy(telemetryService);
        mockServer = MockRestServiceServer.createServer(restTemplate);
        telemetryServiceSpy.useTelemetry = true;
    }

    @Test
    void testSendTelemetry_TelemetryEnabled() throws Exception {
        mockServer.expect(ExpectedCount.once(), requestTo(new URI(destination + "/api/telemetry"))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString("Success!")));
        telemetryServiceSpy.sendTelemetry();
        await().atMost(1, SECONDS).untilAsserted(() -> mockServer.verify());
    }

    @Test
    void testSendTelemetry_TelemetryDisabled() throws Exception {
        mockServer.expect(ExpectedCount.never(), requestTo(new URI(destination + "/api/telemetry"))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString("Success!")));
        telemetryServiceSpy.useTelemetry = false;
        telemetryServiceSpy.sendTelemetry();
        await().atMost(1, SECONDS).untilAsserted(() -> mockServer.verify());
    }

    @Test
    void testSendTelemetry_ExceptionHandling() throws Exception {
        mockServer.expect(ExpectedCount.once(), requestTo(new URI(destination + "/api/telemetry"))).andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body(mapper.writeValueAsString("Failure!")));
        telemetryServiceSpy.sendTelemetry();
        await().atMost(1, SECONDS).untilAsserted(() -> mockServer.verify());
    }
}
