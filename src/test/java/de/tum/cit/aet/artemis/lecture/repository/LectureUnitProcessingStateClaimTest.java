package de.tum.cit.aet.artemis.lecture.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

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
 * <p>
 * Every assertion is scoped to the row the test created. The processing state table is shared with whatever else runs
 * against the same database, so an assertion on the whole candidate list would depend on the rest of the suite.
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

        // An unclaimed IDLE row that no test touches. It is here to keep the assertions below honest: any of them
        // written against the whole candidate list rather than one row fails on this, which is how the first version
        // of this class passed locally and failed in CI, where the rest of the suite supplies rows like it.
        LectureUnitProcessingState untouched = new LectureUnitProcessingState(lectureUtilService.createAttachmentVideoUnitWithoutAttachment(lecture));
        untouched.setPhase(ProcessingPhase.IDLE);
        processingStateRepository.save(untouched);
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

        assertThat(retryCandidateIds(ZonedDateTime.now())).as("a permanent failure must never become eligible for retry, however long it sits there")
                .doesNotContain(permanentFailure.getId());

        // Run the real recovery path, not just the surviving query, so that re-introducing any sweep that resurrects
        // this shape fails here rather than in production.
        scheduler.processScheduledRetries();

        LectureUnitProcessingState reloaded = processingStateRepository.findById(permanentFailure.getId()).orElseThrow();
        assertThat(reloaded.getRetryEligibleAt()).as("no recovery pass may schedule a retry for a permanent failure").isNull();
        assertThat(reloaded.getPhase()).as("a permanent failure must stay failed").isEqualTo(ProcessingPhase.FAILED);
        assertThat(retryCandidateIds(ZonedDateTime.now())).doesNotContain(permanentFailure.getId());
    }

    @Test
    void testOnlyOneCallerClaimsARetryAndTheClaimHidesTheRow() {
        ZonedDateTime now = ZonedDateTime.now();
        LectureUnitProcessingState retryable = new LectureUnitProcessingState(unit);
        retryable.setPhase(ProcessingPhase.FAILED);
        retryable.setRetryCount(1);
        retryable.setRetryEligibleAt(now.minusMinutes(5));
        processingStateRepository.save(retryable);

        assertThat(retryCandidateIds(now)).as("a retry whose backoff has passed must be a candidate").contains(retryable.getId());

        ZonedDateTime leaseExpiry = now.plusMinutes(20);
        assertThat(processingStateRepository.claimRetryEligible(retryable.getId(), now, leaseExpiry)).as("the first caller claims the retry").isEqualTo(1);
        assertThat(processingStateRepository.claimRetryEligible(retryable.getId(), now, leaseExpiry)).as("a second caller must lose the race").isZero();

        assertThat(retryCandidateIds(now)).as("the claim must hide the row for the length of the lease").doesNotContain(retryable.getId());
        assertThat(retryCandidateIds(leaseExpiry.plusSeconds(1))).as("an abandoned claim recovers itself once the lease lapses").contains(retryable.getId());
    }

    @Test
    void testAbandonedDispatchClaimIsReleasedAndAFreshOneIsNot() {
        ZonedDateTime now = ZonedDateTime.now();
        LectureUnitProcessingState idle = new LectureUnitProcessingState(unit);
        idle.setPhase(ProcessingPhase.IDLE);
        processingStateRepository.save(idle);

        assertThat(processingStateRepository.claimIdleForDispatch(idle.getId(), now)).as("the first caller claims the dispatch").isEqualTo(1);
        assertThat(processingStateRepository.claimIdleForDispatch(idle.getId(), now)).as("a second caller must lose the race").isZero();
        assertThat(idleCandidateIds(now)).as("a claimed row must leave the queue").doesNotContain(idle.getId());

        // Asserted on the row rather than on the return value: the sweep is table-wide, so its count depends on
        // whatever else the suite left behind.
        processingStateRepository.releaseAbandonedIdleClaims(now.minusMinutes(20), now);
        assertThat(startedAtOf(idle)).as("a claim taken just now is still in flight and must keep its claim").isNotNull();

        processingStateRepository.releaseAbandonedIdleClaims(now.plusMinutes(20), now);
        assertThat(startedAtOf(idle)).as("a claim older than the cutoff is abandoned and must be released").isNull();
        assertThat(idleCandidateIds(ZonedDateTime.now())).as("the released unit must be back in the queue").contains(idle.getId());
    }

    /**
     * A generous limit, because the candidate list is shared with the rest of the suite and these tests only care
     * whether their own row is in it.
     */
    private List<Long> retryCandidateIds(ZonedDateTime now) {
        return processingStateRepository.findStatesReadyForRetry(ProcessingPhase.FAILED.name(), now, 1000).stream().map(LectureUnitProcessingState::getId).toList();
    }

    private List<Long> idleCandidateIds(ZonedDateTime now) {
        return processingStateRepository.findIdleForDispatch(now, 1000).stream().map(LectureUnitProcessingState::getId).toList();
    }

    private ZonedDateTime startedAtOf(LectureUnitProcessingState state) {
        return processingStateRepository.findById(state.getId()).orElseThrow().getStartedAt();
    }
}
