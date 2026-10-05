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

    // Registers the episode or refreshes the row a previous trigger created; repeating a trigger for one episode is
    // normal, and the refresh keeps retention honest. The catch sits outside the transaction that attempted the
    // insert, because a constraint violation marks its transaction rollback-only, which is why both repository
    // methods open their own REQUIRES_NEW transaction. The reread rethrows when it finds nothing, since not every
    // integrity violation is a duplicate key.
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

    // Whether a terminal outcome stands, read episode-wide so the answer is stable under out-of-order persistence.
    // The cheap unlocked fast path, not the decision: every path that goes on to write re-checks inside the same
    // transaction as its write, holding the registry row locked where there is one.
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

    // Registers the episode and records the ambient hint, so a later reveal persists the server's own text. The
    // registration runs first and on its own transaction, because registering from inside the offer's transaction
    // commits independently and the offer would then write to a row it never locked. Returns true when a revealable
    // offer now stands, false when the previous one was already revealed, and null when the episode went terminal.
    @Nullable
    Boolean offerAmbientHint(long userId, long exerciseId, String episodeId, String hintText) {
        registerEpisode(userId, exerciseId, episodeId);
        return irisProactiveEpisodeRepository.recordAmbientOfferUnderLock(userId, exerciseId, episodeId, hintText);
    }
}
