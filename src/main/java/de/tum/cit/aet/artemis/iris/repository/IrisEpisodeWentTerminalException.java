package de.tum.cit.aet.artemis.iris.repository;

/**
 * Thrown from inside {@link IrisProactiveEpisodeWriteRepository#appendProactiveMessageWithOutcome} when the episode
 * acquired a terminal outcome by someone else's hand between the append and this call's own outcome write.
 *
 * <p>
 * It exists to roll the append back. A closing row committed under a foreign terminal outcome would carry none of its
 * own, and the caller would broadcast {@code resolved=true} for an episode the student had already ended differently.
 * Throwing from inside the transaction drops the row that was inserted a few statements earlier and leaves the episode
 * exactly as the winner left it, which is what {@code TransactionStatus#setRollbackOnly} did while the boundary still
 * lived in the service.
 *
 * <p>
 * Deliberately not a {@code DataAccessException}: the caller retries transient persistence failures, and this is not
 * one. It is a settled outcome that no retry can change.
 */
public class IrisEpisodeWentTerminalException extends RuntimeException {

    public IrisEpisodeWentTerminalException(String episodeId) {
        super("Proactive episode " + episodeId + " went terminal before its append could record an outcome");
    }
}
