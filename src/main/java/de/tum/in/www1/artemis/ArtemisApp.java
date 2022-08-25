package de.tum.in.www1.artemis;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.Environment;

import de.tum.in.www1.artemis.service.connectors.ProgrammingLanguageConfiguration;
import tech.jhipster.config.DefaultProfileUtil;
import tech.jhipster.config.JHipsterConstants;

@SpringBootApplication
@EnableConfigurationProperties({ LiquibaseProperties.class, ProgrammingLanguageConfiguration.class })
public class ArtemisApp {

    private static final Logger log = LoggerFactory.getLogger(ArtemisApp.class);

    private final Environment env;

    public ArtemisApp(Environment env) {
        this.env = env;
    }

    /**
     * Initializes Artemis.
     * <p>
     * Spring profiles can be configured with a program argument --spring.profiles.active=your-active-profile
     * <p>
     * You can find more information on how profiles work with JHipster on <a href="https://www.jhipster.tech/profiles/">https://www.jhipster.tech/profiles/</a>.
     */
    @PostConstruct
    public void initApplication() {
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
        var buildProperties = context.getBean(BuildProperties.class);
        var gitProperties = context.getBean(GitProperties.class);
        logApplicationStartup(env, buildProperties, gitProperties);
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
                \tLocal:      {}://localhost:{}{}
                \tExternal:   {}://{}:{}{}
                \tProfiles:   {}
                \tVersion:    {}
                \tGit Commit: {}
                \tGit Branch: {}
                ----------------------------------------------------------

                """, env.getProperty("spring.application.name"), protocol, serverPort, contextPath, protocol, hostAddress, serverPort, contextPath,
                env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles(), version, gitCommitId, gitBranch);
    }
}
