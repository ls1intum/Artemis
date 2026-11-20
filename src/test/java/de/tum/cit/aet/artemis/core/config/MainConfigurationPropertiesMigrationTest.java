package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * Lets {@code spring-boot-properties-migrator} check the production configuration files.
 * <p>
 * The integration tests use their own {@code config/application.yml}, which shadows the production one, so a key in
 * {@code src/main/resources/config} that Spring cannot bind any more would otherwise only show when a server starts. The migrator
 * fails the start on such a key ({@code spring.properties.migrator.fail}), and its error log names the key.
 * <p>
 * The test runs the migrator on an {@link org.springframework.core.env.Environment} that it fills with the configuration files, and
 * does not start a {@link SpringApplication}. The test JVM runs many other tests at the same time, and a started application changes
 * what they share: Spring Boot installs a turbo filter that denies all log events until its logging system is initialised, and a
 * test that does not finish that initialisation leaves every later log event, and every log assertion, without an event.
 */
class MainConfigurationPropertiesMigrationTest {

    private static final Path MAIN_CONFIG_DIRECTORY = Path.of("src/main/resources/config");

    @Test
    void shouldNotSetAnyKeyThatSpringCannotBindAnyMore() throws IOException {
        List<Path> configFiles;
        try (Stream<Path> files = Files.list(MAIN_CONFIG_DIRECTORY)) {
            configFiles = files.filter(file -> file.getFileName().toString().matches("application.*\\.ya?ml")).sorted().toList();
        }
        assertThat(configFiles).as("the production configuration files").isNotEmpty();

        assertThatCode(() -> checkWithMigrator(configFiles)).as("the migrator found a key in the production configuration that Spring cannot bind any more, see its error log")
                .doesNotThrowAnyException();
    }

    /**
     * Without this, the test above would also pass if the migrator did not look at the files at all.
     */
    @Test
    void shouldRejectAKeyThatSpringBootCannotBindAnyMore(@TempDir Path directory) throws IOException {
        Path configFile = directory.resolve("application.yml");
        Files.writeString(configFile, "server:\n  forward-headers-strategy: native\n");

        assertThatThrownBy(() -> checkWithMigrator(List.of(configFile))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration keys that need to be migrated");
    }

    /**
     * Loads the files into a new environment and lets the migrator check it, as it does when an application is prepared. The
     * migrator logs its report when the application is ready or has failed, which this method does if the check fails.
     */
    private static void checkWithMigrator(List<Path> configFiles) {
        ConfigurableEnvironment environment = new StandardEnvironment();
        List<String> locations = configFiles.stream().map(file -> "file:" + file.toAbsolutePath()).toList();
        // The first property source wins, so neither the files nor the environment of the build can switch the check off.
        environment.getPropertySources()
                .addFirst(new MapPropertySource("migrator-test", Map.of("spring.config.location", String.join(",", locations), "spring.properties.migrator.fail", "on-error")));
        ConfigDataEnvironmentPostProcessor.applyTo(environment, new DefaultResourceLoader(), new DefaultBootstrapContext(), List.of());

        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        SpringApplication application = mock(SpringApplication.class);
        ApplicationListener<ApplicationEvent> migrator = migratorListener();
        try {
            migrator.onApplicationEvent(new ApplicationPreparedEvent(application, new String[0], context));
        }
        catch (IllegalStateException ex) {
            migrator.onApplicationEvent(new ApplicationFailedEvent(application, new String[0], context, ex));
            throw ex;
        }
    }

    /**
     * The listener of the migrator, which is not public. Spring Boot registers it in {@code spring.factories}, where this finds it.
     */
    @SuppressWarnings("unchecked")
    private static ApplicationListener<ApplicationEvent> migratorListener() {
        return SpringFactoriesLoader.forDefaultResourceLocation(MainConfigurationPropertiesMigrationTest.class.getClassLoader()).load(ApplicationListener.class).stream()
                .filter(listener -> listener.getClass().getSimpleName().equals("PropertiesMigrationListener")).map(listener -> (ApplicationListener<ApplicationEvent>) listener)
                .findFirst().orElseThrow(() -> new IllegalStateException("spring-boot-properties-migrator registers no PropertiesMigrationListener"));
    }
}
