package de.tum.cit.aet.artemis.lecture.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.service.LectureContentProcessingScheduler;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies the claim semantics of {@link LectureUnitProcessingStateRepository} against a real database.
 * <p>
 * These are the queries that replaced {@code SELECT ... FOR UPDATE SKIP LOCKED} when the service-level transaction
 * was removed, so what they guarantee is now a property of the statements themselves rather than of a boundary.
 * The rest of the processing pipeline is covered by mock-based tests, which cannot show any of this.
 */
class LectureUnitProcessingStateClaimTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "lectureunitprocessingclaim";

    @Autowired
    private LectureUnitProcessingStateRepository processingStateRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureContentProcessingScheduler scheduler;

    private AttachmentVideoUnit unit;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        unit = lectureUtilService.createAttachmentVideoUnitWithoutAttachment(lecture);
    }

    /**
     * The regression this test exists for. A permanently failed unit — a private YouTube video, say — is stored as
     * FAILED with no scheduled retry and attempts still on the clock, because {@code handleProcessingFailure} skips
     * {@code scheduleRetry} for a non-retryable error code. That is indistinguishable from a retry claim that cleared
     * the field, so a recovery sweep keyed on "FAILED, no retry scheduled, attempts left, untouched for a while"
     * turns every permanent failure back into an eligible retry and sends the video to Iris again. Leasing the claim
     * instead of clearing it is what makes the two states distinguishable, and leaves nothing to sweep.
     */
    @Test
    void testPermanentFailureIsNeverEligibleForRetryHoweverStaleItGets() {
        LectureUnitProcessingState permanentFailure = new LectureUnitProcessingState(unit);
        permanentFailure.setPhase(ProcessingPhase.FAILED);
        permanentFailure.setErrorKey("artemisApp.attachmentVideoUnit.processing.error.youtubePrivate");
        // Well below the ceiling: "attempts left" is exactly what made this row look retryable.
        permanentFailure.setRetryCount(1);
        permanentFailure.setRetryEligibleAt(null);
        permanentFailure.setLastUpdated(ZonedDateTime.now().minusDays(30));
        processingStateRepository.save(permanentFailure);

        assertThat(processingStateRepository.findStatesReadyForRetry(ProcessingPhase.FAILED.name(), ZonedDateTime.now(), 10))
                .as("a permanent failure must never become eligible for retry, however long it sits there").isEmpty();

        // Run the real recovery path, not just the surviving query, so that re-introducing any sweep that resurrects
        // this shape fails here rather than in production.
        scheduler.processScheduledRetries();

        LectureUnitProcessingState reloaded = processingStateRepository.findById(permanentFailure.getId()).orElseThrow();
        assertThat(reloaded.getRetryEligibleAt()).as("no recovery pass may schedule a retry for a permanent failure").isNull();
        assertThat(reloaded.getPhase()).as("a permanent failure must stay failed").isEqualTo(ProcessingPhase.FAILED);
        assertThat(processingStateRepository.findStatesReadyForRetry(ProcessingPhase.FAILED.name(), ZonedDateTime.now(), 10)).isEmpty();
    }

    @Test
    void testOnlyOneCallerClaimsARetryAndTheClaimHidesTheRow() {
        ZonedDateTime now = ZonedDateTime.now();
        LectureUnitProcessingState retryable = new LectureUnitProcessingState(unit);
        retryable.setPhase(ProcessingPhase.FAILED);
        retryable.setRetryCount(1);
        retryable.setRetryEligibleAt(now.minusMinutes(5));
        processingStateRepository.save(retryable);

        assertThat(processingStateRepository.findStatesReadyForRetry(ProcessingPhase.FAILED.name(), now, 10)).extracting(LectureUnitProcessingState::getId)
                .as("a retry whose backoff has passed must be a candidate").containsExactly(retryable.getId());

        ZonedDateTime leaseExpiry = now.plusMinutes(20);
        assertThat(processingStateRepository.claimRetryEligible(retryable.getId(), now, leaseExpiry)).as("the first caller claims the retry").isEqualTo(1);
        assertThat(processingStateRepository.claimRetryEligible(retryable.getId(), now, leaseExpiry)).as("a second caller must lose the race").isZero();

        assertThat(processingStateRepository.findStatesReadyForRetry(ProcessingPhase.FAILED.name(), now, 10)).as("the claim must hide the row for the length of the lease")
                .isEmpty();
        assertThat(processingStateRepository.findStatesReadyForRetry(ProcessingPhase.FAILED.name(), leaseExpiry.plusSeconds(1), 10)).extracting(LectureUnitProcessingState::getId)
                .as("an abandoned claim recovers itself once the lease lapses").containsExactly(retryable.getId());
    }

    @Test
    void testAbandonedDispatchClaimIsReleasedAndAFreshOneIsNot() {
        ZonedDateTime now = ZonedDateTime.now();
        LectureUnitProcessingState idle = new LectureUnitProcessingState(unit);
        idle.setPhase(ProcessingPhase.IDLE);
        processingStateRepository.save(idle);

        assertThat(processingStateRepository.claimIdleForDispatch(idle.getId(), now)).as("the first caller claims the dispatch").isEqualTo(1);
        assertThat(processingStateRepository.claimIdleForDispatch(idle.getId(), now)).as("a second caller must lose the race").isZero();
        assertThat(processingStateRepository.findIdleForDispatch(now, 10)).as("a claimed row must leave the queue").isEmpty();

        assertThat(processingStateRepository.releaseAbandonedIdleClaims(now.minusMinutes(20), now)).as("a claim taken just now is still in flight").isZero();
        assertThat(processingStateRepository.releaseAbandonedIdleClaims(now.plusMinutes(20), now)).as("a claim older than the cutoff is abandoned and released").isEqualTo(1);
        assertThat(processingStateRepository.findIdleForDispatch(ZonedDateTime.now(), 10)).extracting(LectureUnitProcessingState::getId)
                .as("the released unit must be back in the queue").containsExactly(idle.getId());
    }
}
