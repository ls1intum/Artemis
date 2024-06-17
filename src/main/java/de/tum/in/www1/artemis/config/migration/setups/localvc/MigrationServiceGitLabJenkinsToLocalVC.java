package de.tum.in.www1.artemis.config.migration.setups.localvc;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_MIGRATE_GITLAB_JENKINS_TO_LOCALVC;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.migration.setups.localvc.gitlab.MigrationEntryGitLabToLocalVC;

@Service
@Profile(PROFILE_MIGRATE_GITLAB_JENKINS_TO_LOCALVC)
public class MigrationServiceGitLabJenkinsToLocalVC {

    private final MigrationEntryGitLabToLocalVC migrationEntryGitLabToLocalVC;

    public MigrationServiceGitLabJenkinsToLocalVC(MigrationEntryGitLabToLocalVC migrationEntryGitLabToLocalVC) {
        this.migrationEntryGitLabToLocalVC = migrationEntryGitLabToLocalVC;
    }

    @PostConstruct
    public void execute() {
        // TODO Add call to migration entry for Jenkins
        migrationEntryGitLabToLocalVC.execute();
    }

}
