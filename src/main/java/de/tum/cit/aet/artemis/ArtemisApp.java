package de.tum.cit.aet.artemis;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.UPLOADS_FILE_PATH_DEFAULT;
import static de.tum.cit.aet.artemis.core.config.Constants.UPLOADS_FILE_PATH_PROPERTY_NAME;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_DEVELOPMENT;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import de.tum.cit.aet.artemis.core.PrintStartupBeansEvent;
import de.tum.cit.aet.artemis.core.config.ArtemisCompatibleVersionsConfiguration;
import de.tum.cit.aet.artemis.core.config.DeferredEagerBeanInitializer;
import de.tum.cit.aet.artemis.core.config.LicenseConfiguration;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.config.TheiaConfiguration;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import tech.jhipster.config.DefaultProfileUtil;
import tech.jhipster.config.JHipsterConstants;

@SpringBootApplication
@EnableConfigurationProperties({ LiquibaseProperties.class, ProgrammingLanguageConfiguration.class, TheiaConfiguration.class, LicenseConfiguration.class,
        ArtemisCompatibleVersionsConfiguration.class })
public class ArtemisApp {

    private static final Logger log = LoggerFactory.getLogger(ArtemisApp.class);

    private final Environment env;

    public static final long appStart = System.nanoTime();

    public ArtemisApp(Environment env) {
        this.env = env;
    }

    /**
     * Initializes Artemis.
     * <p>
     * Spring profiles can be configured with a program argument --spring.profiles.active=your-active-profile
     * <p>
     * You can find more information and how profiles work with JHipster on <a href="https://www.jhipster.tech/profiles/">https://www.jhipster.tech/profiles/</a>.
     */
    @PostConstruct
    public void initApplication() {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (activeProfiles.contains(SPRING_PROFILE_DEVELOPMENT) && activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            log.error("You have misconfigured your application! It should not run with both the 'dev' and 'prod' profiles at the same time.");
        }
        if (activeProfiles.contains(SPRING_PROFILE_DEVELOPMENT) && activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_CLOUD)) {
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
        var buildProperties = context.getBean(BuildProperties.class);
        var gitProperties = context.getBean(GitProperties.class);
        logApplicationStartup(env, buildProperties, gitProperties);
        // only publish the PrintStartupBeansEvent if the development profile is active.
        // This event is only consumed by the BeanInstantiationTracer. This class prints a dependency graph of the initialized beans in the application context to startupBeans.dot
        // This is useful for debugging and performance improvements, but should not be enabled in production environments.
        // startupBeans.dot can be visualized with graphviz, e.g. with http://www.webgraphviz.com/
        if (env.acceptsProfiles(Profiles.of(SPRING_PROFILE_DEVELOPMENT))) {
            context.publishEvent(new PrintStartupBeansEvent());
        }

        if (env.acceptsProfiles(Profiles.of(PROFILE_CORE))) {
            deferredEagerBeanInitialization(context);
        }
    }

    /**
     * Initializes deferred eager beans after the application context is fully initialized.
     * We explicitly call this method instead of invoking it on ApplicationReadyEvent, because we want to start this process as soon as the run method returns which is after all
     * logic related to the ApplicationReadyEvent has been executed.
     * The deferred eager bean initialization is useful to ensure that all beans are initialized before the first request is made.
     *
     * @param context the application context
     */
    private static void deferredEagerBeanInitialization(ConfigurableApplicationContext context) {
        DeferredEagerBeanInitializer initializer = context.getBean(DeferredEagerBeanInitializer.class);
        initializer.initializeDeferredEagerBeans();
    }

    private static void logApplicationStartup(Environment env, BuildProperties buildProperties, GitProperties gitProperties) {
        String protocol = "http";
        if (env.getProperty("server.ssl.key-store") != null) {
            protocol = "https";
        }
        String serverPort = env.getProperty("server.port");
        String version = buildProperties.getVersion();
        String gitCommitId = gitProperties.getShortCommitId();
        String gitBranch = gitProperties.getBranch();
        String contextPath = env.getProperty("server.servlet.context-path");
        if (StringUtils.isBlank(contextPath)) {
            contextPath = "/";
        }
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        }
        catch (UnknownHostException e) {
            log.warn("The host name could not be determined, using `localhost` as fallback");
        }
        log.info("""

                ----------------------------------------------------------
                \t'{}' is running! Access URLs:
                \tLocal:        {}://localhost:{}{}
                \tExternal:     {}://{}:{}{}
                \tProfiles:     {}
                \tVersion:      {}
                \tGit Commit:   {}
                \tGit Branch:   {}
                \tFull startup: {}
                ----------------------------------------------------------

                """, env.getProperty("spring.application.name"), protocol, serverPort, contextPath, protocol, hostAddress, serverPort, contextPath,
                env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles(), version, gitCommitId, gitBranch,
                TimeLogUtil.formatDurationFrom(appStart));
    }
}
