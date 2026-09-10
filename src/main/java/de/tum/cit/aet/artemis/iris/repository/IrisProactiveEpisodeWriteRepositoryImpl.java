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
 * Both repositories are injected through an {@link ObjectProvider} and resolved per call. A fragment cannot depend on
 * the repository it is composed into without forming a cycle, and the application runs with
 * {@code spring.main.allow-circular-references: false}. The session repository would be acyclic as a plain
 * dependency, but Spring Data instantiates a fragment eagerly even when the composing repository is {@code @Lazy},
 * so injecting it directly builds {@link IrisSessionWriteRepositoryImpl} during startup. Calls go through the
 * repository proxy either way, so their writes join the boundary opened here.
 */
public class IrisProactiveEpisodeWriteRepositoryImpl implements IrisProactiveEpisodeWriteRepository {

    private static final Logger log = LoggerFactory.getLogger(IrisProactiveEpisodeWriteRepositoryImpl.class);

    private final ObjectProvider<IrisProactiveEpisodeRepository> irisProactiveEpisodeRepository;

    private final IrisMessageRepository irisMessageRepository;

    private final ObjectProvider<IrisSessionRepository> irisSessionRepository;

    public IrisProactiveEpisodeWriteRepositoryImpl(ObjectProvider<IrisProactiveEpisodeRepository> irisProactiveEpisodeRepository, IrisMessageRepository irisMessageRepository,
            ObjectProvider<IrisSessionRepository> irisSessionRepository) {
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
        // Carry over a terminal outcome this episode reached before it had a registry row, so a reused id does not
        // get a fresh open row that later checks would trust.
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
        var episode = lockEpisode(episodeId, userId, exerciseId);
        // No row despite the caller's registration means retention removed it in between. Treat it as terminal
        // rather than announcing a pointer at an episode nothing can resolve. Nothing is appended, so no session lock.
        if (episode == null || isTerminal(episode, episodeId, userId, exerciseId)) {
            return null;
        }
        if (episode.getConsumedAt() != null) {
            // Already revealed, so its message exists. Overwriting the text would rewrite what the student saw.
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
            // Return that reveal's row so a replay is idempotent rather than a second insert. First, ahead of the
            // terminal and no-offer refusals, because a revealed hint normally goes on to be dismissed: checking the
            // outcome first would turn every replay after that into a 409 for a message the student is still reading.
            return Optional.ofNullable(episode.getConsumedMessageId()).flatMap(irisMessageRepository::findById)
                    .orElseThrow(() -> new ConflictException("The ambient hint for this episode was already revealed", "IrisMessage", "revealAlreadyConsumed"));
        }
        if (episode.getOutcome() != null) {
            throw new ConflictException("The ambient hint for this episode can no longer be revealed", "IrisMessage", "revealEpisodeTerminal");
        }
        // The message rows have to be consulted too, because an outcome that committed while registration was
        // carrying over leaves the registry row open. After the session lock, for the reasons isTerminal gives.
        irisSessionRepository.getObject().findByIdWithWriteLockElseThrow(sessionId);
        if (isTerminal(null, episodeId, userId, exerciseId)) {
            throw new ConflictException("The ambient hint for this episode can no longer be revealed", "IrisMessage", "revealEpisodeTerminal");
        }
        if (episode.getHintText() == null) {
            // Registered, but nothing was ever offered. There is no server-authored text to persist and the
            // caller's copy must never be trusted. Below the terminal check, so a closed episode reports as closed.
            throw new ConflictException("No ambient hint was offered for this episode", "IrisMessage", "revealWithoutDecision");
        }

        // The guarded helper re-checks the session's exercise binding under the session write lock: the ambient lock
        // says nothing about the session, and a run for another exercise can switch it between resolution and write.
        var saved = irisSessionRepository.getObject().appendProactiveMessage(sessionId, exerciseId, episode.getHintText(), episodeId);
        if (saved == null) {
            // Fail the whole reveal rather than consuming the offer, so the student can reveal it again once the
            // session is back on this exercise.
            throw new ConflictException("Cannot persist reveal: the chat session moved to another exercise", "IrisMessage", "revealSessionConflict");
        }

        consumeOffer(episode, saved.getId());
        return saved;
    }

    @Override
    public ProactiveAppendOutcome appendProactiveMessageWithOutcome(long sessionId, long userId, long exerciseId, String text, @Nullable String episodeId,
            @Nullable IrisProactiveOutcome outcomeOnSuccess) {
        var episode = episodeId == null ? null : lockEpisode(episodeId, userId, exerciseId);
        if (episodeId != null) {
            // Session write lock before deciding, so the decision and the append it guards are one unit and the
            // terminal read runs on a view opened after that mutex was won. The append re-takes it, re-entrantly.
            irisSessionRepository.getObject().findByIdWithWriteLockElseThrow(sessionId);
            if (isTerminal(episode, episodeId, userId, exerciseId)) {
                // Nothing was written yet, so this commits rather than rolls back.
                return new ProactiveAppendOutcome(true, null);
            }
        }
        var saved = irisSessionRepository.getObject().appendProactiveMessage(sessionId, exerciseId, text, episodeId);
        if (saved != null && episodeId != null && outcomeOnSuccess != null) {
            // Same transaction as the append, still under the same lock.
            var write = recordOutcomeForLockedEpisode(episode, episodeId, userId, exerciseId, outcomeOnSuccess);
            if (write != OutcomeWrite.APPLIED) {
                // The terminal check reads the message rows without locking them, so a dismiss can commit between
                // it and this write. The guarded UPDATE detects that, and the only honest answer is to roll back.
                throw new IrisEpisodeWentTerminalException(episodeId);
            }
        }
        return new ProactiveAppendOutcome(false, saved);
    }

