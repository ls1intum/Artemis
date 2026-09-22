package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.util.ExamExerciseStartPreparationStatus;

class ExamExercisePreparationStatusServiceTest {

    private final WebsocketMessagingService websocket = mock();

    private final ExamExercisePreparationStatusService service = new ExamExercisePreparationStatusService(new ConcurrentMapCacheManager(), websocket);

    @Test
    void lateUpdatesCannotMoveRetainedOrPublishedProgressBackwards() {
        ZonedDateTime started = ZonedDateTime.now();
        ReentrantLock lock = new ReentrantLock();
        service.update(1L, 5, 2, 10, 15, started, lock);
        service.update(1L, 3, 1, 8, 9, started, lock);

        var expected = new ExamExerciseStartPreparationStatus(5, 2, 10, 15, started);
        assertThat(service.get(1L)).contains(expected);
        verify(websocket, org.mockito.Mockito.times(2)).sendMessage("/topic/exams/1/exercise-start-status", expected);
        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    void invalidationStartsANewBatchWithoutInheritingItsPredecessorsCounters() {
        ZonedDateTime started = ZonedDateTime.now();
        service.update(1L, 5, 2, 10, 15, started, new ReentrantLock());
        service.invalidate(1L);
        assertThat(service.get(1L)).isEmpty();

        service.update(1L, 0, 0, 4, 0, started.plusMinutes(1), new ReentrantLock());

        assertThat(service.get(1L)).contains(new ExamExerciseStartPreparationStatus(0, 0, 4, 0, started.plusMinutes(1)));
    }

    @Test
    void failedPublicationStillRetainsProgressAndReleasesTheBatchLock() {
        ZonedDateTime started = ZonedDateTime.now();
        var expected = new ExamExerciseStartPreparationStatus(1, 0, 1, 1, started);
        doThrow(new IllegalStateException("disconnected")).when(websocket).sendMessage(eq("/topic/exams/1/exercise-start-status"), eq(expected));
        ReentrantLock lock = new ReentrantLock();

        service.update(1L, 1, 0, 1, 1, started, lock);

        assertThat(service.get(1L)).contains(expected);
        assertThat(lock.isLocked()).isFalse();
    }
}
