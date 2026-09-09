package de.tum.cit.aet.artemis.iris.repository;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveEpisode;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;

/**
 * Implementation of {@link IrisProactiveEpisodeWriteRepository}.
 *
 * <p>
 * The containing repository is injected through an {@link ObjectProvider} and resolved per call, for the same reason
 * as in {@link IrisSessionWriteRepositoryImpl}: a fragment cannot depend on the repository it is composed into
 * without forming a cycle, and the application runs with {@code spring.main.allow-circular-references: false}.
 * {@link IrisSessionRepository} is a plain dependency, and it never depends back on this one, so that direction is
 * acyclic. Calls into it go through its proxy, so its own writes keep their transaction semantics and simply join
 * the boundary opened here.
 */
public class IrisProactiveEpisodeWriteRepositoryImpl implements IrisProactiveEpisodeWriteRepository {

    private static final Logger log = LoggerFactory.getLogger(IrisProactiveEpisodeWriteRepositoryImpl.class);

    private final ObjectProvider<IrisProactiveEpisodeRepository> irisProactiveEpisodeRepository;

    private final IrisMessageRepository irisMessageRepository;

    private final IrisSessionRepository irisSessionRepository;

    public IrisProactiveEpisodeWriteRepositoryImpl(ObjectProvider<IrisProactiveEpisodeRepository> irisProactiveEpisodeRepository, IrisMessageRepository irisMessageRepository,
            IrisSessionRepository irisSessionRepository) {
        this.irisProactiveEpisodeRepository = irisProactiveEpisodeRepository;
        this.irisMessageRepository = irisMessageRepository;
        this.irisSessionRepository = irisSessionRepository;
    }

