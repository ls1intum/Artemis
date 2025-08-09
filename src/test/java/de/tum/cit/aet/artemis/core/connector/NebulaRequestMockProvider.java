package de.tum.cit.aet.artemis.core.connector;

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
import de.tum.cit.aet.artemis.nebula.dto.FaqConsistencyDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqConsistencyResponse;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingResponse;

@Component
@Conditional(NebulaEnabled.class)
@Lazy
public class NebulaRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @Value("${artemis.nebula.base-url}")
    private String nebulaBaseUrl;

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

    public void mockFaqConsistencyRequestReturning(Function<FaqConsistencyDTO, FaqConsistencyResponse> responder) {
        mockPostJson("/faq/check-consistency", FaqConsistencyDTO.class, responder, HttpStatus.OK, ExpectedCount.once());
    }

    public void mockFaqRewritingRequestReturning(Function<FaqRewritingDTO, FaqRewritingResponse> responder) {
        mockPostJson("/faq/rewrite-text", FaqRewritingDTO.class, responder, HttpStatus.OK, ExpectedCount.once());
    }

    /* ===================== Generische Helfer ===================== */

    private String buildUrl(String path) {
        return UriComponentsBuilder.fromHttpUrl(nebulaBaseUrl).path(path).toUriString();
    }

    private <Req, Res> void mockPostJson(String path, Class<Req> requestType, Function<Req, Res> responder, HttpStatus status, ExpectedCount count) {
        mockServer.expect(count, requestTo(buildUrl(path))).andExpect(method(HttpMethod.POST)).andRespond(request -> {
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

    private void mockPostError(String path, HttpStatus status, Object errorBody) {
        mockServer.expect(ExpectedCount.once(), requestTo(buildUrl(path))).andExpect(method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withStatus(status).contentType(MediaType.APPLICATION_JSON).body(errorBody != null ? toJson(errorBody) : ""));
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
