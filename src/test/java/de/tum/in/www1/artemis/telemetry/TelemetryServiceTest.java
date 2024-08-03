package de.tum.in.www1.artemis.telemetry;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.service.scheduled.TelemetryService;

@ExtendWith(MockitoExtension.class)
public class TelemetryServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final Logger log = LoggerFactory.getLogger(TelemetryServiceTest.class);

    @Value("${artemis.telemetry.destination}")
    private String destination;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    private ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private TelemetryService telemetryService; // = new TelemetryService(env, restTemplate);

    private AutoCloseable closeable;

    @BeforeEach
    public void init() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    public void givenMockingIsDoneByMockRestServiceServer_whenPostIsCalled_thenReturnsMockedObject() throws Exception {

        mockServer.expect(ExpectedCount.once(), requestTo(new URI(destination + "/telemetry"))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString("Success!")));

        telemetryService.sendTelemetry();
        mockServer.verify();
    }

    @Test
    public void testSendTelemetry_TelemetryDisabled() throws Exception {
        mockServer.expect(ExpectedCount.never(), requestTo(new URI(destination + "/telemetry"))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString("Success!")));
        telemetryService.useTelemetry = false;
        telemetryService.sendTelemetry();
        mockServer.verify();
    }

    @Test
    public void testSendTelemetry_ExceptionHandling() throws Exception {
        mockServer.expect(ExpectedCount.once(), requestTo(new URI(destination + "/telemetry"))).andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body(mapper.writeValueAsString("Failure!")));
        telemetryService.useTelemetry = true;
        telemetryService.sendTelemetry();
        mockServer.verify();
    }
}
