package de.tum.in.www1.artemis.connector;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URL;
import java.util.List;
import java.util.UUID;

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

import de.tum.in.www1.artemis.service.dto.OpenAIChatResponseDTO;

@Component
@Profile("iris-gpt3_5")
public class IrisGPT3_5RequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @Value("${artemis.iris.models.gpt3_5.url}")
    private URL apiURL;

    @Autowired
    private ObjectMapper mapper;

    private AutoCloseable closeable;

    public IrisGPT3_5RequestMockProvider(@Qualifier("gpt35RestTemplate") RestTemplate restTemplate) {
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
     * Mocks response call for the GPT 3.5 model.
     */
    public void mockResponse(String responseMessage) throws JsonProcessingException {
        var usage = new OpenAIChatResponseDTO.Usage(42, 42, 84);
        var message = new OpenAIChatResponseDTO.Message("assistant", responseMessage);
        var choices = List.of(new OpenAIChatResponseDTO.Choice(message, 0, "stop"));
        var response = new OpenAIChatResponseDTO(UUID.randomUUID().toString(), "chat.completion", System.currentTimeMillis(), "gpt-35-turbo", usage, choices);
        var json = mapper.writeValueAsString(response);

        mockServer.expect(ExpectedCount.once(), requestTo(apiURL.toString())).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
}
