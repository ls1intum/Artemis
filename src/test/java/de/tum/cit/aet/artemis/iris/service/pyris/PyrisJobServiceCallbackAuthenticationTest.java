package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DefaultTimeToLiveDistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DelegatingDistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.service.IrisLectureMaterialVersionService;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.GlobalSearchAnswerJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;

/**
 * Unit tests for how {@link PyrisJobService} authenticates Pyris callbacks with signed tokens, and for the reservation that keeps a client-chosen global search
 * run id from ever being reused while a token for it could be valid. Real local maps stand behind the distributed ones, so putIfAbsent and expiry behave as in
 * production.
 */
class PyrisJobServiceCallbackAuthenticationTest {

    private final Map<String, DistributedMap<?, ?>> maps = new HashMap<>();

    /**
     * Records until when each entry was last written to stay, which a local map does not expose.
     */
    private static final class ExpiryRecordingMap extends DelegatingDistributedMap<String, String> {

        private final Map<String, Instant> writtenUntil = new HashMap<>();

        private ExpiryRecordingMap(Duration defaultTimeToLive) {
            super(new DefaultTimeToLiveDistributedMap<>(new LocalMap<>(), defaultTimeToLive));
        }

        @Override
        public void put(String key, String value, Duration timeToLive) {
            writtenUntil.put(key, Instant.now().plus(timeToLive));
            super.put(key, value, timeToLive);
        }
    }

    private ExpiryRecordingMap reservations;

    private PyrisCallbackTokenService tokenService;

    private PyrisJobService service;

    @BeforeEach
    void setUp() {
        var distributedDataProvider = mock(DistributedDataProvider.class);
        doAnswer(invocation -> maps.computeIfAbsent(invocation.getArgument(0), name -> {
            Duration defaultTimeToLive = invocation.getArgument(1);
            if ("pyris-global-search-run-ids".equals(name)) {
                reservations = new ExpiryRecordingMap(defaultTimeToLive);
                return reservations;
            }
            return new DefaultTimeToLiveDistributedMap<>(new LocalMap<>(), defaultTimeToLive);
        })).when(distributedDataProvider).getExpiringMap(anyString(), any(Duration.class));
        tokenService = PyrisCallbackTokenTestUtil.tokenService();
        service = new PyrisJobService(distributedDataProvider, Optional.empty(), new IrisProactiveProperties(), mock(IrisLectureMaterialVersionService.class), tokenService);
        ReflectionTestUtils.setField(service, "jobTimeout", 300);
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
    void aGlobalSearchRunIdCannotBeUsedTwiceEvenAfterTheRunFinished() {
        String runId = UUID.randomUUID().toString();
        service.addGlobalSearchAnswerJob("student", runId);
        service.finishGlobalSearchAnswerJob((GlobalSearchAnswerJob) service.getJob(runId), null, false);

        assertThatThrownBy(() -> service.addGlobalSearchAnswerJob("student", runId)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.addGlobalSearchAnswerJob("someone-else", runId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aGlobalSearchRunIdCannotReplaceAnotherJob() {
        String chatRunId = chatRun();
        var chatJob = service.getJob(chatRunId);

        assertThatThrownBy(() -> service.addGlobalSearchAnswerJob("attacker", chatRunId)).isInstanceOf(ConflictException.class);
        assertThat(service.getJob(chatRunId)).as("the existing job is unchanged").isEqualTo(chatJob);
    }

    @Test
    void theGlobalSearchReservationOutlivesTheTokenEvenWhenSigningIsDelayed() {
        String runId = UUID.randomUUID().toString();
        service.addGlobalSearchAnswerJob("student", runId);
        var job = service.getJob(runId);
        var expiry = new AtomicReference<Instant>();

        tokenService.issue(job, tokenExpiry -> {
            expiry.set(tokenExpiry);
            service.reserveGlobalSearchRunIdBeyond(runId, "student", tokenExpiry);
            try {
                // A pause after the reservation and before signing: the token keeps the expiry it was given
                Thread.sleep(1100);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(reservations.get(runId)).isEqualTo("student");
        // Both instants come from the same clock, apart from the few milliseconds between computing the time to live and recording it.
        assertThat(reservations.writtenUntil.get(runId)).as("the reservation ends at least the margin after the token expires")
                .isAfterOrEqualTo(expiry.get().plus(PyrisJobService.GLOBAL_SEARCH_RUN_ID_RESERVATION_MARGIN).minusMillis(100));
    }

    @Test
    void onlyTheOwnerCanExtendAGlobalSearchReservation() {
        String runId = UUID.randomUUID().toString();
        service.addGlobalSearchAnswerJob("student", runId);

        assertThatThrownBy(() -> service.reserveGlobalSearchRunIdBeyond(runId, "someone-else", Instant.now().plus(Duration.ofHours(6)))).isInstanceOf(ConflictException.class);
    }
}
