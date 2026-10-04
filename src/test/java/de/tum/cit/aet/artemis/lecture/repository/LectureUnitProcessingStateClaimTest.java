package de.tum.cit.aet.artemis.lecture.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
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
    private LectureUnitProcessingStateReconcileRepository reconcileStateRepository;

    @Autowired
    private LectureUnitProcessingStateRecoveryRepository recoveryRepository;

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureContentProcessingScheduler scheduler;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

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
        assertThat(processingStateRepository.claimRetryEligible(retryable.getId(), "claim-1", now, leaseExpiry)).as("the first caller claims the retry").isEqualTo(1);
        assertThat(processingStateRepository.claimRetryEligible(retryable.getId(), "claim-2", now, leaseExpiry)).as("a second caller must lose the race").isZero();

        assertThat(retryCandidateIds(now)).as("the claim must hide the row for the length of the lease").doesNotContain(retryable.getId());
        assertThat(retryCandidateIds(leaseExpiry.plusSeconds(1))).as("an abandoned claim recovers itself once the lease lapses").contains(retryable.getId());
    }

    @Test
    void testAbandonedDispatchClaimIsReleasedAndAFreshOneIsNot() {
        ZonedDateTime now = ZonedDateTime.now();
        LectureUnitProcessingState idle = new LectureUnitProcessingState(unit);
        idle.setPhase(ProcessingPhase.IDLE);
        processingStateRepository.save(idle);

        assertThat(processingStateRepository.claimIdleForDispatch(idle.getId(), "claim-1", now)).as("the first caller claims the dispatch").isEqualTo(1);
        assertThat(processingStateRepository.claimIdleForDispatch(idle.getId(), "claim-2", now)).as("a second caller must lose the race").isZero();
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
     * Claudia-Anthropica review finding on PR #13798. A claim used to be identified by the whole-second timestamp it
     * wrote, so two claims of the same row taken within one second were indistinguishable and the earlier one could
     * activate the newer, attaching its stale job token and content fingerprint. This reproduces that interleaving
     * directly -- claim, requeue, re-claim, all with one timestamp -- and pins that the superseded claim now matches
     * nothing. It is the regression this column exists for; with identity back on the timestamp it fails.
     */
    @Test
    void testASupersededClaimCannotActivateAClaimTakenInTheSameSecond() {
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LectureUnitProcessingState idle = new LectureUnitProcessingState(unit);
        idle.setPhase(ProcessingPhase.IDLE);
        processingStateRepository.save(idle);

        assertThat(processingStateRepository.claimIdleForDispatch(idle.getId(), "claim-A", now)).as("worker A claims the unit").isEqualTo(1);

        // A content update requeues the unit while A is still preparing, and a concurrent pull claim takes it again.
        // Same wall-clock second, so the timestamps of both claims are identical: only the identity tells them apart.
        assertThat(processingStateRepository.requeueIfStillClaimed(idle.getId(), "claim-A", now)).as("the content update releases A's claim").isEqualTo(1);
        assertThat(processingStateRepository.claimIdleForDispatch(idle.getId(), "claim-B", now)).as("worker B claims the requeued unit in the same second").isEqualTo(1);

        assertThat(processingStateRepository.activatePushDispatch(unit.getId(), ProcessingPhase.INGESTING, "stale-token", "stale-fingerprint", "claim-A", ZonedDateTime.now()))
                .as("A's activation must not take over B's claim").isZero();

        LectureUnitProcessingState afterStaleActivation = processingStateRepository.findById(idle.getId()).orElseThrow();
        assertThat(afterStaleActivation.getPhase()).as("B's claim must still be unactivated").isEqualTo(ProcessingPhase.IDLE);
        assertThat(afterStaleActivation.getIngestionJobToken()).as("A's stale token must not have been attached").isNull();

        assertThat(processingStateRepository.activatePushDispatch(unit.getId(), ProcessingPhase.INGESTING, "current-token", "current-fingerprint", "claim-B", ZonedDateTime.now()))
                .as("B's own activation must still succeed").isEqualTo(1);

        LectureUnitProcessingState activated = processingStateRepository.findById(idle.getId()).orElseThrow();
        assertThat(activated.getIngestionJobToken()).isEqualTo("current-token");
        assertThat(activated.getContentFingerprint()).as("the current content's fingerprint must be the one certified").isEqualTo("current-fingerprint");
        assertThat(activated.getClaimToken()).as("activation consumes the claim, so nothing can match it afterwards").isNull();
    }

    /** Every path that releases a claim must drop its identity, or a late activation could still match the dead claim. */
    @Test
    void testReleasingAClaimDropsItsIdentity() {
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LectureUnitProcessingState idle = new LectureUnitProcessingState(unit);
        idle.setPhase(ProcessingPhase.IDLE);
        processingStateRepository.save(idle);

        assertThat(processingStateRepository.claimIdleForDispatch(idle.getId(), "claim-A", now)).isEqualTo(1);
        processingStateRepository.releaseAbandonedIdleClaims(now.plusMinutes(20), now);

        assertThat(processingStateRepository.findById(idle.getId()).orElseThrow().getClaimToken()).as("the abandoned-claim sweep must drop the identity").isNull();
        assertThat(processingStateRepository.activatePushDispatch(unit.getId(), ProcessingPhase.INGESTING, "late-token", "fingerprint", "claim-A", ZonedDateTime.now()))
                .as("an activation for the released claim must match nothing").isZero();
    }

    /**
     * The content check reads the row, hashes files and calls Iris before writing. A run claimed and activated during
     * that window used to be reverted by the whole-entity save that followed, leaving Pyris working on a job the row
     * no longer tracked. Recording the markers must leave the live run alone.
     */
    @Test
    void testRecordingContentMarkersDoesNotRevertARunActivatedMeanwhile() {
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LectureUnitProcessingState idle = new LectureUnitProcessingState(unit);
        idle.setPhase(ProcessingPhase.IDLE);
        processingStateRepository.save(idle);

        // A dispatch claims and activates the unit while the content check is still running.
        assertThat(processingStateRepository.claimIdleForDispatch(idle.getId(), "claim-A", now)).isEqualTo(1);
        assertThat(processingStateRepository.activatePushDispatch(unit.getId(), ProcessingPhase.INGESTING, "live-token", "fp", "claim-A", now)).isEqualTo(1);

        processingStateRepository.updateContentMarkers(idle.getId(), "video-hash", 7, 0, ZonedDateTime.now());

        LectureUnitProcessingState after = processingStateRepository.findById(idle.getId()).orElseThrow();
        assertThat(after.getPhase()).as("the live run must survive the marker update").isEqualTo(ProcessingPhase.INGESTING);
        assertThat(after.getIngestionJobToken()).as("the live run's token must survive").isEqualTo("live-token");
        assertThat(after.getVideoSourceHash()).as("the marker must still have been recorded").isEqualTo("video-hash");
        assertThat(after.getAttachmentVersion()).isEqualTo(7);
    }

    /** A content change supersedes whatever was in flight, and drops the evidence describing the old content. */
    @Test
    void testRequeueForContentChangeSupersedesTheRunAndDropsStaleEvidence() {
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LectureUnitProcessingState state = new LectureUnitProcessingState(unit);
        state.setPhase(ProcessingPhase.IDLE);
        processingStateRepository.save(state);
        assertThat(processingStateRepository.claimIdleForDispatch(state.getId(), "claim-A", now)).isEqualTo(1);
        assertThat(processingStateRepository.activatePushDispatch(unit.getId(), ProcessingPhase.INGESTING, "old-token", "old-fp", "claim-A", now)).isEqualTo(1);

        processingStateRepository.requeueForContentChange(state.getId(), "new-video-hash", 9, 0, ZonedDateTime.now());

        LectureUnitProcessingState after = processingStateRepository.findById(state.getId()).orElseThrow();
        assertThat(after.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        assertThat(after.getIngestionJobToken()).as("the superseded run's token must be gone so its callback is dropped").isNull();
        assertThat(after.getClaimToken()).isNull();
        assertThat(after.getStartedAt()).as("the row must be back in the dispatch queue").isNull();
        assertThat(after.getConfirmedFingerprint()).as("evidence describing the replaced content must not survive").isNull();
        assertThat(after.getVideoSourceHash()).isEqualTo("new-video-hash");
    }

    /** Settling a unit with no processable content leaves nothing behind that claims the index still holds its data. */
    @Test
    void testSettlingAsNothingIndexedDropsEveryContentMarker() {
        LectureUnitProcessingState state = new LectureUnitProcessingState(unit);
        state.setPhase(ProcessingPhase.IDLE);
        state.setVideoSourceHash("hash");
        state.setAttachmentVersion(3);
        state.setContentFingerprint("fp");
        state.setConfirmedFingerprint("fp");
        processingStateRepository.save(state);

        assertThat(processingStateRepository.settleAsNothingIndexed(state.getId(), ZonedDateTime.now())).isEqualTo(1);

        LectureUnitProcessingState after = processingStateRepository.findById(state.getId()).orElseThrow();
        assertThat(after.getPhase()).isEqualTo(ProcessingPhase.DONE);
        assertThat(after.getConfirmedFingerprint()).as("a confirmed fingerprint would falsely certify an empty index").isNull();
        assertThat(after.getContentFingerprint()).isNull();
        assertThat(after.getVideoSourceHash()).isNull();
        assertThat(after.getAttachmentVersion()).isNull();
    }

    /**
     * Restart recovery resets in-flight runs from a batch read. A terminal callback landing between that read and the
     * reset used to revert the finished run to IDLE and re-ingest work that had already completed.
     */
    @Test
    void testRecoveryDoesNotRevertARunThatCompletedSinceTheBatchRead() {
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LectureUnitProcessingState running = new LectureUnitProcessingState(unit);
        running.setPhase(ProcessingPhase.INGESTING);
        running.setIngestionJobToken("run-token");
        processingStateRepository.save(running);

        // The run completes between the batch read (which saw INGESTING/run-token) and the recovery write.
        assertThat(processingStateRepository.completeIngestionIfLive(running.getId(), "run-token", ZonedDateTime.now())).isEqualTo(1);

        assertThat(processingStateRepository.resetToIdleIfStillLive(running.getId(), ProcessingPhase.INGESTING, "run-token", ZonedDateTime.now()))
                .as("recovery must not revert a run that finished since the read").isZero();
        assertThat(processingStateRepository.findById(running.getId()).orElseThrow().getPhase()).isEqualTo(ProcessingPhase.DONE);
    }

    /** The same reset must still apply to a run that really is still in flight. */
    @Test
    void testRecoveryResetsARunThatIsStillInFlight() {
        LectureUnitProcessingState running = new LectureUnitProcessingState(unit);
        running.setPhase(ProcessingPhase.INGESTING);
        running.setIngestionJobToken("run-token");
        processingStateRepository.save(running);

        assertThat(processingStateRepository.resetToIdleIfStillLive(running.getId(), ProcessingPhase.INGESTING, "run-token", ZonedDateTime.now())).isEqualTo(1);

        LectureUnitProcessingState after = processingStateRepository.findById(running.getId()).orElseThrow();
        assertThat(after.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        assertThat(after.getIngestionJobToken()).isNull();
        assertThat(after.getStartedAt()).isNull();
    }

    /**
     * Claudia-Anthropica review finding on PR #13798. The reconcile walk decides on a batch read, then hashes PDFs
     * and asks Iris for a census before writing, so the decision is always older than the write. Committing it
     * through a whole-entity save merged that stale snapshot back over every column. These pin the guard that now
     * carries the decision's own conditions into the statement, so deciding and writing cannot drift apart.
     */
    @Test
    void testReconcileRequeueIsRejectedWhenTheRowMovedOnSinceTheBatchRead() {
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LectureUnitProcessingState done = new LectureUnitProcessingState(unit);
        done.setPhase(ProcessingPhase.DONE);
        done.setConfirmedFingerprint("v1:confirmed");
        done.setVideoSourceHash("hash-new");
        processingStateRepository.save(done);

        // A concurrent completion re-confirmed the unit against different content since the batch read.
        done.setConfirmedFingerprint("v1:reconfirmed");
        processingStateRepository.save(done);

        assertThat(reconcileStateRepository.requeueForReconcileIfUnchanged(done.getId(), ProcessingPhase.DONE, "v1:confirmed", null, true, null, 2, now))
                .as("the fingerprint observed at batch-read time no longer matches, so nothing may be written").isZero();

        LectureUnitProcessingState after = processingStateRepository.findById(done.getId()).orElseThrow();
        assertThat(after.getPhase()).isEqualTo(ProcessingPhase.DONE);
        assertThat(after.getConfirmedFingerprint()).as("the fresher confirmation must survive").isEqualTo("v1:reconfirmed");
    }

    /** A content requeue moves the row to IDLE, which the phase guard must reject — that was the reported case. */
    @Test
    void testReconcileRequeueIsRejectedAfterAContentRequeueAndLeavesItsMarkersIntact() {
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LectureUnitProcessingState done = new LectureUnitProcessingState(unit);
        done.setPhase(ProcessingPhase.DONE);
        done.setConfirmedFingerprint("v1:confirmed");
        processingStateRepository.save(done);

        // The content changed and the unit was requeued with the new content's markers.
        assertThat(processingStateRepository.requeueForContentChange(done.getId(), "new-video-hash", 11, 0, ZonedDateTime.now())).isEqualTo(1);

        assertThat(reconcileStateRepository.requeueForReconcileIfUnchanged(done.getId(), ProcessingPhase.DONE, "v1:confirmed", null, true, null, 2, now))
                .as("the row is no longer the DONE unit the walk decided on").isZero();

        LectureUnitProcessingState after = processingStateRepository.findById(done.getId()).orElseThrow();
        assertThat(after.getVideoSourceHash()).as("the new content's markers must survive").isEqualTo("new-video-hash");
        assertThat(after.getAttachmentVersion()).isEqualTo(11);
    }

    /**
     * A retry claim leaves the phase FAILED, so the claim clause is what has to stop a revival wiping it. The
     * observed facts are read back <em>after</em> the claim so that the error, timestamp and count all match and
     * the claim is the only thing that differs — otherwise the timestamp pin would reject the write on its own
     * and this would pass with the claim clause deleted.
     */
    @Test
    void testRevivalIsRejectedWhileTheUnitIsClaimed() {
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LectureUnitProcessingState failed = new LectureUnitProcessingState(unit);
        failed.setPhase(ProcessingPhase.FAILED);
        failed.setErrorKey("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
        failed.setRetryEligibleAt(now.minusMinutes(5));
        processingStateRepository.save(failed);

        assertThat(processingStateRepository.claimRetryEligible(failed.getId(), "claim-R", now, now.plusMinutes(20))).isEqualTo(1);
        LectureUnitProcessingState claimed = processingStateRepository.findById(failed.getId()).orElseThrow();

        assertThat(reconcileStateRepository.reviveFailedIfUnchanged(claimed.getId(), claimed.getErrorKey(), claimed.getLastUpdated(), claimed.getRevivalCount(), 2,
                ZonedDateTime.now())).as("a claim the dispatcher is about to activate must not be wiped by a revival").isZero();

        LectureUnitProcessingState after = processingStateRepository.findById(failed.getId()).orElseThrow();
        assertThat(after.getClaimToken()).isEqualTo("claim-R");
        assertThat(after.getPhase()).isEqualTo(ProcessingPhase.FAILED);
    }

    /** The happy path: the row is untouched since the batch read, so the requeue applies with its intent. */
    @Test
    void testReconcileRequeueAppliesItsIntentWhenTheRowIsUnchanged() {
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LectureUnitProcessingState done = new LectureUnitProcessingState(unit);
        done.setPhase(ProcessingPhase.DONE);
        done.setConfirmedFingerprint("v1:confirmed");
        done.setContentFingerprint("v1:content");
        done.setVideoSourceHash("hash");
        done.setAttachmentVersion(3);
        done.setRetryCount(4);
        done.setRevivalCount(1);
        processingStateRepository.save(done);

        assertThat(reconcileStateRepository.requeueForReconcileIfUnchanged(done.getId(), ProcessingPhase.DONE, "v1:confirmed", null, true, 7, 2, now)).isEqualTo(1);

        LectureUnitProcessingState after = processingStateRepository.findById(done.getId()).orElseThrow();
        assertThat(after.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        assertThat(after.getConfirmedFingerprint()).as("a forced rebuild drops the confirmation").isNull();
        assertThat(after.isForceReingest()).isTrue();
        assertThat(after.getLastQualityPipelineVersion()).isEqualTo(7);
        assertThat(after.getRevivalCount()).as("only a revival spends the budget; a reconcile requeue leaves it").isEqualTo(1);
        assertThat(after.getRetryCount()).isZero();
        assertThat(after.getDispatchPriority()).isEqualTo(2);
        // The content markers belong to the content-change path and must never be touched by a reconcile requeue.
        assertThat(after.getVideoSourceHash()).isEqualTo("hash");
        assertThat(after.getAttachmentVersion()).isEqualTo(3);
        assertThat(after.getContentFingerprint()).isEqualTo("v1:content");
    }

    /**
     * Claudia-Anthropica follow-up finding on PR #13798. A revival is authorized by three FAILED-state facts the
     * general reconcile guard does not pin: {@code errorKey} (never revive a permanent error), {@code lastUpdated}
     * (the cooldown is measured from it) and {@code revivalCount} (the bounded budget). Between the batch read and
     * the write, the row can be claimed, dispatched, and fail right back to FAILED — {@code markFailed} clears the
     * claim and restores the phase, so phase-and-claim alone cannot tell the two failures apart.
     */
    @Test
    void testRevivalIsRejectedWhenTheUnitFailedAgainWithADifferentError() {
        LectureUnitProcessingState failed = new LectureUnitProcessingState(unit);
        failed.setPhase(ProcessingPhase.FAILED);
        failed.setErrorKey("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
        failed.setLastUpdated(ZonedDateTime.now().minusHours(4));
        processingStateRepository.save(failed);
        // What the batch read observed, read back at the database's own precision.
        LectureUnitProcessingState observed = processingStateRepository.findById(failed.getId()).orElseThrow();

        // Claimed, retried, and failed again — this time with a permanent error that must never be revived.
        failed.markFailed("artemisApp.attachmentVideoUnit.processing.error.youtubePrivate");
        processingStateRepository.save(failed);

        assertThat(reconcileStateRepository.reviveFailedIfUnchanged(observed.getId(), observed.getErrorKey(), observed.getLastUpdated(), observed.getRevivalCount(), 2,
                ZonedDateTime.now())).as("the failure that authorized this revival is not the failure on the row now").isZero();

        LectureUnitProcessingState after = processingStateRepository.findById(failed.getId()).orElseThrow();
        assertThat(after.getPhase()).isEqualTo(ProcessingPhase.FAILED);
        assertThat(after.getErrorKey()).as("the newer, permanent error must survive").isEqualTo("artemisApp.attachmentVideoUnit.processing.error.youtubePrivate");
        assertThat(after.getRevivalCount()).as("a rejected write spends no revival").isZero();
    }

    /**
     * The budget pin on its own: another node's revival already spent a unit of it while the error and timestamp
     * stayed exactly as observed, so only {@code revivalCount} can tell this decision is stale.
     */
    @Test
    void testRevivalIsRejectedWhenAnotherRevivalAlreadySpentTheBudget() {
        LectureUnitProcessingState failed = new LectureUnitProcessingState(unit);
        failed.setPhase(ProcessingPhase.FAILED);
        failed.setErrorKey("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
        failed.setLastUpdated(ZonedDateTime.now().minusHours(4));
        processingStateRepository.save(failed);
        LectureUnitProcessingState observed = processingStateRepository.findById(failed.getId()).orElseThrow();

        // The entity sets lastUpdated only explicitly, so this leaves the other two pinned facts untouched.
        failed.setRevivalCount(1);
        processingStateRepository.save(failed);

        assertThat(reconcileStateRepository.reviveFailedIfUnchanged(observed.getId(), observed.getErrorKey(), observed.getLastUpdated(), observed.getRevivalCount(), 2,
                ZonedDateTime.now())).as("the budget this decision counted on has already been spent").isZero();
        assertThat(processingStateRepository.findById(failed.getId()).orElseThrow().getRevivalCount()).isEqualTo(1);
    }

    /** The happy path: the failure is still the one that was judged, so the revival applies and spends one unit. */
    @Test
    void testRevivalAppliesWhenTheFailureIsStillTheOneThatWasJudged() {
        LectureUnitProcessingState failed = new LectureUnitProcessingState(unit);
        failed.setPhase(ProcessingPhase.FAILED);
        failed.setErrorKey("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
        failed.setLastUpdated(ZonedDateTime.now().minusHours(4));
        failed.setRetryCount(3);
        failed.setRevivalCount(1);
        failed.setConfirmedFingerprint("v1:confirmed");
        failed.setVideoSourceHash("hash");
        failed.setAttachmentVersion(3);
        processingStateRepository.save(failed);
        LectureUnitProcessingState observed = processingStateRepository.findById(failed.getId()).orElseThrow();

        assertThat(reconcileStateRepository.reviveFailedIfUnchanged(observed.getId(), observed.getErrorKey(), observed.getLastUpdated(), observed.getRevivalCount(), 2,
                ZonedDateTime.now())).isEqualTo(1);

        LectureUnitProcessingState after = processingStateRepository.findById(failed.getId()).orElseThrow();
        assertThat(after.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        assertThat(after.getErrorKey()).isNull();
        assertThat(after.getRetryCount()).as("the short retry budget starts over").isZero();
        assertThat(after.getRevivalCount()).as("exactly one revival is spent, atomically").isEqualTo(2);
        assertThat(after.getDispatchPriority()).isEqualTo(2);
        // A revival re-runs the unit exactly as it stands: it owns none of these columns.
        assertThat(after.getConfirmedFingerprint()).isEqualTo("v1:confirmed");
        assertThat(after.getVideoSourceHash()).isEqualTo("hash");
        assertThat(after.getAttachmentVersion()).isEqualTo(3);
    }

    /**
     * The shape only an interrupted content change leaves behind: the token invalidation committed, but the requeue that
     * would take the row out of flight never did. The sweep's query must find it, and must not pick up a healthy run that
     * still holds its token.
     */
    @Test
    void testInterruptedContentChangeIsFoundAndAHealthyRunIsNot() {
        ZonedDateTime longAgo = ZonedDateTime.now().minusMinutes(30);
        LectureUnitProcessingState stranded = new LectureUnitProcessingState(unit);
        stranded.setPhase(ProcessingPhase.INGESTING);
        stranded.setIngestionJobToken("content-change-token");
        processingStateRepository.save(stranded);
        assertThat(processingStateRepository.invalidateTokenIfMatches(stranded.getId(), "content-change-token", longAgo)).isEqualTo(1);

        LectureUnitProcessingState healthy = new LectureUnitProcessingState(lectureUtilService.createAttachmentVideoUnitWithoutAttachment(unit.getLecture()));
        healthy.setPhase(ProcessingPhase.INGESTING);
        healthy.setIngestionJobToken("live-token");
        healthy.setLastUpdated(longAgo);
        processingStateRepository.save(healthy);

        ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(20);
        List<Long> found = recoveryRepository.findStrandedRuns(cutoff).stream().map(LectureUnitProcessingState::getId).toList();
        assertThat(found).contains(stranded.getId()).doesNotContain(healthy.getId());

        // Only one recovery claims it, and a healthy run can never be claimed
        ZonedDateTime now = ZonedDateTime.now();
        assertThat(recoveryRepository.claimStrandedRun(stranded.getId(), "recovery-1", cutoff, now)).isEqualTo(1);
        assertThat(recoveryRepository.claimStrandedRun(stranded.getId(), "recovery-2", cutoff, now)).as("a second pass that read the same batch").isZero();
        assertThat(recoveryRepository.claimStrandedRun(healthy.getId(), "recovery-3", cutoff, now)).isZero();
        assertThat(recoveryRepository.findStrandedRuns(cutoff).stream().map(LectureUnitProcessingState::getId)).as("the claim hides it until the cutoff passes again")
                .doesNotContain(stranded.getId());
    }

    /**
     * The interleaving the recovery claim exists for: an edit requeues the unit after the sweep claimed it, and a newer run is
     * dispatched before the recovery writes. The edit's requeue clears the claim, so every recovery write matches nothing and
     * the newer run's state and transcript survive.
     */
    @Test
    void testRecoveryWritesNothingOnceAnEditTookTheUnitOver() {
        ZonedDateTime longAgo = ZonedDateTime.now().minusMinutes(30);
        LectureUnitProcessingState stranded = new LectureUnitProcessingState(unit);
        stranded.setPhase(ProcessingPhase.INGESTING);
        stranded.setIngestionJobToken("content-change-token");
        processingStateRepository.save(stranded);
        processingStateRepository.invalidateTokenIfMatches(stranded.getId(), "content-change-token", longAgo);
        ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(20);
        assertThat(recoveryRepository.claimStrandedRun(stranded.getId(), "recovery", cutoff, ZonedDateTime.now())).isEqualTo(1);

        // The edit's own requeue, then a newer run activated by a fresh claim, with its transcript stored
        processingStateRepository.requeueForContentChange(stranded.getId(), "new-video-hash", null, 0, ZonedDateTime.now());
        assertThat(processingStateRepository.claimIdleForDispatch(stranded.getId(), "dispatch-claim", ZonedDateTime.now())).isEqualTo(1);
        assertThat(processingStateRepository.activatePushDispatch(unit.getId(), ProcessingPhase.TRANSCRIBING, "newer-run-token", "v1:new", "dispatch-claim", ZonedDateTime.now()))
                .isEqualTo(1);
        lectureTranscriptionRepository.save(new LectureTranscription("en", List.of(new LectureTranscriptionSegment(0.0, 1.0, "Newer run", 0)), unit));

        assertThat(lectureTranscriptionRepository.deleteIfRecoveryClaimHolds(unit.getId(), "recovery")).isZero();
        assertThat(recoveryRepository.requeueStrandedRunIfClaimed(stranded.getId(), "recovery", "old-hash", null, 0, ZonedDateTime.now())).isZero();
        assertThat(recoveryRepository.settleStrandedRunIfClaimed(stranded.getId(), "recovery", ZonedDateTime.now())).isZero();

        LectureUnitProcessingState after = processingStateRepository.findById(stranded.getId()).orElseThrow();
        assertThat(after.getIngestionJobToken()).isEqualTo("newer-run-token");
        assertThat(after.getPhase()).isEqualTo(ProcessingPhase.TRANSCRIBING);
        assertThat(lectureTranscriptionRepository.findByLectureUnit_Id(unit.getId())).isPresent();
    }

    /**
     * While the recovery still holds its claim, it deletes the previous video's transcript and requeues the unit for its
     * current content.
     */
    @Test
    void testRecoveryDeletesAndRequeuesWhileItsClaimHolds() {
        ZonedDateTime longAgo = ZonedDateTime.now().minusMinutes(30);
        LectureUnitProcessingState stranded = new LectureUnitProcessingState(unit);
        stranded.setPhase(ProcessingPhase.TRANSCRIBING);
        stranded.setIngestionJobToken("content-change-token");
        processingStateRepository.save(stranded);
        processingStateRepository.invalidateTokenIfMatches(stranded.getId(), "content-change-token", longAgo);
        lectureTranscriptionRepository.save(new LectureTranscription("en", List.of(new LectureTranscriptionSegment(0.0, 1.0, "Old video", 1)), unit));
        assertThat(recoveryRepository.claimStrandedRun(stranded.getId(), "recovery", ZonedDateTime.now().minusMinutes(20), ZonedDateTime.now())).isEqualTo(1);

        assertThat(lectureTranscriptionRepository.deleteIfRecoveryClaimHolds(unit.getId(), "recovery")).isEqualTo(1);
        assertThat(processingStateRepository.findById(stranded.getId())).as("the transcript delete leaves the processing state alone").isPresent();
        assertThat(recoveryRepository.requeueStrandedRunIfClaimed(stranded.getId(), "recovery", "current-hash", null, 0, ZonedDateTime.now())).isEqualTo(1);

        LectureUnitProcessingState after = processingStateRepository.findById(stranded.getId()).orElseThrow();
        assertThat(after.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        assertThat(after.getClaimToken()).isNull();
        assertThat(after.getVideoSourceHash()).isEqualTo("current-hash");
        assertThat(lectureTranscriptionRepository.findByLectureUnit_Id(unit.getId())).isEmpty();
    }

    /**
     * A raw checkpoint delayed past the enriched one finds the run INGESTING and is turned away before it writes anything.
     */
    @Test
    void testRawLivenessTouchIsRefusedOnceTheRunIsIngesting() {
        LectureUnitProcessingState running = new LectureUnitProcessingState(unit);
        running.setPhase(ProcessingPhase.INGESTING);
        running.setIngestionJobToken("run-token");
        processingStateRepository.save(running);

        assertThat(processingStateRepository.touchLastUpdated(running.getId(), "run-token", ZonedDateTime.now())).isZero();
        assertThat(processingStateRepository.recordTranscriptionVersionIfTranscribing(running.getId(), "run-token", 2, "hash")).isZero();
    }

    /**
     * Two heartbeats of one run can overlap when a request is slow enough for Iris to time out and move on. Whether a
     * reported counter is progress depends on the stored one, so the comparison has to run against the committed row:
     * the older heartbeat must block on the newer one's row lock and then see 41, rather than write its stale 40 back
     * over it and roll the stall clock back with it.
     */
    @Test
    void testOverlappingHeartbeatsCannotRollProgressBack() throws Exception {
        raiseLockTimeoutOnH2();
        LectureUnitProcessingState running = new LectureUnitProcessingState(unit);
        running.setPhase(ProcessingPhase.INGESTING);
        running.setIngestionJobToken("heartbeat-token");
        running.recordStageProgress("vision", 40, 180);
        processingStateRepository.save(running);
        long stateId = running.getId();

        var holderHasLock = new CountDownLatch(1);
        var releaseHolder = new CountDownLatch(1);
        var holder = Executors.newSingleThreadExecutor();
        var staleHeartbeat = Executors.newSingleThreadExecutor();
        try {
            // The newer heartbeat (41) applies and keeps its transaction, and with it the row lock, open until released.
            var newer = holder.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                Optional<LectureUnitProcessingState> advanced = processingStateRepository.applyHeartbeatLocked(stateId, "heartbeat-token", ZonedDateTime.now(), "vision", 41, 180);
                holderHasLock.countDown();
                try {
                    releaseHolder.await(30, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return advanced.isPresent();
            }));
            assertThat(holderHasLock.await(10, TimeUnit.SECONDS)).isTrue();

            var older = staleHeartbeat.submit(() -> processingStateRepository.applyHeartbeatLocked(stateId, "heartbeat-token", ZonedDateTime.now(), "vision", 40, 180));

            assertThatThrownBy(() -> older.get(2, TimeUnit.SECONDS)).as("the older heartbeat must wait for the newer one's row lock").isInstanceOf(TimeoutException.class);

            releaseHolder.countDown();
            assertThat(newer.get(30, TimeUnit.SECONDS)).as("41 is progress over the stored 40").isTrue();
            assertThat(older.get(30, TimeUnit.SECONDS)).as("40 is not progress over the committed 41").isEmpty();
        }
        finally {
            releaseHolder.countDown();
            holder.shutdownNow();
            staleHeartbeat.shutdownNow();
        }

        assertThat(processingStateRepository.findById(stateId).orElseThrow().getStageProgress()).isEqualTo(41);
    }

    // The heartbeat's FOR UPDATE blocks on the held lock. H2 gives up after one second by default, so raise its limit
    // rather than loosen the assertion; a no-op on the other engines.
    private void raiseLockTimeoutOnH2() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:")) {
                return;
            }
            try (var statement = connection.createStatement()) {
                statement.execute("SET DEFAULT_LOCK_TIMEOUT 10000");
            }
        }
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

    private LectureUnitProcessingState claimedStrandedRun(AttachmentVideoUnit target, String claimToken) {
        LectureUnitProcessingState stranded = new LectureUnitProcessingState(target);
        stranded.setPhase(ProcessingPhase.INGESTING);
        stranded.setIngestionJobToken("content-change-token");
        stranded.setVideoSourceHash("old-hash");
        processingStateRepository.save(stranded);
        processingStateRepository.invalidateTokenIfMatches(stranded.getId(), "content-change-token", ZonedDateTime.now().minusMinutes(30));
        assertThat(recoveryRepository.claimStrandedRun(stranded.getId(), claimToken, ZonedDateTime.now().minusMinutes(20), ZonedDateTime.now())).isEqualTo(1);
        return stranded;
    }

    /**
     * The recovery's Iris deletion cannot be pinned to its claim. When an edit took the unit over and a newer run then wrote
     * content, which the deletion may have removed, that run is requeued with the edit's markers intact, as the edit's own
     * closing requeue would have done had it come last.
     */
    @Test
    void testRepairRequeueRestartsARunThatTookTheUnitOverDuringTheRecovery() {
        LectureUnitProcessingState stranded = claimedStrandedRun(unit, "recovery");
        processingStateRepository.requeueForContentChange(stranded.getId(), "new-hash", 2, 0, ZonedDateTime.now());
        assertThat(processingStateRepository.claimIdleForDispatch(stranded.getId(), "dispatch-claim", ZonedDateTime.now())).isEqualTo(1);
        assertThat(processingStateRepository.activatePushDispatch(unit.getId(), ProcessingPhase.INGESTING, "newer-run-token", "v1:new", "dispatch-claim", ZonedDateTime.now()))
                .isEqualTo(1);

        // In flight: the run is cancelled and requeued, so its later completion matches nothing
        assertThat(recoveryRepository.requeueRunExposedToRecoveryCleanup(unit.getId(), "recovery", 0, ZonedDateTime.now())).isEqualTo(1);
        LectureUnitProcessingState requeued = processingStateRepository.findById(stranded.getId()).orElseThrow();
        assertThat(requeued.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        assertThat(requeued.getIngestionJobToken()).isNull();
        assertThat(requeued.getClaimToken()).isNull();
        assertThat(requeued.getVideoSourceHash()).as("the edit's markers are kept").isEqualTo("new-hash");
        assertThat(requeued.getAttachmentVersion()).isEqualTo(2);
        assertThat(processingStateRepository.completeIngestionIfLive(stranded.getId(), "newer-run-token", ZonedDateTime.now())).isZero();

        // DONE: a run that completed before the deletion landed is requeued as well
        assertThat(processingStateRepository.claimIdleForDispatch(stranded.getId(), "dispatch-claim-2", ZonedDateTime.now())).isEqualTo(1);
        processingStateRepository.activatePushDispatch(unit.getId(), ProcessingPhase.INGESTING, "third-run-token", "v1:new", "dispatch-claim-2", ZonedDateTime.now());
        assertThat(processingStateRepository.completeIngestionIfLive(stranded.getId(), "third-run-token", ZonedDateTime.now())).isEqualTo(1);
        assertThat(recoveryRepository.requeueRunExposedToRecoveryCleanup(unit.getId(), "recovery", 0, ZonedDateTime.now())).isEqualTo(1);
        LectureUnitProcessingState afterDone = processingStateRepository.findById(stranded.getId()).orElseThrow();
        assertThat(afterDone.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        assertThat(afterDone.getConfirmedFingerprint()).isNull();
    }

    /**
     * A manual retry deletes the state the recovery claimed and saves a replacement. The deletion reaches the replacement's
     * run just the same, so the repair finds it through the unit rather than through the deleted state.
     */
    @Test
    void testRepairRequeueReachesTheStateAManualRetrySavedInPlaceOfTheClaimedOne() {
        LectureUnitProcessingState stranded = claimedStrandedRun(unit, "recovery");
        processingStateRepository.deleteById(stranded.getId());
        LectureUnitProcessingState replacement = new LectureUnitProcessingState(unit);
        replacement.setVideoSourceHash("new-hash");
        replacement.setPhase(ProcessingPhase.DONE);
        replacement.setConfirmedFingerprint("v1:new");
        processingStateRepository.save(replacement);

        assertThat(recoveryRepository.requeueRunExposedToRecoveryCleanup(unit.getId(), "recovery", 0, ZonedDateTime.now())).isEqualTo(1);
        LectureUnitProcessingState requeued = processingStateRepository.findById(replacement.getId()).orElseThrow();
        assertThat(requeued.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        assertThat(requeued.getConfirmedFingerprint()).isNull();
        assertThat(requeued.getVideoSourceHash()).as("the replacement's markers are kept").isEqualTo("new-hash");
    }

    /**
     * The repair only touches a run the deletion can have reached. A row still under the recovery's own claim, an unclaimed
     * IDLE row that dispatches after the deletion anyway, and a row an edit settled because no content is left all stay as
     * they are.
     */
    @Test
    void testRepairRequeueLeavesRowsTheDeletionCannotHaveReachedAlone() {
        LectureUnitProcessingState stranded = claimedStrandedRun(unit, "recovery");
        assertThat(recoveryRepository.requeueRunExposedToRecoveryCleanup(unit.getId(), "recovery", 0, ZonedDateTime.now())).as("own claim still holds").isZero();

        processingStateRepository.requeueForContentChange(stranded.getId(), "new-hash", null, 0, ZonedDateTime.now());
        assertThat(recoveryRepository.requeueRunExposedToRecoveryCleanup(unit.getId(), "recovery", 0, ZonedDateTime.now())).as("unclaimed IDLE").isZero();

        processingStateRepository.settleAsNothingIndexed(stranded.getId(), ZonedDateTime.now());
        assertThat(recoveryRepository.requeueRunExposedToRecoveryCleanup(unit.getId(), "recovery", 0, ZonedDateTime.now())).as("settled, no content left").isZero();
        assertThat(processingStateRepository.findById(stranded.getId()).orElseThrow().getPhase()).isEqualTo(ProcessingPhase.DONE);
    }

    /**
     * The recovery clears the page mapping only while its claim holds; once an edit took the unit over, the mapping may be a
     * newer run's and stays.
     */
    @Test
    void testRecoveryClearsThePageMappingOnlyWhileItsClaimHolds() {
        AttachmentVideoUnit withAttachment = lectureUtilService.createAttachmentVideoUnit(unit.getLecture(), false);
        long attachmentId = withAttachment.getAttachment().getId();
        attachmentRepository.updateDisplayPageNumbers(attachmentId, List.of(1, 2, 3));
        LectureUnitProcessingState stranded = claimedStrandedRun(withAttachment, "recovery");

        assertThat(attachmentRepository.clearDisplayPageNumbersIfRecoveryClaimHolds(attachmentId, withAttachment.getId(), "recovery")).isEqualTo(1);
        assertThat(attachmentRepository.findById(attachmentId).orElseThrow().getDisplayPageNumbers()).isNull();

        attachmentRepository.updateDisplayPageNumbers(attachmentId, List.of(4, 5));
        processingStateRepository.requeueForContentChange(stranded.getId(), "new-hash", 2, 0, ZonedDateTime.now());
        assertThat(attachmentRepository.clearDisplayPageNumbersIfRecoveryClaimHolds(attachmentId, withAttachment.getId(), "recovery")).isZero();
        assertThat(attachmentRepository.findById(attachmentId).orElseThrow().getDisplayPageNumbers()).containsExactly(4, 5);
    }
}
