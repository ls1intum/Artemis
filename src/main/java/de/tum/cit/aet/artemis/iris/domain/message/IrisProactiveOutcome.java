package de.tum.cit.aet.artemis.iris.domain.message;

/**
 * Durable per-message record of how a proactive struggle episode ended. All four values are
 * persisted by {@code IrisProactiveEpisodeService#writeEpisodeOutcome}; each one is terminal for its episode
 * and the first one written wins. DISMISSED is the student's explicit rejection, RECOVERED is a resolution
 * confirmed by the close flow, ABANDONED is an episode given up on, and INTERRUPTED marks a delivered episode
 * that ended because the exercise/session changed before the student resolved it (#350).
 *
 * <p>
 * "Engaged" is deliberately NOT a value here: it is derived from a helpful rating or a follow-up reply, so there
 * is nothing for a client to write. Note that only DISMISSED is rendered into the Pyris history prefix - see
 * {@code PyrisDTOService#proactiveOutcomeTag}, whose tags describe the interaction with a hint rather than the
 * lifecycle of the episode.
 */
public enum IrisProactiveOutcome {
    DISMISSED, RECOVERED, ABANDONED, INTERRUPTED
}
