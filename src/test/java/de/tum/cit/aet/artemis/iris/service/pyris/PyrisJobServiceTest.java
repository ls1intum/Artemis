package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.service.IrisLectureMaterialVersionService;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitProcessingStateRepositoryApi;

class PyrisJobServiceTest {

    private DistributedMap<String, PyrisJob> jobMap;

    private DistributedMap<String, String> clientIdMap;

    private IrisLectureMaterialVersionService materialVersionService;

    private PyrisJobService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        var distributedDataProvider = mock(DistributedDataProvider.class);
        jobMap = mock(DistributedMap.class);
        clientIdMap = mock(DistributedMap.class);
        doAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "pyris-job-map-v2" -> jobMap;
            case "pyris-chat-job-client-id-map" -> clientIdMap;
            default -> throw new AssertionError("Unexpected distributed map: " + invocation.getArgument(0));
        }).when(distributedDataProvider).getExpiringMap(anyString(), any(Duration.class));

        materialVersionService = mock(IrisLectureMaterialVersionService.class);
        service = new PyrisJobService(distributedDataProvider, Optional.<LectureUnitProcessingStateRepositoryApi>empty(), mock(IrisProactiveProperties.class),
                materialVersionService, PyrisCallbackTokenTestUtil.tokenService());
        ReflectionTestUtils.setField(service, "jobTimeout", 300);
    }

    @Test
    void addChatJobStoresClientIdWithoutChangingTheDistributedJobShape() {
        String runId = service.addChatJob(1L, 2L, 3L, 4L, "tab-7");

        assertThat(UUID.fromString(runId).toString()).as("the run id is a plain random UUID").isEqualTo(runId);
        verify(jobMap).putIfAbsent(runId, new ChatJob(runId, 1L, 2L, 3L, null, 4L, null), Duration.ofSeconds(300));
        verify(clientIdMap).put(runId, "tab-7");
    }

    @Test
    void updateAndRemoveChatJobKeepClientIdLifecycleAligned() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, 4L, 5L, 6L);
        when(clientIdMap.get(job.jobId())).thenReturn("tab-7");

        service.updateJob(job);

        verify(jobMap).put(job.jobId(), job, Duration.ofSeconds(300));
        verify(clientIdMap).put(job.jobId(), "tab-7", Duration.ofSeconds(300));
        assertThat(service.getChatJobClientId(job.jobId())).isEqualTo("tab-7");

        when(jobMap.get(job.jobId())).thenReturn(job);
        service.removeJob(job);

        verify(materialVersionService).refresh(job.jobId());
        verify(materialVersionService).remove(job.jobId());
        verify(jobMap).remove(job.jobId());
        verify(clientIdMap).remove(job.jobId());
    }

    @Test
    void getAndAuthenticateJobWithoutAuthorizationHeaderThrowsAccessForbidden() {
        var request = new MockHttpServletRequest();

        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(request, "run-1", ChatJob.class)).isInstanceOf(AccessForbiddenException.class)
                .hasMessage("No valid token provided");
    }
}
