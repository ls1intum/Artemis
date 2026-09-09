package de.tum.cit.aet.artemis.iris.service.session;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeWriteRepository.OutcomeWrite;

/**
 * The proactive episode's lifecycle as its callers see it: registering it, deciding whether it is terminal, and asking
 * for the writes that settle its terminal outcome and its ambient offer.
 *
 * <p>
 * The writes themselves live in {@code IrisProactiveEpisodeWriteRepository}, because each one is a multi-statement
 * unit that needs a transaction boundary, and those belong in a repository. What is left here is the part that is not
 * persistence: rejecting an unusable episode id, ordering the registration before the offer, and turning a duplicate
 * insert into a no-op.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisProactiveEpisodeService {

    private final IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    private final IrisMessageRepository irisMessageRepository;

    public IrisProactiveEpisodeService(IrisProactiveEpisodeRepository irisProactiveEpisodeRepository, IrisMessageRepository irisMessageRepository) {
        this.irisProactiveEpisodeRepository = irisProactiveEpisodeRepository;
        this.irisMessageRepository = irisMessageRepository;
    }

    /**
     * Register the episode so it has a row to lock and a place to hold its terminal outcome, or refresh the row a
     * previous trigger already created for it.
     *
     * <p>
     * Repeating a trigger for one episode is normal rather than exceptional: a {@code decide} run and the
     * {@code confirm_close} run that follows it carry the same episode id. The refresh is also what keeps retention
     * honest, since it moves {@code lastTriggeredAt} forward and an episode still being triggered is never reaped.
     * The unique key decides who wins an insert race, and the loser rereads.
     *
     * <p>
     * The catch sits OUTSIDE the transaction that attempted the insert, because a constraint violation marks its
     * transaction rollback-only: catching it inside and carrying on would surface as an
     * {@code UnexpectedRollbackException} at commit rather than as the handled duplicate it is. That is why both
     * repository methods below open their own {@code REQUIRES_NEW} transaction, and why the catch stays here. The
     * reread rethrows the original failure if it finds nothing, since not every integrity violation is a duplicate
     * key.
     *
     * @param userId     the struggling student
     * @param exerciseId the exercise the run belongs to
     * @param episodeId  the client-allocated episode id, already validated as usable
     */
    void registerEpisode(long userId, long exerciseId, String episodeId) {
        try {
            irisProactiveEpisodeRepository.registerOrTouchInNewTransaction(userId, exerciseId, episodeId);
        }
        catch (DataIntegrityViolationException duplicate) {
            // Another trigger for the same episode won the insert race. The row it created is the one every later
            // path locks, so there is nothing left to do here; only a violation that is NOT a duplicate key must
            // surface, which the reread distinguishes.
            irisProactiveEpisodeRepository.findInNewTransaction(userId, exerciseId, episodeId).orElseThrow(() -> duplicate);
        }
    }

    /**
     * Returns true when the episode already has a terminal outcome persisted. Every value of the outcome enum is
     * terminal (DISMISSED, RECOVERED, ABANDONED, INTERRUPTED); both branches below decide on presence, not on which
     * one it is. Used by the branches that would deliver something, to skip what arrived after the episode ended.
     *
     * <p>
     * Reads episode-wide: checks ALL rows tagged with the episodeId, not just the earliest, so the result is
     * stable under out-of-order persistence.
     *
     * <p>
     * This is the cheap, unlocked read: a fast path that lets a caller complete silently without opening a
     * transaction. It is not the decision. Every path that goes on to write re-checks inside the same transaction as
     * its write, holding the episode's registry row locked where there is one. An episode with no registry row has no
     * row to lock, so that re-check is only as atomic as the guarded update it precedes.
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
        if (registered.isPresent() && registered.get().getOutcome() != null) {
            return true;
        }
        // An open registry row is not on its own proof that the episode is open. Registration reads the message rows
        // unlocked before it inserts, so an outcome committing in that window is not carried over and leaves the row
        // open while a message row already says otherwise. Reading both and taking either as terminal is the
        // fail-closed answer, and it is the same read an episode with no registry row has always used.
        return !irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty();
    }

    /**
     * Episode-wide first-terminal-wins outcome write. Returns {@code true} whenever a terminal outcome is established
     * for the episode, whether THIS call wrote it or a prior one did.
     *
     * <p>
     * A registered episode can always record an outcome, even before its first message exists, so the only
     * {@code false} comes from the pre-registry fallback: an episode with no registry row has nowhere but a message
     * row to put the outcome, and defers until one exists.
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
        var verdict = irisProactiveEpisodeRepository.recordOutcomeUnderLock(episodeId, userId, exerciseId, outcome);
        // "Established", not "written by this call": a terminal outcome that another call put there ends the
        // episode just as well, and the client has nothing left to back-fill. Only DEFERRED keeps it back-filling.
        return verdict != OutcomeWrite.DEFERRED;
    }

    /**
     * Register the episode and record the ambient hint Artemis is about to offer for it, so a later reveal persists
     * the server's own text rather than whatever the caller sends back.
     *
     * <p>
     * The registration runs FIRST and on its own transaction, before the offer transaction opens. Registering from
     * inside that transaction would be worse than useless: it commits independently, so the row the offer then wrote
     * to would be one that transaction never locked, and the terminal check and the write would stop being atomic.
     * That ordering is why these stay two calls here rather than one repository method.
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
        return irisProactiveEpisodeRepository.recordAmbientOfferUnderLock(userId, exerciseId, episodeId, hintText);
    }
}
