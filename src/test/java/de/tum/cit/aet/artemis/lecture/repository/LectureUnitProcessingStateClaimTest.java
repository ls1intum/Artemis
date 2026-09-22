package de.tum.cit.aet.artemis.lecture.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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

        assertThat(processingStateRepository.requeueForReconcileIfUnchanged(done.getId(), ProcessingPhase.DONE, "v1:confirmed", null, true, null, 0, 2, now))
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

        assertThat(processingStateRepository.requeueForReconcileIfUnchanged(done.getId(), ProcessingPhase.DONE, "v1:confirmed", null, true, null, 0, 2, now))
                .as("the row is no longer the DONE unit the walk decided on").isZero();

        LectureUnitProcessingState after = processingStateRepository.findById(done.getId()).orElseThrow();
        assertThat(after.getVideoSourceHash()).as("the new content's markers must survive").isEqualTo("new-video-hash");
        assertThat(after.getAttachmentVersion()).isEqualTo(11);
    }

    /** A retry claim leaves the phase FAILED, so only the claim guard stops a requeue wiping it. */
    @Test
    void testReconcileRequeueIsRejectedWhileTheUnitIsClaimed() {
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LectureUnitProcessingState failed = new LectureUnitProcessingState(unit);
        failed.setPhase(ProcessingPhase.FAILED);
        failed.setRetryEligibleAt(now.minusMinutes(5));
        processingStateRepository.save(failed);

        assertThat(processingStateRepository.claimRetryEligible(failed.getId(), "claim-R", now, now.plusMinutes(20))).isEqualTo(1);

        assertThat(processingStateRepository.requeueForReconcileIfUnchanged(failed.getId(), ProcessingPhase.FAILED, null, null, null, null, 1, 2, now))
                .as("a claim the dispatcher is about to activate must not be wiped by a reconcile requeue").isZero();
        assertThat(processingStateRepository.findById(failed.getId()).orElseThrow().getClaimToken()).isEqualTo("claim-R");
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

        assertThat(processingStateRepository.requeueForReconcileIfUnchanged(done.getId(), ProcessingPhase.DONE, "v1:confirmed", null, true, 7, 1, 2, now)).isEqualTo(1);

        LectureUnitProcessingState after = processingStateRepository.findById(done.getId()).orElseThrow();
        assertThat(after.getPhase()).isEqualTo(ProcessingPhase.IDLE);
        assertThat(after.getConfirmedFingerprint()).as("a forced rebuild drops the confirmation").isNull();
        assertThat(after.isForceReingest()).isTrue();
        assertThat(after.getLastQualityPipelineVersion()).isEqualTo(7);
        assertThat(after.getRevivalCount()).as("the revival budget is spent atomically").isEqualTo(2);
        assertThat(after.getRetryCount()).isZero();
        assertThat(after.getDispatchPriority()).isEqualTo(2);
        // The content markers belong to the content-change path and must never be touched by a reconcile requeue.
        assertThat(after.getVideoSourceHash()).isEqualTo("hash");
        assertThat(after.getAttachmentVersion()).isEqualTo(3);
        assertThat(after.getContentFingerprint()).isEqualTo("v1:content");
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
