package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.CARRIED_OVER_STRUCTURES;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.MIGRATION_LOCK_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.RELEASE_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.namespaceFor;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RLock;
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
 * Carried-over entries are <b>moved</b> rather than copied, so peak memory stays flat even for a long build queue, and
 * each entry moves with an atomic take-from-old followed by a write to the new namespace. An entry is therefore never
 * in both namespaces at once, and a node that dies part way through leaves the rest where a rerun will find it: the
 * version key is written only after the last entry has moved, so an interrupted migration is indistinguishable from
 * one that never started.
 */
class RedissonDistributedDataMigrator {

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedDataMigrator.class);

    /**
     * How long a node waits for another node's migration before giving up. Generous, because the alternative to
     * waiting is starting against a namespace that is still being filled.
     */
    private static final long LOCK_WAIT_MINUTES = 10;

    /**
     * How long the lock survives if the node holding it dies, so a crashed migration does not block the cluster
     * forever. A rerun is safe, which is what makes releasing it automatically acceptable.
     */
    private static final long LOCK_LEASE_MINUTES = 30;

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
            acquired = lock.tryLock(LOCK_WAIT_MINUTES, LOCK_LEASE_MINUTES, TimeUnit.MINUTES);
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
        Integer storedVersion = readStoredVersion(versionBucket);

        if (storedVersion == null) {
            log.info("Distributed store carries no schema version yet, claiming it for version {}", VERSION);
            recordCurrentVersion(versionBucket);
            return;
        }
        if (storedVersion == VERSION) {
            log.debug("Distributed store is already at schema version {}", VERSION);
            return;
        }
        if (storedVersion > VERSION) {
            throw new IllegalStateException("The distributed store was written by a newer release, at schema version " + storedVersion + ", and this release reads version "
                    + VERSION + ". Artemis migrates distributed data forward only, and the namespace of version " + VERSION
                    + " no longer exists. Deploy the newer release again, or clear the store if its contents can be lost.");
        }

        log.info("Migrating distributed data from schema version {} to {}", storedVersion, VERSION);
        for (CarriedOverStructure structure : CARRIED_OVER_STRUCTURES) {
            long moved = drain(storedVersion, structure);
            log.info("Carried {} entries of '{}' over to schema version {}", moved, structure.name(), VERSION);
        }
        long removed = redissonClient.getKeys().deleteByPattern(namespaceFor(storedVersion) + "*");
        log.info("Discarded {} remaining keys of schema version {}; structures that are not carried over start empty", removed, storedVersion);
        recordCurrentVersion(versionBucket);
        log.info("Distributed data is now at schema version {}", VERSION);
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

    private void recordCurrentVersion(RBucket<String> versionBucket) {
        versionBucket.set(String.valueOf(VERSION));
        redissonClient.getBucket(RELEASE_KEY, StringCodec.INSTANCE).set(artemisVersion);
    }

    /**
     * Moves every entry of one structure from the old namespace into the current one.
     *
     * @param fromVersion the schema version to take the entries from
     * @param structure   the structure to move
     * @return how many entries were moved
     */
    private long drain(int fromVersion, CarriedOverStructure structure) {
        String from = namespaceFor(fromVersion) + structure.name();
        String to = namespaceFor(VERSION) + structure.name();
        return switch (structure.kind()) {
            case QUEUE -> drainQueue(redissonClient.getQueue(from), redissonClient.getQueue(to));
            case PRIORITY_QUEUE -> drainQueue(redissonClient.getPriorityQueue(from), redissonClient.getPriorityQueue(to));
            case MAP -> drainMap(from, to);
            case SET -> drainSet(from, to);
        };
    }

    private long drainQueue(Queue<Object> from, Queue<Object> to) {
        long moved = 0;
        Object entry;
        // poll() removes and returns in one round trip, so an entry is never readable in both namespaces.
        while ((entry = from.poll()) != null) {
            to.add(entry);
            moved++;
        }
        return moved;
    }

    private long drainMap(String from, String to) {
        var source = redissonClient.getMap(from);
        var target = redissonClient.getMap(to);
        long moved = 0;
        // Iterating the key set and removing per key rather than reading the whole map keeps memory flat for a large
        // structure, and remove() returning the value is what makes each move atomic.
        for (Object key : source.keySet()) {
            Object value = source.remove(key);
            if (value != null) {
                target.put(key, value);
                moved++;
            }
        }
        return moved;
    }

    private long drainSet(String from, String to) {
        var source = redissonClient.getSet(from);
        var target = redissonClient.getSet(to);
        long moved = 0;
        for (Object element : source.readAll()) {
            if (source.remove(element)) {
                target.add(element);
                moved++;
            }
        }
        return moved;
    }
}
