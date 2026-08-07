package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.core.config.HazelcastConfiguration;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;

class ProgrammingExerciseMutationGuardTest {

    @Test
    void claimExternalMutation_holdsTheHyperionSlotUntilLeaseCloses() {
        HyperionExerciseMutationApi hyperionApi = mock(HyperionExerciseMutationApi.class);
        when(hyperionApi.claimExternalMutationSlot(42L)).thenReturn("mutation-42");
        HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
        ProgrammingExerciseMutationGuardService guard = new ProgrammingExerciseMutationGuardService(Optional.of(hyperionApi), hazelcastInstance);

        try (ProgrammingExerciseMutationGuardService.MutationLease ignored = guard.claimExternalMutation(42L)) {
            verify(hyperionApi).claimExternalMutationSlot(42L);
        }

        verify(hyperionApi).clearExternalMutationSlot(42L, "mutation-42");
        verifyNoInteractions(hazelcastInstance);
    }

    @Test
    void claimExternalMutation_propagatesConflictFromTheSlotClaimWithoutClearingIt() {
        HyperionExerciseMutationApi hyperionApi = mock(HyperionExerciseMutationApi.class);
        when(hyperionApi.claimExternalMutationSlot(42L))
                .thenThrow(new ConflictException("Exercise 42 is already being mutated.", "programmingExercise", "hyperionMutationSlotConflict"));
        ProgrammingExerciseMutationGuardService guard = new ProgrammingExerciseMutationGuardService(Optional.of(hyperionApi), mock(HazelcastInstance.class));

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> guard.claimExternalMutation(42L));

        verify(hyperionApi, never()).clearExternalMutationSlot(anyLong(), anyString());
    }

    @Test
    void claimExternalMutation_isNoOpWhenGenerationIsDisabledOnAllCurrentMembers() {
        ProgrammingExerciseMutationGuardService guard = disabledGuard(hazelcastWithMembers(dataMember("false"), dataMember("false")), 2);

        assertThatCode(() -> guard.claimExternalMutation(42L).close()).doesNotThrowAnyException();
    }

    @Test
    void claimExternalMutation_rejectsProfileSkewBeforeMutationWhenCurrentMemberIsGenerationCapable() {
        ProgrammingExerciseMutationGuardService guard = disabledGuard(hazelcastWithMembers(dataMember("false"), dataMember("true")), 2);

        assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> guard.claimExternalMutation(42L))
                .satisfies(exception -> assertServiceUnavailable(exception, "hyperionExerciseGenerationProfileSkew")).withMessageContaining("cluster member")
                .withMessageContaining("unavailable on this node");
    }

    @Test
    void claimExternalMutation_rejectsIncompleteDataMemberTopology() {
        ProgrammingExerciseMutationGuardService guard = disabledGuard(hazelcastWithMembers(dataMember("false")), 2);

        assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> guard.claimExternalMutation(42L))
                .satisfies(exception -> assertServiceUnavailable(exception, "hyperionDataMemberTopologyMismatch")).withMessageContaining("expected 2")
                .withMessageContaining("observed 1");
    }

    @Test
    void claimExternalMutation_rejectsMissingCapabilityAttribute() {
        ProgrammingExerciseMutationGuardService guard = disabledGuard(hazelcastWithMembers(dataMember("false"), dataMember(null)), 2);

        assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> guard.claimExternalMutation(42L))
                .satisfies(exception -> assertServiceUnavailable(exception, "hyperionExerciseGenerationCapabilityUnavailable"));
    }

    @Test
    void claimExternalMutation_ignoresLiteMembersWhenProvingAllDataMembersDisabled() {
        ProgrammingExerciseMutationGuardService guard = disabledGuard(hazelcastWithMembers(dataMember("false"), liteMember(null)), 1);

        assertThatCode(() -> guard.claimExternalMutation(42L).close()).doesNotThrowAnyException();
    }

    @Test
    void claimExternalMutation_isNoOpForAnUnrelatedRepository() {
        HyperionExerciseMutationApi hyperionApi = mock(HyperionExerciseMutationApi.class);
        ProgrammingExerciseMutationGuardService guard = new ProgrammingExerciseMutationGuardService(Optional.of(hyperionApi), mock(HazelcastInstance.class));

        assertThatCode(() -> guard.claimExternalMutation(OptionalLong.empty()).close()).doesNotThrowAnyException();
        verifyNoInteractions(hyperionApi);
    }

    private static ProgrammingExerciseMutationGuardService disabledGuard(HazelcastInstance hazelcastInstance, int expectedDataMemberCount) {
        return new ProgrammingExerciseMutationGuardService(Optional.empty(), hazelcastInstance, expectedDataMemberCount);
    }

    private static HazelcastInstance hazelcastWithMembers(Member... members) {
        HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
        Cluster cluster = mock(Cluster.class);
        when(cluster.getMembers()).thenReturn(Set.of(members));
        when(hazelcastInstance.getCluster()).thenReturn(cluster);
        return hazelcastInstance;
    }

    private static Member dataMember(String capability) {
        return member(false, capability);
    }

    private static Member liteMember(String capability) {
        return member(true, capability);
    }

    private static Member member(boolean liteMember, String capability) {
        Member member = mock(Member.class);
        when(member.isLiteMember()).thenReturn(liteMember);
        when(member.getAttribute(HazelcastConfiguration.HYPERION_EXERCISE_GENERATION_CAPABLE_MEMBER_ATTRIBUTE)).thenReturn(capability);
        return member;
    }

    private static void assertServiceUnavailable(ServiceUnavailableAlertException exception, String errorKey) {
        org.assertj.core.api.Assertions.assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        org.assertj.core.api.Assertions.assertThat(exception.getErrorKey()).isEqualTo(errorKey);
    }
}
