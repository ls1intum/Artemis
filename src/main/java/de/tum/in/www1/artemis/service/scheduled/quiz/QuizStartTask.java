package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.io.Serial;
import java.io.Serializable;

import org.springframework.beans.factory.annotation.Autowired;

import com.hazelcast.scheduledexecutor.NamedTask;
import com.hazelcast.spring.context.SpringAware;
import de.tum.in.www1.artemis.config.Constants;

/**
 * Task to start a quiz with the given id that can be serialized and distributed
 */
@SpringAware
final class QuizStartTask implements Runnable, Serializable, NamedTask {

    static final String HAZELCAST_QUIZ_START_TASK = "-start";

    /**
     * Initial implementation 13.06.2020
     */
    @Serial
    private static final long serialVersionUID = 1L;

    final Long quizExerciseId;

    @Autowired
    transient QuizScheduleService quizScheduleService;

    QuizStartTask(Long quizExerciseId) {
        this.quizExerciseId = quizExerciseId;
    }

    @Override
    public void run() {
        quizScheduleService.executeQuizStartNowTask(quizExerciseId);
    }

    @Override
    public String getName() {
        return Constants.HAZELCAST_QUIZ_PREFIX + quizExerciseId + HAZELCAST_QUIZ_START_TASK;
    }
}
