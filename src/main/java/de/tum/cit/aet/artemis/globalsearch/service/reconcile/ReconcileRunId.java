package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A short tag identifying one run of one reconcile pass, carried on every line that run logs.
 * <p>
 * The passes run on their own schedules and can overlap, and each one ticks indefinitely, so a log holds many
 * interleaved runs of the same pass. Naming the pass separates the three from each other; the tag separates one
 * run from the next, which is what makes it possible to read a single tick's decisions as a group.
 * <p>
 * Deliberately short and not unique across restarts. It is a reading aid within one log, not an identifier
 * anything stores or correlates on.
 */
final class ReconcileRunId {

    private ReconcileRunId() {
    }

    /**
     * @return a fresh four-byte tag, rendered as eight hex characters
     */
    static String next() {
        byte[] bytes = new byte[4];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
