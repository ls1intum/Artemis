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
 * The proactive episode's lifecycle as its callers see it: registering it, deciding whether it is terminal, and
 * asking for the writes that settle its terminal outcome and its ambient offer. The writes themselves live in
 * {@code IrisProactiveEpisodeWriteRepository}, because each is a multi-statement unit that needs a transaction
 * boundary. What is left here is not persistence: rejecting an unusable episode id, ordering the registration before
 * the offer, and turning a duplicate insert into a no-op.
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
     * Register the episode so it has a row to lock, or refresh the row a previous trigger already created for it.
     * Repeating a trigger for one episode is normal: a {@code decide} run and the {@code confirm_close} that follows
     * carry the same id. The refresh also keeps retention honest, since it moves {@code lastTriggeredAt} forward.
     *
     * <p>
     * The catch sits outside the transaction that attempted the insert, because a constraint violation marks its
     * transaction rollback-only and catching it inside would surface as an {@code UnexpectedRollbackException} at
     * commit. That is why both repository methods open their own {@code REQUIRES_NEW} transaction. The reread
     * rethrows the original failure if it finds nothing, since not every integrity violation is a duplicate key.
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
            // Another trigger won the insert race, and its row is the one every later path locks. Only a violation
            // that is not a duplicate key must surface, which the reread distinguishes.
            irisProactiveEpisodeRepository.findInNewTransaction(userId, exerciseId, episodeId).orElseThrow(() -> duplicate);
        }
    }

    /**
     * Whether the episode already has a terminal outcome persisted. Every value of the outcome enum is terminal, so
     * both branches decide on presence rather than on which one it is. Reads episode-wide across all rows tagged with
     * the episode id, so the result is stable under out-of-order persistence.
     *
     * <p>
     * The cheap, unlocked read: a fast path that lets a caller complete silently without opening a transaction, not
     * the decision. Every path that goes on to write re-checks inside the same transaction as its write, holding the
     * registry row locked where there is one. An episode with no registry row falls back to the message rows, as this
     * worked before the registry existed.
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
        // An open registry row is not proof on its own: registration reads the message rows unlocked before it
        // inserts, so an outcome committing in that window leaves the row open while a message row says otherwise.
        return !irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty();
    }

    /**
     * Episode-wide first-terminal-wins outcome write. Returns {@code true} whenever a terminal outcome is
     * established, whether this call wrote it or a prior one did. A registered episode can always record one, so the
     * only {@code false} comes from the pre-registry fallback, which defers until a message row exists.
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
            // A blank id is not an episode identity, and treating it as one makes distinct episodes share an
            // outcome. Reached from the {episodeId} path variable, which the trigger endpoint's validation misses.
            return false;
        }
        var verdict = irisProactiveEpisodeRepository.recordOutcomeUnderLock(episodeId, userId, exerciseId, outcome);
        // Established, not written by this call: another call's outcome ends the episode just as well. Only
        // DEFERRED keeps the client back-filling.
        return verdict != OutcomeWrite.DEFERRED;
    }

    /**
     * Register the episode and record the ambient hint Artemis is about to offer, so a later reveal persists the
     * server's own text rather than whatever the caller sends back. The registration runs first and on its own
     * transaction: registering from inside the offer's transaction commits independently, so the offer would write to
     * a row that transaction never locked. That ordering is why these stay two calls rather than one repository
     * method.
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
