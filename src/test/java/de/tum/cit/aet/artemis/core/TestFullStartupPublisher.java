package de.tum.cit.aet.artemis.core;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_BUILDAGENT;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.FullStartupEvent;

/**
 * We need this component to publish the FullStartupEvent in our tests as we publish it in the main method that is not run for the tests.
 */
@Component
@Profile({ SPRING_PROFILE_TEST, PROFILE_TEST_BUILDAGENT })
@Lazy
public class TestFullStartupPublisher {

    private final ApplicationContext applicationContext;

    public TestFullStartupPublisher(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        applicationContext.publishEvent(new FullStartupEvent());
    }
}
