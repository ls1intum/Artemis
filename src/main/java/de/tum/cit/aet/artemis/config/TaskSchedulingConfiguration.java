package de.tum.cit.aet.artemis.config;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Profile(PROFILE_CORE)
@Configuration
@EnableScheduling
public class TaskSchedulingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TaskSchedulingConfiguration.class);

    @Bean(name = "taskScheduler")
    public TaskScheduler taskScheduler() {
        log.debug("Creating Task Scheduler ");
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        return scheduler;
    }
}
