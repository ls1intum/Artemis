package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history.GenerationVersionRecoveryService.Recovery;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class GenerationRestoreMetadataServiceTest {

    private final ProgrammingExerciseTestRepository exercises = mock(ProgrammingExerciseTestRepository.class);

    private final ExerciseVariantGroupRepository groups = mock(ExerciseVariantGroupRepository.class);

    private final ProgrammingExerciseCreationUpdateService updates = mock(ProgrammingExerciseCreationUpdateService.class);

    private final GenerationRestoreMetadataService metadata = new GenerationRestoreMetadataService(exercises, groups, updates);

    private final ProgrammingExercise exercise = new ProgrammingExercise();

    private Recovery pair;

    @BeforeEach
    void setup() throws Exception {
        exercise.setId(12L);
        var group = new ExerciseVariantGroup();
        group.setId(7L);
        exercise.setExerciseVariantGroup(group);
        exercise.setDueDate(ZonedDateTime.parse("2030-02-01T12:00:00Z"));
        var before = JsonObjectMapper.get().readValue("""
                {"id":12,"dueDate":"2030-01-01T12:00:00Z", "programmingData":{
                    "buildAndTestStudentSubmissionsAfterDueDate":"2030-01-01T13:00:00Z"}}
                """, ExerciseSnapshotDTO.class);
        var after = JsonObjectMapper.get().readValue("""
                {"id":12,"variantGroupId":7,"dueDate":"2030-02-01T12:00:00Z","programmingData":{}}
                """, ExerciseSnapshotDTO.class);
        pair = new Recovery("job", 82L, before, after, null);
        when(exercises.findByIdElseThrow(12L)).thenReturn(exercise);
    }

    @Test
    void restoresDestinationMembershipAndTheOriginalTimelineOffset() {
        assertThat(metadata.canRestore(12L, pair)).isTrue();
        metadata.restore(12L, pair, () -> true);
        assertThat(exercise.getExerciseVariantGroup()).isNull();
        verify(exercises).save(exercise);
        var timeline = ArgumentCaptor.forClass(ProgrammingExerciseTimelineUpdateDTO.class);
        verify(updates).updateTimeline(timeline.capture(), eq(null), eq(Duration.ofHours(1)));
        assertThat(timeline.getValue().dueDate().toInstant()).isEqualTo(pair.before().dueDate().toInstant());
        assertThat(timeline.getValue().buildAndTestStudentSubmissionsAfterDueDate().toInstant())
                .isEqualTo(pair.before().programmingData().buildAndTestStudentSubmissionsAfterDueDate().toInstant());
        org.mockito.Mockito.verifyNoInteractions(groups);
    }

    @Test
    void refusesLaterInstructorTimelineChangesBeforeWritingAnything() {
        exercise.setDueDate(exercise.getDueDate().plusDays(1));
        assertThat(metadata.canRestore(12L, pair)).isFalse();
        assertThatThrownBy(() -> metadata.restore(12L, pair, () -> true)).isInstanceOf(IllegalStateException.class).hasMessageContaining("edited after");
        verify(exercises, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(updates);
    }

    @Test
    void refusesLaterInstructorGroupChanges() {
        exercise.getExerciseVariantGroup().setId(99L);
        assertThat(metadata.canRestore(12L, pair)).isFalse();
    }

    @Test
    void partialMembershipRestoreRemainsRetryable() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> metadata.restore(12L, pair, () -> calls.incrementAndGet() == 1)).isInstanceOf(IllegalStateException.class).hasMessageContaining("mutation guard");
        assertThat(exercise.getExerciseVariantGroup()).isNull();
        org.mockito.Mockito.verifyNoInteractions(updates);
        assertThat(metadata.canRestore(12L, pair)).isTrue();
        metadata.restore(12L, pair, () -> true);
        verify(updates).updateTimeline(any(ProgrammingExerciseTimelineUpdateDTO.class), eq(null), eq(Duration.ofHours(1)));
    }

    @Test
    void comparesInstantsRatherThanZoneRepresentation() {
        exercise.setDueDate(exercise.getDueDate().withZoneSameInstant(java.time.ZoneId.of("Europe/Berlin")));
        assertThat(metadata.canRestore(12L, pair)).isTrue();
    }
}
