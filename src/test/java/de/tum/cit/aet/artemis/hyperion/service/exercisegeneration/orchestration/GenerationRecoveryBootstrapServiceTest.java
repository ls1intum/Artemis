package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;
import de.tum.cit.aet.artemis.hyperion.test_repository.AuthoringRunTestRepository;

class GenerationRecoveryBootstrapServiceTest {

    private final AuthoringRunTestRepository runs = mock(AuthoringRunTestRepository.class);

    @Test
    void freshClusterRefusesWritersUntilDurableRestoresAreLoaded() {
        var data = new LocalDataProviderService();
        var writer = new GenerationExternalMutationService(data, 1);
        assertThatThrownBy(() -> writer.claimExternalMutationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
        assertThatThrownBy(() -> writer.claimParticipationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
        new GenerationRecoveryBootstrapService(runs, data).initialize();
        String token = writer.claimExternalMutationSlot(42);
        writer.clearExternalMutationSlot(42, token);
    }

    @Test
    void reconstructsPendingRestoreInAFreshClusterAndKeepsItGuardedWithGenerationDisabled() {
        when(runs.findPendingRestoreExercises(eq(0L), any())).thenReturn(List.of(42L));
        when(runs.existsByExerciseIdAndRestoreStartedAtIsNotNullAndRevertedAtIsNull(42)).thenReturn(true);
        for (int restart = 0; restart < 2; restart++) {
            var data = new LocalDataProviderService();
            new GenerationRecoveryBootstrapService(runs, data).initialize();
            var writer = new GenerationExternalMutationService(data, 1);
            var slot = writer.getWedgedSlotInfo(42).orElseThrow();
            assertThat(slot.kind()).isEqualTo(GenerationJobService.WedgedSlotKind.REVERT_RECOVERY);
            assertThatThrownBy(() -> writer.claimExternalMutationSlot(42)).isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> writer.claimParticipationSlot(42)).isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void failedDatabaseReadNeverOpensWriterBarrier() {
        var data = new LocalDataProviderService();
        when(runs.findPendingRestoreExercises(eq(0L), any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> new GenerationRecoveryBootstrapService(runs, data).initialize()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> GenerationRecoveryBootstrapService.requireInitialized(data)).isInstanceOf(ServiceUnavailableAlertException.class);
    }

    @Test
    void stalePageCannotResurrectACompletedRestore() {
        var data = new LocalDataProviderService();
        when(runs.findPendingRestoreExercises(eq(0L), any())).thenReturn(List.of(42L));
        new GenerationRecoveryBootstrapService(runs, data).initialize();
        assertThat(data.getMap(GenerationJobService.JOB_MAP_NAME).get("42")).isNull();
        verify(runs).existsByExerciseIdAndRestoreStartedAtIsNotNullAndRevertedAtIsNull(42);
        verify(runs).findPendingRestoreExercises(eq(42L), any());
    }

    @Test
    void joiningCoreNeverReplacesAnExistingWriter() {
        var data = new LocalDataProviderService();
        DistributedMap<String, JobInfo> jobs = data.getMap(GenerationJobService.JOB_MAP_NAME);
        var original = new JobInfo("active", "owner", 42, Instant.now(), null, "core", Instant.now(), false, null);
        jobs.put("42", original);
        when(runs.findPendingRestoreExercises(eq(0L), any())).thenReturn(List.of(42L));
        new GenerationRecoveryBootstrapService(runs, data).initialize();
        assertThat(jobs.get("42")).isEqualTo(original);
    }

    @Test
    void failedDurableReconciliationRestoresExactGuardBeforeReleasingItsLock() {
        var data = new LocalDataProviderService();
        when(runs.findPendingRestoreExercises(eq(0L), any())).thenReturn(List.of(42L));
        when(runs.existsByExerciseIdAndRestoreStartedAtIsNotNullAndRevertedAtIsNull(42)).thenReturn(true);
        var bootstrap = new GenerationRecoveryBootstrapService(runs, data);
        bootstrap.initialize();
        var writer = new GenerationExternalMutationService(data, 1);
        var token = writer.getWedgedSlotInfo(42).orElseThrow().token();
        when(runs.reconcileRestore(42)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> bootstrap.reconcile(42, token, () -> writer.recoverWedgedSlot(42, token))).isInstanceOf(IllegalStateException.class);
        assertThat(writer.getWedgedSlotInfo(42).orElseThrow().token()).isEqualTo(token);
        org.mockito.Mockito.doReturn(1).when(runs).reconcileRestore(42);
        assertThat(bootstrap.reconcile(42, token, () -> writer.recoverWedgedSlot(42, token))).isTrue();
        assertThat(writer.getWedgedSlotInfo(42)).isEmpty();
    }

    @Test
    void staleOperatorTokenCannotClearADurableObligation() {
        var data = new LocalDataProviderService();
        var bootstrap = new GenerationRecoveryBootstrapService(runs, data);
        bootstrap.initialize();
        DistributedMap<String, JobInfo> jobs = data.getMap(GenerationJobService.JOB_MAP_NAME);
        var replacement = new JobInfo(GenerationRevertSlots.RECOVERY_PREFIX + "new", "owner", 42, Instant.now(), null, null, Instant.now(), false, null);
        jobs.put("42", replacement);
        assertThat(bootstrap.reconcile(42, "stale", () -> {
            throw new AssertionError("must not release");
        })).isFalse();
        org.mockito.Mockito.verify(runs, org.mockito.Mockito.never()).reconcileRestore(42);
        assertThat(jobs.get("42")).isEqualTo(replacement);
    }
}
