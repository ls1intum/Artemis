package de.tum.cit.aet.artemis.iris.service.session;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveEpisode;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;

/**
 * Owns the proactive episode's registry row: registering it, deciding whether it is terminal, and the writes that
 * settle its terminal outcome and its ambient offer.
 *
 * <p>
 * The row exists so an episode has an identity that can be locked before its first message is written. Every
 * terminal decision used to be a check-then-act with nothing to serialize on, so a dismiss committing between the
 * check and the write produced a hint the student had already dismissed. This service is where that lock is taken
 * and where first-terminal-wins is decided; {@code iris_message.proactive_outcome} is only a mirror of it, kept for
 * the history replayed to Pyris.
 *
 * <p>
 * The methods fall into two groups, and the distinction is load-bearing:
 * <ul>
 * <li>Transaction-opening: {@link #registerEpisode}, {@link #isEpisodeTerminal}, {@link #writeEpisodeOutcome} and
 * {@link #offerAmbientHint}. They must NOT be called from inside a running transaction. The template propagates as
 * {@code REQUIRED}, so an outer transaction would be joined, and {@link #offerAmbientHint} would lose the ordering
 * it depends on: its registration has to commit on its own {@code REQUIRES_NEW} template BEFORE the offer
 * transaction starts, or the offer writes to a row that transaction never locked.</li>
 * <li>Participating, named {@code ...InCurrentTransaction} and package-private: they run inside the caller's
 * transaction and must never open one of their own. The caller already holds the episode's row lock at that point,
 * so a nested transaction would block on the lock its own caller is holding.</li>
 * </ul>
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisProactiveEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(IrisProactiveEpisodeService.class);

    private final IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    private final IrisMessageRepository irisMessageRepository;

    private final TransactionTemplate transactionTemplate;

    /**
     * For work that must commit or fail on its own, independently of whatever transaction the caller is in: the
     * episode registration. It can hit the unique constraint, and a constraint violation marks its transaction
     * rollback-only, so catching it inside the caller's transaction would turn a handled duplicate into an
     * {@code UnexpectedRollbackException} at that transaction's commit.
     */
    private final TransactionTemplate requiresNewTransactionTemplate;

    public IrisProactiveEpisodeService(IrisProactiveEpisodeRepository irisProactiveEpisodeRepository, IrisMessageRepository irisMessageRepository,
            PlatformTransactionManager transactionManager) {
        this.irisProactiveEpisodeRepository = irisProactiveEpisodeRepository;
        this.irisMessageRepository = irisMessageRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
     * insert keeps its duplicate-key recovery.
     *
     * <p>
     * Repeating a trigger for one episode is normal rather than exceptional: a {@code decide} run and the
     * {@code confirm_close} run that follows it carry the same episode id. The refresh is also what keeps retention
     * honest, since it moves {@code lastTriggeredAt} forward and an episode still being triggered is never reaped.
     * The unique key decides who wins an insert race, and the loser rereads. The catch sits OUTSIDE the transaction
     * that attempted the insert, because a constraint violation marks its transaction rollback-only: catching it
     * inside and carrying on would surface as an {@code UnexpectedRollbackException} at commit rather than as the
     * handled duplicate it is. The reread runs in a second transaction for the same reason, and rethrows the
     * original failure if it finds nothing, since not every integrity violation is a duplicate key.
     *
     * <p>
     * A new row inherits any terminal outcome the episode already reached on its message rows, so an id reused
     * across the deployment that introduced the registry does not come back open. That carry-over is a snapshot, not
     * a serialized read: a legacy outcome write taking the pre-registry path at the very same moment is not ordered
     * against it. The window exists only for episodes that predate the registry and closes as soon as they age out,
     * so it is accepted rather than locked.
     *
     * <p>
     * Returns nothing on purpose. Callers only need the row to exist before they lock it, and handing one back would
     * mean re-reading after the bulk update, which bypasses the persistence context: a caller that already held the
     * episode would be given its stale instance rather than the refreshed row.
     *
     * @param userId     the struggling student
     * @param exerciseId the exercise the run belongs to
     * @param episodeId  the client-allocated episode id, already validated as usable
     */
    void registerEpisode(long userId, long exerciseId, String episodeId) {
        try {
            requiresNewTransactionTemplate.executeWithoutResult(status -> {
                if (irisProactiveEpisodeRepository.touchLastTriggeredAt(userId, exerciseId, episodeId, ZonedDateTime.now()) > 0) {
                    return;
                }
                var episode = new IrisProactiveEpisode();
                episode.setUserId(userId);
                episode.setExerciseId(exerciseId);
                episode.setEpisodeId(episodeId);
                episode.setLastTriggeredAt(ZonedDateTime.now());
                // Carry over a terminal outcome this episode already reached before it had a registry row. A trigger
                // that reuses such an id across the deployment would otherwise get a fresh open row, and every later
                // check would trust it and let a late message through for an episode the student had closed.
                irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).stream().findFirst().ifPresent(episode::setOutcome);
                irisProactiveEpisodeRepository.saveAndFlush(episode);
            });
        }
        catch (DataIntegrityViolationException duplicate) {
            // Another trigger for the same episode won the insert race. The row it created is the one every later
            // path locks, so there is nothing left to do here; only a violation that is NOT a duplicate key must
            // surface, which the reread distinguishes.
            requiresNewTransactionTemplate.execute(status -> irisProactiveEpisodeRepository.find(userId, exerciseId, episodeId).orElseThrow(() -> duplicate));
        }
    }

    /**
     * Returns true when the episode already has a terminal outcome persisted (DISMISSED, RECOVERED, or ABANDONED).
     * Used by the active branch to skip a late escalation that arrived after the student dismissed.
     *
     * <p>
     * Reads episode-wide: checks ALL rows tagged with the episodeId, not just the earliest, so the result is
     * stable under out-of-order persistence.
     *
     * <p>
     * This is the cheap, unlocked read: a fast path that lets a caller complete silently without opening a
     * transaction. It is not the decision. Every path that goes on to write re-checks under the episode's registry
     * write lock inside the same transaction as its write, which is what makes the pair atomic against a concurrent
     * outcome.
     *
     * <p>
     * An episode with no registry row falls back to the message rows, which is exactly how this worked before the
     * registry existed, so a job still in flight across the deployment that introduced it is unaffected.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the job's owning user; only outcomes on rows in this user's sessions are considered
     * @param exerciseId the exercise the job ran for; an episode id reused for another exercise is not this episode
     * @return true if a terminal outcome exists for this episode
     */
    boolean isEpisodeTerminal(String episodeId, long userId, long exerciseId) {
        var registered = irisProactiveEpisodeRepository.find(userId, exerciseId, episodeId);
        if (registered.isPresent()) {
            return registered.get().getOutcome() != null;
        }
        // Not registered: an episode from before this feature branch carried a registry, or a job still in flight
        // from a previous deployment. Fall back to the message rows, which is exactly what this method did before,
        // so such an episode keeps behaving as it always did instead of silently losing its terminal state.
        return !irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty();
    }

    /**
     * Episode-wide first-terminal-wins outcome write. Takes the episode's registry row under a write lock, records
     * {@code outcome} only if none stands yet, and mirrors it onto the episode's message row. Returns {@code true}
     * whenever a terminal outcome is established for the episode, whether THIS call wrote it or a prior one did.
     *
     * <p>
     * A registered episode can always record an outcome, even before its first message exists, so the only
     * {@code false} comes from {@link #writeLegacyEpisodeOutcome}: an episode with no registry row has nowhere but a
     * message row to put the outcome, and defers until one exists.
     *
     * <p>
     * SCOPED to the requesting user's own episode rows in the given exercise:
     * {@code episodeId} is a client-generated UUID, so an unscoped write would let any student write an outcome onto
     * another student's episode by guessing/replaying the id (IDOR). The {@code userId} scope closes that; the
     * {@code exerciseId} scope closes the same reuse INSIDE one student, whose client can send one id for two
     * exercises. Both the target-row lookup and the episode-wide outcome reads carry both predicates, so an episode
     * id that belongs elsewhere is indistinguishable from one that does not exist yet (deferred, never a foreign write).
     *
     * @param episodeId  the client-allocated episode UUID
     * @param outcome    the terminal outcome to write
     * @param userId     the requesting user; only this user's own episode rows are read or written
     * @param exerciseId the exercise the episode belongs to; only rows stamped with it are read or written
     * @return {@code true} if a terminal outcome is established for the episode; {@code false} if none could be
     *         established yet (no row persisted - the caller should back-fill once a row exists)
     */
    public boolean writeEpisodeOutcome(String episodeId, IrisProactiveOutcome outcome, long userId, long exerciseId) {
        if (episodeId == null || episodeId.isBlank()) {
            // A blank id is not an episode identity, and treating it as one is how distinct episodes end up sharing
            // an outcome. The trigger endpoint rejects blank ids outright, but this method is also reached from the
            // {episodeId} path variable, which validation does not cover.
            return false;
        }
        return Boolean.TRUE.equals(transactionTemplate
                .execute(status -> recordOutcomeUnderLockInCurrentTransaction(irisProactiveEpisodeRepository.findForUpdate(userId, exerciseId, episodeId).orElse(null), episodeId,
                        userId, exerciseId, outcome)));
    }

    /**
     * Register the episode and record the ambient hint Artemis is about to offer for it, so a later reveal persists
     * the server's own text rather than whatever the caller sends back.
     *
     * <p>
     * The registration runs FIRST and on its own transaction, before the offer transaction opens. Registering from
     * inside that transaction would be worse than useless: it commits independently, so the row the offer then wrote
     * to would be one the offer's transaction never locked, and the terminal check and the write would stop being
     * atomic. The episode normally has a row already, because the trigger registers it; a job minted before the
     * deployment that introduced the registry does not.
     *
     * @param userId     the struggling student
     * @param exerciseId the exercise the run belongs to
     * @param episodeId  the client-allocated episode id, already validated as usable
     * @param hintText   the hint as authored by Pyris
     * @return {@code true} when the episode now carries a revealable offer the client may be pointed at,
     *         {@code false} when the student already revealed this episode's previous offer, and {@code null} when
     *         the episode went terminal (or lost its row to retention) before the offer could be recorded
     */
    @Nullable
    Boolean offerAmbientHint(long userId, long exerciseId, String episodeId, String hintText) {
        registerEpisode(userId, exerciseId, episodeId);
        return transactionTemplate.execute(status -> {
            var episode = lockEpisodeAndReadTerminalInCurrentTransaction(episodeId, userId, exerciseId).episode();
            // No row despite the registration above means retention removed it in between, which takes seven quiet
            // days and a trigger that then never refreshed it. Treat it as terminal rather than announcing a pointer
            // at an episode nothing can resolve.
            if (episode == null || episode.getOutcome() != null) {
                return null;
            }
            return recordAmbientOffer(episode, hintText);
        });
    }

    /**
     * Take the episode's row under a write lock so the caller can decide on its offer and write in the same
     * transaction. Participating: it must be called from inside the caller's transaction.
     *
     * @param userId     the owning user
     * @param exerciseId the exercise the episode belongs to
     * @param episodeId  the client-allocated episode UUID
     * @return the locked row, or empty when the episode has no registry row
     */
    Optional<IrisProactiveEpisode> lockOfferForRevealInCurrentTransaction(long userId, long exerciseId, String episodeId) {
        return irisProactiveEpisodeRepository.findForUpdate(userId, exerciseId, episodeId);
    }

    /**
     * Mark the episode's offer consumed by the message the reveal just persisted. Participating: the entity is
     * managed by the caller's transaction, so this is part of that transaction's flush. Setting the timestamp and
     * the message id together is what keeps a consumed offer from ever carrying only one of the two.
     *
     * @param episode   the episode row the caller holds write-locked
     * @param messageId the id of the message the reveal persisted
     */
    void consumeOfferInCurrentTransaction(IrisProactiveEpisode episode, long messageId) {
        episode.setConsumedAt(ZonedDateTime.now());
        episode.setConsumedMessageId(messageId);
        irisProactiveEpisodeRepository.save(episode);
    }

    /**
     * Whether the episode is terminal, deciding it under the episode's registry write lock so the caller can write in
     * the same transaction without anything interleaving. Falls back to the message rows for an episode that has no
     * registry row, which behaves exactly as this feature did before the registry existed.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the owning user
     * @param exerciseId the exercise the episode belongs to
     * @return true if a terminal outcome stands for this episode
     */
    LockedEpisode lockEpisodeAndReadTerminalInCurrentTransaction(String episodeId, long userId, long exerciseId) {
        var locked = irisProactiveEpisodeRepository.findForUpdate(userId, exerciseId, episodeId);
        if (locked.isPresent()) {
            return new LockedEpisode(locked.get(), locked.get().getOutcome() != null);
        }
        return new LockedEpisode(null, !irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty());
    }

    /**
     * Record the episode's terminal outcome onto the registry row the caller already holds write-locked, and mirror
     * it onto the message row. An unregistered episode has no row to carry the outcome, so it falls back to the
     * pre-registry write, where the message row is the only record there is.
     *
     * @param episode    the locked registry row, or null when the episode is not registered
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the owning user
     * @param exerciseId the exercise the episode belongs to
     * @param outcome    the terminal outcome to record
     * @return {@code true} if a terminal outcome is established for the episode; {@code false} if none could be
     *         established yet (unregistered episode with no message row - the caller should back-fill once one exists)
     */
    boolean recordOutcomeUnderLockInCurrentTransaction(@Nullable IrisProactiveEpisode episode, String episodeId, long userId, long exerciseId, IrisProactiveOutcome outcome) {
        if (episode == null) {
            // Unregistered: the outcome has nowhere to live but the message row, which is exactly where it lived
            // before the registry. Writing it there keeps such an episode behaving as it always did.
            return writeLegacyEpisodeOutcome(episodeId, outcome, userId, exerciseId);
        }
        // Under the write lock nothing else can establish an outcome between this read and the write below, so the
        // first terminal value is decided here rather than raced for.
        var standing = episode.getOutcome();
        if (standing == null) {
            irisProactiveEpisodeRepository.setOutcomeIfNull(episode.getId(), outcome);
            standing = outcome;
        }
        // Mirror what actually STANDS, not what came in: for an episode that is already terminal the two differ, and
        // the subordinate message row must not claim an outcome the registry rejected.
        mirrorOutcomeOntoMessageRow(episodeId, userId, exerciseId, standing);
        // A registered episode can always record an outcome, even before its first message exists. That is the whole
        // point of the registry, and it is why this never defers.
        return true;
    }

    /**
     * The pre-registry outcome write, kept for episodes that have no registry row: those from before this feature
     * carried one, or a job still in flight across the deployment that introduced it. Behaviour is unchanged, down to
     * returning {@code false} when no message row exists yet so the client back-fills once one does.
     *
     * <p>
     * Race-safe without a pessimistic lock or a same-table subquery (which would trip MySQL 1093), because there is
     * no row to lock: the target is the episode's SMALLEST-id message row, and ids are monotonic, so a row persisting
     * later can never become the target and two concurrent writers pick the SAME one. An episode-wide pre-check makes
     * that stable under out-of-order persistence, the row-scoped {@code WHERE id = ? AND proactive_outcome IS NULL}
     * guard makes the write land at most once, and a zero-row update falls back to re-reading episode-wide so a row
     * that vanished is reported as deferred rather than as a false {@code applied=true}.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param outcome    the terminal outcome to write
     * @param userId     the requesting user
     * @param exerciseId the exercise the episode belongs to
     * @return whether a terminal outcome now stands for the episode
     */
    private boolean writeLegacyEpisodeOutcome(String episodeId, IrisProactiveOutcome outcome, long userId, long exerciseId) {
        var episodeRowIds = irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc(episodeId, userId, exerciseId);
        if (episodeRowIds.isEmpty()) {
            return false;  // DEFERRED: no row persisted yet for this episode under this user's scope; client must back-fill
        }
        var targetId = episodeRowIds.getFirst();
        // Episode-wide first-terminal-wins: if any row already holds an outcome, this is a no-op (applied = true).
        // Deliberately a fresh query rather than a scan of the rows just loaded. This path holds no lock, so the
        // re-read is what lets it observe an outcome another transaction committed since that load.
        if (!irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty()) {
            return true;
        }
        // Write to the episode's stable smallest-id row, guarded on that row still being null (row-scoped, MySQL-safe).
        int updated = irisMessageRepository.setProactiveOutcomeIfNull(targetId, outcome);
        if (updated == 0) {
            // The target was concurrently given an outcome or deleted: only report applied if an outcome now stands.
            return !irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty();
        }
        return true;
    }

    /**
     * Copy the episode's standing outcome onto its first-persisted message row, so the history replayed to Pyris and
     * the message DTO keep carrying it. Subordinate to the registry: this is a projection, not the decision, and it
     * simply does nothing while the episode has no message row yet.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the owning user
     * @param exerciseId the exercise the episode belongs to
     * @param outcome    the outcome that stands on the registry
     */
    private void mirrorOutcomeOntoMessageRow(String episodeId, long userId, long exerciseId, IrisProactiveOutcome outcome) {
        var episodeRowIds = irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc(episodeId, userId, exerciseId);
        if (episodeRowIds.isEmpty()) {
            return;
        }
        irisMessageRepository.setProactiveOutcomeIfNull(episodeRowIds.getFirst(), outcome);
    }

    /**
     * Record the ambient hint Artemis is about to offer, so the later reveal can persist the server's own text
     * instead of whatever the caller sends back.
     *
     * <p>
     * A repeated decision callback for the same episode must not create a second offer, and must not overwrite one
     * the student has already revealed. Both fall out of the offer living on the episode row: there is exactly one,
     * a repeat callback refreshes its text, and a consumed one is left alone because its message already exists.
     *
     * <p>
     * The caller holds this row write-locked and this runs inside that transaction, so the terminal check and the
     * offer commit together. It must NOT open a transaction of its own: updating the same row from a nested one
     * would block on the lock the outer transaction is holding. That is also the one behaviour change from the
     * separate ambient table, where the offer committed independently and survived a caller rollback. Nothing on
     * the success path depended on it, because the websocket event only goes out after the caller commits.
     *
     * @param episode  the episode, already write-locked by the caller
     * @param hintText the hint as authored by Pyris
     * @return {@code true} when the episode now carries a revealable (unconsumed) offer the client may be pointed
     *         at; {@code false} when the student already revealed this episode's previous offer. The caller
     *         announces an ambient pointer only on {@code true}, so it never sends the client to a reveal that
     *         would 409.
     */
    private boolean recordAmbientOffer(IrisProactiveEpisode episode, String hintText) {
        if (episode.getConsumedAt() != null) {
            // The student already revealed this episode's offer, so its message exists and there is nothing fresh to
            // surface. Overwriting the text here would rewrite history the student has already seen.
            log.debug("Ambient offer for episode {} not recorded: the previous offer was already revealed", episode.getEpisodeId());
            return false;
        }
        episode.setHintText(hintText);
        irisProactiveEpisodeRepository.save(episode);
        return true;
    }

    /**
     * The episode's row under the caller's write lock, plus whether it is terminal. The two travel together because
     * every caller that finds it non-terminal goes on to write to that same row, and looking it up again would both
     * cost a round-trip and risk mutating a different instance than the one the lock attached to. A null
     * {@code episode} means the episode has no registry row, where {@code terminal} comes from the message rows
     * instead, exactly as it did before the registry existed.
     *
     * @param episode  the locked row, or null when the episode is not registered
     * @param terminal whether a terminal outcome stands for the episode
     */
    record LockedEpisode(@Nullable IrisProactiveEpisode episode, boolean terminal) {
    }
}
