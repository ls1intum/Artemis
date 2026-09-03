package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.LEGACY_TO_V1_STRUCTURES;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.LEGACY_VERSION;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.MIGRATION_LOCK_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.RELEASE_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.keyFor;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.namespaceFor;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RQueue;
import org.redisson.api.RScript;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.CarriedOverStructure;

/**
 * Brings the distributed store up to {@link DistributedDataSchema#VERSION} before anything reads from it.
 *
 * <p>
 * Runs from {@link RedissonDistributedDataProviderService}'s initialisation rather than from a startup event, because
 * the guarantee that matters is ordering rather than timing: no caller can obtain a structure before the provider bean
 * exists, so no caller can read a namespace this has not finished preparing.
 *
 * <p>
 * Every schema transition is an explicit adjacent-version step. This is important because a step whose carried type
 * changed incompatibly must use that version's DTO and codec; silently reading every old version with the current
 * codec would fail before migration could begin. The initial step is safe to decode because it only adds namespacing
 * without changing the stored representations.
 *
 * <p>
 * Entries are written to the target before they are removed from the source. Queue writes and their temporary
 * idempotency marker happen in one Redis script, so a stopped process can resume without losing or duplicating an
 * entry. The version key is written only after the complete step succeeds.
 */
class RedissonDistributedDataMigrator {

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedDataMigrator.class);

    /**
     * How long a node waits for another node's migration before giving up. Generous, because the alternative to
     * waiting is starting against a namespace that is still being filled.
     */
    private static final long LOCK_WAIT_MINUTES = 10;

    private static final String ADD_QUEUE_ENTRY_SCRIPT = """
            local added = redis.call('sadd', KEYS[2], ARGV[1])
            if added == 1 then
                redis.call('rpush', KEYS[1], ARGV[1])
            end
            return added
            """;

    private static final String REMOVE_QUEUE_HEAD_SCRIPT = """
            if redis.call('lindex', KEYS[1], 0) == ARGV[1] then
                redis.call('lpop', KEYS[1])
                return 1
            end
            return 0
            """;

    private static final String ROLLBACK_QUEUE_ENTRY_SCRIPT = """
            redis.call('lrem', KEYS[1], 1, ARGV[1])
            redis.call('srem', KEYS[2], ARGV[1])
            return 1
            """;

    private final RedissonClient redissonClient;

    private final String artemisVersion;

    RedissonDistributedDataMigrator(RedissonClient redissonClient, String artemisVersion) {
        this.redissonClient = redissonClient;
        this.artemisVersion = artemisVersion;
    }

    /**
     * Prepares the namespace this build reads, migrating from an older one if necessary.
     *
     * @throws IllegalStateException if the store was written by a newer release, or if another node's migration did
     *                                   not finish in time. Both leave the node unable to read the store safely, so
     *                                   failing here is what keeps it from starting against data it cannot trust.
     */
    void migrateToCurrentVersion() {
        RLock lock = redissonClient.getLock(MIGRATION_LOCK_KEY);
        boolean acquired;
        try {
            // The Redisson watchdog renews this lock while the migration node is alive. A fixed lease could expire in
            // the middle of a large queue and let a second node record completion prematurely.
            acquired = lock.tryLock(LOCK_WAIT_MINUTES, TimeUnit.MINUTES);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the distributed data migration lock", e);
        }
        if (!acquired) {
            throw new IllegalStateException("Another node has held the distributed data migration lock for more than " + LOCK_WAIT_MINUTES
                    + " minutes. Not starting, because the namespace this release reads may still be incomplete. Check whether that node is stuck, then restart this one.");
        }
        try {
            migrateUnderLock();
        }
        finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void migrateUnderLock() {
        RBucket<String> versionBucket = redissonClient.getBucket(VERSION_KEY, StringCodec.INSTANCE);
        Integer recordedVersion = readStoredVersion(versionBucket);
        int storedVersion = recordedVersion == null ? LEGACY_VERSION : recordedVersion;

        if (storedVersion == VERSION) {
            log.debug("Distributed store is already at schema version {}", VERSION);
            return;
        }
        if (storedVersion > VERSION) {
            throw new IllegalStateException("The distributed store was written by a newer release, at schema version " + storedVersion + ", and this release reads version "
                    + VERSION + ". Artemis migrates distributed data forward only, and the namespace of version " + VERSION
                    + " no longer exists. Deploy the newer release again, or clear the store if its contents can be lost.");
        }

        if (recordedVersion == null) {
            log.info("Distributed store carries the legacy unversioned schema; checking it for data to migrate");
        }

        while (storedVersion < VERSION) {
            int sourceVersion = storedVersion;
            MigrationStep step = migrationSteps().stream().filter(candidate -> candidate.fromVersion() == sourceVersion).findFirst()
                    .orElseThrow(() -> new IllegalStateException("The distributed store is at schema version " + sourceVersion + ", but this release has no migration step from "
                            + sourceVersion + ". Add an explicit adjacent-version migration before deploying schema version " + VERSION + "."));
            if (step.toVersion() != storedVersion + 1) {
                throw new IllegalStateException("Distributed-data migrations must be adjacent, but the step from " + storedVersion + " targets " + step.toVersion());
            }

            log.info("Migrating distributed data from schema version {} to {}", storedVersion, step.toVersion());
            step.action().run();
            storedVersion = step.toVersion();
            recordVersion(versionBucket, storedVersion);
            log.info("Distributed data is now at schema version {}", storedVersion);
        }
    }

    /**
     * @param versionBucket the bucket holding the stored version
     * @return the stored version, or {@code null} for a store that has never been claimed
     * @throws IllegalStateException if the key holds something that is not a version, which means it was written by
     *                                   something other than Artemis and cannot be reasoned about
     */
    private Integer readStoredVersion(RBucket<String> versionBucket) {
        String raw = versionBucket.get();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        }
        catch (NumberFormatException e) {
            throw new IllegalStateException("The distributed store holds '" + raw + "' under " + VERSION_KEY + ", which is not a schema version. Refusing to start rather than "
                    + "guessing which namespace the data belongs to.", e);
        }
    }

    private void recordVersion(RBucket<String> versionBucket, int version) {
        versionBucket.set(String.valueOf(version));
        redissonClient.getBucket(RELEASE_KEY, StringCodec.INSTANCE).set(artemisVersion);
    }

    /**
     * Lists concrete migration implementations. A future version must add an adjacent step here. A step that changes a
     * carried representation must decode with an old-version DTO/codec and transform it; it must not call
     * {@link #migrateWireCompatibleStructures(int, int, List)} for that structure.
     */
    private List<MigrationStep> migrationSteps() {
        return List.of(new MigrationStep(LEGACY_VERSION, 1, () -> migrateWireCompatibleStructures(LEGACY_VERSION, 1, LEGACY_TO_V1_STRUCTURES)));
    }

    private void migrateWireCompatibleStructures(int fromVersion, int toVersion, List<CarriedOverStructure> structures) {
        for (CarriedOverStructure structure : structures) {
            long moved = drain(fromVersion, toVersion, structure);
            log.info("Carried {} entries of '{}' over to schema version {}", moved, structure.name(), toVersion);
        }

        if (fromVersion == LEGACY_VERSION) {
            // The legacy Redis database may be shared with other applications, so '*' must never be deleted. Draining
            // removes the known carried structures; all other legacy keys are ignored by the namespaced provider.
            log.info("Left unrelated and discarded unversioned Redis keys untouched");
        }
        else {
            long removed = redissonClient.getKeys().deleteByPattern(namespaceFor(fromVersion) + "*");
            log.info("Discarded {} remaining keys of schema version {}; structures that are not carried over start empty", removed, fromVersion);
        }
    }

    /**
     * Moves every entry of one structure from the old namespace into the current one.
     *
     * @param fromVersion the schema version to take the entries from
     * @param structure   the structure to move
     * @return how many entries were moved
     */
    private long drain(int fromVersion, int toVersion, CarriedOverStructure structure) {
        String from = keyFor(fromVersion, structure.name());
        String to = keyFor(toVersion, structure.name());
        return switch (structure.kind()) {
            case QUEUE -> drainQueue(redissonClient.getQueue(from), to);
            case PRIORITY_QUEUE -> drainQueue(redissonClient.getPriorityQueue(from), to);
            case MAP -> drainMap(from, to);
            case EXPIRING_MAP -> drainExpiringMap(from, to);
            case SET -> drainSet(from, to);
        };
    }

    private long drainQueue(RQueue<Object> source, String targetName) {
        String markerName = queueMigrationMarkerKey(targetName);
        long moved = 0;
        Object entry = source.peek();
        while (entry != null) {
            boolean addedToTarget = runIntegerScript(ADD_QUEUE_ENTRY_SCRIPT, List.<Object>of(targetName, markerName), entry);
            boolean removedFromSource = runIntegerScript(REMOVE_QUEUE_HEAD_SCRIPT, List.<Object>of(source.getName()), entry);
            if (!removedFromSource) {
                if (addedToTarget) {
                    runIntegerScript(ROLLBACK_QUEUE_ENTRY_SCRIPT, List.<Object>of(targetName, markerName), entry);
                }
            }
            else {
                redissonClient.getSet(markerName).remove(entry);
                moved++;
            }
            entry = source.peek();
        }
        redissonClient.getKeys().delete(markerName);
        return moved;
    }

    private boolean runIntegerScript(String script, List<Object> keys, Object entry) {
        Number result = redissonClient.getScript().eval(RScript.Mode.READ_WRITE, script, RScript.ReturnType.LONG, keys, entry);
        return result.longValue() == 1L;
    }

    static String queueMigrationMarkerKey(String targetName) {
        return targetName + ":migration-items";
    }

    private long drainMap(String from, String to) {
        RMap<Object, Object> source = redissonClient.getMap(from);
        RMap<Object, Object> target = redissonClient.getMap(to);
        long moved = 0;
        for (Object key : source.keySet()) {
            Object value = source.get(key);
            if (value != null) {
                target.put(key, value);
                if (source.remove(key) != null) {
                    moved++;
                }
                else {
                    target.remove(key);
                }
            }
        }
        assertSourceIsEmpty(source.isEmpty(), from);
        return moved;
    }

    private long drainExpiringMap(String from, String to) {
        RMapCache<Object, Object> source = redissonClient.getMapCache(from);
        RMapCache<Object, Object> target = redissonClient.getMapCache(to);
        long moved = 0;
        for (Object key : source.keySet()) {
            Object value = source.get(key);
            long remainingTimeToLive = source.remainTimeToLive(key);
            if (value == null || remainingTimeToLive < -1) {
                continue;
            }

            if (remainingTimeToLive > 0) {
                target.put(key, value, remainingTimeToLive, TimeUnit.MILLISECONDS);
            }
            else {
                target.put(key, value);
            }
            if (source.remove(key) != null) {
                moved++;
            }
            else {
                target.remove(key);
            }
        }
        assertSourceIsEmpty(source.isEmpty(), from);
        source.delete();
        return moved;
    }

    private long drainSet(String from, String to) {
        RSet<Object> source = redissonClient.getSet(from);
        RSet<Object> target = redissonClient.getSet(to);
        long moved = 0;
        for (Object element : source.readAll()) {
            boolean addedToTarget = target.add(element);
            if (source.remove(element)) {
                moved++;
            }
            else if (addedToTarget) {
                target.remove(element);
            }
        }
        assertSourceIsEmpty(source.isEmpty(), from);
        return moved;
    }

    private static void assertSourceIsEmpty(boolean sourceEmpty, String name) {
        if (!sourceEmpty) {
            throw new IllegalStateException("Distributed structure '" + name + "' changed while it was being migrated. Stop older Artemis nodes and retry the deployment.");
        }
    }

    @FunctionalInterface
    private interface MigrationAction {

        void run();
    }

    private record MigrationStep(int fromVersion, int toVersion, MigrationAction action) {
    }
}
