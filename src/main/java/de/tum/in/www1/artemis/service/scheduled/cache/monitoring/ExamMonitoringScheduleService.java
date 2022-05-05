package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import org.springframework.stereotype.Service;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicReference;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.hazelcast.scheduledexecutor.ScheduledTaskHandler;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;

@Service
public class ExamMonitoringScheduleService {

    private final ExamCache examCache;

    private final IScheduledExecutorService threadPoolTaskScheduler;

    private final IAtomicReference<ScheduledTaskHandler> scheduledProcessExamActivity;

    private static final String HAZELCAST_PROCESS_CACHE_HANDLER = ExamProcessCacheTask.HAZELCAST_PROCESS_CACHE_TASK + "-handler";

    public ExamMonitoringScheduleService(HazelcastInstance hazelcastInstance) {
        this.scheduledProcessExamActivity = hazelcastInstance.getCPSubsystem().getAtomicReference(HAZELCAST_PROCESS_CACHE_HANDLER);
        this.threadPoolTaskScheduler = hazelcastInstance.getScheduledExecutorService(Constants.HAZELCAST_MONITORING_SCHEDULER);
        this.examCache = new ExamCache(hazelcastInstance);
    }

    /**
     * Configures Hazelcast for the ExamActivityService before the HazelcastInstance is created.
     *
     * @param config the {@link Config} the ExamActivityService-specific configuration should be added to
     */
    public static void configureHazelcast(Config config) {
        ExamCache.configureHazelcast(config);
        config.getScheduledExecutorConfig(Constants.HAZELCAST_MONITORING_SCHEDULER).setPoolSize(16).setCapacity(1000).setDurability(1);
    }

    public void updateExamActivity(Long examId, long studentExamId, ExamActivity examActivity) {
        if (examActivity != null) {
            ((ExamMonitoringCache) examCache.getTransientWriteCacheFor(examId)).getActivities().put(studentExamId, examActivity);
        }
    }

    public void addExamAction(Long examId, long studentExamId, ExamAction examAction) {
        if (examAction != null) {
            ExamActivity activity = ((ExamMonitoringCache) examCache.getTransientWriteCacheFor(examId)).getActivities().get(studentExamId);
            if (activity == null) {
                activity = new ExamActivity();
                updateExamActivity(examId, studentExamId, activity);
            }
            activity.addExamAction(examAction);
        }
    }
}
