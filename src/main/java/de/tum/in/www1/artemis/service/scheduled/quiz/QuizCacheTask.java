package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.spring.context.SpringAware;

@SpringAware
class QuizCacheTask implements Runnable, Serializable, HazelcastInstanceAware {

    private static final Logger log = LoggerFactory.getLogger(QuizCacheTask.class);

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

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        log.debug("QuizCacheTask deserialized and initialized");
    }
}
