package de.tum.in.www1.artemis.service.scheduled;

import java.time.Duration;
import java.time.LocalDateTime;

public class TaskConfiguration {

    private final String name;

    private final String cronExpression;

    private final Runnable task;

    private final Duration interval;

    private LocalDateTime nextExecutionTime;

    // Constructor for cron-based tasks
    public TaskConfiguration(String name, String cronExpression, Runnable task) {
        this.name = name;
        this.cronExpression = cronExpression;
        this.task = task;
        this.interval = null;
        this.nextExecutionTime = null;
    }

    // Constructor for interval-based tasks
    public TaskConfiguration(String name, Duration interval, Runnable task, LocalDateTime nextExecutionTime) {
        this.name = name;
        this.cronExpression = null;
        this.task = task;
        this.interval = interval;
        this.nextExecutionTime = nextExecutionTime;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public Runnable getTask() {
        return task;
    }

    public Duration getInterval() {
        return interval;
    }

    public LocalDateTime getNextExecutionTime() {
        return nextExecutionTime;
    }

    public void setNextExecutionTime(LocalDateTime nextExecutionTime) {
        this.nextExecutionTime = nextExecutionTime;
    }
}
