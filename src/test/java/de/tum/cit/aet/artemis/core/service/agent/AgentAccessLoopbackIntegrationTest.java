package de.tum.cit.aet.artemis.core.service.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil.SavedToken;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * The production client of the MCP tools, which calls the REST API over a real connection to the server of the test, with a real token.
 * The other tests of the MCP endpoint replace it by an in-process call.
 */
class AgentAccessLoopbackIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "agentloopback";

    @Autowired
    private LoopbackAgentApiService loopbackAgentApi;

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    private Course course;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 1, 0);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        personalAccessTokenRepository.deleteAll();
    }

    @Test
    void theTokenOfTheRequestAuthenticatesTheCallOverTheLoopbackInterface() {
        actAs(tokenOf("student1", AgentScope.COURSES_READ));

        AgentApiResponse response = loopbackAgentApi.send(HttpMethod.GET, "/api/course/courses/for-dropdown", null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().toString()).contains(course.getTitle());
    }

    @Test
    void theScopesOfTheTokenApplyToTheCallOverTheLoopbackInterface() {
        actAs(tokenOf("student1", AgentScope.COMPETENCIES_READ));

        AgentApiResponse response = loopbackAgentApi.send(HttpMethod.GET, "/api/course/courses/for-dropdown", null);

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void aTextBodyIsSentAsPlainText() {
        actAs(tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT));

        // No such exercise exists, so the authorization of the endpoint answers 403. That comes after the body was read: a text body
        // sent as another media type would have been answered 415 before.
        AgentApiResponse response = loopbackAgentApi.sendText(HttpMethod.PATCH, "/api/programming/programming-exercises/999999/problem-statement", "A problem statement.");

        assertThat(response.status()).isEqualTo(403);
    }

    private SavedToken tokenOf(String loginWithoutPrefix, AgentScope... scopes) {
        long userId = userUtilService.getUserByLogin(TEST_PREFIX + loginWithoutPrefix).getId();
        return PersonalAccessTokenTestUtil.saveToken(personalAccessTokenRepository, userId, Set.of(scopes), Duration.ofDays(1));
    }

    /** Binds a request that carries the token, as the one an agent sends to the MCP endpoint does. */
    private void actAs(SavedToken token) {
        var servedRequest = new MockHttpServletRequest();
        servedRequest.addHeader(HttpHeaders.AUTHORIZATION, token.bearer());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servedRequest));
    }
}
