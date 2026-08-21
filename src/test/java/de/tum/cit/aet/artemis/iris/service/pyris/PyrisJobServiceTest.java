package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryEventType;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryExpiredListener;
import com.hazelcast.map.listener.MapListener;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.iris.service.pyris.event.PyrisJobExpiredEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;

class PyrisJobServiceTest {

    private HazelcastInstance hazelcastInstance;

    @SuppressWarnings("unchecked")
    private final IMap<String, PyrisJob> jobMap = mock(IMap.class);

    private ApplicationEventPublisher eventPublisher;

    private PyrisJobService service;

    @BeforeEach
    void setUp() {
        hazelcastInstance = mock(HazelcastInstance.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        doReturn(jobMap).when(hazelcastInstance).getMap("pyris-job-map");

        service = new PyrisJobService(hazelcastInstance, eventPublisher);
    }

    @Test
    void addAskUserChatJobStoresJobWithAskUserPipelineName() {
        var token = service.addAskUserChatJob(1L, 2L, 3L, 4L);

        var captor = ArgumentCaptor.forClass(ChatJob.class);
        verify(jobMap).put(eq(token), captor.capture());
        assertThat(captor.getValue().pipelineName()).isEqualTo(ChatJob.ASK_USER_PIPELINE_NAME);
        assertThat(captor.getValue().isAskUserPipeline()).isTrue();
        assertThat(captor.getValue().courseId()).isEqualTo(1L);
        assertThat(captor.getValue().sessionId()).isEqualTo(2L);
        assertThat(captor.getValue().entityId()).isEqualTo(3L);
        assertThat(captor.getValue().userMessageId()).isEqualTo(4L);
    }

    @Test
    void addChatJobStoresJobWithChatPipelineName() {
        var token = service.addChatJob(1L, 2L, 3L, 4L);

        var captor = ArgumentCaptor.forClass(ChatJob.class);
        verify(jobMap).put(eq(token), captor.capture());
        assertThat(captor.getValue().pipelineName()).isEqualTo(ChatJob.CHAT_PIPELINE_NAME);
        assertThat(captor.getValue().isAskUserPipeline()).isFalse();
    }

    @Test
    void removeJobReturnsRemovedJobWhenPresent() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, null, null, null);
        when(jobMap.remove("job-1")).thenReturn(job);

        var result = service.removeJob(job);

        assertThat(result).isEqualTo(job);
    }

    @Test
    void removeJobReturnsNullWhenJobDoesNotExist() {
        var job = new ChatJob("missing-job", 1L, 2L, 3L, null, null, null);
        when(jobMap.remove("missing-job")).thenReturn(null);

        var result = service.removeJob(job);

        assertThat(result).isNull();
    }

    @Test
    void jobMapExpiryListenerPublishesJobExpiredEventWhenOldValuePresent() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, null, null, null, ChatJob.ASK_USER_PIPELINE_NAME);
        // Trigger lazy map initialization (and entry-listener registration) via any public method.
        service.getJob("job-1");

        var listener = captureExpiredListener();
        var event = new EntryEvent<String, PyrisJob>("pyris-job-map", null, EntryEventType.EXPIRED.getType(), "job-1", job, null);
        listener.entryExpired(event);

        var eventCaptor = ArgumentCaptor.forClass(PyrisJobExpiredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventObject()).isEqualTo(job);
    }

    @Test
    void jobMapExpiryListenerFallsBackToNewValueWhenOldValueMissing() {
        var job = new ChatJob("job-1", 1L, 2L, 3L, null, null, null, ChatJob.ASK_USER_PIPELINE_NAME);
        service.getJob("job-1");

        var listener = captureExpiredListener();
        var event = new EntryEvent<String, PyrisJob>("pyris-job-map", null, EntryEventType.EXPIRED.getType(), "job-1", null, job);
        listener.entryExpired(event);

        var eventCaptor = ArgumentCaptor.forClass(PyrisJobExpiredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventObject()).isEqualTo(job);
    }

    @Test
    void jobMapExpiryListenerPublishesNothingWhenBothValuesMissing() {
        service.getJob("job-1");

        var listener = captureExpiredListener();
        var event = new EntryEvent<String, PyrisJob>("pyris-job-map", null, EntryEventType.EXPIRED.getType(), "job-1", null, null);
        listener.entryExpired(event);

        verify(eventPublisher, times(0)).publishEvent(any());
    }

    @SuppressWarnings("unchecked")
    private EntryExpiredListener<String, PyrisJob> captureExpiredListener() {
        var captor = ArgumentCaptor.forClass(MapListener.class);
        verify(jobMap).addEntryListener(captor.capture(), anyBoolean());
        return (EntryExpiredListener<String, PyrisJob>) captor.getValue();
    }

    @Test
    void authenticateJobFromHeaderThrowsWhenAuthorizationHeaderIsMissing() {
        var request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(request, ChatJob.class))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void authenticateJobFromHeaderThrowsWhenHeaderHasWrongPrefix() {
        var request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic abc123");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getAndAuthenticateJobFromHeaderElseThrow(request, ChatJob.class))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void authenticateJobFromHeaderReturnsJobForValidToken() {
        var job = new ChatJob("valid-token", 1L, 2L, 3L, null, null, null);
        when(jobMap.get("valid-token")).thenReturn(job);
        var request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(Constants.BEARER_PREFIX + "valid-token");

        var result = service.getAndAuthenticateJobFromHeaderElseThrow(request, ChatJob.class);

        assertThat(result).isEqualTo(job);
    }

    @Test
    void updateJobUsesShorterTimeoutForNonIngestionJobs() {
        ReflectionTestUtils.setField(service, "jobTimeout", 300);
        var job = new ChatJob("job-1", 1L, 2L, 3L, null, null, null);

        service.updateJob(job);

        verify(jobMap).put(eq("job-1"), eq(job), eq(300L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }
}
