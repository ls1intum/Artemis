package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static de.tum.cit.aet.artemis.core.config.Constants.UPLOADS_FILE_PATH_DEFAULT;
import static de.tum.cit.aet.artemis.core.config.Constants.UPLOADS_FILE_PATH_PROPERTY_NAME;

import java.nio.file.Path;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import de.tum.cit.aet.artemis.core.util.FilePathConverter;

/**
 * Publishes the configured upload root to {@link FilePathConverter} before the application context exists.
 *
 * <p>
 * {@link FilePathConverter} keeps the root every stored file is resolved against in a process-wide static, so something
 * has to put it there. Until this class existed that something was {@code ArtemisApp.main}, <b>after</b>
 * {@code SpringApplication.run} had returned, and that is too late by a whole startup: {@code ApplicationReadyEvent}
 * fires inside {@code run}, so every listener of it - {@code MigrationRegistry} above all - ran while the root was still
 * unset. A migration entry that resolves a file location therefore resolved it against nothing, and because such an
 * entry catches its own failures it could count every file as failed, log, and still be recorded as executed.
 *
 * <p>
 * An {@link EnvironmentPostProcessor} is the earliest point at which the answer is known: the configuration files have
 * been read, and no bean, no {@code @PostConstruct} and no application event listener has run yet, so there is no
 * ordering left for a future entry to get wrong. It is also the only point both production entry points share.
 * {@code ArtemisApp.main} is one of them; {@code ApplicationWebXml} is the other, and it never called {@code main} at
 * all, so a deployment into a servlet container never set the root in the first place.
 *
 * <p>
 * The test profile is skipped on purpose. Server tests set the root once per JVM from
 * {@code AbstractArtemisIntegrationTest}, and a test that has to point it somewhere else puts the previous value back
 * when it is done. Spring creates test contexts lazily and evicts them again, so running this for every context would
 * reset the static in the middle of such a test. Nothing is lost by skipping: {@code MigrationService} returns
 * immediately under the test profile, so no migration entry runs there either.
 */
public class FileUploadPathEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return;
        }
        String configuredPath = environment.getProperty(UPLOADS_FILE_PATH_PROPERTY_NAME);
        FilePathConverter.setFileUploadPath(Path.of(configuredPath == null ? UPLOADS_FILE_PATH_DEFAULT : configuredPath));
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Runs last, so that the configuration files and the active profiles it reads are already in place.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
