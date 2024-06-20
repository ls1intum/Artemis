package de.tum.in.www1.artemis.config.migration.setups.localvc;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_MIGRATE_GITLAB_JENKINS_TO_LOCALVC;

import java.util.HashSet;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.migration.setups.localvc.gitlab.MigrationEntryGitLabToLocalVC;

@Service
@Profile(PROFILE_MIGRATE_GITLAB_JENKINS_TO_LOCALVC)
public class MigrationServiceGitLabJenkinsToLocalVC {

    private final static Logger log = LoggerFactory.getLogger(MigrationServiceGitLabJenkinsToLocalVC.class);

    private final MigrationEntryGitLabToLocalVC migrationEntryGitLabToLocalVC;

    private final Environment environment;

    public MigrationServiceGitLabJenkinsToLocalVC(MigrationEntryGitLabToLocalVC migrationEntryGitLabToLocalVC, Environment environment) {
        this.migrationEntryGitLabToLocalVC = migrationEntryGitLabToLocalVC;
        this.environment = environment;
    }

    @PostConstruct
    public void execute() {
        log.info("Migration GitLab-Jenkins to LocalVC-Jenkins starting");
        List<String> activeProfiles = List.of(environment.getActiveProfiles());
        if (!new HashSet<>(activeProfiles).containsAll(List.of("gitlab", "jenkins"))) {
            log.error("Migration not possible, because the system does not support GitLab and Jenkins: {}", activeProfiles);
            return;
        }
        log.info("Migration startes with the Jenkins build plans");
        // TODO Add call to migration entry for Jenkins
        log.info("Migration follow ups with copy the repositories and adjusting the urls in the database");
        if (migrationEntryGitLabToLocalVC.execute()) {
            log.info("Migration GitLab-Jenkins to LocalVC-Jenkins finished (Check previous log if there are errors with some repositories)");
        }
        else {
            log.error("Migration GitLab-Jenkins to LocalVC-Jenkins failed at the GitLab to LocalVC part (Details in previous error message)");
        }
    }

}
