package de.tum.cit.aet.artemis.course.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignment;
import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignments;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeAiClient;
import de.tum.cit.aet.artemis.course.service.home.SpringAiCourseHomeClient;
import io.micrometer.core.instrument.MeterRegistry;

/** Home's optional welcome provider, built from its LLM preset with its own limits and workers. */
@Lazy
@Configuration(proxyBeanMethods = false)
@Profile(PROFILE_CORE)
@EnableConfigurationProperties(CourseHomeAiProperties.class)
public class CourseHomeAiConfiguration {

    @Bean(destroyMethod = "close")
    @Conditional(ProviderConfigured.class)
    CourseHomeAiClient courseHomeAiClient(CourseHomeAiProperties properties, LlmClientService llmClientService, MeterRegistry meters) {
        return new SpringAiCourseHomeClient(properties, llmClientService, meters);
    }

    /**
     * Requires Home's preset once Home AI is enabled. A static method without the properties bean keeps a disabled Home out of the startup beans.
     *
     * @param environment the environment
     * @return Home's preset assignment
     */
    @Bean
    @ConditionalOnProperty(name = "artemis.course-home.ai.enabled", havingValue = "true")
    static LlmPresetAssignments courseHomeLlmPresetAssignments(Environment environment) {
        String preset = Binder.get(environment).bind(CourseHomeAiProperties.PRESET_KEY, String.class).orElse(null);
        return () -> List.of(LlmPresetAssignment.ofSingle(CourseHomeAiProperties.PRESET_KEY, preset, LlmPresetAssignment.Usage.CHAT));
    }

    static class ProviderConfigured implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return Binder.get(context.getEnvironment()).bind("artemis.course-home.ai", CourseHomeAiProperties.class).map(CourseHomeAiProperties::isConfigured).orElse(false);
        }
    }
}
