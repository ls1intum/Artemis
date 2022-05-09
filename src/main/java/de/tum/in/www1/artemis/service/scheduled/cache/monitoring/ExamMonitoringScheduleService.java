package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;
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
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.exam.monitoring.ExamActivityService;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;

@Service
public class ExamMonitoringScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ExamMonitoringScheduleService.class);

    private final ExamCache examCache;

    private final IScheduledExecutorService threadPoolTaskScheduler;

    private final ExamService examService;

    private final StudentExamRepository studentExamRepository;

    private final ExamActivityService examActivityService;

    public ExamMonitoringScheduleService(HazelcastInstance hazelcastInstance, ExamService examService, StudentExamRepository studentExamRepository,
            ExamActivityService examActivityService) {
        this.threadPoolTaskScheduler = hazelcastInstance.getScheduledExecutorService(Constants.HAZELCAST_MONITORING_SCHEDULER);
        this.examCache = new ExamCache(hazelcastInstance);
        this.examService = examService;
        this.studentExamRepository = studentExamRepository;
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

    public void updateExamActivity(Long examId, long studentExamId, ExamActivity examActivity) {
        if (examActivity != null) {
            ((ExamMonitoringCache) examCache.getTransientWriteCacheFor(examId)).getActivities().put(studentExamId, examActivity);
        }
    }

    public void addExamAction(Long examId, long studentExamId, ExamAction examAction) {
        if (examAction != null) {
            ExamActivity examActivity = ((ExamMonitoringCache) examCache.getTransientWriteCacheFor(examId)).getActivities().get(studentExamId);

            if (examActivity == null) {
                examActivity = new ExamActivity();
                examActivity.setStudentExamId(studentExamId);
                examActivity = examActivityService.save(examActivity);
            }

            examAction.setExamActivity(examActivity);
            examActivity.addExamAction(examAction);
            updateExamActivity(examId, studentExamId, examActivity);
        }
    }

    public void startSchedule() {
        List<Exam> exams = examService.findAllCurrentAndUpcomingExams().stream().filter(Exam::isMonitoring).toList();
        log.info("Found {} exams that are not yet ended or are scheduled to start in the future", exams.size());
        for (Exam exam : exams) {
            cancelExamActivitySave(exam.getId());
            if (exam.isMonitoring()) {
                scheduleExamActivitySave(exam.getId());
            }
        }
    }

    public void stopSchedule() {
        for (Cache cachedExamMonitoring : examCache.getAllCaches()) {
            if (((ExamMonitoringCache) cachedExamMonitoring).getExamActivitySaveHandler() != null)
                cancelExamActivitySave(((ExamMonitoringCache) cachedExamMonitoring).getExamId());
        }
        threadPoolTaskScheduler.shutdown();
        threadPoolTaskScheduler.destroy();
    }

    public void scheduleExamActivitySave(final long examId) {
        this.cancelExamActivitySave(examId);
        // reload from database to make sure there are no proxy objects
        final var exam = examService.findByIdOrElseThrow(examId);
        try {
            // Check if there is a student exam with longer working time
            var studentExams = studentExamRepository.findByExamId(examId);
            var studentExam = studentExams.stream().max(Comparator.comparingLong(StudentExam::getWorkingTime));
            var duration = Duration.between(ZonedDateTime.now(), exam.getEndDate());
            long delay = duration.toMillis();
            if (studentExam.isPresent()) {
                var durationWithTimeExtension = Duration.between(ZonedDateTime.now(), exam.getStartDate()).toSeconds() + studentExam.get().getWorkingTime();
                if (duration.toSeconds() < durationWithTimeExtension) {
                    delay = durationWithTimeExtension * 1000;
                }
            }

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
