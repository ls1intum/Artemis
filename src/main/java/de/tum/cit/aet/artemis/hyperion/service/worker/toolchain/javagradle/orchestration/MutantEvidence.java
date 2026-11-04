package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

/**
 * The single record of what executed evidence says about every semantic mutant a run tracks. Each check, whichever path ran it (the acceptance and regression recheck after a
 * verified repair, the review's recheck of earlier survivors, a printed-transcript probe, the resolution of a survivor whose class changed), reports one outcome per mutant
 * through {@link #record}; the current survivors, the regression history, the unavailable evidence and the current repair's progress credit are all derived from those
 * outcomes, so no path can update one view and forget another.
 * <p>
 * Authored variants are identified by their replacement source as well as their novelty exclusion and evidence kind. Rekeying the reference source preserves that identity;
 * a re-derived transcript probe explicitly inherits the identity of the probe it replaces. Only a
 * mutant that once survived the graded suite is tracked; a proposal the suite killed at once is not evidence of anything a repair could undo. Per run and not thread-safe,
 * like the ledger that owns it.
 */
final class MutantEvidence {

    /** What the latest outcome says about a tracked mutant. */
    enum Status {
        /** The graded suite lets it survive: a current weakness. */
        SURVIVING,
        /** It survived earlier and the latest check gave no verdict: unresolved, reported as unavailable evidence. */
        SURVIVOR_UNVERIFIED,
        /** A candidate's suite killed it after it had survived: regression coverage every later repair rechecks. */
        KILLED,
        /** It was killed, and the latest regression check gave no verdict or was deferred: reported as unavailable evidence. */
        KILL_UNVERIFIED
    }

    private record Entry(SemanticMutant mutant, Status status) {

        boolean killed() {
            return status == Status.KILLED || status == Status.KILL_UNVERIFIED;
        }
    }

    /** Insertion order is check order: an entry moves to the end whenever an outcome is recorded for it, so the least recently checked kills come first. */
    private final Map<Variant, Entry> entries = new LinkedHashMap<>();

    private record Variant(SemanticMutant.Exclusion exclusion, String replacement, SemanticMutant.Evidence evidence) {
    }

    private final Map<Variant, Variant> inheritedIdentities = new HashMap<>();

    private Variant identity(SemanticMutant mutant) {
        Variant variant = new Variant(mutant.exclusion(), mutant.mutantSource(), mutant.evidence());
        return inheritedIdentities.getOrDefault(variant, variant);
    }

    /** A server-derived replacement probe continues the evidence record of the probe it replaces. */
    void inheritIdentity(SemanticMutant original, SemanticMutant replacement) {
        inheritedIdentities.put(new Variant(replacement.exclusion(), replacement.mutantSource(), replacement.evidence()), identity(original));
    }

    /** Tracked survivors a later candidate's suite killed since the current repair began, from any path. */
    private int killsSinceRepairBegan;

    /**
     * Records one outcome. A kill of a survivor credits the current repair; a survivor that a later check kills becomes regression coverage; a survival of an earlier kill is
     * a regression, which the caller reports, while the record keeps the kill of the accepted candidate; no verdict leaves the mutant unresolved.
     *
     * @param outcome the outcome, whose mutant becomes the tracked form of its record
     * @return whether the outcome is a regression of an earlier kill
     */
    boolean record(SemanticMutantOutcome outcome) {
        SemanticMutant mutant = outcome.mutant();
        Entry previous = entries.remove(identity(mutant));
        Status status = switch (outcome.disposition()) {
            case KILLED_BY_GRADED_SUITE -> previous == null ? null : Status.KILLED;
            case SURVIVED_GRADED_SUITE -> previous != null && previous.killed() ? Status.KILLED : Status.SURVIVING;
            default -> previous == null ? null : previous.killed() ? Status.KILL_UNVERIFIED : Status.SURVIVOR_UNVERIFIED;
        };
        if (status == null) {
            return false;
        }
        if (status == Status.KILLED && !previous.killed()) {
            killsSinceRepairBegan++;
        }
        entries.put(identity(mutant), new Entry(mutant, status));
        return outcome.disposition() == Disposition.SURVIVED_GRADED_SUITE && previous != null && previous.killed();
    }

    /** Records that a tracked mutant could not be checked at all, for example because its file was deleted or a bound deferred it. */
    void recordWithoutVerdict(SemanticMutant mutant) {
        record(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE));
    }

    void beginRepair() {
        killsSinceRepairBegan = 0;
    }

    int killsSinceRepairBegan() {
        return killsSinceRepairBegan;
    }

    /** Current survivors, the weaknesses a repair must close. */
    List<SemanticMutant> surviving() {
        return withStatus(Set.of(Status.SURVIVING));
    }

    /** Survivors whose latest check gave no verdict. */
    List<SemanticMutant> unverifiedSurvivors() {
        return withStatus(Set.of(Status.SURVIVOR_UNVERIFIED));
    }

    /** Every survivor, with or without a current verdict, in check order. */
    List<SemanticMutant> unresolvedSurvivors() {
        return withStatus(Set.of(Status.SURVIVING, Status.SURVIVOR_UNVERIFIED));
    }

    /** The regression history, least recently checked first. */
    List<SemanticMutant> kills() {
        return withStatus(Set.of(Status.KILLED, Status.KILL_UNVERIFIED));
    }

    /** Kills whose latest regression check gave no verdict or was deferred. */
    List<SemanticMutant> unverifiedKills() {
        return withStatus(Set.of(Status.KILL_UNVERIFIED));
    }

    private List<SemanticMutant> withStatus(Set<Status> statuses) {
        return entries.values().stream().filter(entry -> statuses.contains(entry.status())).map(Entry::mutant).toList();
    }
}
