package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

class GenerationLiveSaveBoundaryTest {

    private final LocalDataProviderService provider = spy(new LocalDataProviderService());

    private DistributedMap<String, GenerationLiveSaveService.LiveSaveSlot> slots;

    private GenerationLiveSaveService service;

    @BeforeEach
    void setup() {
        slots = spy(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationLiveSaveService.MAP_NAME);
        service = new GenerationLiveSaveService(provider, true);
    }

    @Test
    void wrongOrNestedTokensCannotReleaseOrRetainAnotherOperation() {
        assertThatThrownBy(() -> service.release(7L, "unknown")).isInstanceOf(IllegalStateException.class).hasMessageContaining("owning operation");
        assertThatThrownBy(() -> service.retain(7L, "unknown")).isInstanceOf(IllegalStateException.class).hasMessageContaining("unowned or nested");
        String token = service.claim(7L);
        assertThatThrownBy(() -> service.release(7L, "wrong")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.retain(7L, "wrong")).isInstanceOf(IllegalStateException.class);
        assertThat(service.claim(7L)).isEqualTo(token);
        assertThatThrownBy(() -> service.retain(7L, token)).isInstanceOf(IllegalStateException.class).hasMessageContaining("nested");
        service.release(7L, token);
        assertThat(service.getSlotInfo(7L)).isPresent();
        service.release(7L, token);
        assertThat(service.getSlotInfo(7L)).isEmpty();
    }

    @Test
    void lostOrReplacedStoreTokensCannotVerifyOrAcknowledgeRelease() {
        String token = service.claim(7L);
        var original = slots.get("7");
        slots.remove("7");
        assertThatThrownBy(() -> service.verify(7L, token)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.expectTestCaseSynchronization(7L, "solution", "tests")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.release(7L, token)).isInstanceOf(IllegalStateException.class).hasMessageContaining("lost its reservation");
        slots.put("7", new GenerationLiveSaveService.LiveSaveSlot("other", original.ownerNodeId(), original.startedAt()));
        assertThatThrownBy(() -> service.verify(7L, token)).isInstanceOf(ConflictException.class);
        assertThat(service.recoverSlot(7L, token)).isFalse();
        assertThat(service.recoverSlot(7L, "other")).isTrue();
        assertThat(service.recoverSlot(7L, "other")).isFalse();
    }

    @Test
    void activeSynchronizationPreventsFurtherWritesAndReleaseUntilReconciled() {
        String token = service.claim(7L);
        var slot = slots.get("7");
        slots.put("7", new GenerationLiveSaveService.LiveSaveSlot(slot.token(), slot.ownerNodeId(), slot.startedAt(), Map.of(),
                new GenerationLiveSaveService.TestCaseSynchronization("solution", "tests", true, false)));
        assertThatThrownBy(() -> service.verify(7L, token)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.expectTestCaseSynchronization(7L, "new-solution", "tests")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot replace an active test synchronization");
        assertThatThrownBy(() -> service.release(7L, token)).isInstanceOf(IllegalStateException.class).hasMessageContaining("still running");
        assertThat(service.getSlotInfo(7L)).isPresent();
        assertThat(service.recoverSlot(7L, token)).isTrue();
    }

    @Test
    void readersAndWriterReleaseRetryAfterConcurrentMembershipChanges() {
        String first = service.claimReader(7L);
        AtomicBoolean retry = new AtomicBoolean(true);
        doAnswer(invocation -> retry.getAndSet(false) ? false : invocation.callRealMethod()).when(slots).replace(eq("7"), any(), any());
        String second = service.claimReader(7L);
        assertThat(service.getSlotInfo(7L).orElseThrow().readers()).containsOnlyKeys(first, second);
        retry.set(true);
        service.releaseReader(7L, first);
        assertThat(service.getSlotInfo(7L).orElseThrow().readers()).containsOnlyKeys(second);
        service.releaseReader(7L, "unowned");
        assertThat(service.getSlotInfo(7L).orElseThrow().readers()).containsOnlyKeys(second);
        AtomicBoolean removeRetry = new AtomicBoolean(true);
        doAnswer(invocation -> removeRetry.getAndSet(false) ? false : invocation.callRealMethod()).when(slots).remove(eq("7"), any());
        service.releaseReader(7L, second);
        assertThat(service.getSlotInfo(7L)).isEmpty();
        assertThatCode(() -> service.releaseReader(7L, second)).doesNotThrowAnyException();
        String writer = service.claim(7L);
        removeRetry.set(true);
        service.release(7L, writer);
        assertThat(service.getSlotInfo(7L)).isEmpty();
    }

    @Test
    void synchronizationRequiresOwnedPermitAndReportsOnlyMatchingSuccessfulCompletion() {
        assertThatThrownBy(() -> service.expectTestCaseSynchronization(7L, "solution", "tests")).isInstanceOf(IllegalStateException.class).hasMessageContaining("owned save");
        assertThat(service.testCaseSynchronizationCompleted(7L, "solution", "tests")).isFalse();
        String token = service.claim(7L);
        assertThat(service.testCaseSynchronizationCompleted(7L, "solution", "tests")).isFalse();
        service.expectTestCaseSynchronization(7L, "solution", "tests");
        assertThat(service.synchronizeTestCases(7L, "solution", "tests", true, () -> true)).isTrue();
        assertThat(service.testCaseSynchronizationCompleted(7L, "solution", "tests")).isTrue();
        assertThat(service.testCaseSynchronizationCompleted(7L, "other", "tests")).isFalse();
        assertThat(service.testCaseSynchronizationCompleted(7L, "solution", "other")).isFalse();
        service.release(7L, token);
    }
}
