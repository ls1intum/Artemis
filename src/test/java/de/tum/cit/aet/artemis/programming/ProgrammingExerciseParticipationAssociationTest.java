package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;

class ProgrammingExerciseParticipationAssociationTest {

    static Stream<ProgrammingExerciseParticipation> participations() {
        return Stream.of(new ProgrammingExerciseStudentParticipation(), new TemplateProgrammingExerciseParticipation(), new SolutionProgrammingExerciseParticipation());
    }

    @ParameterizedTest
    @MethodSource("participations")
    void requiredExercise_rejectsMissingAssociations(ProgrammingExerciseParticipation participation) {
        assertThat(participation.getProgrammingExercise()).isNull();
        assertThatThrownBy(participation::getProgrammingExerciseElseThrow).isInstanceOf(IllegalStateException.class)
                .hasMessage("The programming exercise of the participation cannot be resolved");
    }

    @ParameterizedTest
    @MethodSource("participations")
    void requiredExercise_returnsTheAssociatedExercise(ProgrammingExerciseParticipation participation) {
        var exercise = new ProgrammingExercise();
        participation.setProgrammingExercise(exercise);

        assertThat(participation.getProgrammingExerciseElseThrow()).isSameAs(exercise);
    }
}
