package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseWriteOwnershipService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseWriteOwnershipService.Ownership;

/** Delegation tests; physical database exclusivity is proved by ProgrammingExerciseWriteOwnershipTest. */
class GenerationExternalMutationServiceTest {

    private final ProgrammingExerciseWriteOwnershipService ownership = mock(ProgrammingExerciseWriteOwnershipService.class);

    private final GenerationExternalMutationService service = new GenerationExternalMutationService(ownership);

    @Test
    void ordinaryWritesUseAnExactCallerAllocatedToken() {
        when(ownership.claim(eq(42L), eq("WRITE"), anyString())).thenAnswer(invocation -> invocation.getArgument(2));
        String token = service.claimExternalMutationSlot(42);
        assertThat(token).startsWith(GenerationJobService.EXTERNAL_MUTATION_JOB_PREFIX);
        service.clearExternalMutationSlot(42, token);
        verify(ownership).release(42, token);
    }

    @Test
    void copiesAndSeedCaptureUseShortDatabaseReservations() {
        when(ownership.claimReader(42)).thenReturn("copy-token");
        when(ownership.claim(42, "SEED")).thenReturn("seed-token");
        assertThat(service.claimParticipationSlot(42)).isEqualTo("copy-token");
        service.clearParticipationSlot(42, "copy-token");
        assertThat(service.claimSeedSlot(42)).isEqualTo("seed-token");
        service.clearSeedSlot(42, "seed-token");
        verify(ownership).releaseReader(42, "copy-token");
        verify(ownership).release(42, "seed-token");
    }

    @Test
    void releaseSaveSlotIfOwnedReturnsOnlyTheAtomicDatabaseResult() {
        when(ownership.releaseActiveSave(42, "generation-job")).thenReturn(true, false);
        assertThat(service.releaseSaveSlotIfOwned(42, "generation-job")).isTrue();
        assertThat(service.releaseSaveSlotIfOwned(42, "generation-job")).isFalse();
        verify(ownership, never()).get(42);
        verify(ownership, never()).release(eq(42L), anyString());
    }

    @Test
    void finalSaveUsesTheGenerationJobToken() {
        service.claimSaveSlot(42, "generation-job");
        verify(ownership).claim(42, "SAVE", "generation-job");
        when(ownership.get(42)).thenReturn(Optional.of(slot("generation-job", "SAVE")));
        assertThat(service.owns(42, "generation-job")).isTrue();
        assertThat(service.owns(42, "wrong")).isFalse();
        service.releaseSaveSlot(42, "generation-job");
        verify(ownership).releaseActiveSave(42, "generation-job");
    }

    @ParameterizedTest
    @ValueSource(strings = { "SAVE_RECOVERY", "UNDO_RECOVERY" })
    void finalWorkerCleanupCannotReleaseARecoveryReservation(String purpose) {
        when(ownership.get(42)).thenReturn(Optional.of(slot("generation-job", purpose)));
        assertThat(service.owns(42, "generation-job")).isFalse();
        service.releaseSaveSlot(42, "generation-job");
        verify(ownership).releaseActiveSave(42L, "generation-job");
        verify(ownership, never()).release(eq(42L), anyString());
    }

    @Test
    void partialSaveRetainsItsExactToken() {
        service.retainSaveRecovery(42, "generation-job");
        verify(ownership).retain(42, "generation-job", "SAVE_RECOVERY");
        verify(ownership, never()).release(eq(42L), anyString());
    }

    @Test
    void undoRecoveryRetryTransfersWithoutAnUnreservedInterval() {
        when(ownership.get(42)).thenReturn(Optional.of(slot("original", "UNDO_RECOVERY")));
        when(ownership.replaceRecovery(eq(42L), eq("original"), eq("UNDO_RECOVERY"), anyString(), eq("UNDO"))).thenReturn(true);
        String retry = service.claimRevertSlot(42);
        assertThat(retry).startsWith(GenerationRecoverySlots.RETRY_PREFIX);
        verify(ownership).replaceRecovery(42, "original", "UNDO_RECOVERY", retry, "UNDO");
        verify(ownership, never()).claim(eq(42L), anyString(), anyString());
        verify(ownership, never()).release(eq(42L), anyString());
        service.retainRevertRecoverySlot(42, retry);
        verify(ownership).retain(42, retry, "UNDO_RECOVERY");
    }

    @Test
    void changedUndoRecoveryReservationRejectsTheRetryWithoutRelease() {
        when(ownership.get(42)).thenReturn(Optional.of(slot("original", "UNDO_RECOVERY")));
        assertThatThrownBy(() -> service.claimRevertSlot(42)).isInstanceOf(ConflictException.class);
        verify(ownership, never()).release(eq(42L), anyString());
        verify(ownership, never()).claim(eq(42L), anyString(), anyString());
    }

    @Test
    void activeStatusAndRecoveryReadOnlyAuthoritativeDatabaseOwnership() {
        when(ownership.get(42)).thenReturn(Optional.empty());
        assertThat(service.isGenerationActive(42)).isFalse();
        assertThat(service.getWedgedSlotInfo(42)).isEmpty();
        when(ownership.get(42)).thenReturn(Optional.of(slot("retained", "SAVE_RECOVERY")));
        assertThat(service.isGenerationActive(42)).isTrue();
        assertThat(service.getWedgedSlotInfo(42)).hasValueSatisfying(info -> {
            assertThat(info.token()).isEqualTo("retained");
            assertThat(info.ownerLeftCluster()).isFalse();
        });
        when(ownership.release(42, "retained")).thenReturn(true);
        assertThat(service.recoverWedgedSlot(42, "retained")).isTrue();
        verify(ownership).release(42, "retained");
    }

    @Test
    void exactCopyReaderRemainsVisibleAndRecoverableWithoutAWriter() {
        when(ownership.get(42)).thenReturn(Optional.empty());
        when(ownership.readers(42)).thenReturn(java.util.List.of(slot("reader-token", "COPY")));
        when(ownership.releaseReader(42, "reader-token")).thenReturn(true);

        assertThat(service.isGenerationActive(42)).isTrue();
        assertThat(service.getWedgedSlotInfo(42)).hasValueSatisfying(info -> assertThat(info.token()).isEqualTo("reader-token"));
        assertThat(service.recoverWedgedSlot(42, "reader-token")).isTrue();
        verify(ownership).releaseReader(42, "reader-token");
    }

    private static Ownership slot(String token, String purpose) {
        return new Ownership(token, "process", purpose, Instant.now(), null);
    }
}
