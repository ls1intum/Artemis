package de.tum.cit.aet.artemis.videosource.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastApprovalAttemptRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;
import de.tum.cit.aet.artemis.videosource.service.GocastAuthenticationService;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingService;
import de.tum.cit.aet.artemis.videosource.service.GocastConnectorService;

class GocastConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(EnabledGocastTestConfiguration.class).withPropertyValues(
            "artemis.tum-live.integration-enabled=true", "artemis.tum-live.api-base-url=http://localhost:18081/api/v2", "artemis.tum-live.web-base-url=http://localhost:18081",
            "artemis.tum-live.service-account-email=service@example.org", "artemis.tum-live.service-account-password=test-password", "server.url=http://localhost:8080");

    @Test
    void enabledConfigurationResolvesTheRealConnectorAndBindingService() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GocastConnectorService.class).hasSingleBean(GocastBindingService.class);
            assertThat(context.getBean(GocastConnectorService.class)).isInstanceOf(GocastConnectorService.class);
            assertThat(context.getBean(GocastBindingService.class)).isInstanceOf(GocastBindingService.class);
        });
    }

    @Test
    void acceptsHttpsAndExplicitLocalHttpBaseUrls() {
        assertThat(GocastConfiguration.validateBaseUrl("https://live.example.edu/api/v2", "URL")).hasScheme("https").hasHost("live.example.edu");
        assertThat(GocastConfiguration.validateBaseUrl("http://gocast.localhost:8081/api/v2", "URL")).hasScheme("http").hasHost("gocast.localhost");
        assertThat(GocastConfiguration.validateBaseUrl("http://service.orb.local/api/v2", "URL")).hasHost("service.orb.local");
    }

    @Test
    void rejectsRemoteHttpCredentialsQueriesFragmentsAndEncodedPaths() {
        assertInvalid("http://live.example.edu/api/v2");
        assertInvalid("https://user:password@live.example.edu/api/v2");
        assertInvalid("https://live.example.edu/api/v2?token=secret");
        assertInvalid("https://live.example.edu/api/v2#fragment");
        assertInvalid("https://live.example.edu/api/%2e%2e/v2");
        assertInvalid("/api/v2");
    }

    private static void assertInvalid(String value) {
        assertThatThrownBy(() -> GocastConfiguration.validateBaseUrl(value, "URL")).isInstanceOf(IllegalArgumentException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ GocastConfiguration.class, GocastAuthenticationService.class, GocastConnectorService.class, GocastConnectionRepository.class, GocastBindingService.class })
    static class EnabledGocastTestConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        CourseRepository courseRepository() {
            return mock(CourseRepository.class);
        }

        @Bean
        GocastCourseBindingRepository bindingRepository() {
            return mock(GocastCourseBindingRepository.class);
        }

        @Bean
        GocastApprovalAttemptRepository attemptRepository() {
            return mock(GocastApprovalAttemptRepository.class);
        }
    }
}
