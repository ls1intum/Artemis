package de.tum.cit.aet.artemis.core.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
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
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.service.telemetry.TelemetrySendingService;
import de.tum.cit.aet.artemis.core.service.telemetry.TelemetryService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private TelemetrySendingService telemetrySendingService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private ConfigurableApplicationContext context;

    private MockRestServiceServer mockServer;

    private final ObjectMapper mapper = new ObjectMapper();

    private TelemetryService telemetryServiceSpy;

    @Value("${artemis.telemetry.destination}")
    private String destination;

    @Value("${eureka.client.service-url.defaultZone}")
    private String eurekaDefaultZoneUrl;

    private String eurekaRequestUrl;

    private byte[] applicationsBody;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        TestPropertyValues.of("artemis.telemetry.enabled=true").applyTo(context);
        try {
            var eurekaURI = new URI(eurekaDefaultZoneUrl);
            eurekaRequestUrl = eurekaURI.getScheme() + "://" + eurekaURI.getAuthority() + "/api/eureka/applications";
        }
        catch (Exception ignored) {
            // Exception can be ignored because defaultZoneUrl is guaranteed to be valid in the test environment
        }

        applicationsBody = mapper.writeValueAsBytes(Map.of("applications", List.of(Map.of("name", "ARTEMIS", "instances", List.of(Map.of())))));
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }

    @Test
    void testSendTelemetry_TelemetryEnabled() throws Exception {
        TelemetryService telemetryService = new TelemetryService(profileService, telemetrySendingService, true, true, true, 0);
        telemetryServiceSpy = spy(telemetryService);

        mockServer.expect(ExpectedCount.once(), requestTo(new URI(eurekaRequestUrl))).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW46YWRtaW4="))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(applicationsBody));

        mockServer.expect(ExpectedCount.once(), requestTo(new URI(destination + "/api/telemetry"))).andExpect(method(HttpMethod.POST))
                .andExpect(request -> assertThat(request.getBody().toString()).contains("adminName"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString("Success!")));
        telemetryServiceSpy.scheduleTelemetryTask();

        await().atMost(2, SECONDS).untilAsserted(() -> mockServer.verify());
    }

    @Test
    void testSendTelemetry_TelemetryEnabledWithoutPersonalData() throws Exception {
        TelemetryService telemetryService = new TelemetryService(profileService, telemetrySendingService, true, false, true, 0);
        telemetryServiceSpy = spy(telemetryService);

        mockServer.expect(ExpectedCount.once(), requestTo(new URI(eurekaRequestUrl))).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW46YWRtaW4="))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(applicationsBody));

        mockServer.expect(ExpectedCount.once(), requestTo(new URI(destination + "/api/telemetry"))).andExpect(method(HttpMethod.POST))
                .andExpect(request -> assertThat(request.getBody().toString()).doesNotContain("adminName"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString("Success!")));
        telemetryServiceSpy.scheduleTelemetryTask();

        await().atMost(2, SECONDS).untilAsserted(() -> mockServer.verify());
    }

    @Test
    void testSendTelemetry_TelemetryDisabled() throws Exception {
        TelemetryService telemetryService = new TelemetryService(profileService, telemetrySendingService, false, true, true, 0);
        telemetryServiceSpy = spy(telemetryService);

        mockServer.expect(ExpectedCount.never(), requestTo(new URI(destination + "/api/telemetry"))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString("Success!")));
        telemetryServiceSpy.scheduleTelemetryTask();
        await().atMost(2, SECONDS).untilAsserted(() -> mockServer.verify());
    }

    @Test
    void testSendTelemetry_ExceptionHandling() throws Exception {
        TelemetryService telemetryService = new TelemetryService(profileService, telemetrySendingService, true, true, true, 0);
        telemetryServiceSpy = spy(telemetryService);
        mockServer.expect(ExpectedCount.once(), requestTo(new URI(eurekaRequestUrl))).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(applicationsBody));
        mockServer.expect(ExpectedCount.once(), requestTo(new URI(destination + "/api/telemetry"))).andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body(mapper.writeValueAsString("Failure!")));

        telemetryServiceSpy.scheduleTelemetryTask();
        await().atMost(2, SECONDS).untilAsserted(() -> mockServer.verify());
    }
}
