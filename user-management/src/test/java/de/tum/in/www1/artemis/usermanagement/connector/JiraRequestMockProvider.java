package de.tum.in.www1.artemis.usermanagement.connector;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.URL;
import java.util.Set;
import java.util.regex.Pattern;

import org.hamcrest.text.MatchesPattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@Component
@Profile("jira")
public class JiraRequestMockProvider {

    @Value("${artemis.user-management.external.url}")
    private URL JIRA_URL;

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    public JiraRequestMockProvider(@Qualifier("jiraRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    public void mockIsGroupAvailableForMultiple(Set<String> groups) {
        final var regexGroups = String.join("|", groups);
        final var uriPattern = Pattern.compile(JIRA_URL + "/rest/api/2/group/member\\?groupname=(" + regexGroups + ")");
        mockServer.expect(ExpectedCount.times(groups.size()), requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));
    }

    public void mockAddUserToGroupForMultipleGroups(Set<String> groups) {
        mockIsGroupAvailableForMultiple(groups);
        final var regexGroups = String.join("|", groups);
        final var uriPattern = Pattern.compile(JIRA_URL + "/rest/api/2/group/user\\?groupname=(" + regexGroups + ")");
        mockServer.expect(ExpectedCount.times(groups.size()), requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK));
    }

    public void mockRemoveUserFromGroup(Set<String> groups, String username, boolean shouldFail) {
        final var regexGroups = String.join("|", groups);
        final var uriPattern = Pattern.compile(JIRA_URL + "/rest/api/2/group/user\\?groupname=(" + regexGroups + ")&username=" + username);
        var status = shouldFail ? HttpStatus.NOT_FOUND : HttpStatus.OK;
        mockServer.expect(ExpectedCount.twice(), requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(status));
    }

}
