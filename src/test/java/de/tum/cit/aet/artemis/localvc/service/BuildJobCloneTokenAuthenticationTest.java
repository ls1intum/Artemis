package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkConfiguration;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.localci.service.BuildAgentAddressRegistryService;
import de.tum.cit.aet.artemis.localci.service.BuildJobCloneTokenService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Tests the decision made when a build agent presents the clone token of a build job over https.
 * <p>
 * This is the authorization decision that replaces the installation-wide build agent credential, so each way it can be
 * abused gets its own case: presenting a valid token for a repository the job does not use, after the job has finished,
 * from an address the agent is not connected from, or under a different agent's name.
 * <p>
 * Two properties matter as much as the rejections themselves. The username is an identifier and never a credential, so
 * naming an agent must achieve nothing on its own. And every rejection must fall through to normal user authentication
 * rather than refusing the request outright, because an agent short name could collide with a real login and that
 * person has to keep being able to use their own credentials.
 */
class BuildJobCloneTokenAuthenticationTest {

    private static final String BASE_URI = "http://localhost:8000";

    private static final String AGENT_NAME = "artemis-build-agent-1";

    private static final String AGENT_ADDRESS = "10.0.0.5";

    private static final String CLONE_TOKEN = "bjct-the-token-of-this-job";

    private static final String BUILD_JOB_ID = "job-1";

    private LocalVCServletService localVCServletService;

    private DistributedDataAccessService distributedDataAccessService;

    private BuildAgentAddressRegistryService buildAgentAddressRegistryService;

    private DistributedMap<String, BuildAgentInformation> buildAgentInformationMap;

    private String assignmentRepositoryUri;

    private String unrelatedRepositoryUri;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        distributedDataAccessService = mock(DistributedDataAccessService.class);
        buildAgentAddressRegistryService = mock(BuildAgentAddressRegistryService.class);

        // The cheap gate the authorization runs first: a single-key lookup that rejects a username which is not a
        // build agent at all, before anything reads the whole processing map.
        buildAgentInformationMap = mock(DistributedMap.class);
        when(distributedDataAccessService.getDistributedBuildAgentInformation()).thenReturn(buildAgentInformationMap);
        when(buildAgentInformationMap.get(AGENT_NAME))
                .thenReturn(new BuildAgentInformation(new BuildAgentDTO(AGENT_NAME, "address", "display"), 1, 0, List.of(), null, null, null, 0));

        localVCServletService = new LocalVCServletService(null, null, null, null, null, null, null, null, null, null, null, null, Optional.empty(), null, null, null,
                Optional.of(distributedDataAccessService), Optional.of(buildAgentAddressRegistryService), Optional.of(new BuildJobCloneTokenService()), policyAllowingEverything());
        ReflectionTestUtils.setField(localVCServletService, "localVCBaseUri", URI.create(BASE_URI));

        // Build the expected URIs the same way the production code derives them from the request path, rather than
        // hard-coding a format that LocalVCRepositoryUri could change
        assignmentRepositoryUri = repositoryUriOf("/git/TESTEXERCISE/testexercise-student1.git");
        unrelatedRepositoryUri = repositoryUriOf("/git/TESTEXERCISE/testexercise-student2.git");

