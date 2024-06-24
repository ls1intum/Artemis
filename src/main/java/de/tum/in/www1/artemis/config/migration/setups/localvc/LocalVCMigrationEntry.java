package de.tum.in.www1.artemis.config.migration.setups.localvc;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.config.migration.setups.ProgrammingExerciseMigrationEntry;

public abstract class LocalVCMigrationEntry extends ProgrammingExerciseMigrationEntry {

    @Value("${server.url}")
    protected URL localVCBaseUrl;
}
