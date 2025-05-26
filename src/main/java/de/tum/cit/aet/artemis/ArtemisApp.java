package de.tum.cit.aet.artemis;

import static de.tum.cit.aet.artemis.core.config.Constants.UPLOADS_FILE_PATH_DEFAULT;
import static de.tum.cit.aet.artemis.core.config.Constants.UPLOADS_FILE_PATH_PROPERTY_NAME;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import de.tum.cit.aet.artemis.core.config.ArtemisCompatibleVersionsConfiguration;
import de.tum.cit.aet.artemis.core.config.LicenseConfiguration;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.config.TheiaConfiguration;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import tech.jhipster.config.DefaultProfileUtil;
import tech.jhipster.config.JHipsterConstants;

@SpringBootApplication
@EnableConfigurationProperties({ LiquibaseProperties.class, ProgrammingLanguageConfiguration.class, TheiaConfiguration.class, LicenseConfiguration.class,
        ArtemisCompatibleVersionsConfiguration.class })
@ComponentScan(basePackages = {
// @formatter:off
    "de.tum.cit.aet.artemis.assessment",
    "de.tum.cit.aet.artemis.athena",
    "de.tum.cit.aet.artemis.atlas",
    "de.tum.cit.aet.artemis.buildagent",
    "de.tum.cit.aet.artemis.communication",
    "de.tum.cit.aet.artemis.core",
    "de.tum.cit.aet.artemis.exam",
    "de.tum.cit.aet.artemis.exercise",
    "de.tum.cit.aet.artemis.fileupload",
    "de.tum.cit.aet.artemis.iris",
    "de.tum.cit.aet.artemis.lecture",
    "de.tum.cit.aet.artemis.lti",
    "de.tum.cit.aet.artemis.modeling",
    "de.tum.cit.aet.artemis.plagiarism",
    "de.tum.cit.aet.artemis.programming",
    "de.tum.cit.aet.artemis.quiz",
    "de.tum.cit.aet.artemis.text",
    "de.tum.cit.aet.artemis.tutorialgroup"
    // @formatter:on
}, lazyInit = true)
public class ArtemisApp {

    private static final Logger log = LoggerFactory.getLogger(ArtemisApp.class);

    private final Environment env;

    public static final long appStart = System.nanoTime();

    public ArtemisApp(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkProfiles() {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT) && activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            log.error("You have misconfigured your application! It should not run with both the 'dev' and 'prod' profiles at the same time.");
        }
        if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT) && activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_CLOUD)) {
            log.error("You have misconfigured your application! It should not run with both the 'dev' and 'cloud' profiles at the same time.");
        }
    }

    /**
     * Main method, used to run the application.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ArtemisApp.class);
        DefaultProfileUtil.addDefaultProfile(app);
        var context = app.run(args);
        Environment env = context.getEnvironment();
        String fileUploadPath = env.getProperty(UPLOADS_FILE_PATH_PROPERTY_NAME);
        // Set the file upload path for the FilePathConverter, use the default path "uploads" if not specified
        FilePathConverter.setFileUploadPath(Path.of(fileUploadPath == null ? UPLOADS_FILE_PATH_DEFAULT : fileUploadPath));
    }
}
