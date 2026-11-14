package de.tum.cit.aet.artemis.course.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.course.service.home.CourseHomeAiClient;
import de.tum.cit.aet.artemis.course.service.home.SpringAiCourseHomeClient;
import io.micrometer.core.instrument.MeterRegistry;

/** Isolates this optional provider from global Spring AI selection and application executors. */
@Lazy
@Configuration(proxyBeanMethods = false)
@Profile(PROFILE_CORE)
@EnableConfigurationProperties(CourseHomeAiProperties.class)
public class CourseHomeAiConfiguration {

    @Bean(destroyMethod = "close")
    @Conditional(ProviderConfigured.class)
    CourseHomeAiClient courseHomeAiClient(CourseHomeAiProperties properties, MeterRegistry meters) {
        return new SpringAiCourseHomeClient(properties, meters);
    }

    static class ProviderConfigured implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return Binder.get(context.getEnvironment()).bind("artemis.course-home.ai", CourseHomeAiProperties.class).map(CourseHomeAiProperties::isConfigured).orElse(false);
        }
    }
}
