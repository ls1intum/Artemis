package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseWriteOwnershipRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseWriteOwnershipRepository.OwnershipProjection;

class ProgrammingExerciseWriteOwnershipServiceTest {

    @Test
    void lostAcquisitionResponseAcceptsOnlyTheMatchingCommittedReservation() {
        var repository = mock(ProgrammingExerciseWriteOwnershipRepository.class);
        var service = new ProgrammingExerciseWriteOwnershipService(repository);
        var owner = new AtomicReference<String>();
        doAnswer(invocation -> {
            owner.set(invocation.getArgument(2));
            throw new IllegalStateException("response lost after commit");
        }).when(repository).claim(anyLong(), anyString(), anyString(), anyString(), any());
        when(repository.findOwnership(42)).thenAnswer(_ -> Optional.of(projection("job", owner.get(), "SAVE")));
        assertThat(service.claim(42, "SAVE", "job")).isEqualTo("job");
    }

    @ParameterizedTest
    @ValueSource(strings = { "token", "owner", "purpose", "missing", "unavailable" })
    void ambiguousClaimCannotAcceptAnotherOperationOrUnavailableReadback(String mismatch) {
        var repository = mock(ProgrammingExerciseWriteOwnershipRepository.class);
        var service = new ProgrammingExerciseWriteOwnershipService(repository);
        var owner = new AtomicReference<String>();
        doAnswer(invocation -> {
            owner.set(invocation.getArgument(2));
            throw new IllegalStateException("claim response lost");
        }).when(repository).claim(anyLong(), anyString(), anyString(), anyString(), any());
        when(repository.findOwnership(42)).thenAnswer(_ -> {
            if (mismatch.equals("unavailable")) {
                throw new IllegalStateException("database unavailable");
            }
            if (mismatch.equals("missing")) {
                return Optional.empty();
            }
            return Optional.of(projection(mismatch.equals("token") ? "different" : "job", mismatch.equals("owner") ? "old-process" : owner.get(),
                    mismatch.equals("purpose") ? "SAVE_RECOVERY" : "SAVE"));
        });
        assertThatThrownBy(() -> service.claim(42, "SAVE", "job")).isInstanceOf(ServiceUnavailableAlertException.class);
    }

    @Test
    void rejectsUnboundedOwnershipFieldsBeforeDatabaseAccess() {
        var repository = mock(ProgrammingExerciseWriteOwnershipRepository.class);
        var service = new ProgrammingExerciseWriteOwnershipService(repository);
        assertThatThrownBy(() -> service.claim(42, "SAVE", "x".repeat(65))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.claim(42, "x".repeat(33), "job")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.claim(42, "SAVE", " ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = { "JOB", "job ", " job", "job\t", "job_1", "job:1", "jób" })
    void rejectsNonCanonicalTokensBeforeAnyDatabaseMutation(String token) {
        var repository = mock(ProgrammingExerciseWriteOwnershipRepository.class);
        var service = new ProgrammingExerciseWriteOwnershipService(repository);
        assertThatThrownBy(() -> service.claim(42, "SAVE", token)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.release(42, token)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.releaseActiveSave(42, token)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.releaseReader(42, token)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.retain(42, token, "SAVE_RECOVERY")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.authorizeTestsBuild(42, token, "a".repeat(40))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stopTestsBuildAuthorization(42, token)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.executeAuthorizedTestsBuild(42, token, "a".repeat(40), () -> true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replaceRecovery(42, token, "UNDO_RECOVERY", "retry", "UNDO")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replaceRecovery(42, "current", "UNDO_RECOVERY", token, "UNDO")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void lostReaderClaimResponseRequiresMatchingTokenAndProcessReadback() {
        var repository = mock(ProgrammingExerciseWriteOwnershipRepository.class);
        var service = new ProgrammingExerciseWriteOwnershipService(repository);
        var token = new AtomicReference<String>();
        var owner = new AtomicReference<String>();
        doAnswer(invocation -> {
            token.set(invocation.getArgument(1));
            owner.set(invocation.getArgument(2));
            throw new IllegalStateException("reader response lost after commit");
        }).when(repository).claimReader(anyLong(), anyString(), anyString(), any());
        when(repository.findReaders(42)).thenAnswer(_ -> List.of(projection(token.get(), owner.get(), "COPY")));
        assertThat(service.claimReader(42)).isEqualTo(token.get());
    }

    @ParameterizedTest
    @ValueSource(strings = { "token", "owner", "missing", "unavailable" })
    void ambiguousReaderClaimNeverUsesAnotherReaderOrAnUnavailableView(String mismatch) {
        var repository = mock(ProgrammingExerciseWriteOwnershipRepository.class);
        var service = new ProgrammingExerciseWriteOwnershipService(repository);
        var token = new AtomicReference<String>();
        var owner = new AtomicReference<String>();
        doAnswer(invocation -> {
            token.set(invocation.getArgument(1));
            owner.set(invocation.getArgument(2));
            throw new IllegalStateException("reader claim response lost");
        }).when(repository).claimReader(anyLong(), anyString(), anyString(), any());
        when(repository.findReaders(42)).thenAnswer(_ -> {
            if (mismatch.equals("unavailable")) {
                throw new IllegalStateException("database unavailable");
            }
            if (mismatch.equals("missing")) {
                return List.of();
            }
            return List.of(projection(mismatch.equals("token") ? "other-reader" : token.get(), mismatch.equals("owner") ? "old-process" : owner.get(), "COPY"));
        });
        assertThatThrownBy(() -> service.claimReader(42)).isInstanceOf(ServiceUnavailableAlertException.class);
    }

    private static OwnershipProjection projection(String token, String owner, String purpose) {
        var row = mock(OwnershipProjection.class);
        when(row.getToken()).thenReturn(token);
        when(row.getOwner()).thenReturn(owner);
        when(row.getPurpose()).thenReturn(purpose);
        when(row.getStartedAt()).thenReturn(Instant.now());
        return row;
    }
}
