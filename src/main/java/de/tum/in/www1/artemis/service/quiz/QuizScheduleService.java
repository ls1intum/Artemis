package de.tum.in.www1.artemis.service.quiz;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

@Profile(PROFILE_CORE)
@Service
public class QuizScheduleService {

    public void scheduleQuizStart(Long quizExerciseId) {
        // TODO: implement
        // this involves the quiz start (sending out a websocket message) and the quiz end (sending out the result to the student)
    }

    public void cancelScheduledQuizStart(Long quizExerciseId) {
        // TODO: implement
    }

    public void stopSchedule() {
    }

    public void updateQuizExercise(QuizExercise quizExercise) {
        cancelScheduledQuizStart(quizExercise.getId());
        scheduleQuizStart(quizExercise.getId());
    }
}
