package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateRecoveryApi;

/**
 * Unit tests for {@link PyrisRestartWatchService}: boot id observation, restart detection,
 * and the restore-on-failed-reset behavior that keeps the restart signal alive.
 */
class PyrisRestartWatchServiceTest {

    private static final String BOOT_ID_KEY = "bootId";

    private PyrisRestartWatchService service;

    private DistributedMap<String, String> bootIdMap;

    private ProcessingStateRecoveryApi recoveryApi;

    @BeforeEach
    void setUp() {
        DistributedDataProvider distributedDataProvider = mock(DistributedDataProvider.class);
        bootIdMap = mock(StringDistributedMap.class);
        when(distributedDataProvider.<String, String>getMap("pyris-restart-watch")).thenReturn(bootIdMap);
        recoveryApi = mock(ProcessingStateRecoveryApi.class);
        service = new PyrisRestartWatchService(distributedDataProvider, Optional.of(recoveryApi));
    }

    /**
     * Concrete generic binding so Mockito can mock the map with the right type parameters.
     */
    interface StringDistributedMap extends DistributedMap<String, String> {
    }

    @Test
    void shouldIgnoreMissingBootId() {
        service.observeBootId(null);
        service.observeBootId("");
        service.observeBootId("   ");

        verifyNoInteractions(bootIdMap, recoveryApi);
    }

    @Test
    void shouldOnlyStoreTheFirstObservedBootId() {
        when(bootIdMap.get(BOOT_ID_KEY)).thenReturn(null);

        service.observeBootId("boot-1");

        verify(bootIdMap).put(BOOT_ID_KEY, "boot-1");
        verifyNoInteractions(recoveryApi);
    }

    @Test
    void shouldDoNothingWhenBootIdIsUnchanged() {
        when(bootIdMap.get(BOOT_ID_KEY)).thenReturn("boot-1");

        service.observeBootId("boot-1");

        verify(bootIdMap, never()).put(anyString(), anyString());
        verifyNoInteractions(recoveryApi);
    }

    @Test
    void shouldResetInFlightJobsWhenBootIdChanged() {
        when(bootIdMap.get(BOOT_ID_KEY)).thenReturn("boot-1");

        service.observeBootId("boot-2");

        verify(bootIdMap).put(BOOT_ID_KEY, "boot-2");
        verify(recoveryApi).handleIrisReset();
    }

    @Test
    void shouldRestorePreviousBootIdWhenTheResetFails() {
        when(bootIdMap.get(BOOT_ID_KEY)).thenReturn("boot-1");
        when(recoveryApi.handleIrisReset()).thenThrow(new RuntimeException("database unavailable"));

        service.observeBootId("boot-2");

        // The new id is stored first; after the failed reset the previous id is restored,
        // so the next health observation sees the change again and retries the reset.
        var order = inOrder(bootIdMap);
        order.verify(bootIdMap).put(BOOT_ID_KEY, "boot-2");
        order.verify(bootIdMap).put(BOOT_ID_KEY, "boot-1");
    }

    @Test
    void shouldStoreBootIdEvenWithoutRecoveryApi() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        when(provider.<String, String>getMap("pyris-restart-watch")).thenReturn(bootIdMap);
        PyrisRestartWatchService withoutRecovery = new PyrisRestartWatchService(provider, Optional.empty());
        when(bootIdMap.get(BOOT_ID_KEY)).thenReturn("boot-1");

        withoutRecovery.observeBootId("boot-2");

        verify(bootIdMap).put(BOOT_ID_KEY, "boot-2");
    }
}
