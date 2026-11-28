package de.tum.cit.aet.artemis.videosource.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Lazy;

class GocastInfoContributorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(InfoContributorTestConfiguration.class).withPropertyValues(
            "spring.profiles.active=core", "artemis.tum-live.integration-enabled=true", "artemis.tum-live.api-base-url=https://live.example.edu/api/v2",
            "artemis.tum-live.web-base-url=https://live.example.edu", "artemis.tum-live.api-key=server-side-secret", "server.url=https://artemis.example.edu");

    @Test
    void publishesEnabledWhenTheCompleteIntegrationConfigurationIsAvailable() {
        contextRunner.run(context -> {
            Info.Builder builder = new Info.Builder();
            context.getBeansOfType(InfoContributor.class).values().forEach(contributor -> contributor.contribute(builder));

            assertThat(builder.build().getDetails()).hasSize(1).containsEntry("gocastEnabled", true);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "artemis.tum-live.integration-enabled=false", "artemis.tum-live.api-base-url=", "artemis.tum-live.web-base-url=", "artemis.tum-live.api-key=",
            "server.url=" })
    void publishesDisabledWhenAnyRequiredConfigurationIsUnavailable(String unavailableProperty) {
        contextRunner.withPropertyValues(unavailableProperty).run(context -> {
            Info.Builder builder = new Info.Builder();
            context.getBeansOfType(InfoContributor.class).values().forEach(contributor -> contributor.contribute(builder));

            assertThat(builder.build().getDetails()).hasSize(1).containsEntry("gocastEnabled", false);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Lazy
    @ComponentScan(basePackageClasses = GocastEnabled.class, useDefaultFilters = false, includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = InfoContributor.class))
    static class InfoContributorTestConfiguration {
    }
}
