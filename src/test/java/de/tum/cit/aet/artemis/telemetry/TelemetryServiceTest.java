package de.tum.cit.aet.artemis.telemetry;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.core.service.telemetry.TelemetryService;

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

    @Value("${eureka.client.service-url.defaultZone}")
    private String defaultZoneUrl;

    private String eurekaRequestUrl;

    private byte[] appliciationsBody;

    @BeforeEach
    void init() throws JsonProcessingException {
        try {
            var eurekaURI = new URI(defaultZoneUrl);
            eurekaRequestUrl = eurekaURI.getScheme() + "://" + eurekaURI.getAuthority() + "/api/eureka/applications";

        }
        catch (Exception ignored) {
        }

        telemetryServiceSpy = spy(telemetryService);

        appliciationsBody = mapper.writeValueAsBytes(Map.of("applications", List.of(Map.of("name", "ARTEMIS", "instances", List.of(Map.of()) // Mocking an instance object
        ))));
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(true);
        mockServer = builder.build();

        telemetryServiceSpy.useTelemetry = true;
    }

    @Test
    void testSendTelemetry_TelemetryEnabled() throws Exception {
        mockServer.expect(ExpectedCount.once(), requestTo(new URI(eurekaRequestUrl))).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW46YWRtaW4="))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(appliciationsBody));

        mockServer.expect(ExpectedCount.once(), requestTo(new URI(destination + "/api/telemetry"))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString("Success!")));
        telemetryServiceSpy.sendTelemetry();

        await().atMost(2, SECONDS).untilAsserted(() -> mockServer.verify());
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
        mockServer.expect(ExpectedCount.once(), requestTo(new URI(eurekaRequestUrl))).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(appliciationsBody));
        mockServer.expect(ExpectedCount.once(), requestTo(new URI(destination + "/api/telemetry"))).andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body(mapper.writeValueAsString("Failure!")));

        telemetryServiceSpy.sendTelemetry();
        await().atMost(2, SECONDS).untilAsserted(() -> mockServer.verify());
    }
}
