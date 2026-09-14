package de.tum.cit.aet.artemis.hyperionworker.session;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;

/** Local generation policy. No persistence or transport operations are exposed to model tools. */
public interface GenerationEngine {

    /**
     * Runs one assignment; cancellation stops authoring, while the returned verified checkpoint may survive compute-budget exhaustion.
     *
     * @param assignment immutable core-authorized input
     * @param cancelled  explicit cancellation, shutdown or loss of coordinator contact
     * @param progress   bounded instructor activity
     * @param checkpoint a frozen inspectable candidate; publishing does not request persistence
     * @return the selected candidate, verification and accounting
     */
    GenerationOutput generate(GenerationAssignment assignment, BooleanSupplier cancelled, GenerationObserver progress, Consumer<GenerationOutput> checkpoint);

    /**
     * Requests prompt local teardown without interrupting an in-flight candidate capture.
     *
     * @param identity the exact execution to cancel
     * @return whether this engine owns teardown; false lets the supervisor use its emergency Docker cleanup
     */
    default boolean requestCancel(de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity identity) {
        return false;
    }
}
