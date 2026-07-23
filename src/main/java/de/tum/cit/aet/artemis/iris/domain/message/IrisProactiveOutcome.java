package de.tum.cit.aet.artemis.iris.domain.message;

/**
 * Durable per-message record of how the student reacted to a proactive struggle hint (spec §7.4/§7.5).
 * Only an explicit dismiss is persisted; "engaged" is derived (helpful rating or a follow-up reply),
 * so there is no OPENED value to write client-side. INTERRUPTED marks a delivered episode that ended
 * because the exercise/session changed before the student resolved it (#350).
 */
public enum IrisProactiveOutcome {
    DISMISSED, RECOVERED, ABANDONED, INTERRUPTED
}
