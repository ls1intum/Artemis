package de.tum.cit.aet.artemis.course.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;

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

/** Home's optional welcome provider, built from the configured LLM presets with its own limits and workers. */
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
     * Registers the assigned Home presets for validation when Home AI is enabled. A static method without the properties bean keeps a disabled Home out of the startup beans.
     *
     * @param environment the environment
     * @return Home's preset assignments
     */
    @Bean
    @Conditional(HomeAiEnabled.class)
    static LlmPresetAssignments courseHomeLlmPresetAssignments(Environment environment) {
        var properties = Binder.get(environment).bind(CourseHomeAiProperties.PREFIX, CourseHomeAiProperties.class).get();
        List<LlmPresetAssignment> assignments = new ArrayList<>();
        if (properties.localLlmPreset() != null || (properties.cloudLlmPreset() == null && properties.llmPreset() == null)) {
            assignments.add(LlmPresetAssignment.ofSingle(CourseHomeAiProperties.LOCAL_PRESET_KEY, properties.localLlmPreset(), LlmPresetAssignment.Usage.CHAT));
        }
        if (properties.cloudLlmPreset() != null) {
            assignments.add(LlmPresetAssignment.ofSingle(CourseHomeAiProperties.CLOUD_PRESET_KEY, properties.cloudLlmPreset(), LlmPresetAssignment.Usage.CHAT));
        }
        if (properties.llmPreset() != null) {
            assignments.add(LlmPresetAssignment.ofSingle(CourseHomeAiProperties.PRESET_KEY, properties.llmPreset(), LlmPresetAssignment.Usage.CHAT));
        }
        return () -> List.copyOf(assignments);
    }

    /** Binds the flag as Spring binds the properties (true, on, yes, ...), so the assignment exists exactly when Home counts as enabled. */
    static class HomeAiEnabled implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return Binder.get(context.getEnvironment()).bind("artemis.course-home.ai.enabled", Boolean.class).orElse(false);
        }
    }

    static class ProviderConfigured implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return Binder.get(context.getEnvironment()).bind(CourseHomeAiProperties.PREFIX, CourseHomeAiProperties.class).map(CourseHomeAiProperties::isConfigured).orElse(false);
        }
    }
}
