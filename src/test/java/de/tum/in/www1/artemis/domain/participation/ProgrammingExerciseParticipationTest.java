package de.tum.in.www1.artemis.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.util.ModelFactory;

class ProgrammingExerciseParticipationTest {

    @Test
    void shouldNotBeLockedBeforeTheIndividualDueDate() {
        final ZonedDateTime now = ZonedDateTime.now();
        final var participation = setupParticipation(now.minusHours(2));
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(2));
        assertThat(participation.isLocked()).isFalse();
    }

    @Test
    void shouldBeLockedAfterTheIndividualDueDate() {
        final ZonedDateTime now = ZonedDateTime.now();
        final var participation = setupParticipation(now.minusHours(2));
        participation.setIndividualDueDate(ZonedDateTime.now().minusHours(1));
        assertThat(participation.isLocked()).isTrue();
    }

    @Test
    void shouldNotBeLockedBeforeTheRegularDueDate() {
        final var participation = setupParticipation(ZonedDateTime.now().plusHours(1));
        assertThat(participation.isLocked()).isFalse();
    }

    @Test
    void shouldBeLockedAfterTheRegularDueDate() {
        final var participation = setupParticipation(ZonedDateTime.now().minusHours(1));
        assertThat(participation.isLocked()).isTrue();
    }

    @Test
    void shouldNotBeLockedIfNoExerciseDueDate() {
        final var participation = setupParticipation(null);
        participation.getExercise().setDueDate(null);
        assertThat(participation.isLocked()).isFalse();
    }

    private ProgrammingExerciseStudentParticipation setupParticipation(final ZonedDateTime exerciseDueDate) {
        final ZonedDateTime now = ZonedDateTime.now();
        final var user = ModelFactory.generateActivatedUser("student1");
        final var course = ModelFactory.generateCourse(1L, now.minusDays(10), now.plusDays(20), new HashSet<>());
        final var exercise = ModelFactory.generateProgrammingExercise(now.minusHours(10), exerciseDueDate, course);
        return ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, exercise, user);
    }
}
