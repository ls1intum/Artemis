package de.tum.cit.aet.artemis.versioning.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.versioning.service.ExerciseVersionInterceptor;

/**
 * Configuration for integrating the ExerciseVersionInterceptor with Hibernate.
 * This configuration registers the interceptor with Hibernate's session factory
 * so it can intercept Exercise entity save and update operations.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
public class ExerciseVersionHibernateConfiguration {

    /**
     * Configures Hibernate to use the ExerciseVersionInterceptor.
     * This interceptor will automatically create exercise versions when Exercise entities are saved or updated.
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(ExerciseVersionInterceptor exerciseVersionInterceptor) {
        return hibernateProperties -> hibernateProperties.put(AvailableSettings.INTERCEPTOR, exerciseVersionInterceptor);
    }
}
