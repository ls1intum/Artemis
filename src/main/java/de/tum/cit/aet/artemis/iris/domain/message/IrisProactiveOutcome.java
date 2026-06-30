package de.tum.cit.aet.artemis.iris.domain.message;

/**
 * Durable per-message record of how the student reacted to a proactive struggle hint (spec §7.4/§7.5).
 * Only an explicit dismiss is persisted; "engaged" is derived (helpful rating or a follow-up reply),
 * so there is no OPENED value to write client-side.
 */
public enum IrisProactiveOutcome {
    DISMISSED, RECOVERED, ABANDONED
}
