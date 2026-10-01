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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;

class PyrisJobServiceTest {

    private DistributedMap<String, PyrisJob> jobMap;

    private DistributedMap<String, String> clientIdMap;

    private PyrisJobService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        var distributedDataProvider = mock(DistributedDataProvider.class);
        jobMap = mock(DistributedMap.class);
        clientIdMap = mock(DistributedMap.class);
        doAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "pyris-job-map" -> jobMap;
            case "pyris-chat-job-client-id-map" -> clientIdMap;
            default -> throw new AssertionError("Unexpected distributed map: " + invocation.getArgument(0));
        }).when(distributedDataProvider).getExpiringMap(anyString(), any(Duration.class));

        service = new PyrisJobService(distributedDataProvider, mock(IrisProactiveProperties.class));
        ReflectionTestUtils.setField(service, "serverUrl", "https://artemis.example");
        ReflectionTestUtils.setField(service, "instanceId", "node-1");
        ReflectionTestUtils.setField(service, "jobTimeout", 300);
    }

    @Test
    void addChatJobStoresClientIdWithoutChangingTheDistributedJobShape() {
        String token = service.addChatJob(1L, 2L, 3L, 4L, "tab-7");

        verify(jobMap).put(token, new ChatJob(token, 1L, 2L, 3L, null, 4L, null));
        verify(clientIdMap).put(token, "tab-7");
    }

    @Test
    void updateAndRemoveChatJobKeepClientIdLifecycleAligned() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, 4L, 5L, 6L);
        when(clientIdMap.get(job.jobId())).thenReturn("tab-7");

        service.updateJob(job);

        verify(jobMap).put(job.jobId(), job, Duration.ofSeconds(300));
        verify(clientIdMap).put(job.jobId(), "tab-7", Duration.ofSeconds(300));
        assertThat(service.getChatJobClientId(job.jobId())).isEqualTo("tab-7");

        service.removeJob(job);

        verify(jobMap).remove(job.jobId());
        verify(clientIdMap).remove(job.jobId());
    }

    @Test
    void getAndAuthenticateJobWithoutAuthorizationHeaderThrowsAccessForbidden() {
        var request = new MockHttpServletRequest();

        assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(request, ChatJob.class)).isInstanceOf(AccessForbiddenException.class)
                .hasMessage("No valid token provided");
    }
}
