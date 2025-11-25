package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseSynchronizationDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseSynchronizationDTO.SynchronizationTarget;

class ProgrammingExerciseSynchronizationServiceTest {

    private WebsocketMessagingService websocketMessagingService;

    private ProgrammingExerciseSynchronizationService synchronizationService;

    @BeforeEach
    void setUp() {
        websocketMessagingService = mock(WebsocketMessagingService.class);
        synchronizationService = new ProgrammingExerciseSynchronizationService(websocketMessagingService);
        when(websocketMessagingService.sendMessage(anyString(), any(Object.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void determineChangeDetectsRepositoryUpdates() {
        var baseSnapshot = createExerciseSnapshot("problem", "template1", "solution1", "tests1", Map.of(7L, "aux1"));
        var templateChange = createExerciseSnapshot("problem", "template2", "solution1", "tests1", Map.of(7L, "aux1"));

        var change = synchronizationService.determineChange(templateChange, baseSnapshot);
        assertThat(change).isNotNull();
        assertThat(change.target()).isEqualTo(SynchronizationTarget.TEMPLATE_REPOSITORY);

        var solutionChange = createExerciseSnapshot("problem", "template2", "solution2", "tests1", Map.of(7L, "aux1"));
        change = synchronizationService.determineChange(solutionChange, templateChange);
        assertThat(change).isNotNull();
        assertThat(change.target()).isEqualTo(SynchronizationTarget.SOLUTION_REPOSITORY);

        var testsChange = createExerciseSnapshot("problem", "template2", "solution2", "tests2", Map.of(7L, "aux1"));
        change = synchronizationService.determineChange(testsChange, solutionChange);
        assertThat(change).isNotNull();
        assertThat(change.target()).isEqualTo(SynchronizationTarget.TESTS_REPOSITORY);

        var auxiliaryChange = createExerciseSnapshot("problem", "template2", "solution2", "tests2", Map.of(7L, "aux2"));
        change = synchronizationService.determineChange(auxiliaryChange, testsChange);
        assertThat(change).isNotNull();
        assertThat(change.target()).isEqualTo(SynchronizationTarget.AUXILIARY_REPOSITORY);
        assertThat(change.auxiliaryRepositoryId()).isEqualTo(7L);
    }

    @Test
    void determineChangeDetectsProblemStatementUpdate() {
        var previousSnapshot = createExerciseSnapshot("problem", "template1", "solution1", "tests1", Map.of());
        var newSnapshot = createExerciseSnapshot("updatedProblem", "template1", "solution1", "tests1", Map.of());

        var change = synchronizationService.determineChange(newSnapshot, previousSnapshot);
        assertThat(change).isNotNull();
        assertThat(change.target()).isEqualTo(SynchronizationTarget.PROBLEM_STATEMENT);
    }

    @Test
    void broadcastChangeSendsPayloadToTopic() {
        var change = new ProgrammingExerciseSynchronizationDTO(SynchronizationTarget.TESTS_REPOSITORY, 9L, "client-17");

        synchronizationService.broadcastChange(42L, change);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseSynchronizationDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/42/synchronization"), captor.capture());

        var sentMessage = captor.getValue();
        assertThat(sentMessage.target()).isEqualTo(SynchronizationTarget.TESTS_REPOSITORY);
        assertThat(sentMessage.auxiliaryRepositoryId()).isEqualTo(9L);
        assertThat(sentMessage.clientInstanceId()).isEqualTo("client-17");
    }

    private ExerciseSnapshotDTO createExerciseSnapshot(String problemStatement, String templateCommitId, String solutionCommitId, String testsCommitId,
            Map<Long, String> auxiliaryCommitIds) {
        var programmingData = new ProgrammingExerciseSnapshotDTO(null,
                auxiliaryCommitIds.entrySet().stream().map(entry -> new ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO(entry.getKey(), null, entry.getValue()))
                        .toList(),
                null, null, null, null, null, null, null, null, null, null, new ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO(1L, null, null, templateCommitId),
                new ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO(2L, null, null, solutionCommitId), Set.of(), null, null, null, null, null, null, testsCommitId);

        return new ExerciseSnapshotDTO(1L, null, null, null, null, null, null, null, null, null, null, null, ExerciseMode.INDIVIDUAL, null, null, null,
                IncludedInOverallScore.INCLUDED_COMPLETELY, problemStatement, null, null, null, null, null, null, null, null, programmingData, null, null, null, null);
    }
}
