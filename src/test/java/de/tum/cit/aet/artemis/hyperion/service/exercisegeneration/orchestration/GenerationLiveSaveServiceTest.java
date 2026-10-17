package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

class GenerationLiveSaveServiceTest {

    @Test
    void disabledCoordinationDoesNotChangeOrdinaryOperations() {
        var provider = mock(DistributedDataProvider.class);
        DistributedMap<String, GenerationLiveSaveService.LiveSaveSlot> map = mock();
        when(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME)).thenReturn(map);
        var service = new GenerationLiveSaveService(provider, false);

        String write = service.claim(1L);
        String read = service.claimReader(1L);
        service.verify(1L, write);
        service.releaseReader(1L, read);
        service.retain(1L, write);
        service.release(1L, write);

        assertThat(service.getSlotInfo(1L)).isEmpty();
        assertThat(service.recoverSlot(1L, "token")).isFalse();
        verifyNoInteractions(map);
    }

    @Test
    void readerMembershipChangesInvalidateTheOperatorRecoveryToken() {
        var service = new GenerationLiveSaveService(new LocalDataProviderService(), true);
        String first = service.claimReader(1L);
        String firstGroup = service.getSlotInfo(1L).orElseThrow().token();
        String second = service.claimReader(1L);
        String secondGroup = service.getSlotInfo(1L).orElseThrow().token();
        assertThat(secondGroup).isNotEqualTo(firstGroup);
        assertThat(service.recoverSlot(1L, firstGroup)).isFalse();
        assertThatThrownBy(() -> service.claim(1L)).isInstanceOf(ConflictException.class);

        service.releaseReader(1L, first);
        assertThat(service.recoverSlot(1L, secondGroup)).isFalse();
        assertThat(service.getSlotInfo(1L).orElseThrow().readers()).containsOnlyKeys(second);
        service.releaseReader(1L, second);
        String write = service.claim(1L);
        service.release(1L, write);
        assertThat(service.getSlotInfo(1L)).isEmpty();
    }

    @Test
    void aRetainedSaveCannotBeBorrowedByTheNextRequestOnTheSameThread() {
        var service = new GenerationLiveSaveService(new LocalDataProviderService(), true);
        String token = service.claim(1L);
        service.retain(1L, token);

        assertThatThrownBy(() -> service.claim(1L)).isInstanceOf(ConflictException.class);
        assertThat(service.recoverSlot(1L, "wrong-token")).isFalse();
        assertThat(service.recoverSlot(1L, token)).isTrue();
        String replacement = service.claim(1L);
        service.release(1L, replacement);
    }

    @Test
    void synchronousSnapshotBorrowsItsCallingWritersReservation() {
        var service = new GenerationLiveSaveService(new LocalDataProviderService(), true);
        String write = service.claim(1L);
        String read = service.claimReader(1L);
        assertThat(read).isEqualTo(write);
        service.releaseReader(1L, read);
        service.verify(1L, write);
        service.release(1L, write);
        assertThat(service.getSlotInfo(1L)).isEmpty();
    }
}
