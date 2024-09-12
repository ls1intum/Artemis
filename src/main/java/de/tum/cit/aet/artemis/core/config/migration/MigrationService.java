package de.tum.cit.aet.artemis.core.config.migration;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.domain.MigrationChangelog;
import de.tum.cit.aet.artemis.core.repository.MigrationChangeRepository;

/**
 * This service contains utility functionality that verifies a changelog to prevent corruption and executes a given changelog.
 */
@Service
@Profile(PROFILE_SCHEDULING)
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    @Value("${artemis.version}")
    private String artemisVersion;

    private final MessageDigest messageDigest = MessageDigest.getInstance("MD5");

    private final MigrationChangeRepository migrationChangeRepository;

    private final AutowireCapableBeanFactory beanFactory;

    public MigrationService(MigrationChangeRepository migrationChangeRepository, AutowireCapableBeanFactory beanFactory) throws NoSuchAlgorithmException {
        this.migrationChangeRepository = migrationChangeRepository;
        this.beanFactory = beanFactory;
    }

    /**
     * First checks the integrity of the passed changelog, then executes not yet executed entries.
     * After each execution it marks the entry as executed. All entries of one startup run get the same hash assigned.
     *
     * @param event         Specifies when this method gets called and provides the event with all application data
     * @param entryClassMap The changelog to be executed
     */
    public void execute(ApplicationReadyEvent event, SortedMap<Integer, Class<? extends MigrationEntry>> entryClassMap) throws MigrationIntegrityException {
        if (event.getApplicationContext().getEnvironment().acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return;
        }

        log.debug("Starting Artemis migration");

        SortedMap<Integer, MigrationEntry> entryMap = instantiateEntryMap(entryClassMap);

        if (!checkIntegrity(entryMap)) {
            log.error("{} corrupted. Aborting startup.", getClass().getSimpleName());
            throw new MigrationIntegrityException();
        }
        else {
            log.debug("Integrity check passed.");
        }

        Set<String> executedChanges = migrationChangeRepository.findAll().stream().map(MigrationChangelog::getDateString).collect(Collectors.toCollection(HashSet::new));

        Map<Integer, MigrationEntry> migrationEntryMap = entryMap.entrySet().stream().filter(entry -> !executedChanges.contains(entry.getValue().date()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!migrationEntryMap.isEmpty()) {
            log.info("Executing migration entries");
            String startupHash = toMD5(ZonedDateTime.now().toString());
            for (Map.Entry<Integer, MigrationEntry> integerClassEntry : migrationEntryMap.entrySet()) {
                MigrationEntry entry = integerClassEntry.getValue();
                log.debug("Executing entry {}", entry.date());
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
            log.info("Executed {} migration entries", migrationEntryMap.size());
        }
        else {
            log.debug("No migration entries executed");
        }
        log.debug("Ending Artemis migration");
    }

    public SortedMap<Integer, MigrationEntry> instantiateEntryMap(SortedMap<Integer, Class<? extends MigrationEntry>> entryClassMap) {
        return entryClassMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            // We have to manually autowire the components here
            return (MigrationEntry) beanFactory.autowire(entry.getValue(), AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, true);
        }, (prev, next) -> next, TreeMap::new));
    }

    /**
     * Checks the given changelog for integrity. It must have distinct keys, distinct date strings and the date strings must be in order.
     * It is set public to allow an external integrity check without starting the actual application and therefore migration.
     * All occurring errors will be logged but duplicate errors are not logged.
     *
     * @param entryMap A changelog in form of an entryMap that should be checked
     * @return True if the check was successful, otherwise false
     */
    public boolean checkIntegrity(SortedMap<Integer, MigrationEntry> entryMap) {
        log.debug("Starting migration integrity check");
        boolean passed = true;
        Map<Integer, MigrationEntry> brokenInstances = entryMap.entrySet().stream()
                .filter(entry -> !StringUtils.hasLength(entry.getValue().date()) || !StringUtils.hasLength(entry.getValue().author()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!brokenInstances.isEmpty()) {
            passed = false;
            log.error("{} corrupted. Migration entries not properly set up.", getClass().getSimpleName());
            brokenInstances.forEach((key, value) -> log.error("Entry {} has not all information methods defined.", value.getClass().getSimpleName()));
            log.info("Please refer to the documentation on how to set up migration entries.");
        }
        List<MigrationEntry> entryList = entryMap.values().stream().toList();
        if (!entryList.isEmpty()) {
            int startIndex = 1;
            MigrationEntry baseEntry = entryList.getFirst();
            // Make sure the base date is not null. If it is, it was already caught and logged above.
            while (!StringUtils.hasLength(baseEntry.date()) && startIndex < entryList.size()) {
                baseEntry = entryList.get(startIndex);
                startIndex++;
            }
            // Go through the list of registered entries and make sure that every date is smaller than the date of its successor.
            for (int i = startIndex; i < entryList.size(); i++) {
                MigrationEntry entry = entryList.get(i);
                if (StringUtils.hasLength(entry.date()) && baseEntry.date().compareTo(entry.date()) >= 0) {
                    log.error("{} corrupted. Invalid date order detected. {} ({}) should come before {} ({})", getClass().getSimpleName(), entry.date(),
                            entry.getClass().getSimpleName(), baseEntry.date(), baseEntry.getClass().getSimpleName());
                    passed = false;
                }
                baseEntry = entry;
            }
        }
        log.debug("Ending migration integrity check.");
        return passed;
    }

    private String toMD5(String string) {
        return HexFormat.of().formatHex(messageDigest.digest(string.getBytes(StandardCharsets.UTF_8)));
    }
}
