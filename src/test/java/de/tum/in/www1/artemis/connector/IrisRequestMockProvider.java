package de.tum.in.www1.artemis.connector;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Collections;

import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.service.connectors.iris.IrisModel;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisMessageResponseDTO;

@Component
@Profile("iris")
public class IrisRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @Value("${artemis.iris.url}/api/v1/messages")
    private URL apiURL;

    @Autowired
    private ObjectMapper mapper;

    private AutoCloseable closeable;

    public IrisRequestMockProvider(@Qualifier("irisRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        closeable = MockitoAnnotations.openMocks(this);
    }

    public void reset() throws Exception {
        if (mockServer != null) {
            mockServer.reset();
        }

        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Mocks response call for the pyris call
     */
    public void mockResponse(String responseMessage) throws JsonProcessingException {
        var irisMessage = new IrisMessage();
        var irisMessageContent = new IrisMessageContent();
        irisMessageContent.setTextContent(responseMessage);
        irisMessage.setContent(Collections.singletonList(irisMessageContent));
        irisMessage.setSender(IrisMessageSender.LLM);
        irisMessage.setSentAt(ZonedDateTime.now());

        var response = new IrisMessageResponseDTO(IrisModel.GPT35_TURBO, irisMessage);
        var json = mapper.writeValueAsString(response);

        mockServer.expect(ExpectedCount.once(), requestTo(apiURL.toString())).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
}
