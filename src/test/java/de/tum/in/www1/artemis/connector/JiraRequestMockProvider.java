package de.tum.in.www1.artemis.connector;

import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

import org.hamcrest.text.MatchesPattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.service.connectors.jira.dto.JiraUserDTO;
import de.tum.in.www1.artemis.service.connectors.jira.dto.JiraUserDTO.JiraUserGroupDTO;
import de.tum.in.www1.artemis.service.connectors.jira.dto.JiraUserDTO.JiraUserGroupsDTO;

@Component
@Profile("jira")
public class JiraRequestMockProvider {

    @Value("${artemis.user-management.external.url}")
    private URL JIRA_URL;

    private final RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    private MockRestServiceServer mockServer;

    public JiraRequestMockProvider(@Qualifier("jiraRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    public void mockIsGroupAvailable(String group) {
        final var uriPattern = Pattern.compile(JIRA_URL + "/rest/api/2/group/member\\?groupname=" + group);

        mockServer.expect(requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockIsGroupAvailableForMultiple(Set<String> groups) {
        final var regexGroups = String.join("|", groups);
        final var uriPattern = Pattern.compile(JIRA_URL + "/rest/api/2/group/member\\?groupname=(" + regexGroups + ")");
        mockServer.expect(ExpectedCount.times(groups.size()), requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));
    }

    public void mockAddUserToGroup(String group, boolean shouldFail) {
        mockIsGroupAvailable(group);
        final var uriPattern = Pattern.compile(JIRA_URL + "/rest/api/2/group/user\\?groupname=" + group);
        var status = shouldFail ? HttpStatus.NOT_FOUND : HttpStatus.OK;
        mockServer.expect(requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.POST)).andRespond(withStatus(status));
    }

    public void mockAddUserToGroupForMultipleGroups(Set<String> groups) {
        mockIsGroupAvailableForMultiple(groups);
        final var regexGroups = String.join("|", groups);
        final var uriPattern = Pattern.compile(JIRA_URL + "/rest/api/2/group/user\\?groupname=(" + regexGroups + ")");
        mockServer.expect(ExpectedCount.times(groups.size()), requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK));
    }

    public void mockRemoveUserFromGroup(Set<String> groups, String username, boolean shouldFail, boolean found) {
        final var regexGroups = String.join("|", groups);
        final var uriPattern = Pattern.compile(JIRA_URL + "/rest/api/2/group/user\\?groupname=(" + regexGroups + ")&username=" + username);
        var status = shouldFail ? HttpStatus.INTERNAL_SERVER_ERROR : (found ? HttpStatus.OK : HttpStatus.NOT_FOUND);
        mockServer.expect(ExpectedCount.twice(), requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(status));
    }