        when(buildAgentAddressRegistryService.isRegisteredAddressOfAgent(AGENT_NAME, AGENT_ADDRESS)).thenReturn(true);
    }

    private static BuildAgentNetworkPolicy policyAllowingEverything() {
        return new BuildAgentNetworkPolicy(new BuildAgentNetworkConfiguration());
    }

    private static BuildAgentNetworkPolicy policyAllowingOnly(String range) {
        BuildAgentNetworkConfiguration configuration = new BuildAgentNetworkConfiguration();
        configuration.setAllowedRanges(List.of(range));
        return new BuildAgentNetworkPolicy(configuration);
    }

    private static String repositoryUriOf(String path) {
        return new LocalVCRepositoryUri(URI.create(BASE_URI), Path.of(path)).toString();
    }

    private static BuildJobQueueItem buildJob(String cloneToken, String... repositoryUris) {
        String assignment = repositoryUris.length > 0 ? repositoryUris[0] : null;
        var repositoryInfo = new RepositoryInfo("slug", RepositoryType.USER, RepositoryType.USER, assignment, null, null, new String[0], new String[0]);
        var jobTimingInfo = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now(), null, null, 60);
        var buildConfig = new BuildConfig("script", "image", "commit", "assignmentCommit", "testCommit", "main", null, null, false, false, List.of(), 0, null, null, null, null);
        return new BuildJobQueueItem(BUILD_JOB_ID, "name", new BuildAgentDTO(AGENT_NAME, "address", "display"), 1L, 2L, 3L, 0, 1, null, repositoryInfo, jobTimingInfo, buildConfig,
                null, cloneToken);
    }

    private static HttpServletRequest request(String username, String password, String repositoryPath, String peerAddress) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String credentials = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic " + credentials);
        // Deliberately not /info/refs: that path additionally writes an access log entry, which needs the database.
        // The authorization decision under test is identical for both requests of a git operation.
        when(request.getRequestURI()).thenReturn(repositoryPath + "/git-upload-pack");
        when(request.getRemoteAddr()).thenReturn(peerAddress);
        return request;
    }

    private boolean authenticate(HttpServletRequest request) {
        return Boolean.TRUE
                .equals(ReflectionTestUtils.invokeMethod(localVCServletService, "authenticateBuildJobCloneToken", request, request.getHeader(HttpHeaders.AUTHORIZATION)));
    }

    @Test
    void shouldAcceptTheTokenOfARunningJobForItsOwnRepository() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isTrue();
    }

    /**
     * The point of scoping the token: holding it must not open the repositories of other participants.
     */
    @Test
    void shouldRejectARepositoryThatTheJobDoesNotUse() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student2.git", AGENT_ADDRESS)))
                .as("the token of one participation must not open another").isFalse();
        assertThat(unrelatedRepositoryUri).isNotEqualTo(assignmentRepositoryUri);
    }

    /**
     * What replaces an expiry. The job leaves the processing list when it finishes, is cancelled, or hits the build
     * timeout, and the token has to stop working at that moment without any clock being involved.
     */
    @Test
    void shouldRejectATokenOnceTheJobIsNoLongerRunning() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of());

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
    }

    @Test
    void shouldRejectAnAddressTheAgentIsNotConnectedFrom() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));
        when(buildAgentAddressRegistryService.isRegisteredAddressOfAgent(AGENT_NAME, "203.0.113.9")).thenReturn(false);

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", "203.0.113.9")))
                .as("a token stolen from the queue is useless unless it is also presented from the owning agent's address").isFalse();
    }

    @Test
    void shouldRejectAPeerOutsideTheConfiguredNetworks() {
        ReflectionTestUtils.setField(localVCServletService, "buildAgentNetworkPolicy", policyAllowingOnly("10.0.0.0/8"));
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", "203.0.113.9"))).isFalse();
    }

    @Test
    void shouldAcceptAPeerInsideTheConfiguredNetworks() {
        ReflectionTestUtils.setField(localVCServletService, "buildAgentNetworkPolicy", policyAllowingOnly("10.0.0.0/8"));
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isTrue();
    }

    @Test
    void shouldRejectAWrongToken() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, "bjct-not-the-token", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
    }

    /**
     * The username carries no authority of its own. Naming an agent that is running a job, with anything other than
     * that job's token, must achieve nothing.
     */
    @Test
    void shouldRejectAnAgentNameWithoutAMatchingToken() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, "", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
        assertThat(authenticate(request(AGENT_NAME, "arbitrary-password", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
    }

    /**
     * A job that carries no token, which is what a rolling upgrade produces while some core node still issues none,
     * must not be openable by presenting no password.
     */
    @Test
    void shouldRejectAJobWithoutATokenEvenWhenNoPasswordIsPresented() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(null, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, "", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
    }

    /**
     * A node running LocalVC with Jenkins has no local CI, so there are no build jobs and nothing can present a token.
     * The branch has to decline rather than fail.
     */
    @Test
    void shouldDeclineWhenTheNodeHasNoLocalCi() {
        localVCServletService = new LocalVCServletService(null, null, null, null, null, null, null, null, null, null, null, null, Optional.empty(), null, null, null,
                Optional.empty(), Optional.empty(), Optional.empty(), policyAllowingEverything());
        ReflectionTestUtils.setField(localVCServletService, "localVCBaseUri", URI.create(BASE_URI));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
        verify(buildAgentAddressRegistryService, never()).isRegisteredAddressOfAgent(any(), any());
    }

    /**
     * A malformed authorization header must decline rather than propagate, so the request still reaches normal user
     * authentication.
     */
    @Test
    void shouldDeclineOnAMalformedAuthorizationHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic not-valid-base64!!");
        when(request.getRequestURI()).thenReturn("/git/TESTEXERCISE/testexercise-student1.git/git-upload-pack");

        assertThat(authenticate(request)).isFalse();
    }

    /**
     * The gate that keeps this method cheap for unauthenticated callers. It runs for every read request carrying any
     * Basic header and ahead of the rate limiter, so a username that is not a build agent must be rejected by a single
     * key lookup and must never reach the whole-map read below.
     */
    @Test
    void shouldRejectAnUnknownUsernameWithoutReadingTheProcessingJobs() {
        assertThat(authenticate(request("not-an-agent", CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();

        verify(buildAgentInformationMap).get("not-an-agent");
        verify(distributedDataAccessService, never()).getProcessingJobsForAgentByName(any());
    }

    /**
     * The same ordering property for a known agent calling from an address it is not connected from: the origin is
     * checked before anything expensive, and before the token is compared at all.
     */
    @Test
    void shouldRejectAnUnregisteredAddressWithoutReadingTheProcessingJobs() {
        when(buildAgentAddressRegistryService.isRegisteredAddressOfAgent(AGENT_NAME, "203.0.113.9")).thenReturn(false);

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", "203.0.113.9"))).isFalse();

        verify(distributedDataAccessService, never()).getProcessingJobsForAgentByName(any());
    }
}
