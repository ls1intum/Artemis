package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;

class LocalVCBuildAgentAuthenticationServiceTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);

    private LocalVCBuildAgentAuthenticationService buildAgentAuthenticationService;

    @BeforeEach
    void setUp() {
        // A node without local CI: Jenkins is its only client, and the only one the shared credential applies to.
        buildAgentAuthenticationService = new LocalVCBuildAgentAuthenticationService(Optional.empty(), Optional.empty(), Optional.empty(), null, null, null, Optional.empty(), null,
                null);
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    /** A node without local CI, which is the only kind of node where the shared build agent credential can still apply. */
    private void configureNodeWithoutLocalCI(String buildAgentUsername, String buildAgentPassword, boolean useSshForBuildAgent) {
        ReflectionTestUtils.setField(buildAgentAuthenticationService, "buildAgentGitUsername", buildAgentUsername);
        ReflectionTestUtils.setField(buildAgentAuthenticationService, "buildAgentGitPassword", buildAgentPassword);
        ReflectionTestUtils.setField(buildAgentAuthenticationService, "useSshForBuildAgent", useSshForBuildAgent);
    }

    @Test
    void authenticateSharedBuildAgentCredential_withTheSharedCredential_isAccepted() throws Exception {
        configureNodeWithoutLocalCI("build-agent", "a-long-shared-secret", false);

        assertThat(buildAgentAuthenticationService.authenticateSharedBuildAgentCredential(request, basicAuth("build-agent", "a-long-shared-secret"))).isTrue();

        // marked, so that the hook completing the access log of a fetch does not take it for the fetch of a student
        verify(request).setAttribute(LocalVCBuildAgentAuthenticationService.BUILD_AGENT_CLONE_REQUEST_ATTRIBUTE, "build-agent");
    }

    @Test
    void authenticateSharedBuildAgentCredential_withAWrongPassword_isDeclined() throws Exception {
        configureNodeWithoutLocalCI("build-agent", "a-long-shared-secret", false);

        assertThat(buildAgentAuthenticationService.authenticateSharedBuildAgentCredential(request, basicAuth("build-agent", "another-secret"))).isFalse();

        verify(request, never()).setAttribute(any(), any());
    }

    @Test
    void authenticateSharedBuildAgentCredential_withAnotherUserName_isDeclined() throws Exception {
        configureNodeWithoutLocalCI("build-agent", "a-long-shared-secret", false);

        assertThat(buildAgentAuthenticationService.authenticateSharedBuildAgentCredential(request, basicAuth("student", "a-long-shared-secret"))).isFalse();
    }

    @Test
    void authenticateSharedBuildAgentCredential_withABlankConfiguredPassword_doesNotAcceptABlankPassword() throws Exception {
        configureNodeWithoutLocalCI("build-agent", "", false);

        // a blank configured password would otherwise match the equally blank password of anyone who knows the user name
        assertThat(buildAgentAuthenticationService.authenticateSharedBuildAgentCredential(request, basicAuth("build-agent", ""))).isFalse();
    }

    @Test
    void authenticateSharedBuildAgentCredential_whenBuildAgentsUseSsh_doesNotAcceptTheSharedCredential() throws Exception {
        configureNodeWithoutLocalCI("build-agent", "a-long-shared-secret", true);

        assertThat(buildAgentAuthenticationService.authenticateSharedBuildAgentCredential(request, basicAuth("build-agent", "a-long-shared-secret"))).isFalse();
    }

    @Test
    void authenticateSharedBuildAgentCredential_onANodeWithLocalCI_doesNotAcceptTheSharedCredential() throws Exception {
        configureNodeWithoutLocalCI("build-agent", "a-long-shared-secret", false);
        ReflectionTestUtils.setField(buildAgentAuthenticationService, "distributedDataAccessService", Optional.of(mock(DistributedDataAccessService.class)));

        // every build job there carries its own token, so the shared credential opens nothing
        assertThat(buildAgentAuthenticationService.authenticateSharedBuildAgentCredential(request, basicAuth("build-agent", "a-long-shared-secret"))).isFalse();
    }

    @Test
    void authenticateSharedBuildAgentCredential_withAMalformedHeader_failsLikeAnyOtherCredentialCheck() {
        configureNodeWithoutLocalCI("build-agent", "a-long-shared-secret", false);

        assertThatExceptionOfType(LocalVCAuthException.class)
                .isThrownBy(() -> buildAgentAuthenticationService.authenticateSharedBuildAgentCredential(request, "not-a-basic-header"));
    }

    private HttpServletRequest requestOf(String authorizationHeader) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorizationHeader);
        return request;
    }

    @Test
    void aRequestAuthorizedAsABuildAgentClone_isABuildAgentClone() {
        HttpServletRequest request = requestOf(basicAuth("student", "pw"));
        when(request.getAttribute("artemis.buildAgentClone")).thenReturn("build-agent-1");

        assertThat(buildAgentAuthenticationService.isBuildAgentClone(request)).isTrue();
    }

    @Test
    void anOrdinaryUserNamedLikeTheBuildJobUser_isNoBuildAgentClone() {
        // the user name proves nothing: both build agent mechanisms mark the request they accepted instead
        assertThat(buildAgentAuthenticationService.isBuildAgentClone(requestOf(basicAuth("buildjob_user", "pw")))).isFalse();
    }

    @Test
    void anOrdinaryUserNamedLikeTheConfiguredBuildAgentUser_isNoBuildAgentClone() {
        ReflectionTestUtils.setField(buildAgentAuthenticationService, "buildAgentGitUsername", "custom-agent");

        assertThat(buildAgentAuthenticationService.isBuildAgentClone(requestOf(basicAuth("custom-agent", "pw")))).isFalse();
    }

    @Test
    void aRequestOfTheSharedCredential_isABuildAgentCloneOnceItWasAccepted() throws Exception {
        configureNodeWithoutLocalCI("custom-agent", "a-long-shared-secret", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, basicAuth("custom-agent", "a-long-shared-secret"));

        assertThat(buildAgentAuthenticationService.isBuildAgentClone(request)).isFalse();
        assertThat(buildAgentAuthenticationService.authenticateSharedBuildAgentCredential(request, request.getHeader(HttpHeaders.AUTHORIZATION))).isTrue();
        assertThat(buildAgentAuthenticationService.isBuildAgentClone(request)).isTrue();
    }

    @Test
    void aRequestWithoutCredentials_isNoBuildAgentClone() {
        assertThat(buildAgentAuthenticationService.isBuildAgentClone(requestOf(null))).isFalse();
    }
}
