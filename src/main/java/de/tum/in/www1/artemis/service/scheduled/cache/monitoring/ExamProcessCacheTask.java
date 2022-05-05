package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.io.Serial;
import java.io.Serializable;

import org.springframework.beans.factory.annotation.Autowired;

import com.hazelcast.scheduledexecutor.NamedTask;
import com.hazelcast.spring.context.SpringAware;

import de.tum.in.www1.artemis.config.Constants;

@SpringAware
final class ExamProcessCacheTask implements Runnable, Serializable, NamedTask {

    static final String HAZELCAST_PROCESS_CACHE_TASK = Constants.HAZELCAST_MONITORING_PREFIX + "process-cache";

    @Serial
    private static final long serialVersionUID = 1L;

    @Autowired // ok
    transient ExamMonitoringScheduleService examMonitoringScheduleService;

    @Override
    public void run() {
        // TODO
    }

    @Override
    public String getName() {
        return HAZELCAST_PROCESS_CACHE_TASK;
    }
}
