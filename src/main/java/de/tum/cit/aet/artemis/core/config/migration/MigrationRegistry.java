package de.tum.cit.aet.artemis.core.config.migration;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.SortedMap;
import java.util.TreeMap;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * This component allows registering certain entries containing functionality that gets executed on application startup. The entries must extend {@link MigrationEntry}.
 */
@Component
@Lazy
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class MigrationRegistry {

    // Using SortedMap to allow sorting. I'm using a map because with a list entries could accidentally be switched.
    private final SortedMap<Integer, Class<? extends MigrationEntry>> migrationEntryMap = new TreeMap<>();

    private final MigrationService migrationService;

    public MigrationRegistry(MigrationService migrationService) {
        this.migrationService = migrationService;
        // Here we define the order of the ChangeEntries
    }

    /**
     * Hooks into the {@link ApplicationReadyEvent} and executes the registered events
     *
     * @param event Specifies when this method gets called and provides the event with all application data
     */
    @EventListener
    public void execute(ApplicationReadyEvent event) throws IOException, NoSuchAlgorithmException, MigrationIntegrityException {
        migrationService.execute(event, this.migrationEntryMap);
    }

    public SortedMap<Integer, Class<? extends MigrationEntry>> getMigrationEntryMap() {
        return migrationEntryMap;
    }
}
