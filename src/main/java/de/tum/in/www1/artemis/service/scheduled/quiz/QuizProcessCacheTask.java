package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.io.Serializable;

import org.springframework.beans.factory.annotation.Autowired;

import com.hazelcast.spring.context.SpringAware;

/**
 * Task to process the cached quiz submissions that can be serialized and distributed
 */
@SpringAware
class QuizProcessCacheTask implements Runnable, Serializable {

    /**
     * Initial implementation 13.06.2020
     */
    private static final long serialVersionUID = 1L;

    @Autowired(required = true)
    transient QuizScheduleService quizScheduleService;

    @Override
    public void run() {
        quizScheduleService.processCachedQuizSubmissions();
    }
}
