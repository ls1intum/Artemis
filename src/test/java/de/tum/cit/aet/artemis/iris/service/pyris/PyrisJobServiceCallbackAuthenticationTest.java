package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DefaultTimeToLiveDistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.service.IrisLectureMaterialVersionService;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.GlobalSearchAnswerJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;

/**
 * Unit tests for how {@link PyrisJobService} authenticates Pyris callbacks with signed tokens, including client-chosen global search run ids that arrive
 * again. Real local maps stand behind the distributed ones, so putIfAbsent and expiry behave as in production.
 */
class PyrisJobServiceCallbackAuthenticationTest {

    private PyrisCallbackTokenService tokenService;

    private PyrisJobService service;

    @BeforeEach
    void setUp() {
        var distributedDataProvider = mock(DistributedDataProvider.class);
        tokenService = PyrisCallbackTokenTestUtil.tokenService();
        service = serviceOverFreshDistributedState();
    }

    // A service whose distributed maps start empty, as after a restart of the whole cluster; the token key stays the same.
    private PyrisJobService serviceOverFreshDistributedState() {
        Map<String, DistributedMap<?, ?>> maps = new HashMap<>();
        var distributedDataProvider = mock(DistributedDataProvider.class);
        doAnswer(invocation -> maps.computeIfAbsent(invocation.getArgument(0), name -> new DefaultTimeToLiveDistributedMap<>(new LocalMap<>(), invocation.getArgument(1))))
                .when(distributedDataProvider).getExpiringMap(anyString(), any(Duration.class));
        var freshService = new PyrisJobService(distributedDataProvider, Optional.empty(), new IrisProactiveProperties(), mock(IrisLectureMaterialVersionService.class),
                tokenService);
        ReflectionTestUtils.setField(freshService, "jobTimeout", 300);
        return freshService;
    }

    private static MockHttpServletRequest withAuthorization(String value) {
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, value);
        return request;
    }

    private String chatRun() {
        return service.addChatJob(1L, 2L, null, null, null);
    }

    @Test
    void acceptsTheTokenIssuedForTheRun() {
        String runId = chatRun();
        var request = withAuthorization("Bearer " + tokenService.issue(service.getJob(runId)));

        ChatJob job = service.getAndAuthenticateJobFromHeaderElseThrow(request, runId, ChatJob.class);

        assertThat(job.jobId()).isEqualTo(runId);
    }

    @Test
    void rejectsTheRunIdItselfAsCredential() {
        String runId = chatRun();

        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization("Bearer " + runId), runId, ChatJob.class))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void rejectsMissingOrNonBearerHeaders() {
        String runId = chatRun();
        String token = tokenService.issue(service.getJob(runId));

        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(new MockHttpServletRequest(), runId, ChatJob.class)).isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization(token), runId, ChatJob.class)).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void rejectsATokenFromAnotherInstallation() {
        String runId = chatRun();
        String foreignToken = PyrisCallbackTokenTestUtil.tokenService().issue(service.getJob(runId));

        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization("Bearer " + foreignToken), runId, ChatJob.class))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void rejectsAnotherRunsTokenForThisRun() {
        String runA = chatRun();
        String runB = chatRun();
        String tokenOfA = tokenService.issue(service.getJob(runA));

        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization("Bearer " + tokenOfA), runB, ChatJob.class))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsTheTokenAsTheRunIdInTheUrl() {
        // An earlier Pyris used the token as its run id; that form is no longer accepted.
        String runId = chatRun();
        String token = tokenService.issue(service.getJob(runId));

        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization("Bearer " + token), token, ChatJob.class))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsATokenIssuedForAnotherKindOfRunWithTheSameId() {
        String runId = chatRun();
        String tutorToken = tokenService.issue(new TutorSuggestionJob(runId, 1L, 2L, 3L, null, null, null));

        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization("Bearer " + tutorToken), runId, PyrisJob.class))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void rejectsATokenOnceTheJobIsGone() {
        String runId = chatRun();
        String token = tokenService.issue(service.getJob(runId));
        service.removeJob(service.getJob(runId));

        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization("Bearer " + token), runId, ChatJob.class))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void rejectsAJobOfTheWrongClass() {
        String runId = chatRun();
        String token = tokenService.issue(service.getJob(runId));

        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization("Bearer " + token), runId, TutorSuggestionJob.class))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aTokenOfAnEarlierGlobalSearchRequestNeverAuthenticatesALaterOneWithTheSameRunId() {
        String runId = UUID.randomUUID().toString();
        service.addGlobalSearchAnswerJob("student", runId);
        String earlierToken = tokenService.issue(service.getJob(runId));
        service.finishGlobalSearchAnswerJob((GlobalSearchAnswerJob) service.getJob(runId), null, false);

        service.addGlobalSearchAnswerJob("student", runId);

        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization("Bearer " + earlierToken), runId, GlobalSearchAnswerJob.class))
                .isInstanceOf(AccessForbiddenException.class);
        String laterToken = tokenService.issue(service.getJob(runId));
        assertThat(service.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization("Bearer " + laterToken), runId, GlobalSearchAnswerJob.class).jobId()).isEqualTo(runId);
    }

    @Test
    void aTokenOfAnotherUsersGlobalSearchRequestIsRejectedAfterTheDistributedStateWasLost() {
        String runId = UUID.randomUUID().toString();
        service.addGlobalSearchAnswerJob("alice", runId);
        String aliceToken = tokenService.issue(service.getJob(runId));

        // The cluster restarts: every job and map entry is gone, the signing key is not.
        PyrisJobService afterRestart = serviceOverFreshDistributedState();
        afterRestart.addGlobalSearchAnswerJob("bob", runId);

        assertThatThrownBy(() -> afterRestart.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization("Bearer " + aliceToken), runId, GlobalSearchAnswerJob.class))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void aGlobalSearchRunIdCannotReplaceAJobInProgress() {
        String runId = UUID.randomUUID().toString();
        service.addGlobalSearchAnswerJob("student", runId);
        var inProgress = service.getJob(runId);

        assertThatThrownBy(() -> service.addGlobalSearchAnswerJob("someone-else", runId)).isInstanceOf(ConflictException.class);
        assertThat(service.getJob(runId)).isEqualTo(inProgress);
    }

    @Test
    void aGlobalSearchRunIdCannotReplaceAnotherJob() {
        String chatRunId = chatRun();
        var chatJob = service.getJob(chatRunId);

        assertThatThrownBy(() -> service.addGlobalSearchAnswerJob("attacker", chatRunId)).isInstanceOf(ConflictException.class);
        assertThat(service.getJob(chatRunId)).as("the existing job is unchanged").isEqualTo(chatJob);
    }
}