    // Records the outcome onto the write-locked registry row and mirrors it onto the message row. An open row whose
    // message rows already carry an outcome is reconciled to that one and reports a loss.
    private OutcomeWrite recordOutcomeForLockedEpisode(@Nullable IrisProactiveEpisode episode, String episodeId, long userId, long exerciseId, IrisProactiveOutcome outcome) {
        if (episode == null) {
            // Unregistered: the outcome has nowhere to live but the message row, as before the registry existed.
            return writeLegacyEpisodeOutcome(episodeId, outcome, userId, exerciseId);
        }
        // Under the write lock nothing else can establish a registry outcome between this read and the write below.
        var standing = episode.getOutcome();
        if (standing == null) {
            // The registry row can still be open while a message row carries an outcome, because registration reads
            // those rows unlocked. First-terminal-wins is episode-wide, so adopt what stands instead of overwriting.
            standing = irisMessageRepository.findEpisodeOutcomesForUpdate(episodeId, userId, exerciseId).stream().findFirst().orElse(null);
            if (standing != null) {
                irisProactiveEpisodeRepository.getObject().setOutcomeIfNull(episode.getId(), standing);
            }
        }
        boolean wrote = standing == null;
        if (wrote) {
            irisProactiveEpisodeRepository.getObject().setOutcomeIfNull(episode.getId(), outcome);
            standing = outcome;
        }
        // Mirror what stands, not what came in: the message row must not claim an outcome the registry rejected.
        mirrorOutcomeOntoMessageRow(episodeId, userId, exerciseId, standing);
        // A registered episode can always record an outcome, so this never defers. It can still lose to one that
        // already stands.
        return wrote ? OutcomeWrite.APPLIED : OutcomeWrite.LOST;
    }

    // The pre-registry outcome write, for an episode whose only record is its message row.
    private OutcomeWrite writeLegacyEpisodeOutcome(String episodeId, IrisProactiveOutcome outcome, long userId, long exerciseId) {
        var episodeRowIds = irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc(episodeId, userId, exerciseId);
        if (episodeRowIds.isEmpty()) {
            return OutcomeWrite.DEFERRED;  // no row persisted yet for this episode under this user's scope
        }
        var targetId = episodeRowIds.getFirst();
        // Fast path, only ever used to skip the write and report a loss, never to claim success, so a stale
        // snapshot costs nothing: the guarded UPDATE catches what this read misses.
        if (!irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty()) {
            return OutcomeWrite.LOST;
        }
        // Write to the episode's stable smallest-id row, guarded on that row still being null (row-scoped, MySQL-safe).
        int updated = irisMessageRepository.setProactiveOutcomeIfNull(targetId, outcome);
        if (updated == 0) {
            // The target was concurrently given an outcome or deleted, so this outcome did not take. Which of the
            // two decides the caller's follow-up, and that has to be read with a lock: this transaction's snapshot
            // predates the write we lost to and would report the episode as open, sending the client back-filling.
            return irisMessageRepository.findEpisodeOutcomesForUpdate(episodeId, userId, exerciseId).isEmpty() ? OutcomeWrite.DEFERRED : OutcomeWrite.LOST;
        }
        return OutcomeWrite.APPLIED;
    }

    // A projection of the registry's decision onto a message row, so the history replayed to Pyris carries it.
    // Walks the candidates in id order because the superseded-message delete holds the session row while this holds
    // the episode row, so a delete can take the row this was aiming at. The episode-wide pre-check is what keeps a
    // first row that already carries an outcome from looking like a deleted one and stamping a second row.
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

    // Timestamp and message id are set together so a consumed offer never carries only one of the two. The hint text
    // goes with them: a replay reads consumedMessageId, and that branch returns before the null-hint refusal.
    private void consumeOffer(IrisProactiveEpisode episode, long messageId) {
        episode.setConsumedAt(ZonedDateTime.now());
        episode.setConsumedMessageId(messageId);
        episode.setHintText(null);
        irisProactiveEpisodeRepository.getObject().save(episode);
    }

    // Locking is a separate step from deciding terminality, because that read also touches the message rows and has
    // to happen after the session lock, which is the caller's business.
    private @Nullable IrisProactiveEpisode lockEpisode(String episodeId, long userId, long exerciseId) {
        return irisProactiveEpisodeRepository.getObject().findForUpdate(userId, exerciseId, episodeId).orElse(null);
    }

    // Either record closes the episode: an open registry row does not settle it, because registration carries a
    // pre-registry outcome over with an unlocked read. The message read is deliberately not a locking one and every
    // appending caller runs it after taking the session write lock. A locking read would scan the (episode, exercise)
    // index and, under REPEATABLE READ, hold every row it scanned, taking message locks before the session lock while
    // deleteSupersededProactiveMessageAndCompact takes them the other way round, which deadlocks with no retry on
    // either side. Running after the session lock keeps the plain read current: the repeatable-read view opens once
    // the append's mutex is held, so it sees everything committed before the append could begin.
    private boolean isTerminal(@Nullable IrisProactiveEpisode episode, String episodeId, long userId, long exerciseId) {
        return (episode != null && episode.getOutcome() != null) || !irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty();
    }

}
