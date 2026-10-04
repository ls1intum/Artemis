package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@ExtendWith(MockitoExtension.class)
class ExerciseScoresChartServiceTest {

    @Mock
    private StudentScoreRepository studentScoreRepository;

    @Mock
    private TeamScoreRepository teamScoreRepository;

    @Mock
    private ParticipantScoreRepository participantScoreRepository;

    @InjectMocks
    private ExerciseScoresChartService exerciseScoresChartService;

    @Test
    void getExerciseScores_exerciseWithoutCourse_throws() {
        var exercise = new TextExercise();
        exercise.setId(7L);

        assertThatThrownBy(() -> exerciseScoresChartService.getExerciseScores(Set.of(exercise), new User())).isInstanceOf(IllegalStateException.class).hasMessageContaining("7");
    }
}
