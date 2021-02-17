package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.io.Serial;
import java.io.Serializable;

import com.hazelcast.scheduledexecutor.NamedTask;
import com.hazelcast.spring.context.SpringAware;
import de.tum.in.www1.artemis.config.Constants;

/**
 * Task to process the cached quiz submissions that can be serialized and distributed
 */
@SpringAware
final class QuizProcessCacheTask implements Runnable, Serializable, NamedTask {

    static final String HAZELCAST_PROCESS_CACHE_TASK = Constants.HAZELCAST_QUIZ_PREFIX + "process-cache";

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient QuizScheduleService quizScheduleService;

    QuizProcessCacheTask(QuizScheduleService quizScheduleService) {
        this.quizScheduleService = quizScheduleService;
    }

    @Override
    public void run() {
        quizScheduleService.processCachedQuizSubmissions();
    }

    @Override
    public String getName() {
        return HAZELCAST_PROCESS_CACHE_TASK;
    }
}
