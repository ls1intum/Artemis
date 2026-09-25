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
import org.springframework.test.util.ReflectionTestUtils;

class PropertiesConfigurationGuardTest {

    private PropertiesConfigurationGuard guard(String operator, String admin, String university) {
        var guard = new PropertiesConfigurationGuard();
        ReflectionTestUtils.setField(guard, "operatorName", operator);
        ReflectionTestUtils.setField(guard, "operatorAdminName", admin);
        ReflectionTestUtils.setField(guard, "universityName", university);
        return guard;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  ", "Admin", "Some Artemis Operator", "Your University", "<university>", "TODO", "Example University", "Max Mustermann", "Anonymous University",
            "anonymous university admin", "Example University IT Services" })
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
    @ValueSource(strings = { "core,scheduling", "core", "dev,core", "prod,core,buildagent" })
    void rejectsInvalidMetadataOnEveryCoreNode(String profiles) {
        try (var context = contextWithOperatorNameOnly(profiles)) {
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalArgumentException.class).hasStackTraceContaining("info.operatorAdminName")
                    .hasStackTraceContaining("info.universityName");
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
