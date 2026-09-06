package de.tum.cit.aet.artemis.videosource.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastApprovalAttemptRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingService;
import de.tum.cit.aet.artemis.videosource.service.GocastConnectorService;

class GocastConfigurationTest {

    private static final String API_KEY = "fixture-api-key-that-must-stay-server-side";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(EnabledGocastTestConfiguration.class).withPropertyValues(
            "spring.profiles.active=core", "artemis.tum-live.integration-enabled=true", "artemis.tum-live.api-base-url=http://localhost:18081/api/v2",
            "artemis.tum-live.web-base-url=http://localhost:18081", "artemis.tum-live.api-key=" + API_KEY, "server.url=http://localhost:8080");

    @Test
    void enabledConfigurationSendsTheApiKeyOnTheFirstIntegrationOperation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient.Builder configuredClone = spy(builder.clone());
        doReturn(configuredClone).when(configuredClone).requestFactory(any());
        RestClient.Builder sourceBuilder = spy(builder);
        doReturn(configuredClone).when(sourceBuilder).clone();
        server.expect(requestTo("http://localhost:18081/api/v2/integration")).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY)).andRespond(withSuccess(
                "{\"id\":17,\"name\":\"Artemis\",\"returnUrl\":\"http://localhost:8080/api/videosource/public/gocast/approval/callback\"}", MediaType.APPLICATION_JSON));

        contextRunner.withBean(RestClient.Builder.class, () -> sourceBuilder).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GocastConnectorService.class).hasSingleBean(GocastBindingService.class);
            assertThat(context.getBean(GocastConnectorService.class).getIntegration().id()).isEqualTo(17);
            assertThat(context.getBean(GocastBindingService.class)).isInstanceOf(GocastBindingService.class);
            server.verify();
        });
    }

    @Test
    void missingOrBlankApiKeyDisablesTheIntegration() {
        configuredContext().withBean(RestClient.Builder.class, RestClient::builder).run(context -> {
            assertThat(context).doesNotHaveBean(GocastConnectorService.class).doesNotHaveBean(GocastBindingService.class);
        });
        configuredContext().withPropertyValues("artemis.tum-live.api-key=   ").withBean(RestClient.Builder.class, RestClient::builder).run(context -> {
            assertThat(context).doesNotHaveBean(GocastConnectorService.class).doesNotHaveBean(GocastBindingService.class);
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

    private static ApplicationContextRunner configuredContext() {
        return new ApplicationContextRunner().withUserConfiguration(EnabledGocastTestConfiguration.class).withPropertyValues("spring.profiles.active=core",
                "artemis.tum-live.integration-enabled=true", "artemis.tum-live.api-base-url=http://localhost:18081/api/v2", "artemis.tum-live.web-base-url=http://localhost:18081",
                "server.url=http://localhost:8080");
    }

    @Configuration(proxyBeanMethods = false)
    @Lazy
    @Import({ GocastConfiguration.class, GocastConnectorService.class, GocastConnectionRepository.class, GocastBindingService.class })
    static class EnabledGocastTestConfiguration {

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