    @Override
    public void registerOrTouchInNewTransaction(long userId, long exerciseId, String episodeId) {
        var episodes = irisProactiveEpisodeRepository.getObject();
        if (episodes.touchLastTriggeredAt(userId, exerciseId, episodeId, ZonedDateTime.now()) > 0) {
            return;
        }
        var episode = new IrisProactiveEpisode();
        episode.setUserId(userId);
        episode.setExerciseId(exerciseId);
        episode.setEpisodeId(episodeId);
        episode.setLastTriggeredAt(ZonedDateTime.now());
        // Carry over a terminal outcome this episode already reached before it had a registry row. A trigger that
        // reuses such an id across the deployment would otherwise get a fresh open row, and every later check would
        // trust it and let a late message through for an episode the student had closed.
        irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).stream().findFirst().ifPresent(episode::setOutcome);
        episodes.saveAndFlush(episode);
    }

    @Override
    public Optional<IrisProactiveEpisode> findInNewTransaction(long userId, long exerciseId, String episodeId) {
        return irisProactiveEpisodeRepository.getObject().find(userId, exerciseId, episodeId);
    }

    @Override
    public OutcomeWrite recordOutcomeUnderLock(String episodeId, long userId, long exerciseId, IrisProactiveOutcome outcome) {
        var locked = irisProactiveEpisodeRepository.getObject().findForUpdate(userId, exerciseId, episodeId).orElse(null);
        return recordOutcomeForLockedEpisode(locked, episodeId, userId, exerciseId, outcome);
    }

    @Override
    public @Nullable Boolean recordAmbientOfferUnderLock(long userId, long exerciseId, String episodeId, String hintText) {
        var episode = lockEpisodeAndReadTerminal(episodeId, userId, exerciseId).episode();
        // No row despite the caller's registration means retention removed it in between, which takes seven quiet days
        // and a trigger that then never refreshed it. Treat it as terminal rather than announcing a pointer at an
        // episode nothing can resolve.
        if (episode == null || episode.getOutcome() != null) {
            return null;
        }
        if (episode.getConsumedAt() != null) {
            // The student already revealed this episode's offer, so its message exists and there is nothing fresh to
            // surface. Overwriting the text here would rewrite history the student has already seen.
            log.debug("Ambient offer for episode {} not recorded: the previous offer was already revealed", episode.getEpisodeId());
            return false;
        }
        episode.setHintText(hintText);
        irisProactiveEpisodeRepository.getObject().save(episode);
        return true;
    }

    @Override
    public IrisMessage revealAmbient(long userId, long exerciseId, String episodeId, long sessionId) {
        // One lock, one row. The episode's write lock is both the terminal gate and the offer's mutex.
        var episode = irisProactiveEpisodeRepository.getObject().findForUpdate(userId, exerciseId, episodeId)
                .orElseThrow(() -> new ConflictException("No ambient hint was offered for this episode", "IrisMessage", "revealWithoutDecision"));
        if (episode.getConsumedAt() != null) {
            // Already revealed. Return that reveal's row so a replay is idempotent rather than a second insert; if the
            // row is gone (superseded and deleted), the offer is spent and there is nothing to surface.
            //
            // FIRST, ahead of the terminal and the no-offer refusals below, because a revealed hint normally goes on
            // to acquire a terminal outcome: the student reads it and dismisses it. Checking the outcome first turns
            // every replay after that dismiss into a 409 for a message the student is still looking at, which is
            // exactly the idempotency this branch exists to provide. A terminal outcome ends the episode; it does not
            // un-deliver a row already written.
            return Optional.ofNullable(episode.getConsumedMessageId()).flatMap(irisMessageRepository::findById)
                    .orElseThrow(() -> new ConflictException("The ambient hint for this episode was already revealed", "IrisMessage", "revealAlreadyConsumed"));
        }
        if (episode.getOutcome() != null) {
            throw new ConflictException("The ambient hint for this episode can no longer be revealed", "IrisMessage", "revealEpisodeTerminal");
        }
        if (episode.getHintText() == null) {
            // Registered, but nothing was ever offered for it: an active decision, a silent run, or a trigger whose
            // callback never arrived. There is no server-authored text to persist and the caller's copy must never be
            // trusted, so this is the same refusal as an unknown episode.
            throw new ConflictException("No ambient hint was offered for this episode", "IrisMessage", "revealWithoutDecision");
        }

        // Append through the guarded helper, which re-checks the session's exercise binding under the session write
        // lock. The ambient lock held here says nothing about the session: a run for a DIFFERENT exercise can switch
        // this same session between the caller's resolution and the write.
        var saved = irisSessionRepository.appendProactiveMessage(sessionId, exerciseId, episode.getHintText(), episodeId);
        if (saved == null) {
            // Fail the whole reveal rather than consuming the offer: rolling back leaves the offer unconsumed, so the
            // student can reveal it again once the session is back on this exercise. Thrown from inside the boundary
            // for exactly that reason.
            throw new ConflictException("Cannot persist reveal: the chat session moved to another exercise", "IrisMessage", "revealSessionConflict");
        }

        consumeOffer(episode, saved.getId());
        return saved;
    }

    @Override
    public ProactiveAppendOutcome appendProactiveMessageWithOutcome(long sessionId, long userId, long exerciseId, String text, @Nullable String episodeId,
            @Nullable IrisProactiveOutcome outcomeOnSuccess) {
        var locked = episodeId == null ? null : lockEpisodeAndReadTerminal(episodeId, userId, exerciseId);
        if (locked != null && locked.terminal()) {
            // Nothing was written yet, so this commits rather than rolls back, exactly as before.
            return new ProactiveAppendOutcome(true, null);
        }
        var saved = irisSessionRepository.appendProactiveMessage(sessionId, exerciseId, text, episodeId);
        if (saved != null && locked != null && outcomeOnSuccess != null) {
            // Same transaction as the append, still under the same lock.
            var write = recordOutcomeForLockedEpisode(locked.episode(), episodeId, userId, exerciseId, outcomeOnSuccess);
            if (write != OutcomeWrite.APPLIED) {
                // An unregistered episode has no registry row to lock. Its fallback check is a locking read, but one
                // that can only lock rows that ALREADY carry an outcome, so a dismiss setting one on a still-null row
                // can still commit between it and this write. The guarded UPDATE inside the write is what actually
                // detects that, and the only honest answer once it does is to take the append back.
                throw new IrisEpisodeWentTerminalException(episodeId);
            }
        }
        return new ProactiveAppendOutcome(false, saved);
    }

    /**
     * Record the episode's terminal outcome onto the registry row the caller already holds write-locked, and mirror it
     * onto the message row. An unregistered episode has no row to carry the outcome, so it falls back to the
     * pre-registry write, where the message row is the only record there is.
     *
     * @param episode    the locked registry row, or null when the episode is not registered
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the owning user
     * @param exerciseId the exercise the episode belongs to
     * @param outcome    the terminal outcome to record
     * @return whether {@code outcome} is the one that now stands, a different terminal outcome won, or nothing could
     *         be recorded yet
     */
    private OutcomeWrite recordOutcomeForLockedEpisode(@Nullable IrisProactiveEpisode episode, String episodeId, long userId, long exerciseId, IrisProactiveOutcome outcome) {
        if (episode == null) {
            // Unregistered: the outcome has nowhere to live but the message row, which is exactly where it lived
            // before the registry. Writing it there keeps such an episode behaving as it always did.
            return writeLegacyEpisodeOutcome(episodeId, outcome, userId, exerciseId);
        }
        // Under the write lock nothing else can establish an outcome between this read and the write below, so the
        // first terminal value is decided here rather than raced for.
        var standing = episode.getOutcome();
        boolean wrote = standing == null;
        if (wrote) {
            irisProactiveEpisodeRepository.getObject().setOutcomeIfNull(episode.getId(), outcome);
            standing = outcome;
        }
        // Mirror what actually STANDS, not what came in: for an episode that is already terminal the two differ, and
        // the subordinate message row must not claim an outcome the registry rejected.
        mirrorOutcomeOntoMessageRow(episodeId, userId, exerciseId, standing);
        // A registered episode can always record an outcome, even before its first message exists. That is the whole
        // point of the registry, and it is why this never defers. It can still LOSE: a caller that did not gate on the
        // terminal state under this lock reaches this with an outcome already standing.
        return wrote ? OutcomeWrite.APPLIED : OutcomeWrite.LOST;
    }

    /**
     * The pre-registry outcome write, for an episode that has no registry row: the message row is the only record
     * there is.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param outcome    the terminal outcome to write
     * @param userId     the requesting user
     * @param exerciseId the exercise the episode belongs to
     * @return whether this outcome took, another one won, or there is nothing to write onto yet
     */
    private OutcomeWrite writeLegacyEpisodeOutcome(String episodeId, IrisProactiveOutcome outcome, long userId, long exerciseId) {
        var episodeRowIds = irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc(episodeId, userId, exerciseId);
        if (episodeRowIds.isEmpty()) {
            return OutcomeWrite.DEFERRED;  // no row persisted yet for this episode under this user's scope
        }
        var targetId = episodeRowIds.getFirst();
        // Episode-wide first-terminal-wins fast path: an outcome already standing means the write below cannot take.
        // Deliberately a fresh query rather than a scan of the rows just loaded. Only ever used to SKIP the write and
        // report a loss, never to claim success, so a stale snapshot here costs nothing: the guarded UPDATE catches
        // what this read misses.
        if (!irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty()) {
            return OutcomeWrite.LOST;
        }
        // Write to the episode's stable smallest-id row, guarded on that row still being null (row-scoped, MySQL-safe).
        int updated = irisMessageRepository.setProactiveOutcomeIfNull(targetId, outcome);
        if (updated == 0) {
            // The target was concurrently given an outcome or deleted. Either way this outcome did NOT take, and the
            // count is the authoritative statement of that. What is left to pick is only the caller's follow-up, and
            // that has to be read with a LOCK rather than from this transaction's snapshot: the snapshot predates the
            // write we just lost to and would report the episode as still open, which tells the client to keep
            // back-filling an outcome that can never land. A target that merely vanished, on the other hand, does
            // leave the episode open, and that back-fill is the pre-registry behaviour this path exists to keep.
            return irisMessageRepository.findEpisodeOutcomesForUpdate(episodeId, userId, exerciseId).isEmpty() ? OutcomeWrite.DEFERRED : OutcomeWrite.LOST;
        }
        return OutcomeWrite.APPLIED;
    }

    /**
     * Copy the episode's standing outcome onto one of its message rows, so the history replayed to Pyris and the
     * message DTO keep carrying it. Subordinate to the registry: this is a projection, not the decision, and it simply
     * does nothing while the episode has no message row yet.
     *
     * <p>
     * Walks the candidates in id order instead of writing blindly to the first one. The guarded update reports whether
     * the row it aimed at survived, and it can miss: this transaction holds the EPISODE row locked, while the
     * superseded-message delete holds the SESSION row, so the two do not serialize against each other. A delete
     * landing between the read and the write takes the row this projection was aiming at, the update then touches
     * nothing, and a surviving message of the same episode would be replayed to Pyris without the outcome.
     *
     * <p>
     * The episode-wide pre-check keeps the "at most one outcome row per episode" shape: without it, a first row that
     * already carries an outcome would report zero rows exactly like a deleted one, and the loop would stamp a second
     * row.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the owning user
     * @param exerciseId the exercise the episode belongs to
     * @param outcome    the outcome that stands on the registry
     */
    private void mirrorOutcomeOntoMessageRow(String episodeId, long userId, long exerciseId, IrisProactiveOutcome outcome) {
        var episodeRowIds = irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc(episodeId, userId, exerciseId);
        if (episodeRowIds.isEmpty() || !irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty()) {
            return;
        }
        for (long rowId : episodeRowIds) {
            if (irisMessageRepository.setProactiveOutcomeIfNull(rowId, outcome) == 1) {
                return;
            }
        }
    }

    /**
     * Mark the episode's offer consumed by the message the reveal just persisted. The entity is managed by this
     * transaction, so this is part of its flush. Setting the timestamp and the message id together is what keeps a
     * consumed offer from ever carrying only one of the two.
     *
     * <p>
     * The hint text goes with it. It was the server's own copy of what it had offered, kept so that the reveal
     * persists Artemis' text rather than the caller's; once the reveal has written that message the copy has no reader
     * left. A replay reads {@code consumedMessageId}, never the text, and the consumed branch of
     * {@link #revealAmbient} returns before the null-hint refusal, so clearing it here cannot turn a replay into a
     * "nothing was ever offered" 409.
     *
     * @param episode   the episode row this transaction holds write-locked
     * @param messageId the id of the message the reveal persisted
     */
    private void consumeOffer(IrisProactiveEpisode episode, long messageId) {
        episode.setConsumedAt(ZonedDateTime.now());
        episode.setConsumedMessageId(messageId);
        episode.setHintText(null);
        irisProactiveEpisodeRepository.getObject().save(episode);
    }

    /**
     * Whether the episode is terminal, decided under the episode's registry write lock so the caller can write in the
     * same transaction without anything interleaving. Falls back to the message rows for an episode that has no
     * registry row, which behaves exactly as this feature did before the registry existed.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the owning user
     * @param exerciseId the exercise the episode belongs to
     * @return the locked row (or null when unregistered) and whether a terminal outcome stands
     */
    private LockedEpisode lockEpisodeAndReadTerminal(String episodeId, long userId, long exerciseId) {
        var locked = irisProactiveEpisodeRepository.getObject().findForUpdate(userId, exerciseId, episodeId);
        if (locked.isPresent()) {
            return new LockedEpisode(locked.get(), locked.get().getOutcome() != null);
        }
        // The locking variant, so this branch keeps the promise the method's name makes. The registered branch above
        // decides under a write lock; reading the fallback without one would let an outcome commit between this read
        // and the caller's write. On MySQL it matters a second time: a plain read here would open the transaction's
        // repeatable-read view before the session row is locked further down the call, so the message list the append
        // later merges could predate a row another writer committed while we waited for that lock.
        return new LockedEpisode(null, !irisMessageRepository.findEpisodeOutcomesForUpdate(episodeId, userId, exerciseId).isEmpty());
    }

    /**
     * The episode's row under this transaction's write lock, plus whether it is terminal. The two travel together
     * because every caller that finds it non-terminal goes on to write to that same row, and looking it up again would
     * both cost a round-trip and risk mutating a different instance than the one the lock attached to.
     *
     * @param episode  the locked row, or null when the episode is not registered
     * @param terminal whether a terminal outcome stands for the episode
     */
    private record LockedEpisode(@Nullable IrisProactiveEpisode episode, boolean terminal) {
    }
}
