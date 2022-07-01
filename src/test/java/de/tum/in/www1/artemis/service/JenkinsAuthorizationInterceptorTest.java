package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsAuthorizationInterceptor;

class JenkinsAuthorizationInterceptorTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Value("${artemis.continuous-integration.url}")
    private URL jenkinsServerUrl;

    @Autowired
    JenkinsAuthorizationInterceptor jenkinsAuthorizationInterceptor;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockRestServiceServer;

    /**
     * This method initializes the test case by setting up a local repo
     */
    @BeforeEach
    void initTestCase() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
        mockRestServiceServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).bufferContent().build();
    }

    @AfterEach
    void tearDown() throws IOException {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        mockRestServiceServer.reset();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testAuthorizationInterceptorSetCrumbCorrectly() throws Exception {
        // Create a mocked request with a header which shouldn't be erased after the request is intercepted.
        var request = mockHttpRequestWithHeaders();

        // Create the json returned by the response
        var objectMapper = new ObjectMapper();
        ObjectNode crumbJson = objectMapper.createObjectNode();
        crumbJson.put("crumb", "some-crumb");
        var responseBody = objectMapper.writeValueAsString(crumbJson);

        mockGetCrumb(responseBody, HttpStatus.OK);
        try (var response = jenkinsAuthorizationInterceptor.intercept(request, new byte[] {}, mock(ClientHttpRequestExecution.class))) {
            assertThat(request.getHeaders()).containsKeys("some-header", "Jenkins-Crumb", "Cookie");
            assertThat(request.getHeaders().get("some-header")).contains("true");
            assertThat(request.getHeaders().get("Jenkins-Crumb")).contains("some-crumb");
            assertThat(request.getHeaders().get("Cookie")).contains("some-session");
        }

    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testAuthorizationInterceptorCrumbNotSet() throws Exception {
        // Create a mocked request with a header which shouldn't be erased after the request is intercepted.
        var request = mockHttpRequestWithHeaders();

        ReflectionTestUtils.setField(jenkinsAuthorizationInterceptor, "useCrumb", false);
        try (var response = jenkinsAuthorizationInterceptor.intercept(request, new byte[] {}, mock(ClientHttpRequestExecution.class))) {
            ReflectionTestUtils.setField(jenkinsAuthorizationInterceptor, "useCrumb", true);

            assertThat(request.getHeaders()).containsKey("some-header");
            assertThat(request.getHeaders().get("some-header")).contains("true");

            assertThat(request.getHeaders()).doesNotContainKeys("Jenkins-Crumb", "Cookie");
        }
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testAuthorizationInterceptorCrumbException() throws Exception {
        // Create a mocked request with a header which shouldn't be erased after the request is intercepted.
        var request = mockHttpRequestWithHeaders();

        mockGetCrumb("", HttpStatus.NOT_FOUND);
        try (var response = jenkinsAuthorizationInterceptor.intercept(request, new byte[] {}, mock(ClientHttpRequestExecution.class))) {

            assertThat(request.getHeaders()).containsKey("some-header");
            assertThat(request.getHeaders().get("some-header")).contains("true");

            assertThat(request.getHeaders()).doesNotContainKeys("Jenkins-Crumb", "Cookie");
        }

    }

    private HttpRequest mockHttpRequestWithHeaders() {
        var httpRequest = mock(HttpRequest.class);
        var httpHeaders = new HttpHeaders();
        httpHeaders.add("some-header", "true");
        doReturn(httpHeaders).when(httpRequest).getHeaders();
        return httpRequest;
    }

    private void mockGetCrumb(String expectedBody, HttpStatus expectedStatus) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("crumbIssuer/api/json").build().toUri();
        var headers = new HttpHeaders();
        headers.add("Set-Cookie", "some-session");
        mockRestServiceServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(expectedStatus).contentType(MediaType.APPLICATION_JSON).headers(headers).body(expectedBody));
    }
}
