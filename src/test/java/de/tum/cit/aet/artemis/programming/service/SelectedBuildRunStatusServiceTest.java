package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

@ExtendWith(MockitoExtension.class)
class SelectedBuildRunStatusServiceTest {

    @Mock
    private DistributedDataProvider distributedDataProvider;

    @Mock
    private DistributedMap<String, Long> activeRuns;

    private SelectedBuildRunStatusService service;

    @BeforeEach
    void setUp() {
        when(distributedDataProvider.<String, Long>getExpiringMap(anyString(), any(Duration.class))).thenReturn(activeRuns);
        service = new SelectedBuildRunStatusService(distributedDataProvider);
    }

    @Test
    void markStarted_storesTheRunWithItsExercise() {
        service.markStarted("run-1", 7L);

        verify(activeRuns).put("run-1", 7L);
    }

    @Test
    void markEnded_removesTheRun() {
        service.markEnded("run-1");

        verify(activeRuns).remove("run-1");
    }

    @Test
    void isActive_isTrueOnlyForARunOfTheAskedExercise() {
        when(activeRuns.get("run-1")).thenReturn(7L);

        assertThat(service.isActive("run-1", 7L)).isTrue();
        assertThat(service.isActive("run-1", 8L)).as("a run of another exercise").isFalse();
    }

    @Test
    void isActive_isFalseForARunThatIsNotStored() {
        // Ended, never started and expired runs look alike, and none of them may keep a client waiting.
        when(activeRuns.get("run-1")).thenReturn(null);

        assertThat(service.isActive("run-1", 7L)).isFalse();
    }

    @Test
    void aStoreThatCannotBeWritten_doesNotFailTheRunOrItsEnd() {
        doThrow(new IllegalStateException("the distributed store is unreachable")).when(activeRuns).put("run-1", 7L);
        doThrow(new IllegalStateException("the distributed store is unreachable")).when(activeRuns).remove("run-1");

        assertThatCode(() -> service.markStarted("run-1", 7L)).doesNotThrowAnyException();
        assertThatCode(() -> service.markEnded("run-1")).doesNotThrowAnyException();
    }
}
