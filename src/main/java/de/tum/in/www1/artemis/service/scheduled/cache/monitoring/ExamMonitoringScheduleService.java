package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicReference;
import com.hazelcast.scheduledexecutor.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.exam.monitoring.ExamActivityService;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;

@Service
public class ExamMonitoringScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ExamMonitoringScheduleService.class);

    private final ExamCache examCache;

    private final IScheduledExecutorService threadPoolTaskScheduler;

    private final IAtomicReference<ScheduledTaskHandler> scheduledProcessExamActivity;

    private final ExamService examService;

    private final ExamActivityService examActivityService;

    private static final String HAZELCAST_PROCESS_CACHE_HANDLER = ExamProcessCacheTask.HAZELCAST_PROCESS_CACHE_TASK + "-handler";

    public ExamMonitoringScheduleService(HazelcastInstance hazelcastInstance, ExamService examService, ExamActivityService examActivityService) {
        this.scheduledProcessExamActivity = hazelcastInstance.getCPSubsystem().getAtomicReference(HAZELCAST_PROCESS_CACHE_HANDLER);
        this.threadPoolTaskScheduler = hazelcastInstance.getScheduledExecutorService(Constants.HAZELCAST_MONITORING_SCHEDULER);
        this.examCache = new ExamCache(hazelcastInstance);
        this.examService = examService;
        this.examActivityService = examActivityService;
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

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        // activate Exam Monitoring Service
        SecurityUtils.setAuthorizationObject();
        startSchedule(5 * 1000); // every 5 seconds
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

            // TODO: Add filter for Exams
            List<Exam> exams = examService.findAllCurrentAndUpcomingExams();
            log.info("Found {} exams that are not yet ended or are scheduled to start in the future", exams.size());
            for (Exam exam : exams) {
                scheduleExamActivitySave(exam.getId());
            }
        }
        else {
            log.info("Cannot start exam monitoring schedule service, it is already RUNNING");
        }
    }

    public void stopSchedule() {
        if (!scheduledProcessExamActivity.isNull()) {
            log.info("Try to stop Exam Monitoring Schedule Service");
            var scheduledFuture = threadPoolTaskScheduler.getScheduledFuture(scheduledProcessExamActivity.get());
            try {
                // if the task has been disposed, this will throw a StaleTaskException
                boolean cancelSuccess = scheduledFuture.cancel(false);
                scheduledFuture.dispose();
                scheduledProcessExamActivity.set(null);
                log.info("Stop Exam Monitoring Schedule Service was successful: {}", cancelSuccess);
            }
            catch (@SuppressWarnings("unused") StaleTaskException e) {
                log.info("Stop Exam Monitoring Schedule Service already disposed/cancelled");
                // has already been disposed (sadly there is no method to check that)
            }
            for (Cache cachedExamMonitoring : examCache.getAllCaches()) {
                if (((ExamMonitoringCache) cachedExamMonitoring).getExamActivitySaveHandler() != null)
                    cancelExamActivitySave(((ExamMonitoringCache) cachedExamMonitoring).getExamId());
            }
            threadPoolTaskScheduler.shutdown();
            threadPoolTaskScheduler.destroy();
        }
        else {
            log.debug("Cannot stop Exam Monitoring Schedule Service, it was already STOPPED");
        }
    }

    public void scheduleExamActivitySave(final long examId) {
        // first remove and cancel old scheduledFuture if it exists
        cancelExamActivitySave(examId);
        // reload from database to make sure there are no proxy objects
        final var exam = examService.findByIdOrElseThrow(examId);
        try {
            // TODO: Add longest possible working time
            long delay = Duration.between(ZonedDateTime.now(), exam.getEndDate()).toMillis();
            var scheduledFuture = threadPoolTaskScheduler.schedule(new ExamActivitySaveTask(examId), delay, TimeUnit.MILLISECONDS);
            // save scheduled future in HashMap
            examCache.performCacheWrite(examId, examMonitoringCache -> {
                ((ExamMonitoringCache) examMonitoringCache).setExamActivitySaveHandler(List.of(scheduledFuture.getHandler()));
                return examMonitoringCache;
            });
        }
        catch (@SuppressWarnings("unused") DuplicateTaskException e) {
            log.debug("Exam {} monitoring save task already registered", examId);
            // this is expected if we run on multiple nodes
        }
    }

    public void cancelExamActivitySave(final long examId) {
        ((ExamMonitoringCache) examCache.getReadCacheFor(examId)).getExamActivitySaveHandler().forEach(taskHandler -> {
            IScheduledFuture<?> scheduledFuture = threadPoolTaskScheduler.getScheduledFuture(taskHandler);
            try {
                // if the task has been disposed, this will throw a StaleTaskException
                boolean taskNotDone = !scheduledFuture.isDone();
                boolean cancelSuccess = false;
                if (taskNotDone) {
                    cancelSuccess = scheduledFuture.cancel(false);
                }
                scheduledFuture.dispose();
                if (taskNotDone) {
                    log.info("Stop scheduled exam activity save for exam {} was successful: {}", examId, cancelSuccess);
                }
            }
            catch (@SuppressWarnings("unused") StaleTaskException e) {
                log.info("Stop scheduled exam activity save for exam {} already disposed/cancelled", examId);
                // has already been disposed (sadly there is no method to check that)
            }
        });
        examCache.performCacheWriteIfPresent(examId, cachedMonitoring -> {
            ((ExamMonitoringCache) cachedMonitoring).setExamActivitySaveHandler(ExamMonitoringCache.getEmptyExamActivitySaveHandler());
            return cachedMonitoring;
        });
    }

    void executeExamActivitySaveTask(Long examId) {
        examCache.performCacheWriteIfPresent(examId, examMonitoringCache -> {
            ((ExamMonitoringCache) examMonitoringCache).getExamActivitySaveHandler().clear();
            log.debug("Removed exam {} monitoring save tasks", examId);
            return examMonitoringCache;
        });

        ExamMonitoringCache cache = (ExamMonitoringCache) examCache.getReadCacheFor(examId);

        // TODO: Check if this is enough or we need to handle this in different ways
        examActivityService.saveAll(cache.getActivities().values());
        cache.getActivities().clear();
    }
}
