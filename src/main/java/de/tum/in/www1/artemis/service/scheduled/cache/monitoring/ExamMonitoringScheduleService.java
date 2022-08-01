package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.scheduledexecutor.DuplicateTaskException;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;
import tech.jhipster.config.JHipsterConstants;

/**
 * For all {@link Exam}s where monitoring is enabled, the scheduling service schedules the cache reset after another 30 minutes {@link Constants}
 * after the last {@link StudentExam} is completed. In addition, it takes care of adding new {@link ExamAction}s per {@link ExamActivity} and {@link Exam}.
 * The service works as an interface for the distributed hazelcast exam monitoring cache;
 */
@Service
public class ExamMonitoringScheduleService {

    private final Logger logger = LoggerFactory.getLogger(ExamMonitoringScheduleService.class);

    private final ExamCache examCache;

    private final TaskScheduler scheduler;

    private final Map<Long, ScheduledFuture<?>> scheduledExamMonitoring = new HashMap<>();

    private final Environment env;

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    private final WebsocketMessagingService messagingService;

    public ExamMonitoringScheduleService(HazelcastInstance hazelcastInstance, @Qualifier("taskScheduler") TaskScheduler scheduler, Environment env, ExamRepository examRepository,
            StudentExamRepository studentExamRepository, WebsocketMessagingService messagingService) {
        this.examCache = new ExamCache(hazelcastInstance);
        this.scheduler = scheduler;
        this.env = env;
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.messagingService = messagingService;
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

    /**
     * This method schedules all exam activity save tasks after a server (re-)start.
     */
    @PostConstruct
    public void startSchedule() {
        try {
            Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
            if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) {
                // only execute this on production server, i.e. when the prod profile is active
                // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
                return;
            }

            if (!activeProfiles.contains("scheduling")) {
                // only execute this on server with active scheduling profile
                return;
            }

            SecurityUtils.setAuthorizationObject();

            List<Exam> exams = examRepository.findAllCurrentAndUpcomingExams().stream().filter(Exam::isMonitoring).toList();
            logger.info("Found {} exams that are not yet ended or are scheduled to start in the future", exams.size());
            for (Exam exam : exams) {
                scheduleExamMonitoringTask(exam.getId());
            }
        }
        catch (Exception e) {
            logger.error("Failed to start ExamMonitoringScheduleService", e);
        }
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
                    // Since we don't store the activity in the database at the moment, we reuse the student exam id
                    activity.setId(studentExamId);
                    // TODO: Save Activity
                }

                // Connect action and activity
                action.setExamActivityId(activity.getId());

                activity.addExamAction(action);

                return activity;
            });

            // send message to subscribers
            messagingService.sendMessage("/topic/exams/" + examId + "/exam-live-statistics-action", action);
        }
    }

    /**
     * Used to update the exam live statistics during the exam.
     *
     * @param examId identifies the cache
     * @param examLiveStatistics status of the exam live statistics
     */
    public void notifyExamLiveStatisticsUpdate(Long examId, boolean examLiveStatistics) {
        messagingService.sendMessage("/topic/exams/" + examId + "/exam-live-statistics-update", examLiveStatistics);
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
            examActions.addAll(examActivity.getExamActions());
        }

        return examActions;
    }

    /**
     * Stops the exam activity save for all exams.
     */
    public void stopSchedule() {
        for (Cache cachedExamMonitoring : examCache.getAllCaches()) {
            cancelExamMonitoringTask(((ExamMonitoringCache) cachedExamMonitoring).getExamId());
        }
    }

    /**
     * Schedules the exam activity save task for a specific exam.
     *
     * @param examId specific exam
     */
    public void scheduleExamMonitoringTask(final long examId) {
        this.cancelExamMonitoringTask(examId);
        // reload from database to make sure there are no proxy objects
        final var exam = examRepository.findByIdElseThrow(examId);
        try {
            if (!exam.isMonitoring()) {
                return;
            }
            // Check if there is a student exam with longer working time
            var studentExams = studentExamRepository.findByExamId(examId);
            var studentExam = studentExams.stream().max(Comparator.comparingLong(StudentExam::getWorkingTime));
            var schedulingTime = exam.getEndDate();
            if (studentExam.isPresent()) {
                schedulingTime = exam.getStartDate().plus(studentExam.get().getWorkingTime(), ChronoUnit.SECONDS);
            }

            schedulingTime = schedulingTime.plus(Constants.MONITORING_CACHE_RESET_DELAY, ChronoUnit.SECONDS);

            var future = scheduler.schedule(() -> this.executeExamActivitySaveTask(examId), schedulingTime.toInstant());
            scheduledExamMonitoring.put(examId, future);
            logger.info("Schedule task for Exam Monitoring ({}) at {}.", examId, schedulingTime);
        }
        catch (@SuppressWarnings("unused") DuplicateTaskException e) {
            logger.info("Exam {} monitoring save task already registered", examId);
            // this is expected if we run on multiple nodes
        }
    }

    /**
     * Cancels the exam activity save task for a specific exam.
     *
     * @param examId specific exam
     */
    public void cancelExamMonitoringTask(final long examId) {
        ScheduledFuture<?> future = scheduledExamMonitoring.get(examId);
        if (future != null) {
            logger.info("Cancelling scheduled task for Exam Monitoring ({}).", examId);
            future.cancel(true);
            scheduledExamMonitoring.remove(examId);
        }
        // We want to clear the activities from the cache
        executeExamActivitySaveTask(examId);
    }

    /**
     * Saves the exam activities and actions into the database (after the end of the exam) for a specific exam.
     *
     * @param examId specific exam
     */
    public void executeExamActivitySaveTask(Long examId) {
        // TODO: Save actions in future PR in database
        // examActivityService.saveAll(cache.getActivities().values());
        examCache.performCacheWriteIfPresent(examId, cachedMonitoring -> {
            ((ExamMonitoringCache) cachedMonitoring).getActivities().clear();
            return cachedMonitoring;
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
