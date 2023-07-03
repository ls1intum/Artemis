package de.tum.in.www1.artemis.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SortedMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.config.migration.MigrationRegistry;
import de.tum.in.www1.artemis.config.migration.MigrationService;

class MigrationIntegrityTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private MigrationRegistry migrationRegistry;

    @Autowired
    private MigrationService migrationService;

    @Test
    void testIntegrity() {
        SortedMap<Integer, Class<? extends MigrationEntry>> classMap = migrationRegistry.getMigrationEntryMap();
        SortedMap<Integer, MigrationEntry> map = migrationService.instantiateEntryMap(classMap);
        assertThat(migrationService.checkIntegrity(map)).as("Migration Integrity Check")
                .withFailMessage("The Migration integrity check was not successful. Check the logs for errors in the registry.").isTrue();
    }
}
