package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Starts a minimal application on the production configuration files and lets {@code spring-boot-properties-migrator} check them.
 * <p>
 * The integration tests use their own {@code config/application.yml}, which shadows the production one, so a key in
 * {@code src/main/resources/config} that Spring cannot bind any more would otherwise only show when a server starts. The migrator
 * fails the start on such a key ({@code spring.properties.migrator.fail}), and its error log names the key.
 * <p>
 * The application shares the JVM with the integration tests, so it must not change what they depend on. It runs with the
 * {@code test} profile, which makes {@link FileUploadPathEnvironmentPostProcessor} leave the upload root alone, and without the
 * {@link LoggingApplicationListener}, which would apply the production log levels to the whole JVM and tear logging down on close.
 */
class MainConfigurationPropertiesMigrationTest {

    private static final Path MAIN_CONFIG_DIRECTORY = Path.of("src/main/resources/config");

    @Lazy
    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {
    }

    @Test
    void shouldNotSetAnyKeyThatSpringCannotBindAnyMore() throws IOException {
        List<String> configFiles;
        try (Stream<Path> files = Files.list(MAIN_CONFIG_DIRECTORY)) {
            configFiles = files.filter(file -> file.getFileName().toString().matches("application.*\\.ya?ml")).map(file -> "file:" + file.toAbsolutePath()).sorted().toList();
        }
        assertThat(configFiles).as("the production configuration files").isNotEmpty();

        SpringApplication application = new SpringApplicationBuilder(EmptyConfiguration.class).web(WebApplicationType.NONE).profiles(SPRING_PROFILE_TEST).build();
        application.setListeners(application.getListeners().stream().filter(listener -> !(listener instanceof LoggingApplicationListener)).toList());

        // Command line arguments, so that neither the production nor the test configuration can switch the check off.
        String[] arguments = { "--spring.config.location=" + String.join(",", configFiles), "--spring.properties.migrator.fail=on-error", "--spring.main.banner-mode=off" };

        assertThatCode(() -> application.run(arguments).close()).as("the migrator found a key in the production configuration that Spring cannot bind any more, see its error log")
                .doesNotThrowAnyException();
    }
}
