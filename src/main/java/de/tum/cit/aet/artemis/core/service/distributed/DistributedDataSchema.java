package de.tum.cit.aet.artemis.core.service.distributed;

import java.util.List;

/**
 * The version of the data Artemis keeps in the distributed store, and which of that data survives a version bump.
 *
 * <p>
 * Redis and Valkey keep their contents across a release, which is why a queued build survives a deploy instead of
 * vanishing the way it does with Hazelcast. The cost is that a new release reads bytes an older one wrote. Prefixing
 * every key with {@link #VERSION} turns that from something to detect into something that cannot happen: a release
 * only ever sees keys written under its own version.
 *
 * <p>
 * <b>When to bump {@link #VERSION}</b>: whenever a type stored in a distributed structure changes in a way an older
 * or newer build cannot read. Adding, removing, reordering or retyping a component of such a record all qualify,
 * because queue entries, set elements, topic messages and map keys are encoded positionally by Kryo, which carries no
 * schema and no version of its own. This is deliberately not the Artemis release version: most releases change
 * nothing here, and tying the two together would discard the build queue on every deploy.
 */
public final class DistributedDataSchema {

    /**
     * The version of the distributed data written by this build. See the class documentation for when to bump it.
     */
    public static final int VERSION = 1;

    /**
     * The store as it was before schema versions existed, with every structure under its plain name. A deployment that
     * has been running Redis so far is at this version, even though nothing ever wrote it down, which is why a missing
     * {@link #VERSION_KEY} next to existing structures is read as version {@code 0} rather than as an empty store.
     */
    public static final int UNVERSIONED = 0;

    /**
     * Holds the version the store currently contains. Deliberately outside any namespace, since it is the key that
     * says which namespace to look in, and it is written as a plain string so an operator can read it with
     * {@code redis-cli GET}.
     */
    public static final String VERSION_KEY = "artemis:distributed-data-schema";

    /**
     * Holds the Artemis release that last wrote {@link #VERSION_KEY}. Informational, for support.
     */
    public static final String RELEASE_KEY = "artemis:distributed-data-schema:release";

    /**
     * Guards the migration so that exactly one node in the cluster runs it.
     */
    public static final String MIGRATION_LOCK_KEY = "artemis:distributed-data-schema:lock";

    /**
     * The kinds of structure a migration knows how to move, which is what decides how its entries are drained.
     */
    public enum StructureKind {
        MAP, EXPIRING_MAP, QUEUE, PRIORITY_QUEUE, SET
    }

    /**
     * A structure that has to survive a version bump.
     *
     * @param name the logical name it is obtained under, without the namespace prefix
     * @param kind how its entries are stored, and therefore how they are moved
     */
    public record CarriedOverStructure(String name, StructureKind kind) {
    }

    /**
     * The structures whose contents are moved into the new namespace on a version bump. Everything not listed here
     * starts empty, which is how a structure gets discarded: leaving it out is the flush, decided in a reviewed change
     * rather than typed into {@code redis-cli} during a deploy.
     *
     * <p>
     * The bar for adding an entry is that the data is expensive or impossible to reconstruct. Build agent
     * registrations, websocket presence and caches all rebuild themselves within seconds and are deliberately absent.
     *
     * <p>
     * This list belongs specifically to the initial unversioned-to-v1 migration. Every future adjacent migration must
     * declare its own list instead of reusing this one: a wire-compatible structure can be moved byte-for-byte, while
     * an incompatible but indispensable structure needs a custom transformation using its old DTO and codec.
     */
    public static final List<CarriedOverStructure> LEGACY_TO_V1_STRUCTURES = List.of(
            // Queued student builds. Nothing else knows they were requested.
            new CarriedOverStructure("buildJobQueue", StructureKind.PRIORITY_QUEUE),
            // Builds already running on an agent. SharedQueueManagementService reads this map to decide what to
            // re-queue when an agent disappears, so losing it strands those builds.
            new CarriedOverStructure("processingJobs", StructureKind.MAP),
            // Finished results not yet written to the database. Losing one loses a student's feedback.
            new CarriedOverStructure("buildResultQueue", StructureKind.QUEUE),
            // Feature toggles are deliberately not database backed: the defaults come from yml and are seeded at
            // startup. A toggle an admin flipped at runtime exists only here, so it has to be moved.
            new CarriedOverStructure("features", StructureKind.MAP),
            // Iris jobs waiting for a Pyris callback. Obtained as an expiring map, so its entries have to move with
            // their remaining lifetime or a job whose callback never arrives would sit in the new namespace forever.
            new CarriedOverStructure("pyris-job-map", StructureKind.EXPIRING_MAP));

    private DistributedDataSchema() {
    }

    /**
     * The Redis key a structure lives under, for example {@code artemis:v1:{buildJobQueue}}.
     *
     * <p>
     * The braces are a Redis Cluster hash tag, and they are what makes the name rather than the version decide the
     * slot. Every version of one structure therefore lands on the same cluster node, which is what lets the migration
     * move an entry with a single server-side {@code RPOPLPUSH} instead of a read and a write that a crash can fall
     * between. It holds for the unversioned store too: a plain {@code buildJobQueue} hashes over the whole key, which
     * is exactly the text inside the tag. Different structures still hash apart, so nothing is concentrated.
     *
     * @param version the schema version
     * @param name    the logical structure name
     * @return the key it lives under
     */
    public static String keyFor(int version, String name) {
        // The unversioned store has no prefix and no tag: its keys are the plain structure names.
        return version == UNVERSIONED ? name : "artemis:v" + version + ":{" + name + "}";
    }

    /**
     * @param version the schema version
     * @return a pattern matching every key of that version, for deleting what a migration left behind
     */
    public static String keyPatternFor(int version) {
        return "artemis:v" + version + ":*";
    }

    /**
     * @param name the logical structure name
     * @return the key this build reads and writes it under
     */
    public static String currentKeyFor(String name) {
        return keyFor(VERSION, name);
    }
}
