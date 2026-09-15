package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.COURSE_OPERATION_PROGRESS_STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.course.domain.CourseOperationType;
import de.tum.cit.aet.artemis.course.dto.CourseOperationProgressDTO;

class CourseOperationProgressServiceTest {

    private static final long COURSE_ID = 42L;

    private static final ZonedDateTime STARTED_AT = ZonedDateTime.parse("2026-01-01T12:00:00Z");

    private CacheManager cacheManager;

    private CourseOperationProgressService firstNode;

    private CourseOperationProgressService secondNode;

    @BeforeEach
    void setUp() {
        var distributedDataProvider = new LocalDataProviderService();
        cacheManager = new ConcurrentMapCacheManager(COURSE_OPERATION_PROGRESS_STATUS);
        firstNode = new CourseOperationProgressService(cacheManager, mock(WebsocketMessagingService.class), distributedDataProvider, taskScheduler());
        secondNode = new CourseOperationProgressService(cacheManager, mock(WebsocketMessagingService.class), distributedDataProvider, taskScheduler());
    }

    @Test
    void startOperationRejectsCompetingOperationAcrossNodes() {
        ZonedDateTime resetStartedAt = STARTED_AT;
        firstNode.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, resetStartedAt);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> secondNode.startOperation(COURSE_ID, CourseOperationType.DELETE, "Deleting exercises", 14, resetStartedAt.plusSeconds(1)))
                .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(firstNode.getOperationProgress(COURSE_ID)).get().extracting(CourseOperationProgressDTO::operationType).isEqualTo(CourseOperationType.RESET);
    }

    @Test
    void startOperationRejectsSecondResetAcrossNodes() {
        ZonedDateTime resetStartedAt = STARTED_AT;
        firstNode.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, resetStartedAt);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> secondNode.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, resetStartedAt));
    }

    @Test
    void completeOperationReleasesClaimForNextOperation() {
        ZonedDateTime resetStartedAt = STARTED_AT;
        CourseOperationClaim resetClaim = firstNode.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, resetStartedAt);
        firstNode.completeOperation(resetClaim, 11, 0);

        ZonedDateTime archiveStartedAt = resetStartedAt.plusSeconds(1);
        secondNode.startOperation(COURSE_ID, CourseOperationType.ARCHIVE, "Creating directories", 4, archiveStartedAt);

        assertThat(secondNode.getOperationProgress(COURSE_ID)).get().extracting(CourseOperationProgressDTO::operationType).isEqualTo(CourseOperationType.ARCHIVE);
    }

    @Test
    void failOperationReleasesClaimForRetry() {
        ZonedDateTime resetStartedAt = STARTED_AT;
        CourseOperationClaim resetClaim = firstNode.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, resetStartedAt);
        firstNode.failOperation(resetClaim, "Reset failed", 1, 11, 1, "failure", 10.0);

        ZonedDateTime retryStartedAt = resetStartedAt.plusSeconds(1);
        secondNode.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, retryStartedAt);

        assertThat(secondNode.getOperationProgress(COURSE_ID)).get().extracting(CourseOperationProgressDTO::startedAt).isEqualTo(retryStartedAt);
    }

    @Test
    void staleOperationCannotOverwriteNewOwnerProgress() {
        ZonedDateTime resetStartedAt = STARTED_AT;
        CourseOperationClaim resetClaim = firstNode.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, resetStartedAt);
        firstNode.completeOperation(resetClaim, 11, 0);

        ZonedDateTime deleteStartedAt = resetStartedAt.plusSeconds(1);
        secondNode.startOperation(COURSE_ID, CourseOperationType.DELETE, "Deleting exercises", 14, deleteStartedAt);
        assertThatThrownBy(() -> firstNode.updateProgress(resetClaim, "Deleting posts", 5, 11, 50.0)).isInstanceOf(IllegalStateException.class);

        assertThat(secondNode.getOperationProgress(COURSE_ID)).get().extracting(CourseOperationProgressDTO::operationType, CourseOperationProgressDTO::startedAt)
                .containsExactly(CourseOperationType.DELETE, deleteStartedAt);
    }

    @Test
    void activeOperationRenewsClaimAndReleaseStopsRenewal() {
        DistributedDataProvider distributedDataProvider = mock(DistributedDataProvider.class);
        @SuppressWarnings("unchecked")
        DistributedMap<Long, String> operationClaims = mock(DistributedMap.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Void> renewal = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> renewalTask = ArgumentCaptor.forClass(Runnable.class);

        when(distributedDataProvider.<Long, String>getExpiringMap(any(String.class), any(Duration.class))).thenReturn(operationClaims);
        when(taskScheduler.scheduleAtFixedRate(renewalTask.capture(), any(Instant.class), any(Duration.class))).thenAnswer(_ -> renewal);

        var service = new CourseOperationProgressService(cacheManager, mock(WebsocketMessagingService.class), distributedDataProvider, taskScheduler);
        ZonedDateTime startedAt = STARTED_AT;
        CourseOperationClaim operationClaim = service.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, startedAt);
        String claimValue = operationClaim.ownerToken().toString();
        when(operationClaims.get(COURSE_ID)).thenReturn(claimValue);
        renewalTask.getValue().run();

        verify(operationClaims).put(COURSE_ID, claimValue);

        service.releaseOperationClaim(operationClaim);
        verify(renewal).cancel(false);
    }

    @Test
    void completionStopsRenewalWhenDistributedLockFails() {
        DistributedDataProvider distributedDataProvider = mock(DistributedDataProvider.class);
        @SuppressWarnings("unchecked")
        DistributedMap<Long, String> operationClaims = mock(DistributedMap.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Void> renewal = mock(ScheduledFuture.class);

        when(distributedDataProvider.<Long, String>getExpiringMap(any(String.class), any(Duration.class))).thenReturn(operationClaims);
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class))).thenAnswer(_ -> renewal);

        var service = new CourseOperationProgressService(cacheManager, mock(WebsocketMessagingService.class), distributedDataProvider, taskScheduler);
        ZonedDateTime startedAt = STARTED_AT;
        CourseOperationClaim operationClaim = service.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, startedAt);
        doThrow(new IllegalStateException("provider unavailable")).when(operationClaims).lock(COURSE_ID);

        assertThatThrownBy(() -> service.completeOperation(operationClaim, 11, 0)).isInstanceOf(IllegalStateException.class);
        verify(renewal).cancel(false);
    }

    @Test
    void releaseOperationClaimStopsRenewalWhenDistributedLockFails() {
        DistributedDataProvider distributedDataProvider = mock(DistributedDataProvider.class);
        @SuppressWarnings("unchecked")
        DistributedMap<Long, String> operationClaims = mock(DistributedMap.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Void> renewal = mock(ScheduledFuture.class);

        when(distributedDataProvider.<Long, String>getExpiringMap(any(String.class), any(Duration.class))).thenReturn(operationClaims);
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class))).thenAnswer(_ -> renewal);

        var service = new CourseOperationProgressService(cacheManager, mock(WebsocketMessagingService.class), distributedDataProvider, taskScheduler);
        CourseOperationClaim operationClaim = service.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, STARTED_AT);
        doThrow(new IllegalStateException("provider unavailable")).when(operationClaims).lock(COURSE_ID);

        service.releaseOperationClaim(operationClaim);

        verify(renewal).cancel(false);
    }

    @Test
    void delayedReleaseWithIdenticalMetadataCannotReleaseNewOwner() {
        CourseOperationClaim staleClaim = firstNode.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, STARTED_AT);
        firstNode.releaseOperationClaim(staleClaim);

        CourseOperationClaim currentClaim = secondNode.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, STARTED_AT);
        firstNode.releaseOperationClaim(staleClaim);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> firstNode.startOperation(COURSE_ID, CourseOperationType.ARCHIVE, "Creating directories", 4, STARTED_AT.plusSeconds(2)));
        assertThat(secondNode.getOperationProgress(COURSE_ID)).get().extracting(CourseOperationProgressDTO::operationType, CourseOperationProgressDTO::startedAt)
                .containsExactly(currentClaim.operationType(), currentClaim.startedAt());
    }

    @Test
    void startOperationRollsBackClaimWhenInitialPublicationFails() {
        DistributedDataProvider distributedDataProvider = new LocalDataProviderService();
        WebsocketMessagingService websocketMessagingService = mock(WebsocketMessagingService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Void> renewal = mock(ScheduledFuture.class);
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class))).thenAnswer(_ -> renewal);
        doThrow(new AssertionError("publication failed")).when(websocketMessagingService).sendMessage(any(String.class), any(Object.class));
        var service = new CourseOperationProgressService(cacheManager, websocketMessagingService, distributedDataProvider, taskScheduler);

        assertThatThrownBy(() -> service.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, STARTED_AT)).isInstanceOf(AssertionError.class);

        secondNode = new CourseOperationProgressService(cacheManager, mock(WebsocketMessagingService.class), distributedDataProvider, taskScheduler());
        secondNode.startOperation(COURSE_ID, CourseOperationType.DELETE, "Deleting exercises", 14, STARTED_AT.plusSeconds(1));
        verify(renewal).cancel(false);
    }

    @Test
    void startOperationRollsBackClaimWhenUnlockFails() {
        DistributedDataProvider distributedDataProvider = mock(DistributedDataProvider.class);
        @SuppressWarnings("unchecked")
        DistributedMap<Long, String> operationClaims = mock(DistributedMap.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Void> renewal = mock(ScheduledFuture.class);
        ArgumentCaptor<String> claimValue = ArgumentCaptor.forClass(String.class);

        when(distributedDataProvider.<Long, String>getExpiringMap(any(String.class), any(Duration.class))).thenReturn(operationClaims);
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class))).thenAnswer(_ -> renewal);
        doThrow(new IllegalStateException("provider unavailable")).when(operationClaims).unlock(COURSE_ID);
        var service = new CourseOperationProgressService(cacheManager, mock(WebsocketMessagingService.class), distributedDataProvider, taskScheduler);

        assertThatThrownBy(() -> service.startOperation(COURSE_ID, CourseOperationType.RESET, "Resetting exercises", 11, STARTED_AT)).isInstanceOf(IllegalStateException.class);

        verify(operationClaims).putIfAbsent(eq(COURSE_ID), claimValue.capture());
        verify(operationClaims).remove(COURSE_ID, claimValue.getValue());
        verify(renewal).cancel(false);
    }

    private TaskScheduler taskScheduler() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class))).thenAnswer(_ -> mock(ScheduledFuture.class));
        return taskScheduler;
    }
}
