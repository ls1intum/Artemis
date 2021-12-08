package de.tum.in.www1.artemis.config.migration;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

import de.tum.in.www1.artemis.domain.MigrationChangelog;
import de.tum.in.www1.artemis.repository.MigrationChangeRepository;

/**
 * This service allows registering certain entries containing functionality that gets executed on application startup. The entries must extend {@link MigrationEntry}.
 * The class contains additional utility functionality that verifies the registry on each execution to prevent a corruption.
 */
@Service
public class MigrationRegistry {

    private final Logger log = LoggerFactory.getLogger(MigrationRegistry.class);

    @Value("${artemis.version}")
    private String artemisVersion;

    // Using TreeMap to allow sorting. I'm using a map because with a list entries could accidentally be switched
    private final TreeMap<Integer, Class<? extends MigrationEntry>> migrationEntryMap = new TreeMap<>();

    private TreeMap<Integer, MigrationEntry> instantiatedMigrationEntryMap;

    private final AutowireCapableBeanFactory beanFactory;

    private final MigrationChangeRepository migrationChangeRepository;

    private final MessageDigest messageDigest = MessageDigest.getInstance("MD5");

    public MigrationRegistry(AutowireCapableBeanFactory beanFactory, MigrationChangeRepository migrationChangeRepository) throws NoSuchAlgorithmException {
        // Here we define the order of the ChangeEntries
        // migrationEntryMap.put(0, MigrationEntry20211127_120000.class);
        this.beanFactory = beanFactory;
        this.migrationChangeRepository = migrationChangeRepository;
    }

    /**
     * Hooks into the {@link ApplicationReadyEvent} and executes after a registry integration check each open migration entry.
     * After each execution it marks the entry as executed. All entries of one startup run get the same hash assigned.
     *
     * @param event Specifies when this method gets called and provides the event with all application data
     */
    @EventListener
    public void execute(ApplicationReadyEvent event) throws IOException, NoSuchAlgorithmException {
        if (event.getApplicationContext().getEnvironment().acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return;
        }

        log.info("Starting Artemis migration");
        String startupHash = toMD5(ZonedDateTime.now().toString());

        instantiateEntryMap();

        if (!checkIntegrity()) {
            log.error(getClass().getSimpleName() + " corrupted. Aborting startup.");
            event.getApplicationContext().close();
            System.exit(1);
            // System.exit(SpringApplication.exit(event.getApplicationContext(), () -> 1));
        }
        else {
            log.info("Integrity check passed.");
        }

        List<String> executedChanges = migrationChangeRepository.findAll().stream().map(MigrationChangelog::getDateString).toList();

        Map<Integer, MigrationEntry> migrationEntryMap = this.instantiatedMigrationEntryMap.entrySet().stream().filter(e -> !executedChanges.contains(e.getValue().date()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!migrationEntryMap.isEmpty()) {
            log.info("Executing migration entries");
            for (Map.Entry<Integer, MigrationEntry> integerClassEntry : migrationEntryMap.entrySet()) {
                MigrationEntry entry = integerClassEntry.getValue();
                log.debug("Executing entry " + entry.date());
                entry.execute();
                MigrationChangelog newChangelog = new MigrationChangelog();
                newChangelog.setAuthor(entry.author());
                newChangelog.setDateExecuted(ZonedDateTime.now());
                newChangelog.setDateString(entry.date());
                newChangelog.setSystemVersion(artemisVersion);
                newChangelog.setDeploymentId(startupHash);

                migrationChangeRepository.save(newChangelog);
                log.debug("Done");
            }
            log.info("Executed " + migrationEntryMap.size() + " migration entries");
        }
        else {
            log.info("No migration entries executed");
        }
        log.info("Ending Artemis migration");
    }

    public void instantiateEntryMap() {
        this.instantiatedMigrationEntryMap = this.migrationEntryMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e ->
        // We have to manually autowire the components here
        (MigrationEntry) beanFactory.autowire(e.getValue(), AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, true), (prev, next) -> next, TreeMap::new));
    }

    /**
     * Checks the registry for integrity. It must have distinct keys, distinct date strings and the date strings must be in order.
     * It is set public to allow an external integrity check without starting the actual application and therefore migration.
     * All occurring errors will be logged but duplicate errors are not logged.
     *
     * @param entryMap A changelog in form of an entryMap that should be checked
     * @return True if the check was successful, otherwise false
     */
    public boolean checkIntegrity(TreeMap<Integer, MigrationEntry> entryMap) {
        log.info("Starting migration integrity check");
        boolean passed = true;
        Map<Integer, MigrationEntry> brokenInstances = entryMap.entrySet().stream()
                .filter(e -> StringUtils.isEmpty(e.getValue().date()) || StringUtils.isEmpty(e.getValue().author()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!brokenInstances.isEmpty()) {
            passed = false;
            log.error(getClass().getSimpleName() + " corrupted. Migration entries not properly set up.");
            brokenInstances.forEach((key, value) -> log.error("Entry " + value.getClass().getSimpleName() + " has not all information methods defined."));
            log.info("Please refer to the documentation on how to set up migration entries.");
        }
        List<MigrationEntry> entryList = entryMap.values().stream().toList();
        if (!entryList.isEmpty()) {
            int startIndex = 1;
            MigrationEntry baseEntry = entryList.get(0);
            // Make sure the base date is not null. If it is, it was already caught and logged above.
            while (StringUtils.isEmpty(baseEntry.date()) && startIndex < entryList.size()) {
                baseEntry = entryList.get(startIndex);
                startIndex++;
            }
            for (int i = startIndex; i < entryList.size(); i++) {
                if (!StringUtils.isEmpty(entryList.get(i).date()) && baseEntry.date().compareTo(entryList.get(i).date()) >= 0) {
                    log.error(getClass().getSimpleName() + " corrupted. Invalid date order detected. " + entryList.get(i).date() + " ("
                            + entryList.get(i).getClass().getSimpleName() + ") should come before " + baseEntry.date() + " (" + baseEntry.getClass().getSimpleName() + ")");
                    passed = false;
                }
                baseEntry = entryList.get(i);
            }
        }
        log.info("Ending migration integrity check.");
        return passed;
    }

    public boolean checkIntegrity() {
        return checkIntegrity(this.instantiatedMigrationEntryMap);
    }

    private String toMD5(String string) {
        return Hex.encodeHexString(messageDigest.digest(string.getBytes(StandardCharsets.UTF_8)));
    }
}
