package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.scheduledexecutor.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.exam.monitoring.ExamActivityService;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;

/**
 * For all {@link Exam}s where monitoring is enabled, the scheduling service schedules the cache reset after another 30 minutes {@link Constants}
 * after the last {@link StudentExam} is completed. In addition, it takes care of adding new {@link ExamAction}s per {@link ExamActivity} and {@link Exam}.
 * The service works as an interface for the distributed hazelcast exam monitoring cache;
 */
@Service
public class ExamMonitoringScheduleService {

    private final Logger logger = LoggerFactory.getLogger(ExamMonitoringScheduleService.class);

    private final ExamCache examCache;

    private final IScheduledExecutorService threadPoolTaskScheduler;

    private final ExamRepository examRepository;

    private final WebsocketMessagingService messagingService;

    private final ExamActivityService examActivityService;

    public ExamMonitoringScheduleService(HazelcastInstance hazelcastInstance, ExamRepository examRepository, WebsocketMessagingService messagingService,
            ExamActivityService examActivityService) {
        this.threadPoolTaskScheduler = hazelcastInstance.getScheduledExecutorService(Constants.HAZELCAST_MONITORING_SCHEDULER);
        this.examCache = new ExamCache(hazelcastInstance);
        this.examRepository = examRepository;
        this.messagingService = messagingService;
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
        startSchedule();
    }

    /**
     * Used to handle the received actions.
     *
     * @param examId    identifies the cache
     * @param action    new exam action
     */
    public void addExamActions(Long examId, ExamAction action) {
        if (action != null && action.getStudentExamId() != null) {
            Long studentExamId = action.getStudentExamId();

            ((ExamMonitoringCache) examCache.getTransientWriteCacheFor(examId)).updateActivity(studentExamId, activity -> {
                if (activity == null) {
                    activity = new ExamActivity();
                    activity.setStudentExamId(studentExamId);
                    activity = examActivityService.save(activity);
                }

                // Connect action and activity
                action.setExamActivityId(activity.getId());
                action.setExamActivity(activity);
                activity.addExamAction(action);

                return activity;
            });

            // send message to subscribers
            messagingService.sendMessage("/topic/exam-monitoring/" + examId + "/action", action);
        }
    }

    /**
     * Used to update monitoring during the exam.
     *
     * @param examId        identifies the cache
     * @param monitoring    new exam action
     */
    public void notifyMonitoringUpdate(Long examId, boolean monitoring) {
        messagingService.sendMessage("/topic/exam-monitoring/" + examId + "/update", monitoring);
    }

    /**
     * Returns all exam actions.
     *
     * @param examId identifies the cache
     * @return all exam actions of the exam
     */
    public List<ExamAction> getAllExamActions(Long examId) {
        var examActivities = ((ExamMonitoringCache) examCache.getTransientWriteCacheFor(examId)).getActivities();
        var examActions = new ArrayList<ExamAction>();

        for (var examActivity : examActivities.values()) {
            var actions = examActivity.getExamActions();
            actions.forEach(examAction -> examAction.setStudentExamId(examActivity.getStudentExamId()));
            examActions.addAll(actions);
        }

        return examActions;
    }

    /**
     * This method schedules all exam activity save tasks after a server (re-)start.
     */
    public void startSchedule() {
        List<Exam> exams = examRepository.findAllCurrentAndUpcomingExams().stream().filter(Exam::isMonitoring).toList();
        logger.info("Found {} exams that are not yet ended or are scheduled to start in the future", exams.size());
        for (Exam exam : exams) {
            cancelExamActivitySave(exam.getId());
            if (exam.isMonitoring()) {
                scheduleExamActivitySave(exam.getId());
            }
        }
    }

    /**
     * Stops the exam activity save for all exams.
     */
    public void stopSchedule() {
        for (Cache cachedExamMonitoring : examCache.getAllCaches()) {
            if (((ExamMonitoringCache) cachedExamMonitoring).getExamActivitySaveHandler() != null) {
                cancelExamActivitySave(((ExamMonitoringCache) cachedExamMonitoring).getExamId());
            }
        }
        threadPoolTaskScheduler.shutdown();
        threadPoolTaskScheduler.destroy();
    }

    /**
     * Schedules the exam activity save task for a specific exam.
     *
     * @param examId specific exam
     */
    public void scheduleExamActivitySave(final long examId) {
        this.cancelExamActivitySave(examId);
        // reload from database to make sure there are no proxy objects
        final var exam = examRepository.findByIdElseThrow(examId);
        try {
            // 3 am after the end of the exam
            ZonedDateTime endOfExamDay = exam.getEndDate().toLocalDate().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault());

            var saveTimeDelay = Duration.between(ZonedDateTime.now(), endOfExamDay).toSeconds() + 3600 * 3;

            var scheduledFuture = threadPoolTaskScheduler.schedule(new ExamActivitySaveTask(examId), saveTimeDelay, TimeUnit.MILLISECONDS);
            // save scheduled future in HashMap
            examCache.performCacheWrite(examId, examMonitoringCache -> {
                ((ExamMonitoringCache) examMonitoringCache).setExamActivitySaveHandler(List.of(scheduledFuture.getHandler()));
                return examMonitoringCache;
            });
        }
        catch (@SuppressWarnings("unused") DuplicateTaskException e) {
            logger.debug("Exam {} monitoring save task already registered", examId);
            // this is expected if we run on multiple nodes
        }
    }

    /**
     * Cancels the exam activity save task for a specific exam.
     *
     * @param examId specific exam
     */
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
                    logger.info("Stop scheduled exam activity save for exam {} was successful: {}", examId, cancelSuccess);
                }
            }
            catch (@SuppressWarnings("unused") StaleTaskException e) {
                logger.info("Stop scheduled exam activity save for exam {} already disposed/cancelled", examId);
                // has already been disposed (sadly there is no method to check that)
            }
        });
        examCache.performCacheWriteIfPresent(examId, cachedMonitoring -> {
            ((ExamMonitoringCache) cachedMonitoring).setExamActivitySaveHandler(ExamMonitoringCache.getEmptyExamActivitySaveHandler());
            return cachedMonitoring;
        });
        // We want to clear the activities from the cache
        executeExamActivitySaveTask(examId);
    }

    /**
     * Saves the exam activities and actions into the database (after the end of the exam) for a specific exam.
     *
     * @param examId specific exam
     */
    public void executeExamActivitySaveTask(Long examId) {
        examCache.performCacheWriteIfPresent(examId, examMonitoringCache -> {
            ((ExamMonitoringCache) examMonitoringCache).getExamActivitySaveHandler().clear();
            logger.debug("Removed exam {} monitoring save tasks", examId);

            // Save actions in database
            examActivityService.saveAll(((ExamMonitoringCache) examMonitoringCache).getActivities().values());
            ((ExamMonitoringCache) examMonitoringCache).getActivities().clear();
            return examMonitoringCache;
        });
    }

    /**
     * Clears all cached exam monitoring data.
     * <p>
     * This will cause cached exam activity to be lost.
     */
    public void clearAllExamMonitoringData() {
        examCache.clear();
    }

    /**
     * Returns the exam activity for a specific exam and student exam.
     *
     * @param examId        id of the current exam
     * @param studentExamId id of the student exam
     * @return ExamActivity performed by the student
     */
    public ExamActivity getExamActivityFromCache(Long examId, Long studentExamId) {
        return ((ExamMonitoringCache) examCache.getReadCacheFor(examId)).getActivities().getOrDefault(studentExamId, null);
    }
}
