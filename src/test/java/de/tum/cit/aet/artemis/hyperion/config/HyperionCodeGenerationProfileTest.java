package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HYPERION_ENABLED_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class HyperionCodeGenerationProfileTest {

    @ParameterizedTest
    @CsvSource({ "core, false", "'core,localvc', false", "'core,localci', true", "'core,jenkins', true" })
    void repositoryGenerationRequiresACiProviderWhileQuizAuthoringIsAvailableOnCore(String profiles, boolean hasCi) {
        var environment = new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, "true");
        environment.setActiveProfiles(profiles.split(","));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.scan("de.tum.cit.aet.artemis.hyperion.web", "de.tum.cit.aet.artemis.hyperion.service.codegeneration");
            context.refresh();

            assertThat(context.containsBeanDefinition("hyperionCodeGenerationResource")).isEqualTo(hasCi);
            assertThat(context.containsBeanDefinition("hyperionCodeGenerationJobService")).isEqualTo(hasCi);
            assertThat(context.containsBeanDefinition("hyperionCodeGenerationTaskService")).isEqualTo(hasCi);
            assertThat(context.containsBeanDefinition("hyperionCodeGenerationExecutionService")).isEqualTo(hasCi);
            assertThat(context.containsBeanDefinition("hyperionQuizQuestionGenerationResource")).isTrue();
        }
    }
}
