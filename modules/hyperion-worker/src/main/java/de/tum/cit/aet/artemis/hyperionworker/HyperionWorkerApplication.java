package de.tum.cit.aet.artemis.hyperionworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import de.tum.cit.aet.artemis.hyperionworker.config.WorkerSettings;

/** Outbound-only generation supervisor; never scans or depends on the Artemis server. */
@SpringBootApplication
@EnableConfigurationProperties(WorkerSettings.class)
public class HyperionWorkerApplication {

    /**
     * Starts a worker without an incoming HTTP server, regardless of Spring configuration overrides.
     *
     * @param args Spring Boot command-line configuration
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(HyperionWorkerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setApplicationContextFactory(type -> {
            if (type != WebApplicationType.NONE) {
                throw new IllegalArgumentException("The generation worker cannot run an incoming web server");
            }
            return new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        });
        application.run(args);
    }
}
