package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService.MutationLease;

class ExerciseVariantGroupServiceReservationTest {

    private final ExerciseVariantGroupRepository groups = mock();

    private final ProgrammingExerciseMutationGuardService guard = mock();

    private final ExerciseVariantGroup group = new ExerciseVariantGroup();

    private final List<Long> released = new ArrayList<>();

    private final RuntimeException firstReleaseFailure = new IllegalStateException("first release failed");

    private final RuntimeException secondReleaseFailure = new IllegalStateException("second release failed");

    private ExerciseVariantGroupService service;

    @BeforeEach
    void setup() {
        service = new ExerciseVariantGroupService(groups, mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), Optional.empty(), guard, mock());
        when(guard.isCoordinationEnabled()).thenReturn(true);
        when(groups.findByIdAndCourseIdElseThrow(9L, 3L)).thenReturn(group);
        when(groups.findByIdAndCourseIdWithoutExercisesElseThrow(9L, 3L)).thenReturn(group);
        for (long id : List.of(1L, 2L)) {
            var exercise = new ProgrammingExercise();
            exercise.setId(id);
            group.addExercise(exercise);
            when(guard.claimExternalMutation(id)).thenReturn(new MutationLease(() -> {
                released.add(id);
                throw id == 1L ? firstReleaseFailure : secondReleaseFailure;
            }));
        }
    }

    @Test
    void successfulDeletionReportsReleaseFailuresAndReleasesEveryMemberInReverseOrder() {
        assertThatThrownBy(() -> service.deleteGroup(9L, 3L)).isSameAs(secondReleaseFailure);
        assertThat(secondReleaseFailure.getSuppressed()).containsExactly(firstReleaseFailure);
        assertThat(released).containsExactly(2L, 1L);
    }

    @Test
    void deletionFailureRemainsPrimaryWhenEveryReleaseFails() {
        var deletionFailure = new IllegalArgumentException("deletion failed");
        doThrow(deletionFailure).when(groups).delete(group);

        assertThatThrownBy(() -> service.deleteGroup(9L, 3L)).isSameAs(deletionFailure);
        assertThat(deletionFailure.getSuppressed()).containsExactly(secondReleaseFailure);
        assertThat(secondReleaseFailure.getSuppressed()).containsExactly(firstReleaseFailure);
        assertThat(released).containsExactly(2L, 1L);
    }

    @Test
    void failedSecondClaimStillReleasesTheFirstMemberWithoutMaskingTheClaimFailure() {
        var claimFailure = new IllegalStateException("claim failed");
        when(guard.claimExternalMutation(2L)).thenThrow(claimFailure);

        assertThatThrownBy(() -> service.deleteGroup(9L, 3L)).isSameAs(claimFailure);
        assertThat(claimFailure.getSuppressed()).containsExactly(firstReleaseFailure);
        assertThat(released).containsExactly(1L);
    }
}
