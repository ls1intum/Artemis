package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Unit test for {@link ExerciseAdaptationRevertService}'s capture-and-revert invariants against a real isolated embedded Hazelcast instance, with the git and persistence
 * collaborators mocked so the reset-to-captured-SHA behaviour is exercised deterministically without a real repository.
 */
class ExerciseAdaptationRevertServiceTest {

    private static final String DEFAULT_BRANCH = "main";

    private HazelcastInstance hazelcastInstance;

    private GitService gitService;

    private GenerationPersistenceService persistenceService;

    private ExerciseAdaptationRevertService revertService;

    private ProgrammingExercise exercise;

    private User user;

    private LocalVCRepositoryUri templateUri;

    private LocalVCRepositoryUri solutionUri;

    private LocalVCRepositoryUri testsUri;

    private Repository templateRepo;

    private Repository solutionRepo;

    private Repository testsRepo;

    @BeforeEach
    void setUp() throws Exception {
        Config config = new Config();
        config.setClusterName("hyperion-revert-service-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);

        gitService = mock(GitService.class);
        persistenceService = mock(GenerationPersistenceService.class);
        revertService = new ExerciseAdaptationRevertService(hazelcastInstance, gitService, persistenceService, DEFAULT_BRANCH);
        revertService.init();

        templateUri = mock(LocalVCRepositoryUri.class);
        solutionUri = mock(LocalVCRepositoryUri.class);
        testsUri = mock(LocalVCRepositoryUri.class);
        templateRepo = mock(Repository.class);
        solutionRepo = mock(Repository.class);
        testsRepo = mock(Repository.class);

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(77L);
        when(exercise.getRepositoryURI(RepositoryType.TEMPLATE)).thenReturn(templateUri);
        when(exercise.getRepositoryURI(RepositoryType.SOLUTION)).thenReturn(solutionUri);
        when(exercise.getRepositoryURI(RepositoryType.TESTS)).thenReturn(testsUri);

        when(gitService.getLastCommitHash(templateUri)).thenReturn("sha-template");
        when(gitService.getLastCommitHash(solutionUri)).thenReturn("sha-solution");
        when(gitService.getLastCommitHash(testsUri)).thenReturn("sha-tests");
        when(gitService.getOrCheckoutRepository(templateUri, true, DEFAULT_BRANCH, false)).thenReturn(templateRepo);
        when(gitService.getOrCheckoutRepository(solutionUri, true, DEFAULT_BRANCH, false)).thenReturn(solutionRepo);
        when(gitService.getOrCheckoutRepository(testsUri, true, DEFAULT_BRANCH, false)).thenReturn(testsRepo);

        user = new User();
        user.setLogin("instructor");
    }

    @AfterEach
    void tearDown() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    void recordBaseline_recordsEveryRepositoryHead() {
        revertService.recordBaseline(exercise, user, "job-1", preAdaptationHeads());

        Optional<AdaptationBaseline> baseline = revertService.latestBaseline(exercise);
        assertThat(baseline).isPresent();
        assertThat(baseline.get().headFor(RepositoryType.TEMPLATE)).isEqualTo("sha-template");
        assertThat(baseline.get().headFor(RepositoryType.SOLUTION)).isEqualTo("sha-solution");
        assertThat(baseline.get().headFor(RepositoryType.TESTS)).isEqualTo("sha-tests");
        assertThat(baseline.get().jobId()).isEqualTo("job-1");
    }

    @Test
    void recordBaseline_omitsRepositoriesWithoutACapturedHead() {
        // A persist that only committed solution + tests hands back only those pre-persist heads; the untouched template is not part of the revertible baseline.
        Map<RepositoryType, String> onlySolutionAndTests = new EnumMap<>(RepositoryType.class);
        onlySolutionAndTests.put(RepositoryType.SOLUTION, "sha-solution");
        onlySolutionAndTests.put(RepositoryType.TESTS, "sha-tests");

        revertService.recordBaseline(exercise, user, "job-1", onlySolutionAndTests);

        Optional<AdaptationBaseline> baseline = revertService.latestBaseline(exercise);
        assertThat(baseline).isPresent();
        assertThat(baseline.get().headFor(RepositoryType.TEMPLATE)).isNull();
        assertThat(baseline.get().headFor(RepositoryType.SOLUTION)).isEqualTo("sha-solution");
        assertThat(baseline.get().headFor(RepositoryType.TESTS)).isEqualTo("sha-tests");
    }

    @Test
    void revert_resetsEveryRepositoryToItsCapturedSha_andResyncsAndConsumesTheBaseline() throws Exception {
        revertService.recordBaseline(exercise, user, "job-1", preAdaptationHeads());

        Optional<ExerciseAdaptationRevertService.RevertResult> result = revertService.revert(exercise, user);

        assertThat(result).isPresent();
        assertThat(result.get().fullyReverted()).isTrue();
        assertThat(result.get().revertedRepositories()).containsExactly(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS);
        // Each repository was force-reset to the EXACT commit captured at job start, on the default branch.
        verify(gitService).resetToCommitAndForcePush(templateRepo, "sha-template", DEFAULT_BRANCH);
        verify(gitService).resetToCommitAndForcePush(solutionRepo, "sha-solution", DEFAULT_BRANCH);
        verify(gitService).resetToCommitAndForcePush(testsRepo, "sha-tests", DEFAULT_BRANCH);
        // Grading is re-synced against the reverted tests commit.
        verify(persistenceService).resyncAfterRevert(eq(exercise), eq(user), eq("sha-tests"));
        // The baseline is consumed so the same adaptation cannot be reverted twice.
        assertThat(revertService.revert(exercise, user)).isEmpty();
    }

    @Test
    void revert_whenNoBaselineRetained_returnsEmpty() {
        assertThat(revertService.revert(exercise, user)).isEmpty();
    }

    /** The pre-persist commit heads a full accepted adaptation of all three repositories hands back to {@code recordBaseline}. */
    private static Map<RepositoryType, String> preAdaptationHeads() {
        Map<RepositoryType, String> heads = new EnumMap<>(RepositoryType.class);
        heads.put(RepositoryType.TEMPLATE, "sha-template");
        heads.put(RepositoryType.SOLUTION, "sha-solution");
        heads.put(RepositoryType.TESTS, "sha-tests");
        return heads;
    }
}
