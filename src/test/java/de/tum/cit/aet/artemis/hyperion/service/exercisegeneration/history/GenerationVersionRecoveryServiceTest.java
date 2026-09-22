package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.test_repository.AuthoringRunTestRepository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class GenerationVersionRecoveryServiceTest {

    private final AuthoringRunTestRepository runs = mock(AuthoringRunTestRepository.class);

    private final ExerciseVersionTestRepository versions = mock(ExerciseVersionTestRepository.class);

    private final AuthoringRun run = mock(AuthoringRun.class);

    private final GenerationVersionRecoveryService recovery = new GenerationVersionRecoveryService(runs, versions);

    @BeforeEach
    void setup() throws Exception {
        when(runs.findLatestMutation(eq(12L), any())).thenReturn(List.of(run));
        when(run.getJobId()).thenReturn("job");
        when(run.getExerciseId()).thenReturn(12L);
        when(run.getBeforeVersionId()).thenReturn(81L);
        when(run.getAfterVersionId()).thenReturn(82L);
        when(run.getRepositoryBranch()).thenReturn("teaching");
        when(run.getStatus()).thenReturn(AuthoringRun.Status.SAVED);
        when(run.getKind()).thenReturn(AuthoringRun.Kind.ADAPT);
        when(versions.findById(81L)).thenReturn(Optional.of(version("before")));
        when(versions.findById(82L)).thenReturn(Optional.of(version("after")));
    }

    private ExerciseVersion version(String prefix) throws Exception {
        var version = new ExerciseVersion();
        version.setExerciseId(12L);
        version.setExerciseSnapshot(JsonObjectMapper.get().readValue("""
                {"id":12,"title":"%s","programmingData":{
                    "templateParticipation":{"id":1,"commitId":"%s-template"},
                    "solutionParticipation":{"id":2,"commitId":"%s-solution"},
                    "testsCommitId":"%s-tests",
                    "testCases":[{"id":1,"testName":"checksStack","weight":2.0,"visibility":"ALWAYS","active":true}]
                }}
                """.formatted(prefix, prefix, prefix, prefix), ExerciseSnapshotDTO.class));
        return version;
    }

    @Test
    void derivesAllRepositoryHeadsAndNamedGradingFromCanonicalVersions() {
        var baseline = recovery.find(12L).orElseThrow().baseline();
        assertThat(baseline.repositoryHeads()).hasSize(3).containsEntry(RepositoryType.TEMPLATE, "before-template");
        assertThat(baseline.expectedCurrentHeads()).hasSize(3).containsEntry(RepositoryType.TESTS, "after-tests");
        assertThat(baseline.title()).isEqualTo("before");
        assertThat(baseline.expectedTitle()).isEqualTo("after");
        assertThat(baseline.repositoryBranch()).isEqualTo("teaching");
        assertThat(baseline.mode()).isEqualTo(GenerationMode.ADAPT);
        assertThat(baseline.previousGrading().tests().get("checksStack").weight()).isEqualTo(2.0);
    }

    @Test
    void survivesAServiceRestartWithoutAnyReplayCache() {
        var restarted = new GenerationVersionRecoveryService(runs, versions);
        assertThat(restarted.find(12L)).isEqualTo(recovery.find(12L));
    }

    @Test
    void doesNotFallBackPastANewerPartialMutation() {
        when(run.getStatus()).thenReturn(AuthoringRun.Status.PARTIAL);
        assertThat(recovery.find(12L)).isEmpty();
        verifyNoInteractions(versions);
    }

    @Test
    void doesNotOfferAnUnfinishedMutation() {
        when(run.getStatus()).thenReturn(AuthoringRun.Status.QUEUED);
        assertThat(recovery.find(12L)).isEmpty();
        verifyNoInteractions(versions);
    }

    @Test
    void missingVersionFailsClosedRatherThanReusingAnOlderRun() {
        when(versions.findById(81L)).thenReturn(Optional.empty());
        assertThat(recovery.find(12L)).isEmpty();
    }

    @Test
    void refusesVersionsFromAnotherExercise() throws Exception {
        var wrong = version("other");
        wrong.setExerciseId(99L);
        when(versions.findById(81L)).thenReturn(Optional.of(wrong));
        assertThat(recovery.find(12L)).isEmpty();
    }

    @Test
    void olderSnapshotsWithoutNamedTestsCannotBePresentedAsCompleteRecovery() throws Exception {
        var old = version("before");
        old.setExerciseSnapshot(JsonObjectMapper.get().readValue("""
                {"id":12,"programmingData":{
                    "templateParticipation":{"id":1,"commitId":"before-template"},
                    "solutionParticipation":{"id":2,"commitId":"before-solution"},
                    "testsCommitId":"before-tests","testCases":[{"id":1,"weight":2.0}]
                }}
                """, ExerciseSnapshotDTO.class));
        when(versions.findById(81L)).thenReturn(Optional.of(old));
        assertThat(recovery.find(12L)).isEmpty();
    }

    @Test
    void consumptionRequiresTheExactVersionAndSurfacesFailure() {
        var pair = recovery.find(12L).orElseThrow();
        assertThatThrownBy(() -> recovery.consumed(pair)).isInstanceOf(IllegalStateException.class);
        when(runs.markReverted(eq("job"), eq(82L), any())).thenReturn(1);
        recovery.consumed(pair);
        verify(runs, org.mockito.Mockito.times(2)).markReverted(eq("job"), eq(82L), any());
    }
}
