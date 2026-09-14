package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void getOrCreateDeduplicatesByText() {
        var message = feedbackMessageService.getOrCreate("dedup test message");
        var reused = feedbackMessageService.getOrCreate("dedup test message");
        assertThat(reused.getId()).isEqualTo(message.getId());
    }

    @Test
    void getOrCreateResolvesEmptyInputToNoMessageRow() {
        // pass markers and successful tests without output have no message at all - the row must not be created
        assertThat(feedbackMessageService.getOrCreate(null)).isNull();
        assertThat(feedbackMessageService.getOrCreate("")).isNull();
    }

    @Test
    void getOrCreateReusesTheRowOfAConcurrentWriterThatWonTheHashRace() {
        // Two nodes processing build results can insert the same text at the same time. The loser of the unique
        // hash race must return the winner's row instead of failing the build-result processing. The lookup is
        // made blind exactly once, which is what a writer sees whose competitor commits right after it looked.
        var winner = feedbackMessageService.getOrCreate("concurrently inserted message");

        var loser = new FeedbackMessageService(blindOnFirstLookup(feedbackMessageRepository), transactionManager).getOrCreate("concurrently inserted message");

        assertThat(loser.getId()).isEqualTo(winner.getId());
        assertThat(feedbackMessageRepository.findAll()).filteredOn(message -> "concurrently inserted message".equals(message.getText())).hasSize(1);
    }

    @Test
    void getOrCreateLosingTheHashRaceInsideATransactionLeavesThatTransactionUsable() {
        // The merge of a multi-container build resolves its messages inside a transaction. Losing the race there must
        // not poison that transaction: the failed insert has to be rolled back on its own, so the winner's row can be
        // re-read and the surrounding transaction can go on to commit.
        var winner = feedbackMessageService.getOrCreate("concurrently inserted message in a transaction");
        var loser = new FeedbackMessageService(blindOnFirstLookup(feedbackMessageRepository), transactionManager);

        var resolved = new TransactionTemplate(transactionManager).execute(status -> {
            var message = loser.getOrCreate("concurrently inserted message in a transaction");
            // the surrounding transaction keeps working after the lost race
            assertThat(feedbackMessageRepository.findByHash(message.getHash())).isPresent();
            return message;
        });

        assertThat(resolved.getId()).isEqualTo(winner.getId());
        assertThat(feedbackMessageRepository.findAll()).filteredOn(message -> "concurrently inserted message in a transaction".equals(message.getText())).hasSize(1);
    }

    @Test
    void getOrCreateInsideATransactionReturnsTheInstanceThatTransactionManages() {
        // The insert of a new message runs in its own transaction. The instance handed back must nevertheless belong to
        // the caller's persistence context: the merge of a multi-container build stores the feedback rows referencing it
        // and later merges the result, and Hibernate replaces a reference to an entity its context does not manage with
        // an uninitialized proxy. The result is reported to the client after the transaction, and reading such a proxy
        // then fails with no session, so the report never reaches the student.
        var managed = new TransactionTemplate(transactionManager).execute(status -> {
            var message = feedbackMessageService.getOrCreate("message created inside the caller's transaction");
            return entityManager.contains(message);
        });

        assertThat(managed).isTrue();
    }

    @Test
    void getOrCreateRecreatesAMessageThatLostTheRefreshAgainstACollection() {
        // The reuse refreshes the grace timestamp; if that refresh finds no row because a concurrent collection
        // already removed it, the message has to be created again rather than referenced as a deleted row.
        var collected = feedbackMessageService.getOrCreate("collected message");
        feedbackMessageRepository.deleteById(collected.getId());
        assertThat(feedbackMessageRepository.refreshCreatedDate(collected.getId(), ZonedDateTime.now())).isZero();

        var recreated = feedbackMessageService.getOrCreate("collected message");

        assertThat(recreated.getId()).isNotEqualTo(collected.getId());
        assertThat(recreated.getText()).isEqualTo("collected message");
    }

    @Test
    void garbageCollectionCountMatchesWhatItDeletes() {
        // The admin cleanup page disables its execute button while every count is zero, so a message the collection
        // would delete has to be counted too - otherwise the collection can never be triggered.
        var message = feedbackMessageService.getOrCreate("gc count message");
        var cutoff = ZonedDateTime.now().minusDays(1);

        // still inside the grace period: neither counted nor collected
        assertThat(feedbackMessageCleanupRepository.countUnreferencedFeedbackMessages(cutoff)).isZero();

        feedbackMessageRepository.refreshCreatedDate(message.getId(), ZonedDateTime.now().minusDays(30));
        assertThat(feedbackMessageCleanupRepository.countUnreferencedFeedbackMessages(cutoff)).isEqualTo(1);
        assertThat(feedbackMessageCleanupRepository.deleteUnreferencedFeedbackMessages(cutoff)).isEqualTo(1);
        assertThat(feedbackMessageCleanupRepository.countUnreferencedFeedbackMessages(cutoff)).isZero();
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

    /**
     * The repository, but the first {@code findByHash} answers empty: what a writer sees whose competitor commits
     * between that lookup and its own insert. Everything else, the failing insert above all, stays real.
     */
    private static FeedbackMessageRepository blindOnFirstLookup(FeedbackMessageRepository repository) {
        var firstLookup = new AtomicBoolean(true);
        return (FeedbackMessageRepository) Proxy.newProxyInstance(FeedbackMessageRepository.class.getClassLoader(), new Class<?>[] { FeedbackMessageRepository.class },
                (proxy, method, args) -> {
                    if ("findByHash".equals(method.getName()) && firstLookup.getAndSet(false)) {
                        return Optional.empty();
                    }
                    try {
                        return method.invoke(repository, args);
                    }
                    catch (InvocationTargetException exception) {
                        // the service reacts to the unique-constraint violation, so it has to reach it unwrapped
                        throw exception.getCause();
                    }
                });
    }
}
