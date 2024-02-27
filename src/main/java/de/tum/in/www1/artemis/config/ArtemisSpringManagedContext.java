package de.tum.in.www1.artemis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.hazelcast.spring.context.SpringManagedContext;

/**
 * This class only exists to improve logging in case of slow Hazelcast operations
 */
public class ArtemisSpringManagedContext extends SpringManagedContext {

    private static final Logger log = LoggerFactory.getLogger(ArtemisSpringManagedContext.class);

    public ArtemisSpringManagedContext(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @Override
    public Object initialize(Object obj) {
        String type = obj != null ? obj.getClass().getName() : "null";
        log.info("Initialize obj {} of type {}", obj, type);
        return super.initialize(obj);
    }
}
