package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

class ExerciseDateServiceAnonymizationTest {

    private final ExerciseDateService exerciseDateService = new ExerciseDateService(mock(ParticipationRepository.class), Optional.empty(), Optional.empty());

    @Test
    void isAssessmentAnonymizationActive_courseQuizIsAssessedAutomatically_participantsStayVisible() {
        var quizExercise = new QuizExercise();

        assertThat(exerciseDateService.isAssessmentAnonymizationActive(quizExercise)).isFalse();
    }

    @Test
    void isAssessmentAnonymizationActive_examQuiz_participantsStayHidden() {
        var quizExercise = new QuizExercise();
        quizExercise.setExerciseGroup(new ExerciseGroup());

        assertThat(exerciseDateService.isAssessmentAnonymizationActive(quizExercise)).isTrue();
    }

    @Test
    void isAssessmentAnonymizationActive_courseModelingExercise_participantsStayHidden() {
        assertThat(exerciseDateService.isAssessmentAnonymizationActive(new ModelingExercise())).isTrue();
    }
}
