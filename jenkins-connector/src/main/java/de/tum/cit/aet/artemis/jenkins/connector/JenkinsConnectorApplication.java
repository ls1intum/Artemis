package de.tum.cit.aet.artemis.jenkins.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for the Jenkins CI Connector microservice.
 * This microservice handles all Jenkins-specific CI operations for Artemis
 * in a stateless manner, managing Jenkins build plans, triggers, and state internally.
 */
@SpringBootApplication
public class JenkinsConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(JenkinsConnectorApplication.class, args);
    }
}