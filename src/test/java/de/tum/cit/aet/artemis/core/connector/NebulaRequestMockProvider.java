package de.tum.cit.aet.artemis.core.connector;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.exception.NebulaException;

@Component
@Conditional(NebulaEnabled.class)
@Lazy
public class NebulaRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @Value("${artemis.nebula.url}")
    private String nebulaBaseUrl;

    @Value("${artemis.nebula.secret}")
    private String nebulaSecretToken;

    @Autowired
    private ObjectMapper mapper;

    public NebulaRequestMockProvider(@Qualifier("nebulaRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    public void reset() {
        if (mockServer != null) {
            mockServer.reset();
        }
    }

    public void verify() {
        if (mockServer != null) {
            mockServer.verify();
        }
    }

    public void mockFaqConsistencyRequestReturning(Function<FaqConsistencyDTO, FaqConsistencyResponseDTO> responder) {
        mockPostJson("/faq/check-consistency", FaqConsistencyDTO.class, responder, HttpStatus.OK, ExpectedCount.once());
    }

    public void mockFaqRewritingRequestReturning(Function<FaqRewritingDTO, FaqRewritingResponseDTO> responder) {
        mockPostJson("/faq/rewrite-faq", FaqRewritingDTO.class, responder, HttpStatus.OK, ExpectedCount.once());
    }

    private String buildUrl(String path) {
        return UriComponentsBuilder.fromHttpUrl(nebulaBaseUrl).path(path).toUriString();
    }

    private <Req, Res> void mockPostJson(String path, Class<Req> requestType, Function<Req, Res> responder, HttpStatus status, ExpectedCount count) {
        mockServer.expect(count, requestTo(buildUrl(path))).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", nebulaSecretToken)).andRespond(request -> {
            var mockRequest = (MockClientHttpRequest) request;
            var reqBody = mapper.readValue(mockRequest.getBodyAsString(), requestType);
            var resBody = responder.apply(reqBody);

            var creator = MockRestResponseCreators.withStatus(status).contentType(MediaType.APPLICATION_JSON);

            if (resBody != null) {
                creator = creator.body(mapper.writeValueAsString(resBody));
            }
            return creator.createResponse(request);
        });
    }

    public void mockThrowingNebulaExceptionForUrl(String path, NebulaException exception) {
        mockServer.expect(ExpectedCount.once(), requestTo(buildUrl(path))).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", nebulaSecretToken))
                .andRespond(request -> {
                    throw exception;
                });
    }

    public void mockThrowingNebulaRuntimeExceptionForUrl(String path, RuntimeException exception) {
        mockServer.expect(ExpectedCount.once(), requestTo(buildUrl(path))).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", nebulaSecretToken))
                .andRespond(request -> {
                    throw exception;
                });
    }
}
