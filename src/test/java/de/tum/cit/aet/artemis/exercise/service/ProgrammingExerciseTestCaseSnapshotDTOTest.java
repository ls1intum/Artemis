package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseTestCaseSnapshotDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;

class ProgrammingExerciseTestCaseSnapshotDTOTest {

    @Test
    void retainsIdentityAndInactiveGradingWithoutReadingTheLiveTestTable() throws Exception {
        var test = new ProgrammingExerciseTestCase().id(7L).testName("popEmptyStack").weight(2.0).bonusMultiplier(1.5).bonusPoints(0.0).active(false);
        test.setType(ProgrammingExerciseTestCaseType.DEFAULT);
        test.setVisibility(Visibility.AFTER_DUE_DATE);
        var snapshot = ProgrammingExerciseTestCaseSnapshotDTO.of(test);
        test.setTestName("renamedLater");
        test.setWeight(9.0);
        var mapper = JsonObjectMapper.get();
        var restored = mapper.readValue(mapper.writeValueAsString(snapshot), ProgrammingExerciseTestCaseSnapshotDTO.class);
        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.testName()).isEqualTo("popEmptyStack");
        assertThat(restored.active()).isFalse();
        assertThat(restored.weight()).isEqualTo(2.0);
        assertThat(restored.bonusMultiplier()).isEqualTo(1.5);
        assertThat(restored.bonusPoints()).isZero();
    }

    @Test
    void exerciseVersionsActuallyCaptureTheNamedTestSnapshot() {
        var exercise = new ProgrammingExercise();
        exercise.getTestCases().add(new ProgrammingExerciseTestCase().id(7L).testName("popEmptyStack").active(true));
        var snapshot = ProgrammingExerciseSnapshotDTO.of(exercise, new ProgrammingExerciseBuildConfig(),
                new ProgrammingExerciseSnapshotDTO.CommitHashesDTO("template", "solution", "tests", Map.of()));
        assertThat(snapshot.testCases()).singleElement().satisfies(test -> {
            assertThat(test.testName()).isEqualTo("popEmptyStack");
            assertThat(test.active()).isTrue();
        });
    }

    @Test
    void readsHistoricalSnapshotsWithoutInventingMissingIdentity() throws Exception {
        var snapshot = JsonObjectMapper.get().readValue("""
                {"id":7,"weight":2.0,"bonusMultiplier":1.5,"bonusPoints":0.0,"visibility":"ALWAYS"}
                """, ProgrammingExerciseTestCaseSnapshotDTO.class);
        assertThat(snapshot.id()).isEqualTo(7L);
        assertThat(snapshot.weight()).isEqualTo(2.0);
        assertThat(snapshot.testName()).isNull();
        assertThat(snapshot.active()).isNull();
        assertThat(snapshot.type()).isNull();
    }
}
