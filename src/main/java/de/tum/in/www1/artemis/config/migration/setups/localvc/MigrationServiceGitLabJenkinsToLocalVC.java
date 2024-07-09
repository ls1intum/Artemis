package de.tum.in.www1.artemis.config.migration.setups.localvc;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_MIGRATE_GITLAB_JENKINS_TO_LOCALVC;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.migration.setups.localvc.gitlab.MigrationEntryGitLabToLocalVC;
import de.tum.in.www1.artemis.config.migration.setups.localvc.jenkins.MigrationEntryJenkinsToLocalVC;

@Service
@Profile("gitlab & jenkins & " + PROFILE_MIGRATE_GITLAB_JENKINS_TO_LOCALVC)
public class MigrationServiceGitLabJenkinsToLocalVC {

    private static final Logger log = LoggerFactory.getLogger(MigrationServiceGitLabJenkinsToLocalVC.class);

    private final ApplicationContext applicationContext;

    private final MigrationEntryGitLabToLocalVC migrationEntryGitLabToLocalVC;

    private final MigrationEntryJenkinsToLocalVC migrationEntryJenkinsToLocalVC;

    public MigrationServiceGitLabJenkinsToLocalVC(ApplicationContext applicationContext, MigrationEntryGitLabToLocalVC migrationEntryGitLabToLocalVC,
            MigrationEntryJenkinsToLocalVC migrationEntryJenkinsToLocalVC) {
        this.applicationContext = applicationContext;
        this.migrationEntryGitLabToLocalVC = migrationEntryGitLabToLocalVC;
        this.migrationEntryJenkinsToLocalVC = migrationEntryJenkinsToLocalVC;
    }

    /**
     * Entry point for this migration service, which triggered at the startup of Artemis.
     * Performs the migration and shutdown Artemis afterward.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationInitialized() {
        log.info("Migration GitLab-Jenkins to LocalVC-Jenkins starting");

        log.info("Migration starts with the Jenkins build plans");
        migrationEntryJenkinsToLocalVC.execute();
        log.info("Migration follow ups with copy the repositories and adjusting the urls in the database");
        migrationEntryGitLabToLocalVC.execute();
        log.info("Migration GitLab-Jenkins to LocalVC-Jenkins finished (Check previous log if there are errors with some repositories)");

        SpringApplication.exit(applicationContext, () -> 0);
    }
}
