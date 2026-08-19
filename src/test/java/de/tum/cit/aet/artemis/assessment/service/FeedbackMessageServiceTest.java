package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.repository.FeedbackMessageRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.FeedbackMessageCleanupRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

class FeedbackMessageServiceTest extends AbstractSpringIntegrationIndependentBatchTest {

    @Autowired
    private FeedbackMessageService feedbackMessageService;

    @Autowired
    private FeedbackMessageRepository feedbackMessageRepository;

    @Autowired
    private FeedbackMessageCleanupRepository feedbackMessageCleanupRepository;

    @Test
    void getOrCreateDeduplicatesByText() {
        var message = feedbackMessageService.getOrCreate("dedup test message");
        var reused = feedbackMessageService.getOrCreate("dedup test message");
        assertThat(reused.getId()).isEqualTo(message.getId());
    }

    @Test
    void garbageCollectionHonorsRefreshedGraceTimestamp() {
        var message = feedbackMessageService.getOrCreate("gc grace message");

        // age the (unreferenced) row far beyond the grace period, then reuse it - the reuse must refresh
        // the grace timestamp so the row cannot be collected before the referencing feedback commits
        feedbackMessageRepository.refreshCreatedDate(message.getId(), ZonedDateTime.now().minusDays(30));
        feedbackMessageService.getOrCreate("gc grace message");
        assertThat(feedbackMessageCleanupRepository.deleteUnreferencedFeedbackMessages(ZonedDateTime.now().minusDays(29))).isZero();
        assertThat(feedbackMessageRepository.findById(message.getId())).isPresent();

        // without a reuse, the aged unreferenced row is collected
        feedbackMessageRepository.refreshCreatedDate(message.getId(), ZonedDateTime.now().minusDays(30));
        assertThat(feedbackMessageCleanupRepository.deleteUnreferencedFeedbackMessages(ZonedDateTime.now().minusDays(29))).isEqualTo(1);
        assertThat(feedbackMessageRepository.findById(message.getId())).isEmpty();
    }
}
