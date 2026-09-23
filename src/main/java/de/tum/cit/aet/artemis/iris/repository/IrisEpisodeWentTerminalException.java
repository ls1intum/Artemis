package de.tum.cit.aet.artemis.iris.repository;

/**
 * Thrown from inside {@link IrisProactiveEpisodeWriteRepository#appendProactiveMessageWithOutcome} when the episode
 * acquired a terminal outcome by someone else's hand between the append and this call's own outcome write.
 *
 * <p>
 * It exists to roll the append back: a closing row committed under a foreign terminal outcome would carry none of
 * its own, and the caller would broadcast {@code resolved=true} for an episode the student ended differently.
 * Deliberately not a {@code DataAccessException}, because the caller retries those and no retry can change a settled
 * outcome.
 */
public class IrisEpisodeWentTerminalException extends RuntimeException {

    public IrisEpisodeWentTerminalException(String episodeId) {
        super("Proactive episode " + episodeId + " went terminal before its append could record an outcome");
    }
}
