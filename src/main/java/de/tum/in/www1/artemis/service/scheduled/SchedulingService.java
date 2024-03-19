package de.tum.in.www1.artemis.service.scheduled;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

@Service
public class SchedulingService {

    // Just an example for illustration purpose
    @Value("${artemis.continuous-integration.image-cleanup.cleanup-schedule-time:0 0 3 * * *})")
    private String dynamicCron; // Example property loaded from application.yml

    private final List<TaskConfiguration> tasks = new ArrayList<>();

    public SchedulingService() {
        // Scheduled interval task - runs every 30 seconds
        tasks.add(new TaskConfiguration("IntervalTask", Duration.ofSeconds(30), () -> System.out.println("Running Interval Task"), LocalDateTime.now()));

        // Add dynamic cron task (cron expression from application.yml)
        tasks.add(new TaskConfiguration("DynamicCronTask", dynamicCron, () -> System.out.println("Running Dynamic Cron Task")));
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
