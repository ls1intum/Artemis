package de.tum.in.www1.artemis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class TaskSchedulingConfiguration {

    private final Logger log = LoggerFactory.getLogger(TaskSchedulingConfiguration.class);

    @Bean(name = "taskScheduler")
    public TaskScheduler taskScheduler() {
        log.debug("Creating Task Scheduler ");
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        return scheduler;
    }
}
