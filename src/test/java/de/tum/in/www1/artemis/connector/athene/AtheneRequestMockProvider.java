package de.tum.in.www1.artemis.connector.athene;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
import de.tum.in.www1.artemis.service.dto.FeedbackConflictResponseDTO;

@Component
@Profile("athene")
public class AtheneRequestMockProvider {

    @Autowired
    public RestTemplate restTemplate;

    @Value("${artemis.athene.feedback-consistency-url}")
    private String feedbackConsistencyApiEndpoint;

    @Autowired
    private ObjectMapper mapper;

    private MockRestServiceServer mockServer;

    public void enableMockingOfRequests() {
        enableMockingOfRequests(false);
    }

    public void enableMockingOfRequests(boolean ignoreExpectOrder) {
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(ignoreExpectOrder);
        mockServer = builder.build();
    }

    public void reset() {
        if (mockServer != null) {
            mockServer.reset();
        }
    }

    /**
     * This method mocks feedback consistency response coming from Athene
     *
     * @param feedbackConflictResponseDTOs List of response DTOs, ideally an array of feedback conflicts
     * @throws JsonProcessingException exception related to mapping the values to json
     */
    public void mockFeedbackConsistency(List<FeedbackConflictResponseDTO> feedbackConflictResponseDTOs) throws JsonProcessingException {
        String jsonArray = "{ \"feedbackInconsistencies\": " + mapper.writeValueAsString(feedbackConflictResponseDTOs) + "}";
        mockServer.expect(ExpectedCount.once(), requestTo(feedbackConsistencyApiEndpoint)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess().body(jsonArray).contentType(MediaType.APPLICATION_JSON));
    }
}
