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
 * The multi-statement writes that settle a proactive episode: registering its row, recording its terminal outcome,
 * recording and consuming its ambient offer, and the appends that have to happen under its lock. A custom fragment of
 * {@link IrisProactiveEpisodeRepository}, so these transaction boundaries live in a repository rather than in a
 * service.
 *
 * <p>
 * The registry row exists so an episode has an identity that can be locked before its first message is written.
 * Without it a terminal decision is a check-then-act with nothing to serialize on, and a dismiss committing between
 * the check and the write surfaces a hint the student has already dismissed. First-terminal-wins is decided here;
 * {@code iris_message.proactive_outcome} is only a mirror of it, kept for the history replayed to Pyris.
 *
 * <p>
 * Lock order runs episode, then session, then message rows, and it has to. A registered episode is locked first
 * ({@link IrisProactiveEpisodeRepository#findForUpdate}); an unregistered one has no row to lock and starts at the
 * session. No operation that goes on to append reads or writes a message row before the session row is held,
 * because
 * {@link IrisSessionWriteRepository#deleteSupersededProactiveMessageAndCompact} takes the session first and the
 * message row second, and the opposite order deadlocks against it on InnoDB: a locking read over the
 * {@code (episode, exercise)} index holds every row it scanned under REPEATABLE READ, including the ones the
 * unindexed outcome predicate rejects. That is why the terminal check reads the message rows plainly and only once
 * the session lock is held.
 */
@Lazy
@Repository
@Conditional(IrisEnabled.class)
public interface IrisProactiveEpisodeWriteRepository {

    /**
     * What an outcome write achieved for the episode. The distinction {@link #APPLIED} vs {@link #LOST} is what a
     * caller needs before it commits anything it wrote alongside the outcome: only APPLIED means the episode ended
     * the way this caller says it did.
     */
    enum OutcomeWrite {

        /** This call wrote the outcome that now stands for the episode. */
        APPLIED,

        /**
         * The outcome did not take. Either a terminal outcome was already there, or the target row it would have been
         * written to no longer exists. An outcome equal to this call's own counts as LOST too: it is someone else's
         * write, so nothing this call did alongside it may be kept on the strength of it. Both cases are fail-closed
         * for a caller that wanted to end the episode its own way, and neither may be reported as success.
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
     * Register the episode so it has a row to lock and a place to hold its terminal outcome, or refresh the row a
     * previous trigger already created for it.
     *
     * <p>
     * An upsert rather than a read followed by a write. Reading first and then updating leaves a window in which
     * retention deletes the row in between, and the update lands on nothing. The refresh is therefore a single
     * guarded statement keyed on the natural key, and only a zero result falls through to the insert. Zero does not
     * prove the row is absent - some databases report changed rather than matched rows - which is exactly why the
     * caller keeps duplicate-key recovery around this.
     *
     * <p>
     * {@code REQUIRES_NEW} because it can hit the unique constraint, and a constraint violation marks its transaction
     * rollback-only: catching it inside the caller's transaction would turn a handled duplicate into an
     * {@code UnexpectedRollbackException} at that transaction's commit. The catch therefore has to sit outside this
     * boundary, which is only possible while this boundary is its own.
     *
     * @param userId     the struggling student
     * @param exerciseId the exercise the run belongs to
     * @param episodeId  the client-allocated episode id, already validated as usable
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // ok: must commit or fail independently of the caller
    void registerOrTouchInNewTransaction(long userId, long exerciseId, String episodeId);

    /**
     * Re-read the episode row on a transaction of its own, after an insert lost the race to a concurrent one.
     *
     * <p>
     * A second transaction for the same reason the first one was its own: the caller reaches this from a catch block
     * whose transaction, if it were shared, would already be marked rollback-only by the violation it is handling.
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
     * {@code outcome} only if none stands yet, and mirrors it onto the episode's message row.
     *
     * <p>
     * SCOPED to the given user's own episode rows in the given exercise: {@code episodeId} is a client-generated
     * UUID, so an unscoped write would let any student write an outcome onto another student's episode by guessing or
     * replaying the id. The {@code exerciseId} scope closes the same reuse inside one student, whose client can send
     * one id for two exercises.
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
     * reveal persists the server's own text rather than whatever the caller sends back.
     *
     * <p>
     * The caller must have registered the episode BEFORE calling this, on the registration's own transaction.
     * Registering from inside this one would be worse than useless: it commits independently, so the row this write
     * then targets would be one this transaction never locked, and the terminal check and the write would stop being
     * atomic.
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
     * text, and mark the offer consumed. All three commit together, so a failure anywhere leaves neither a message
     * nor a consumed offer behind.
     *
     * <p>
     * The pessimistic lock is what makes the unconsumed check and the claim indivisible. Without it two concurrent
     * reveals of the same offer could both read it as unconsumed and both insert a message.
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
     * outcome in the same transaction.
     *
     * <p>
     * The lock is the authoritative terminal check. A caller's cheap pre-check reads outside any lock, so an outcome
     * can commit between it and this write; here the registry row stays locked until this transaction commits, so no
     * REGISTRY outcome can be established between the check and the append. The pre-registry path writes onto a
     * message row instead and does not take that lock, so an outcome arriving through it can still commit alongside an
     * append already under way. What that leaves behind is caught by the terminal check reading both records. Recording the outcome in the same transaction is
     * what makes a confirm-close row and its outcome atomic - splitting the two is what let a concurrent dismiss land
     * between a committed close row and its own outcome.
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
