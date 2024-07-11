package de.tum.in.www1.artemis.config.migration.setups.localvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import de.tum.in.www1.artemis.config.migration.setups.localvc.gitlab.MigrationEntryGitLabToLocalVC;
import de.tum.in.www1.artemis.config.migration.setups.localvc.jenkins.MigrationEntryJenkinsToLocalVC;

class GitlabJenkinsToLocalVCMigrationTest {

    private ApplicationContext applicationContext;

    private MigrationEntryGitLabToLocalVC migrationEntryGitLabToLocalVC;

    private MigrationEntryJenkinsToLocalVC migrationEntryJenkinsToLocalVC;

    private GitLabJenkinsToLocalVCMigration gitLabJenkinsToLocalVCMigration;

    @BeforeEach
    void setup() {
        applicationContext = Mockito.mock(ApplicationContext.class);
        migrationEntryGitLabToLocalVC = Mockito.mock(MigrationEntryGitLabToLocalVC.class);
        migrationEntryJenkinsToLocalVC = Mockito.mock(MigrationEntryJenkinsToLocalVC.class);

        gitLabJenkinsToLocalVCMigration = new GitLabJenkinsToLocalVCMigration(applicationContext, migrationEntryGitLabToLocalVC, migrationEntryJenkinsToLocalVC);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(applicationContext, migrationEntryGitLabToLocalVC, migrationEntryJenkinsToLocalVC);
    }

    @Test
    void shouldStartMigrationOnApplicationInit() {
        gitLabJenkinsToLocalVCMigration.onApplicationInitialized();

        verify(migrationEntryJenkinsToLocalVC, times(1)).execute();
        verify(migrationEntryGitLabToLocalVC, times(1)).execute();
    }
}
