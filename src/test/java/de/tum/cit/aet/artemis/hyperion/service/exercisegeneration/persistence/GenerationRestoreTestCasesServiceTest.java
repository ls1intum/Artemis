package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history.GenerationVersionRecoveryService.Recovery;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;

class GenerationRestoreTestCasesServiceTest {

    private final ProgrammingExerciseTestCaseTestRepository tests = mock(ProgrammingExerciseTestCaseTestRepository.class);

    private final GenerationRestoreTestCasesService service = new GenerationRestoreTestCasesService(tests);

    private final ProgrammingExerciseTestCase test = new ProgrammingExerciseTestCase();

    private Set<ProgrammingExerciseTestCase> current;

    private Recovery pair;

    @BeforeEach
    void setup() throws Exception {
        test.setId(1L);
        test.setTestName("boundary");
        test.setActive(true);
        test.setType(ProgrammingExerciseTestCaseType.DEFAULT);
        test.setWeight(4.0);
        test.setBonusMultiplier(1.0);
        test.setBonusPoints(0.0);
        test.setVisibility(Visibility.ALWAYS);
        current = new HashSet<>(Set.of(test));
        when(tests.findByExerciseId(12L)).thenReturn(current);
        var before = JsonObjectMapper.get().readValue("""
                {"id":12,"programmingData":{"testCases":[{"id":1,"testName":"boundary","active":false,"type":"STRUCTURAL", "weight":2.0,
                  "bonusMultiplier":1.5,"bonusPoints":3.0,"visibility":"NEVER"}]}}
                """, ExerciseSnapshotDTO.class);
        var after = JsonObjectMapper.get().readValue("""
                {"id":12,"programmingData":{"testCases":[{"id":1,"testName":"boundary","active":true,"type":"DEFAULT", "weight":4.0,
                  "bonusMultiplier":1.0,"bonusPoints":0.0,"visibility":"ALWAYS"}]}}
                """, ExerciseSnapshotDTO.class);
        pair = new Recovery("job", 8L, before, after, null);
    }

    @Test
    void restoresEveryCapturedTestFieldAndCanBeRetried() {
        assertThat(service.canRestore(12, pair)).isTrue();
        service.restore(12, pair, () -> true);
        assertThat(test.isActive()).isFalse();
        assertThat(test.getType()).isEqualTo(ProgrammingExerciseTestCaseType.STRUCTURAL);
        assertThat(test.getWeight()).isEqualTo(2.0);
        assertThat(test.getBonusMultiplier()).isEqualTo(1.5);
        assertThat(test.getBonusPoints()).isEqualTo(3.0);
        assertThat(test.getVisibility()).isEqualTo(Visibility.NEVER);
        verify(tests).saveAll(current);
        assertThat(service.canRestore(12, pair)).isTrue();
    }

    @Test
    void anEmptyDraftRestoresWithoutBuildingAndKeepsOnlyInactiveHistoricalRows() throws Exception {
        var empty = JsonObjectMapper.get().readValue("{\"id\":12,\"programmingData\":{}}", ExerciseSnapshotDTO.class);
        pair = new Recovery("job", 8L, empty, pair.after(), null);
        service.restore(12, pair, () -> true);
        assertThat(test.isActive()).isFalse();
        assertThat(test.getId()).isEqualTo(1L);
        assertThat(service.canRestore(12, pair)).isTrue();
    }

    @Test
    void rejectsAnInterveningBonusEditBeforeMutation() {
        test.setBonusPoints(42.0);
        assertThat(service.canRestore(12, pair)).isFalse();
        assertThatThrownBy(() -> service.restore(12, pair, () -> true)).isInstanceOf(IllegalStateException.class);
        verify(tests, never()).saveAll(any());
        assertThat(test.getBonusPoints()).isEqualTo(42.0);
    }

    @Test
    void rejectsMissingAndAddedTests() {
        current.clear();
        assertThat(service.canRestore(12, pair)).isFalse();
        current.add(test);
        var additional = new ProgrammingExerciseTestCase();
        additional.setId(2L);
        additional.setTestName("instructorAdded");
        additional.setActive(true);
        current.add(additional);
        assertThat(service.canRestore(12, pair)).isFalse();
    }

    @Test
    void inactiveHistoryFromALaterUndoneRunDoesNotBlockRestoringThePrecedingVersion() {
        var historical = new ProgrammingExerciseTestCase();
        historical.setId(2L);
        historical.setTestName("laterRunTest");
        historical.setActive(false);
        historical.setWeight(7.0);
        historical.setBonusPoints(3.0);
        current.add(historical);
        assertThat(service.canRestore(12, pair)).isTrue();
        service.restore(12, pair, () -> true);
        assertThat(historical.isActive()).isFalse();
        assertThat(historical.getWeight()).isEqualTo(7.0);
        assertThat(historical.getBonusPoints()).isEqualTo(3.0);
        assertThat(test.getWeight()).isEqualTo(2.0);
    }

    @Test
    void losingOwnershipDoesNotChangeAnyTestFields() {
        assertThatThrownBy(() -> service.restore(12, pair, () -> false)).isInstanceOf(IllegalStateException.class).hasMessageContaining("mutation guard");
        assertThat(test.isActive()).isTrue();
        assertThat(test.getWeight()).isEqualTo(4.0);
        verify(tests, never()).saveAll(any());
    }
}
