package de.tum.in.www1.artemis.service.scheduled;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.plagiarism.ContinuousPlagiarismControlService;

@Service
@Profile("scheduling")
public class SchedulingService {

    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);

    /**
     * Daily triggers plagiarism checks as a part of continuous plagiarism control.
     */
    @Value("${artemis.continuous-integration.image-cleanup.cleanup-schedule-time:0 0 3 * * *})")
    private String continuousPlagiarismCleanUpCron; // Example property loaded from application.yml

    /**
     * Every minute, query for modified results and schedule a task to update the participant scores.
     * We schedule all results that were created/updated since the last run of the cron job.
     * Additionally, we schedule all participant scores that are outdated/invalid.
     */
    private final String participantScoreCron = "0 * * * * *";

    private final List<TaskConfiguration> tasks = new ArrayList<>();

    public SchedulingService(ContinuousPlagiarismControlService continuousPlagiarismControlService, ParticipantScoreScheduleService participantScoreScheduleService) {
        // Example scheduled interval task - runs every 30 seconds
        tasks.add(new TaskConfiguration("IntervalTask", Duration.ofSeconds(30), () -> log.info("Running Interval Task"), LocalDateTime.now()));

        // Examples for tasks with cron expressions
        tasks.add(new TaskConfiguration("ContinuousPlagiarismCleanupTask", continuousPlagiarismCleanUpCron, continuousPlagiarismControlService::executeChecks));
        tasks.add(new TaskConfiguration("ParticipantScoreTask", participantScoreCron, participantScoreScheduleService::scheduleTasks));
    }

    @Scheduled(fixedRate = 1000)
    public void scheduleTasks() {
        LocalDateTime now = LocalDateTime.now();

        tasks.forEach(task -> {
            if (task.getCronExpression() != null) {
                CronExpression cronExpression = CronExpression.parse(task.getCronExpression());
                LocalDateTime nextRunTime = cronExpression.next(now.minusSeconds(1));
                if (nextRunTime != null && (nextRunTime.isBefore(now) || nextRunTime.isEqual(now))) {
                    new Thread(task.getTask()).start();
                }
            }
            else if (task.getInterval() != null && task.getNextExecutionTime() != null && (task.getNextExecutionTime().isBefore(now) || task.getNextExecutionTime().isEqual(now))) {
                new Thread(task.getTask()).start();
                // Update next execution time based on the interval
                task.setNextExecutionTime(now.plus(task.getInterval()));
            }
        });
    }
}
