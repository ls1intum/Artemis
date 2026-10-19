package de.tum.cit.aet.artemis.iris.repository;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveEpisode;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;

/**
 * The multi-statement writes that settle a proactive episode. A custom fragment of
 * {@link IrisProactiveEpisodeRepository}, so these transaction boundaries live in a repository rather than a service.
 *
 * <p>
 * The registry row gives an episode an identity that can be locked before its first message exists. Without it a
 * terminal decision is a check-then-act with nothing to serialize on. First-terminal-wins is decided here;
 * {@code iris_message.proactive_outcome} only mirrors it for the history replayed to Pyris.
 *
 * <p>
 * Lock order runs episode, then session, then message rows. No operation that goes on to append touches a message
 * row before the session row is held, because
 * {@link IrisSessionWriteRepository#deleteSupersededProactiveMessageAndCompact} takes them in that order and the
 * opposite order deadlocks against it on InnoDB: a locking read over the {@code (episode, exercise)} index holds
 * every row it scanned under REPEATABLE READ, including the ones the unindexed outcome predicate rejects.
 */
@Lazy
@Repository
@Conditional(IrisEnabled.class)
public interface IrisProactiveEpisodeWriteRepository {

    /** What an outcome write achieved for the episode. */
    enum OutcomeWrite {

        /** This call wrote the outcome that now stands for the episode. */
        APPLIED,

        /**
         * The outcome did not take: one was already there, or the target row is gone. An outcome equal to this call's
         * own counts as LOST too, because it is someone else's write and nothing this call did alongside it may be
         * kept on the strength of it.
         */
        LOST,

        /** Nothing could be recorded yet: the episode has no registry row and no message row to fall back on. */
        DEFERRED
    }

    /**
     * The result of an append that ran under the episode's lock.
     *
     * @param terminal whether the episode was already terminal when the lock was taken, so nothing was appended
     * @param message  the appended message, or {@code null} when the session was not bound to the exercise
     */
    record ProactiveAppendOutcome(boolean terminal, @Nullable IrisMessage message) {
    }

    /**
     * Register the episode so it has a row to lock, or refresh the row a previous trigger already created for it.
     * An upsert, so retention cannot delete the row between a read and a write; a zero result does not prove absence,
     * which is why the caller keeps duplicate-key recovery around this. {@code REQUIRES_NEW} because a constraint
     * violation marks its transaction rollback-only, so the catch has to sit outside this boundary.
     *
     * @param userId     the struggling student
     * @param exerciseId the exercise the run belongs to
     * @param episodeId  the client-allocated episode id, already validated as usable
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // ok: must commit or fail independently of the caller
    void registerOrTouchInNewTransaction(long userId, long exerciseId, String episodeId);

    /**
     * Re-read the episode row on a transaction of its own, after an insert lost the race to a concurrent one. The
     * caller reaches this from a catch block whose transaction, if shared, would already be rollback-only.
     *
     * @param userId     the owning user
     * @param exerciseId the exercise the episode belongs to
     * @param episodeId  the client-allocated episode UUID
     * @return the row the winner created, or empty when the violation was not a duplicate key
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // ok: the caller's transaction is rollback-only at this point
    Optional<IrisProactiveEpisode> findInNewTransaction(long userId, long exerciseId, String episodeId);

    /**
     * Episode-wide first-terminal-wins outcome write. Takes the episode's registry row under a write lock, records
     * {@code outcome} only if none stands yet, and mirrors it onto the episode's message row. The user and exercise
     * scope is a security guard: {@code episodeId} is client-generated, so an unscoped write would let any student
     * write an outcome onto another student's episode by replaying the id.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the requesting user; only this user's own episode rows are read or written
     * @param exerciseId the exercise the episode belongs to
     * @param outcome    the terminal outcome to record
     * @return whether {@code outcome} is the one that now stands, a different terminal outcome won, or nothing could
     *         be recorded yet
     */
    @Transactional // ok: the lock, the guarded write and the mirror are one first-terminal-wins decision
    OutcomeWrite recordOutcomeUnderLock(String episodeId, long userId, long exerciseId, IrisProactiveOutcome outcome);

    /**
     * Record the ambient hint Artemis is about to offer for this episode, under the episode's write lock, so a later
     * reveal persists the server's own text rather than whatever the caller sends back. The caller must register the
     * episode first, on the registration's own transaction: registering from inside this one commits independently,
     * so this write would target a row it never locked.
     *
     * @param userId     the struggling student
     * @param exerciseId the exercise the run belongs to
     * @param episodeId  the client-allocated episode id, already validated as usable
     * @param hintText   the hint as authored by Pyris
     * @return {@code true} when the episode now carries a revealable offer, {@code false} when the student already
     *         revealed this episode's previous offer, and {@code null} when the episode went terminal (or lost its
     *         row to retention) before the offer could be recorded
     */
    @Transactional // ok: the terminal check and the offer must commit together
    @Nullable
    Boolean recordAmbientOfferUnderLock(long userId, long exerciseId, String episodeId, String hintText);

    /**
     * Take the offered ambient hint under the episode's write lock, persist the message carrying the server-authored
     * text, and mark the offer consumed. The lock makes the unconsumed check and the claim indivisible; without it
     * two concurrent reveals could both read the offer as unconsumed and both insert.
     *
     * @param userId     the student performing the reveal
     * @param exerciseId the programming exercise the episode belongs to
     * @param episodeId  the client-allocated episode UUID
     * @param sessionId  the already-resolved exercise-chat session to persist into
     * @return the persisted message, or the message a previous reveal persisted when this is a replay
     * @throws de.tum.cit.aet.artemis.core.exception.ConflictException when there is nothing to reveal, the offer is
     *                                                                     spent, the episode is terminal, or the
     *                                                                     session moved to another exercise
     */
    @Transactional // ok: the message insert and the offer claim must not be separable
    IrisMessage revealAmbient(long userId, long exerciseId, String episodeId, long sessionId);

    /**
     * Append a proactive message under the episode's write lock and, when asked to, record the episode's terminal
     * outcome in the same transaction, which is what makes a confirm-close row and its outcome atomic.
     *
     * <p>
     * The lock is the authoritative terminal check. The pre-registry path writes onto a message row and takes no such
     * lock, so an outcome arriving through it can still commit alongside an append already under way, which is why
     * the terminal check reads both records.
     *
     * @param sessionId        the resolved exercise-chat session to persist into
     * @param userId           the student the message belongs to
     * @param exerciseId       the exercise the message is bound to
     * @param text             the proactive message text
     * @param episodeId        the client-allocated episode UUID, or {@code null} for a message that belongs to no
     *                             episode; without one no episode lock is taken and no outcome is recorded
     * @param outcomeOnSuccess the terminal outcome to record alongside the append, or {@code null}
     * @return whether the episode was already terminal, and the appended message when one was written
     * @throws IrisEpisodeWentTerminalException when the outcome write did not apply, which rolls the append back
     */
    @Transactional // ok: the terminal check, the append and the outcome write are one unit
    ProactiveAppendOutcome appendProactiveMessageWithOutcome(long sessionId, long userId, long exerciseId, String text, @Nullable String episodeId,
            @Nullable IrisProactiveOutcome outcomeOnSuccess);
}