    public void mockGetUsernameForEmail(String email, String emailToReturn, String usernameToBeReturned) throws IOException {
        final var uriPattern = Pattern.compile(JIRA_URL + "/rest/api/2/user/search\\?username=" + URLEncoder.encode(email, StandardCharsets.UTF_8));
        JiraUserDTO userDTO = new JiraUserDTO(usernameToBeReturned);
        userDTO.setEmailAddress(emailToReturn);
        final var response = List.of(userDTO);
        mockServer.expect(requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response)));
    }

    public void mockGetUsernameForEmailEmptyResponse(String email) throws IOException {
        final var uriPattern = Pattern.compile(JIRA_URL + "/rest/api/2/user/search\\?username=" + URLEncoder.encode(email, StandardCharsets.UTF_8));
        final var response = new ArrayList<>();
        mockServer.expect(requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response)));
    }

    public void mockCreateGroup(String groupName) throws URISyntaxException, JsonProcessingException {
        final var path = UriComponentsBuilder.fromUri(JIRA_URL.toURI()).path("/rest/api/2/group").build().toUri();
        final var body = new JiraUserDTO(groupName);
        mockServer.expect(requestTo(path)).andExpect(method(HttpMethod.POST)).andExpect(content().json(mapper.writeValueAsString(body))).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockDeleteGroup(String groupName) throws URISyntaxException {
        final var path = UriComponentsBuilder.fromUri(JIRA_URL.toURI()).path("/rest/api/2/group").queryParam("groupname", groupName).build().toUri();
        mockServer.expect(requestTo(path)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockGetOrCreateUserLti(String authUsername, String password, String username, String email, String firstName, Set<String> groups)
            throws URISyntaxException, IOException {
        final var groupsResponse = new JiraUserGroupsDTO();
        final var groupDTOs = new HashSet<JiraUserGroupDTO>();
        for (final var group : groups) {
            final var groupDTO = new JiraUserGroupDTO();
            groupDTO.setName(group);
            groupDTO.setSelf(new URL("http://localhost:8080/" + group));
            groupDTOs.add(groupDTO);
        }
        groupsResponse.setSize(groups.size());
        groupsResponse.setItems(groupDTOs);
        final var response = new JiraUserDTO(username, firstName, email, groupsResponse);
        final var path = UriComponentsBuilder.fromUri(JIRA_URL.toURI()).path("/rest/api/2/user").queryParam("username", username).queryParam("expand", "groups").build().toUri();
        final var auth = authUsername + ":" + password;
        final var authHeader = new String(Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8)));

        mockServer.expect(requestTo(path)).andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Basic " + authHeader))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response)));
    }

    public void mockGetOrCreateUserJira(String username, String email, String firstName, Set<String> groups) throws URISyntaxException, IOException {
        final var groupsResponse = new JiraUserGroupsDTO();
        final var groupDTOs = new HashSet<JiraUserGroupDTO>();
        for (final var group : groups) {
            final var groupDTO = new JiraUserGroupDTO();
            groupDTO.setName(group);
            groupDTO.setSelf(new URL("http://localhost:8080/" + group));
            groupDTOs.add(groupDTO);
        }
        groupsResponse.setSize(groups.size());
        groupsResponse.setItems(groupDTOs);
        final var response = new JiraUserDTO(username, firstName, email, groupsResponse);
        final var path = UriComponentsBuilder.fromUri(JIRA_URL.toURI()).path("/rest/api/2/user").queryParam("username", username).queryParam("expand", "groups").build().toUri();

        mockServer.expect(requestTo(path)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response)));
    }

    public void verifyNoGetOrCreateUserJira(String username) throws URISyntaxException {
        final var path = UriComponentsBuilder.fromUri(JIRA_URL.toURI()).path("/rest/api/2/user").queryParam("username", username).queryParam("expand", "groups").build().toUri();
        mockServer.expect(never(), requestTo(path));
    }

    public void mockGetOrCreateUserJiraCaptchaException(String username, String email, String firstName, Set<String> groups) throws URISyntaxException, IOException {
        final var groupsResponse = new JiraUserGroupsDTO();
        final var groupDTOs = new HashSet<JiraUserGroupDTO>();
        for (final var group : groups) {
            final var groupDTO = new JiraUserGroupDTO();
            groupDTO.setName(group);
            groupDTO.setSelf(new URL("http://localhost:8080/" + group));
            groupDTOs.add(groupDTO);
        }
        groupsResponse.setSize(groups.size());
        groupsResponse.setItems(groupDTOs);
        final var response = new JiraUserDTO(username, firstName, email, groupsResponse);
        final var path = UriComponentsBuilder.fromUri(JIRA_URL.toURI()).path("/rest/api/2/user").queryParam("username", username).queryParam("expand", "groups").build().toUri();

        var headers = new HttpHeaders();
        headers.add("X-Authentication-Denied-Reason", "captcha");
        mockServer.expect(requestTo(path)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).headers(headers).body(mapper.writeValueAsString(response)));
    }

    public void mockCreateUserInExternalUserManagement(String username, String fullname, String email) throws URISyntaxException, JsonProcessingException {
        final var path = UriComponentsBuilder.fromUri(JIRA_URL.toURI()).path("/rest/api/2/user").build().toUri();
        final var body = new JiraUserDTO(username, username, fullname, email, List.of("jira-software"));

        mockServer.expect(requestTo(path)).andExpect(method(HttpMethod.POST)).andExpect(content().json(mapper.writeValueAsString(body))).andRespond(withStatus(HttpStatus.OK));
    }
}
