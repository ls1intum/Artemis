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

    // The distributed maps behind the service created last.
    private Map<String, DistributedMap<?, ?>> maps;

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
        maps = new HashMap<>();
        var distributedDataProvider = mock(DistributedDataProvider.class);
        doAnswer(invocation -> maps.computeIfAbsent(invocation.getArgument(0), name -> new DefaultTimeToLiveDistributedMap<>(new LocalMap<>(), invocation.getArgument(1))))
                .when(distributedDataProvider).getExpiringMap(anyString(), any(Duration.class));
        var freshService = new PyrisJobService(distributedDataProvider, Optional.empty(), new IrisProactiveProperties(), mock(IrisLectureMaterialVersionService.class),
                tokenService);
        ReflectionTestUtils.setField(freshService, "jobTimeout", 300);
        return freshService;
    }

    // What the expiry of a job's map entry does.
    @SuppressWarnings("unchecked")
    private void expire(String runId) {
        ((DistributedMap<String, PyrisJob>) maps.get("pyris-job-map-v2")).remove(runId);
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

    @Test
    void aRequestDispatchedAfterItsRunIdWasReusedCarriesItsOwnInvocation() {
        String runId = UUID.randomUUID().toString();
        GlobalSearchAnswerJob alicesJob = service.addGlobalSearchAnswerJob("alice", runId);
        // Alice's request stalls before dispatch; its entry expires and Bob registers the same run id.
        expire(runId);
        service.addGlobalSearchAnswerJob("bob", runId);

        // The dispatch signs the job registered for Alice's request, so her token never authenticates as Bob's job.
        String alicesToken = tokenService.issue(alicesJob);
        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(withAuthorization("Bearer " + alicesToken), runId, GlobalSearchAnswerJob.class))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void aStaleCallbackNeitherOverwritesNorRemovesANewerRequestWithTheSameRunId() {
        String runId = UUID.randomUUID().toString();
        GlobalSearchAnswerJob alicesJob = service.addGlobalSearchAnswerJob("alice", runId);
        // Alice's callback was authenticated, then her entry expired and Bob registered the same run id.
        expire(runId);
        GlobalSearchAnswerJob bobsJob = service.addGlobalSearchAnswerJob("bob", runId);

        service.updateJob(alicesJob);
        assertThat(service.getJob(runId)).as("a stale update does not restore the older request").isEqualTo(bobsJob);
        service.removeJob(alicesJob);
        assertThat(service.getJob(runId)).as("a stale removal does not delete the newer request").isEqualTo(bobsJob);
        service.finishGlobalSearchAnswerJob(alicesJob, null, false);
        assertThat(service.getJob(runId)).as("a stale finish does not delete the newer request").isEqualTo(bobsJob);

        service.finishGlobalSearchAnswerJob(bobsJob, null, false);
        assertThat(service.getJob(runId)).as("the current request finishes as before").isNull();
    }

    @Test
    void anUpdateDoesNotBringBackAnExpiredGlobalSearchRequest() {
        String runId = UUID.randomUUID().toString();
        GlobalSearchAnswerJob job = service.addGlobalSearchAnswerJob("student", runId);
        expire(runId);

        service.updateJob(job);

        assertThat(service.getJob(runId)).isNull();
    }

    @Test
    void aStaleCallbackOfAnotherJobTypeNeitherOverwritesNorRemovesAGlobalSearchRequestWithItsRunId() {
        String runId = service.addTutorSuggestionJob(1L, 2L, 3L);
        var tutorJob = service.getJob(runId);
        // The tutor suggestion callback was authenticated, then its entry expired and a client sent the run id for a global search request.
        expire(runId);
        GlobalSearchAnswerJob globalSearchJob = service.addGlobalSearchAnswerJob("bob", runId);

        service.updateJob(tutorJob);
        assertThat(service.getJob(runId)).as("a stale update of another job type does not overwrite the request").isEqualTo(globalSearchJob);
        service.removeJob(tutorJob);
        assertThat(service.getJob(runId)).as("a stale removal of another job type does not delete the request").isEqualTo(globalSearchJob);
    }

    @Test
    void jobsWithoutInvocationAreUpdatedAndRemovedAsBefore() {
        String runId = chatRun();
        var job = service.getJob(runId);

        service.updateJob(job);
        assertThat(service.getJob(runId)).isEqualTo(job);
        service.removeJob(job);
        assertThat(service.getJob(runId)).isNull();
        service.updateJob(job);
        assertThat(service.getJob(runId)).as("a job whose run id Artemis generated may still be stored again after it expired").isEqualTo(job);
    }

    @Test
    void cleanupOfAJobWhoseRunIdWasTakenByAGlobalSearchRequestLeavesTheRequestAlone() {
        String ingestionRunId = service.addLectureIngestionWebhookJob(1L, 2L, 3L);
        var struggleRunId = service.addStruggleInterventionJobIfNonePending(1L, 7L, 42L, "decide", null, null, null, null).orElseThrow();
        // Both jobs expired, and clients sent their run ids for global search requests.
        expire(ingestionRunId);
        expire(struggleRunId);
        GlobalSearchAnswerJob firstRequest = service.addGlobalSearchAnswerJob("bob", ingestionRunId);
        GlobalSearchAnswerJob secondRequest = service.addGlobalSearchAnswerJob("carol", struggleRunId);

        service.removeJobByRunId(ingestionRunId);
        service.releaseStruggleInFlightJob(struggleRunId, 7L, 42L);

        assertThat(service.getJob(ingestionRunId)).isEqualTo(firstRequest);
        assertThat(service.getJob(struggleRunId)).isEqualTo(secondRequest);
    }

    @Test
    void cleanupRemovesTheJobsArtemisRegistered() {
        String ingestionRunId = service.addLectureIngestionWebhookJob(1L, 2L, 3L);
        var struggleRunId = service.addStruggleInterventionJobIfNonePending(1L, 7L, 42L, "decide", null, null, null, null).orElseThrow();

        service.removeJobByRunId(ingestionRunId);
        service.releaseStruggleInFlightJob(struggleRunId, 7L, 42L);

        assertThat(service.getJob(ingestionRunId)).isNull();
        assertThat(service.getJob(struggleRunId)).isNull();
    }
}
