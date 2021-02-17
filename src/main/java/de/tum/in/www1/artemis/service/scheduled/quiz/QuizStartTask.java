package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.io.Serial;
import java.io.Serializable;

import com.hazelcast.scheduledexecutor.NamedTask;
import com.hazelcast.spring.context.SpringAware;
import de.tum.in.www1.artemis.config.Constants;

/**
 * Task to start a quiz with the given id that can be serialized and distributed
 */
@SpringAware
final class QuizStartTask implements Runnable, Serializable, NamedTask {

    static final String HAZELCAST_QUIZ_START_TASK = "-start";

    @Serial
    private static final long serialVersionUID = 1L;

    final Long quizExerciseId;

    private final transient QuizScheduleService quizScheduleService;

    QuizStartTask(Long quizExerciseId, QuizScheduleService quizScheduleService) {
        this.quizExerciseId = quizExerciseId;
        this.quizScheduleService = quizScheduleService;
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
