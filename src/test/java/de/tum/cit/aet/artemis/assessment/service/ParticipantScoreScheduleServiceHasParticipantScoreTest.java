package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Truth table of the rule that decides whether the results of a participation contribute to a participant score. The score of a team exercise is keyed by team, so a
 * participation that belongs to a student (the practice mode of a team exercise) must not be read as one.
 */
class ParticipantScoreScheduleServiceHasParticipantScoreTest {

    private static final long PARTICIPANT_ID = 7L;

    private enum ParticipantKind {
        NONE, USER, TEAM
    }

    static Stream<Arguments> participations() {
        return Stream.of(
                // a participation without a participant has nothing to be scored for, in any exercise
                Arguments.of(ParticipantKind.NONE, false, ExerciseMode.INDIVIDUAL, false), Arguments.of(ParticipantKind.NONE, true, ExerciseMode.TEAM, false),
                // a student in an individual exercise keeps a score, graded or practice
                Arguments.of(ParticipantKind.USER, false, ExerciseMode.INDIVIDUAL, true), Arguments.of(ParticipantKind.USER, true, ExerciseMode.INDIVIDUAL, true),
                // a student in a team exercise has a score only through the team, so the practice participation of the student has none
                Arguments.of(ParticipantKind.USER, false, ExerciseMode.TEAM, true), Arguments.of(ParticipantKind.USER, true, ExerciseMode.TEAM, false),
                // a team has a score in a team exercise, and the practice flag does not change that
                Arguments.of(ParticipantKind.TEAM, false, ExerciseMode.TEAM, true), Arguments.of(ParticipantKind.TEAM, true, ExerciseMode.TEAM, true));
    }

    @ParameterizedTest(name = "{0}, practice={1}, {2} -> {3}")
    @MethodSource("participations")
    void hasParticipantScore(ParticipantKind kind, boolean practiceMode, ExerciseMode mode, boolean expected) {
        TextExercise exercise = new TextExercise();
        exercise.setMode(mode);
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setPracticeMode(practiceMode);
        participation.setParticipant(participantOf(kind));

        assertThat(ParticipantScoreScheduleService.hasParticipantScore(participation)).isEqualTo(expected);
    }

    @Test
    void hasParticipantScore_gradedParticipationOfAStudent_doesNotNeedTheExercise() {
        // a graded participation of a student is decided by the participant alone, so a participation whose exercise is not loaded still counts
        StudentParticipation participation = new StudentParticipation();
        participation.setParticipant(participantOf(ParticipantKind.USER));
        participation.setPracticeMode(false);

        assertThat(ParticipantScoreScheduleService.hasParticipantScore(participation)).isTrue();
    }

    private static Participant participantOf(ParticipantKind kind) {
        return switch (kind) {
            case NONE -> null;
            case USER -> {
                User user = new User();
                user.setId(PARTICIPANT_ID);
                yield user;
            }
            case TEAM -> {
                Team team = new Team();
                team.setId(PARTICIPANT_ID);
                yield team;
            }
        };
    }
}
