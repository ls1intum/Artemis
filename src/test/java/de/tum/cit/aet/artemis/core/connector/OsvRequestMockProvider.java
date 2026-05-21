package de.tum.cit.aet.artemis.core.connector;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.dto.osv.OsvBatchResponseDTO;
import de.tum.cit.aet.artemis.core.dto.osv.OsvVulnerabilityDTO;
import de.tum.cit.aet.artemis.core.dto.osv.OsvVulnerabilityResultDTO;

/**
 * Mock provider for OSV (Open Source Vulnerabilities) API requests.
 * Used in integration tests to mock external calls to osv.dev API.
 */
@Component
@Profile(PROFILE_CORE)
@Lazy
public class OsvRequestMockProvider {

    private static final String OSV_BATCH_API_URL = "https://api.osv.dev/v1/querybatch";

    private static final String OSV_VULN_API_URL = "https://api.osv.dev/v1/vulns/";

    private final RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockRestServiceServer mockServer;

    public OsvRequestMockProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Enables mocking of REST requests to the OSV API.
     * Must be called before setting up mock expectations.
     */
    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }

    /**
     * Resets the mock server.
     */
    public void reset() {
        if (mockServer != null) {
            mockServer.reset();
        }
    }

    /**
     * Mocks the OSV batch query API to return empty results (no vulnerabilities found).
     */
    public void mockBatchQueryWithNoVulnerabilities(int componentCount) throws JsonProcessingException {
        List<OsvVulnerabilityResultDTO> emptyResults = java.util.stream.IntStream.range(0, componentCount).mapToObj(i -> new OsvVulnerabilityResultDTO(null)).toList();
        OsvBatchResponseDTO response = new OsvBatchResponseDTO(emptyResults);

        mockServer.expect(ExpectedCount.once(), requestTo(OSV_BATCH_API_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks the OSV batch query API to return results with a specific vulnerability.
     *
     * @param vulnId the vulnerability ID to return in the batch response
     */
    public void mockBatchQueryWithVulnerability(String vulnId) throws JsonProcessingException {
        OsvVulnerabilityDTO minimalVuln = new OsvVulnerabilityDTO(vulnId, null, null, null, null, null, null, null);
        OsvVulnerabilityResultDTO resultWithVuln = new OsvVulnerabilityResultDTO(List.of(minimalVuln));
        OsvBatchResponseDTO response = new OsvBatchResponseDTO(List.of(resultWithVuln));

        mockServer.expect(ExpectedCount.once(), requestTo(OSV_BATCH_API_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks the OSV individual vulnerability API to return full vulnerability details.
     *
     * @param vulnerability the full vulnerability details to return
     */
    public void mockVulnerabilityDetails(OsvVulnerabilityDTO vulnerability) throws JsonProcessingException {
        mockServer.expect(ExpectedCount.once(), requestToUriTemplate(OSV_VULN_API_URL + "{vulnId}", vulnerability.id())).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(vulnerability), MediaType.APPLICATION_JSON));
    }

    /**
     * Verifies that all expected requests were made.
     */
    public void verify() {
        if (mockServer != null) {
            mockServer.verify();
        }
    }
}
