package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.FeedbackMessage;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackMessageRepository;

/**
 * Provides content-addressed access to deduplicated {@link FeedbackMessage} rows: identical feedback
 * texts are stored exactly once, addressed by their SHA-256 hash.
 * <p>
 * Messages are immutable — a text change means resolving a different message and re-pointing the
 * referencing row, never editing a (potentially shared) message. Unreferenced messages are removed by
 * the admin orphan cleanup.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class FeedbackMessageService {

    private final FeedbackMessageRepository feedbackMessageRepository;

    public FeedbackMessageService(FeedbackMessageRepository feedbackMessageRepository) {
        this.feedbackMessageRepository = feedbackMessageRepository;
    }

    /**
     * Resolves the deduplicated message row for the given text, creating it if it does not exist yet.
     * Race-safe in both directions: concurrent build-result processing on multiple nodes may insert the
     * same text at the same time — the loser of the unique-hash race re-reads the winner's row. And a
     * reused row has its garbage-collection grace timestamp refreshed atomically, so an old, currently
     * unreferenced message cannot be collected between this lookup and the commit of the referencing
     * feedback rows; if the refresh loses against a concurrent collection, the row is re-created.
     *
     * @param text the message text; {@code null} and empty strings resolve to {@code null} (no message row)
     * @return the shared message row, or {@code null} for empty input
     */
    public FeedbackMessage getOrCreate(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        byte[] hash = FeedbackMessage.hashOf(text);
        Optional<FeedbackMessage> existing = feedbackMessageRepository.findByHash(hash);
        if (existing.isPresent() && feedbackMessageRepository.refreshCreatedDate(existing.get().getId(), ZonedDateTime.now()) == 1) {
            return existing.get();
        }
        return insertOrReRead(hash, text);
    }

    private FeedbackMessage insertOrReRead(byte[] hash, String text) {
        FeedbackMessage message = new FeedbackMessage();
        message.setHash(hash);
        message.setText(text);
        try {
            return feedbackMessageRepository.saveAndFlush(message);
        }
        catch (DataIntegrityViolationException e) {
            // another transaction inserted the same hash concurrently - use its row
            return feedbackMessageRepository.findByHash(hash).orElseThrow(() -> e);
        }
    }
}
