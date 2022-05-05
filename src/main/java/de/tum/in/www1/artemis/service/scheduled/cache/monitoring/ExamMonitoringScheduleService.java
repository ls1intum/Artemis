package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicReference;
import com.hazelcast.scheduledexecutor.DuplicateTaskException;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.hazelcast.scheduledexecutor.ScheduledTaskHandler;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;
import de.tum.in.www1.artemis.service.exam.ExamService;

@Service
public class ExamMonitoringScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ExamMonitoringScheduleService.class);

    private final ExamCache examCache;

    private final IScheduledExecutorService threadPoolTaskScheduler;

    private final IAtomicReference<ScheduledTaskHandler> scheduledProcessExamActivity;

    private final ExamService examService;

    private static final String HAZELCAST_PROCESS_CACHE_HANDLER = ExamProcessCacheTask.HAZELCAST_PROCESS_CACHE_TASK + "-handler";

    public ExamMonitoringScheduleService(HazelcastInstance hazelcastInstance, ExamService examService) {
        this.scheduledProcessExamActivity = hazelcastInstance.getCPSubsystem().getAtomicReference(HAZELCAST_PROCESS_CACHE_HANDLER);
        this.threadPoolTaskScheduler = hazelcastInstance.getScheduledExecutorService(Constants.HAZELCAST_MONITORING_SCHEDULER);
        this.examCache = new ExamCache(hazelcastInstance);
        this.examService = examService;
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

    public void startSchedule(long delayInMillis) {
        if (scheduledProcessExamActivity.isNull()) {
            try {
                var scheduledFuture = threadPoolTaskScheduler.schedule(new ExamProcessCacheTask(), delayInMillis, TimeUnit.MILLISECONDS);
                scheduledProcessExamActivity.set(scheduledFuture.getHandler());
                log.info("ExamMonitoringScheduleService was started to run repeatedly with {} second delay.", delayInMillis / 1000.0);
            }
            catch (@SuppressWarnings("unused") DuplicateTaskException e) {
                log.warn("Exam process cache task already registered");
                // this is expected if we run on multiple nodes
            }

            // schedule quiz start for all existing quizzes that are planned to start in the future
            /*
             * List<Exam> exams = examService. log.info("Found {} exams with planned start in the future", exams.size()); for (Exam exam : exams) {
             * scheduleExamActivitySave(exam.getId()); }
             */
        }
        else {
            log.info("Cannot start exam monitoring schedule service, it is already RUNNING");
        }
    }
}
