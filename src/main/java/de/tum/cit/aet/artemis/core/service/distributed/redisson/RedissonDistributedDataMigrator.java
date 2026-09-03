package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.CARRIED_OVER_STRUCTURES;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.MIGRATION_LOCK_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.RELEASE_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.UNVERSIONED;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.VERSION_KEY;
import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.namespaceFor;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RQueue;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema;
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
 * they move as the bytes they already are: every structure is opened under {@link ByteArrayCodec}, so nothing here
 * decodes a payload. That matters because the condition that requires a version bump is precisely the one under which
 * the current codec cannot read what the previous release wrote - a migration that decoded would fail exactly when it
 * is needed. Whether the bytes remain readable at all is the decision recorded in
 * {@link DistributedDataSchema#CARRIED_OVER_STRUCTURES}.
 *
 * <p>
 * No entry is removed from the source before it exists in the target: queues move with a single server-side
 * {@code RPOPLPUSH}, and maps and sets write first and delete after, which can duplicate an entry if the node dies mid
 * move but can never lose one. The version key is written only after the last entry has moved, so an interrupted
 * migration is indistinguishable from one that never started, and the rerun that follows is idempotent.
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

    /**
     * The schema version to bring the store to. Always {@link DistributedDataSchema#VERSION} in production; a
     * parameter so that a test can exercise a migration between two numbered versions, which the constant alone cannot
     * express while it stands at its first value.
     */
    private final int targetVersion;

    RedissonDistributedDataMigrator(RedissonClient redissonClient, String artemisVersion, int targetVersion) {
        this.redissonClient = redissonClient;
        this.artemisVersion = artemisVersion;
        this.targetVersion = targetVersion;
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
            // A store that predates this change carries no version key, but it does carry the structures the release
            // running before it wrote. Claiming it for the current version without moving them would leave a queued
            // build, an in-flight result or a flipped feature toggle stranded under a key nothing reads again.
            if (!holdsUnversionedData()) {
                log.info("Distributed store carries no schema version and no data yet, claiming it for version {}", targetVersion);
                recordCurrentVersion(versionBucket);
                return;
            }
            log.info("Distributed store predates schema versions, treating its contents as version {}", UNVERSIONED);
            storedVersion = UNVERSIONED;
        }
        if (storedVersion == targetVersion) {
            log.debug("Distributed store is already at schema version {}", targetVersion);
            return;
        }
        if (storedVersion > targetVersion) {
            throw new IllegalStateException("The distributed store was written by a newer release, at schema version " + storedVersion + ", and this release reads version "
                    + targetVersion + ". Artemis migrates distributed data forward only, and the namespace of version " + targetVersion
                    + " no longer exists. Deploy the newer release again, or clear the store if its contents can be lost.");
        }

        log.info("Migrating distributed data from schema version {} to {}", storedVersion, targetVersion);
        for (CarriedOverStructure structure : CARRIED_OVER_STRUCTURES) {
            long moved = drain(storedVersion, structure);
            log.info("Carried {} entries of '{}' over to schema version {}", moved, structure.name(), targetVersion);
        }
        discardRemainderOf(storedVersion);
        recordCurrentVersion(versionBucket);
        log.info("Distributed data is now at schema version {}", targetVersion);
    }

    /**
     * @return whether any structure that has to survive a bump already exists under its plain, unprefixed name, which
     *         is what tells an unversioned store apart from an empty one
     */
    private boolean holdsUnversionedData() {
        String[] names = CARRIED_OVER_STRUCTURES.stream().map(CarriedOverStructure::name).toArray(String[]::new);
        return redissonClient.getKeys().countExists(names) > 0;
    }

    /**
     * Removes what was left behind in the namespace that has been migrated away from: the structures that are not
     * carried over start empty, and that is where they are emptied.
     *
     * @param migratedVersion the schema version that has just been drained
     */
    private void discardRemainderOf(int migratedVersion) {
        if (migratedVersion == UNVERSIONED) {
            // The unversioned namespace is the whole keyspace, so there is no pattern that reaches only its remainder.
            // Deleting by one would take the version key, the namespace just written, and whatever else shares this
            // Redis. What stays behind is never read again and rebuilds itself, so it is left for an operator to drop.
            log.info("Left the structures of the unversioned store in place; they are no longer read, and no pattern deletes them without taking the new namespace too");
            return;
        }
        long removed = redissonClient.getKeys().deleteByPattern(namespaceFor(migratedVersion) + "*");
        log.info("Discarded {} remaining keys of schema version {}; structures that are not carried over start empty", removed, migratedVersion);
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
        versionBucket.set(String.valueOf(targetVersion));
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
        String to = namespaceFor(targetVersion) + structure.name();
        return switch (structure.kind()) {
            case QUEUE -> drainQueue(redissonClient.getQueue(from, ByteArrayCodec.INSTANCE), to);
            case PRIORITY_QUEUE -> drainQueue(redissonClient.getPriorityQueue(from, ByteArrayCodec.INSTANCE), to);
            case MAP -> drainMap(redissonClient.getMap(from, ByteArrayCodec.INSTANCE), redissonClient.getMap(to, ByteArrayCodec.INSTANCE));
            case EXPIRING_MAP -> drainExpiringMap(redissonClient.getMapCache(from, ByteArrayCodec.INSTANCE), redissonClient.getMapCache(to, ByteArrayCodec.INSTANCE));
            case SET -> drainSet(redissonClient.getSet(from, ByteArrayCodec.INSTANCE), redissonClient.getSet(to, ByteArrayCodec.INSTANCE));
        };
    }

    /**
     * Moves a list-backed structure one entry at a time with {@code RPOPLPUSH}, which takes from the tail of the
     * source and prepends to the head of the target in a single server-side step. The entry is therefore in exactly
     * one of the two at any moment, and repeating the transfer preserves the order the source held.
     *
     * @param from      the queue to empty
     * @param toKeyName the Redis key of the queue to fill
     * @return how many entries were moved
     */
    private long drainQueue(RQueue<byte[]> from, String toKeyName) {
        long moved = 0;
        while (from.pollLastAndOfferFirstTo(toKeyName) != null) {
            moved++;
        }
        return moved;
    }

    private long drainMap(RMap<byte[], byte[]> source, RMap<byte[], byte[]> target) {
        long moved = 0;
        // Iterating the key set and moving per key rather than reading the whole map keeps memory flat for a large
        // structure. Writing before removing means a node that dies mid move leaves a duplicate the rerun overwrites,
        // rather than an entry that exists nowhere.
        for (byte[] key : source.keySet()) {
            byte[] value = source.get(key);
            if (value != null) {
                target.put(key, value);
                source.remove(key);
                moved++;
            }
        }
        return moved;
    }

    /**
     * Moves an expiring map, carrying each entry's remaining lifetime with it. Without that, an entry whose deadline
     * was about to pass would become permanent in the new namespace.
     *
     * @param source the map to empty
     * @param target the map to fill
     * @return how many entries were moved
     */
    private long drainExpiringMap(RMapCache<byte[], byte[]> source, RMapCache<byte[], byte[]> target) {
        long moved = 0;
        for (byte[] key : source.keySet()) {
            byte[] value = source.get(key);
            if (value == null) {
                continue;
            }
            // remainTimeToLive answers -1 for an entry without a deadline and -2 for one that vanished between the
            // read above and this call. Zero is what put() takes for "no deadline", so both collapse to it: an entry
            // that is already gone is about to be dropped by the remove below anyway.
            long remainingTimeToLive = Math.max(source.remainTimeToLive(key), 0);
            target.put(key, value, remainingTimeToLive, TimeUnit.MILLISECONDS);
            source.remove(key);
            moved++;
        }
        return moved;
    }

    private long drainSet(RSet<byte[]> source, RSet<byte[]> target) {
        long moved = 0;
        for (byte[] element : source.readAll()) {
            target.add(element);
            source.remove(element);
            moved++;
        }
        return moved;
    }
}
