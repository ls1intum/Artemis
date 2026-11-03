package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.service.IrisLectureMaterialVersionService;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitProcessingStateRepositoryApi;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;

/**
 * Unit tests for the database fallback in {@link PyrisJobService}: a lecture ingestion callback whose
 * job map entry expired is accepted from the processing state that still carries the run id, but only with a
 * valid callback token for that run. Cleared or unknown run ids stay invalid, and the run id alone authenticates nothing.
 */
class PyrisJobServiceDatabaseFallbackTest {

    private static final String RUN_ID = "0b7c2f4e-5d2a-4f43-9d55-0d6c51a0a7e1";

    private PyrisJobService jobService;

    private PyrisCallbackTokenService tokenService;

    private DistributedMap<String, PyrisJob> jobMap;

    private LectureUnitProcessingStateRepositoryApi callbackApi;

    private HttpServletRequest request;

    /**
     * Concrete generic binding so Mockito can mock the map with the right type parameters.
     */
    interface PyrisJobMap extends DistributedMap<String, PyrisJob> {
    }

    @BeforeEach
    void setUp() {
        DistributedDataProvider distributedDataProvider = mock(DistributedDataProvider.class);
        jobMap = mock(PyrisJobMap.class);
        when(distributedDataProvider.<String, PyrisJob>getExpiringMap(eq("pyris-job-map-v2"), any())).thenReturn(jobMap);
        callbackApi = mock(LectureUnitProcessingStateRepositoryApi.class);
        tokenService = PyrisCallbackTokenTestUtil.tokenService();
        jobService = new PyrisJobService(distributedDataProvider, Optional.of(callbackApi), new IrisProactiveProperties(), mock(IrisLectureMaterialVersionService.class),
                tokenService);

        request = mock(HttpServletRequest.class);
        String ingestionToken = tokenService.issue(new LectureIngestionWebhookJob(RUN_ID, 1L, 2L, 3L));
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + ingestionToken);
    }

    @Test
    void shouldAuthenticateFromTheJobMapWithoutTouchingTheDatabase() {
        var job = new LectureIngestionWebhookJob(RUN_ID, 1L, 2L, 3L);
        when(jobMap.get(RUN_ID)).thenReturn(job);

        var result = jobService.getAndAuthenticateJobFromHeaderElseThrow(request, RUN_ID, LectureIngestionWebhookJob.class);

        assertThat(result).isSameAs(job);
        verifyNoInteractions(callbackApi);
    }

    @Test
    void shouldReconstructTheJobFromTheProcessingStateAfterMapExpiry() {
        when(jobMap.get(RUN_ID)).thenReturn(null);
        when(callbackApi.findIngestionJobIdentityByToken(RUN_ID)).thenReturn(Optional.of(new IngestionJobIdentityDTO(1L, 2L, 3L)));

        var result = jobService.getAndAuthenticateJobFromHeaderElseThrow(request, RUN_ID, LectureIngestionWebhookJob.class);

        assertThat(result.courseId()).isEqualTo(1L);
        assertThat(result.lectureId()).isEqualTo(2L);
        assertThat(result.lectureUnitId()).isEqualTo(3L);
        assertThat(result.jobId()).isEqualTo(RUN_ID);
    }

    @Test
    void shouldRejectTheRunIdAsBearerWithoutLookingItUp() {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + RUN_ID);

        assertThatThrownBy(() -> jobService.getAndAuthenticateJobFromHeaderElseThrow(request, RUN_ID, LectureIngestionWebhookJob.class))
                .isInstanceOf(AccessForbiddenException.class);
        verifyNoInteractions(callbackApi, jobMap);
    }

    @Test
    void shouldNotRecoverForATokenOfAnotherKind() {
        String chatToken = tokenService.issue(new ChatJob(RUN_ID, 1L, 2L, null, null, null, null));
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + chatToken);
        when(jobMap.get(RUN_ID)).thenReturn(null);

        assertThatThrownBy(() -> jobService.getAndAuthenticateJobFromHeaderElseThrow(request, RUN_ID, LectureIngestionWebhookJob.class))
                .isInstanceOf(AccessForbiddenException.class);
        verifyNoInteractions(callbackApi);
    }

    @Test
    void shouldRejectRunIdsNoProcessingStateCarries() {
        when(jobMap.get(RUN_ID)).thenReturn(null);
        when(callbackApi.findIngestionJobIdentityByToken(RUN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getAndAuthenticateJobFromHeaderElseThrow(request, RUN_ID, LectureIngestionWebhookJob.class))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void shouldRejectRecoveredJobsOfTheWrongType() {
        when(jobMap.get(RUN_ID)).thenReturn(null);
        when(callbackApi.findIngestionJobIdentityByToken(RUN_ID)).thenReturn(Optional.of(new IngestionJobIdentityDTO(1L, 2L, 3L)));

        assertThatThrownBy(() -> jobService.getAndAuthenticateJobFromHeaderElseThrow(request, RUN_ID, ChatJob.class)).isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldRejectWithoutTheFallbackApiPresent() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        when(provider.<String, PyrisJob>getExpiringMap(eq("pyris-job-map-v2"), any())).thenReturn(jobMap);
        PyrisJobService withoutFallback = new PyrisJobService(provider, Optional.empty(), new IrisProactiveProperties(), mock(IrisLectureMaterialVersionService.class),
                tokenService);
        when(jobMap.get(RUN_ID)).thenReturn(null);

        assertThatThrownBy(() -> withoutFallback.getAndAuthenticateJobFromHeaderElseThrow(request, RUN_ID, LectureIngestionWebhookJob.class))
                .isInstanceOf(AccessForbiddenException.class);
    }
}
