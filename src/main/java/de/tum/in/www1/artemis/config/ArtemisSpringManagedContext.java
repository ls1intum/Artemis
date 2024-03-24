package de.tum.in.www1.artemis.config;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.hazelcast.spring.context.SpringManagedContext;

/**
 * This class only exists to improve logging in case of slow Hazelcast operations
 */
public class ArtemisSpringManagedContext extends SpringManagedContext {

    private static final Logger log = LoggerFactory.getLogger(ArtemisSpringManagedContext.class);

    private final Environment env;

    public ArtemisSpringManagedContext(ApplicationContext applicationContext, Environment env) {
        super(applicationContext);
        this.env = env;
    }

    @Override
    public Object initialize(Object obj) {
        // do not log during server tests to avoid issues
        if (!env.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            String type = obj != null ? obj.getClass().getName() : "null";
            log.debug("Initialize obj {} of type {}", obj, type);
        }
        return super.initialize(obj);
    }
}
