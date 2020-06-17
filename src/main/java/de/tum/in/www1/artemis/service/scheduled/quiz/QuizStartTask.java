package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.io.Serializable;

import org.springframework.beans.factory.annotation.Autowired;

import com.hazelcast.spring.context.SpringAware;

/**
 * Task to start a quiz with the given id that can be serialized and distributed
 */
@SpringAware
class QuizStartTask implements Runnable, Serializable {

    /**
     * Initial implementation 13.06.2020
     */
    private static final long serialVersionUID = 1L;

    final Long quizExerciseId;

    @Autowired(required = true)
    transient QuizScheduleService quizScheduleService;

    QuizStartTask(Long quizExerciseId) {
        this.quizExerciseId = quizExerciseId;
    }

    @Override
    public void run() {
        quizScheduleService.executeQuizStartNowTask(quizExerciseId);
    }
}
