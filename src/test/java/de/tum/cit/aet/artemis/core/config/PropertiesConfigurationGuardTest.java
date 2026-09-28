package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

class PropertiesConfigurationGuardTest {

    private PropertiesConfigurationGuard guard(String operator, String admin, String university) {
        return guard(environment("prod", "core"), operator, admin, university);
    }

    private PropertiesConfigurationGuard guard(MockEnvironment environment, String operator, String admin, String university) {
        setIfPresent(environment, "info.operatorName", operator);
        setIfPresent(environment, "info.operatorAdminName", admin);
        setIfPresent(environment, "info.universityName", university);
        return new PropertiesConfigurationGuard(environment);
    }

    private static MockEnvironment environment(String... profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private static void setIfPresent(MockEnvironment environment, String key, String value) {
        if (value != null) {
            environment.setProperty(key, value);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  ", "Admin", "Some Artemis Operator", "Your University", "<university>", "TODO", "Example University", "Max Mustermann", "Anonymous University",
            "anonymous university admin", "Example University IT Services", "N/A", "None" })
    void rejectsMissingAndPlaceholderMetadata(String invalid) {
        assertThatIllegalArgumentException().isThrownBy(guard(invalid, "Erika Muster", "Technical University of Munich")::afterPropertiesSet)
                .withMessageContaining("info.operatorName (INFO_OPERATORNAME)");
        assertThatIllegalArgumentException().isThrownBy(guard("AET", invalid, "Technical University of Munich")::afterPropertiesSet)
                .withMessageContaining("info.operatorAdminName (INFO_OPERATORADMINNAME)");
        assertThatIllegalArgumentException().isThrownBy(guard("AET", "Erika Muster", invalid)::afterPropertiesSet)
                .withMessageContaining("info.universityName (INFO_UNIVERSITYNAME)");
    }

    @Test
    void allowsValidMetadata() {
        assertThatNoException().isThrownBy(guard("AET", "Erika Muster", "Technical University of Munich")::afterPropertiesSet);
    }

    @ParameterizedTest
    @ValueSource(strings = { "dev,core", "core", "test,core" })
    void doesNotRequireMetadataOutsideProduction(String profiles) {
        assertThatNoException().isThrownBy(guard(environment(profiles.split(",")), null, null, null)::afterPropertiesSet);
    }

    @Test
    void doesNotRequireMetadataOnTestServers() {
        var environment = environment("prod", "core");
        environment.setProperty("info.testServer", "true");
        assertThatNoException().isThrownBy(guard(environment, null, "Admin", null)::afterPropertiesSet);
    }

    @ParameterizedTest
    @ValueSource(strings = { "dev.env", "dev-local-vc-local-ci.env", "prod-multinode.env", "migration-check.env", "playwright.env", "prod-multinode-fast.env" })
    void allowsShippedLocalDeploymentMetadata(String file) throws IOException {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("docker/artemis/config", file))) {
            properties.load(reader);
        }
        assertThatNoException().isThrownBy(
                guard(envValue(properties, "INFO_OPERATORNAME"), envValue(properties, "INFO_OPERATORADMINNAME"), envValue(properties, "INFO_UNIVERSITYNAME"))::afterPropertiesSet);
    }

    private String envValue(Properties properties, String name) {
        String value = properties.getProperty(name);
        return value == null ? null : value.replace("\"", "");
    }

    @ParameterizedTest
    @ValueSource(strings = { "values.yaml", "values-cluster-example.yaml" })
    void rejectsUnconfiguredProductionChartMetadata(String file) throws IOException {
        var values = new YamlPropertySourceLoader().load("chart", new FileSystemResource("helm/artemis/" + file)).getFirst();
        assertThatIllegalArgumentException()
                .isThrownBy(guard((String) values.getProperty("artemis.config.operator.name"), (String) values.getProperty("artemis.config.operator.adminName"),
                        (String) values.getProperty("artemis.config.operator.universityName"))::afterPropertiesSet)
                .withMessageContaining("info.operatorName").withMessageContaining("info.operatorAdminName").withMessageContaining("info.universityName");
    }

    @Test
    void allowsDockerDesktopChartMetadata() throws IOException {
        var values = new YamlPropertySourceLoader().load("chart", new FileSystemResource("helm/artemis/values-docker-desktop.yaml")).getFirst();
        assertThatNoException().isThrownBy(guard((String) values.getProperty("artemis.config.operator.name"), (String) values.getProperty("artemis.config.operator.adminName"),
                (String) values.getProperty("artemis.config.operator.universityName"))::afterPropertiesSet);
    }

    @ParameterizedTest
    @ValueSource(strings = { "prod,core,scheduling", "prod,core", "prod,core,buildagent" })
    void rejectsInvalidMetadataOnProductionCoreNodes(String profiles) {
        try (var context = contextWithOperatorNameOnly(profiles)) {
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalArgumentException.class).hasStackTraceContaining("info.operatorAdminName")
                    .hasStackTraceContaining("info.universityName");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "dev,core,scheduling", "core", "test,core" })
    void startsCoreNodesOutsideProductionWithoutMetadata(String profiles) {
        try (var context = contextWithOperatorNameOnly(profiles)) {
            assertThatNoException().isThrownBy(context::refresh);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "buildagent", "prod,buildagent", "prod,scheduling" })
    void isNotRegisteredOnNodesWithoutTheCoreProfile(String profiles) {
        try (var context = contextWithOperatorNameOnly(profiles)) {
            context.refresh();
            assertThat(context.getBeansOfType(PropertiesConfigurationGuard.class)).isEmpty();
        }
    }

    private static AnnotationConfigApplicationContext contextWithOperatorNameOnly(String profiles) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profiles.split(","));
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("info.operatorName", "AET")));
        context.register(PropertiesConfigurationGuard.class);
        return context;
    }
}
