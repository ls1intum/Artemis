package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;

/**
 * Unit tests for the database fallback in {@link PyrisJobService}: a lecture ingestion callback whose
 * job map entry expired is authenticated against the processing state that still carries the token,
 * while cleared or unknown tokens stay invalid.
 */
class PyrisJobServiceDatabaseFallbackTest {

    private static final String TOKEN = "ingestion-token-123";

    private PyrisJobService jobService;

    private DistributedMap<String, PyrisJob> jobMap;

    private ProcessingStateCallbackApi callbackApi;

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
        when(distributedDataProvider.<String, PyrisJob>getExpiringMap(eq("pyris-job-map"), any())).thenReturn(jobMap);
        callbackApi = mock(ProcessingStateCallbackApi.class);
        jobService = new PyrisJobService(distributedDataProvider, Optional.of(callbackApi));

        request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + TOKEN);
    }

    @Test
    void shouldAuthenticateFromTheJobMapWithoutTouchingTheDatabase() {
        var job = new LectureIngestionWebhookJob(TOKEN, 1L, 2L, 3L);
        when(jobMap.get(TOKEN)).thenReturn(job);

        var result = jobService.getAndAuthenticateJobFromHeaderElseThrow(request, LectureIngestionWebhookJob.class);

        assertThat(result).isSameAs(job);
        org.mockito.Mockito.verifyNoInteractions(callbackApi);
    }

    @Test
    void shouldReconstructTheJobFromTheProcessingStateAfterMapExpiry() {
        when(jobMap.get(TOKEN)).thenReturn(null);
        when(callbackApi.findIngestionJobIdentityByToken(TOKEN)).thenReturn(Optional.of(new IngestionJobIdentityDTO(1L, 2L, 3L)));

        var result = jobService.getAndAuthenticateJobFromHeaderElseThrow(request, LectureIngestionWebhookJob.class);

        assertThat(result.courseId()).isEqualTo(1L);
        assertThat(result.lectureId()).isEqualTo(2L);
        assertThat(result.lectureUnitId()).isEqualTo(3L);
        assertThat(result.jobId()).isEqualTo(TOKEN);
    }

    @Test
    void shouldRejectTokensNoProcessingStateCarries() {
        when(jobMap.get(TOKEN)).thenReturn(null);
        when(callbackApi.findIngestionJobIdentityByToken(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getAndAuthenticateJobFromHeaderElseThrow(request, LectureIngestionWebhookJob.class)).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void shouldRejectRecoveredJobsOfTheWrongType() {
        when(jobMap.get(TOKEN)).thenReturn(null);
        when(callbackApi.findIngestionJobIdentityByToken(TOKEN)).thenReturn(Optional.of(new IngestionJobIdentityDTO(1L, 2L, 3L)));

        assertThatThrownBy(() -> jobService.getAndAuthenticateJobFromHeaderElseThrow(request, ChatJob.class)).isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldRejectWithoutTheFallbackApiPresent() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        when(provider.<String, PyrisJob>getExpiringMap(eq("pyris-job-map"), any())).thenReturn(jobMap);
        PyrisJobService withoutFallback = new PyrisJobService(provider, Optional.empty());
        when(jobMap.get(TOKEN)).thenReturn(null);

        assertThatThrownBy(() -> withoutFallback.getAndAuthenticateJobFromHeaderElseThrow(request, LectureIngestionWebhookJob.class)).isInstanceOf(AccessForbiddenException.class);
    }
}
