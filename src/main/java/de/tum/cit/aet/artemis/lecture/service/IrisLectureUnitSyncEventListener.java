package de.tum.cit.aet.artemis.lecture.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.iris.api.dtos.LectureUnitSyncOutcome;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Conditional(LectureWithIrisEnabled.class)
@Lazy
@Component
public class IrisLectureUnitSyncEventListener {

    private static final Logger log = LoggerFactory.getLogger(IrisLectureUnitSyncEventListener.class);

    private static final int MAX_RETRY_DELAY_MINUTES = 60;

    /**
     * How often a failing synchronisation is retried before it is treated as permanent. Mirrors the limit the
     * ingestion state machine applies in {@code ProcessingStateCallbackService}: without one, a unit whose failure
     * never resolves is pushed to Pyris once an hour for as long as its course stays active.
     *
     * <p>
     * Chosen above the point where the backoff reaches {@link #MAX_RETRY_DELAY_MINUTES}, so that a failure which does
     * resolve on its own still gets several hours of hourly attempts before the unit leaves the hot retry path. It is
     * deliberately larger than the ingestion limit, whose attempts are far more expensive than one webhook call.
     */
    private static final int MAX_SYNC_RETRIES = 10;

    /**
     * How long a state that exhausted its retries waits before it is tried once more. Long enough that a permanent
     * failure costs one request a day, short enough that an installation recovers on its own after a long outage.
     */
    private static final Duration COLD_RETRY_DELAY = Duration.ofHours(24);

    private static final int RETRY_LEASE_MINUTES = 10;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final IrisLectureUnitSyncStateRepository syncStateRepository;

    private final IrisLectureUnitSyncDispatchService syncDispatchService;

    private final SlideRepository slideRepository;

    private final IrisLectureUnitSyncService syncService;

    public IrisLectureUnitSyncEventListener(AttachmentVideoUnitRepository attachmentVideoUnitRepository, IrisLectureUnitSyncStateRepository syncStateRepository,
            IrisLectureUnitSyncDispatchService syncDispatchService, SlideRepository slideRepository, IrisLectureUnitSyncService syncService) {
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.syncStateRepository = syncStateRepository;
        this.syncDispatchService = syncDispatchService;
        this.slideRepository = slideRepository;
        this.syncService = syncService;
    }

    @EventListener
    @Async
    public void handleMetadataDirty(IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent event) {
        synchronize(event.lectureUnitId(), LectureContentUpdateKind.METADATA);
    }

    @EventListener
    @Async
    public void handleVisibilityDirty(IrisLectureUnitSyncService.IrisLectureUnitVisibilityDirtyEvent event) {
        synchronize(event.lectureUnitId(), LectureContentUpdateKind.VISIBILITY, event.slideHiddenUntilBySlideNumber());
    }

    /**
     * Retries Iris/Pyris metadata and visibility updates that failed during event handling.
     */
    @Scheduled(fixedRate = 300000)
    public void retryDirtyStates() {
        syncStateRepository
                .findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        List.of(IrisLectureUnitSyncState.STATUS_DIRTY, IrisLectureUnitSyncState.STATUS_IN_PROGRESS, IrisLectureUnitSyncState.STATUS_FAILED), ZonedDateTime.now())
                .forEach(candidate -> {
                    ZonedDateTime claimTime = ZonedDateTime.now();
                    syncStateRepository.claimRetry(candidate.getLectureUnitId(), claimTime, claimTime.plusMinutes(RETRY_LEASE_MINUTES)).ifPresent(this::synchronizeDirtyState);
                });
    }

    /**
     * Creates visibility synchronization state for active legacy units in bounded batches.
     * The resulting dirty event is handled by the same durable retry path as ordinary updates.
     */
    @Scheduled(fixedRate = 300000)
    public void backfillMissingSyncStates() {
        attachmentVideoUnitRepository.findUnitsMissingIrisSyncStateFromActiveCourses(ZonedDateTime.now(), PageRequest.of(0, 50)).forEach(unit -> {
            try {
                var snapshot = new LectureContentUpdateSnapshot(unit.getId(), null, null, null, null, null, null, null, unit.resolveReleaseDate(),
                        SlideVisibilitySnapshotHelper.toSortedHiddenUntilBySlideNumber(slideRepository.findAllByAttachmentVideoUnitId(unit.getId())));
                syncService.markVisibilityDirty(snapshot);
            }
            catch (Exception e) {
                log.warn("Could not initialize Iris lecture unit sync state {}", unit.getId(), e);
            }
        });
    }

    private void synchronizeDirtyState(IrisLectureUnitSyncState state) {
        if (!Objects.equals(state.getMetadataHash(), state.getLastSyncedMetadataHash())) {
            synchronize(state, LectureContentUpdateKind.METADATA);
        }
        if (!Objects.equals(state.getVisibilityHash(), state.getLastSyncedVisibilityHash())) {
            synchronize(state, LectureContentUpdateKind.VISIBILITY);
        }
    }

    private void synchronize(Long lectureUnitId, LectureContentUpdateKind updateKind) {
        synchronize(lectureUnitId, updateKind, null);
    }

    private void synchronize(Long lectureUnitId, LectureContentUpdateKind updateKind, Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber) {
        try {
            ZonedDateTime claimTime = ZonedDateTime.now();
            syncStateRepository.claimRetry(lectureUnitId, claimTime, claimTime.plusMinutes(RETRY_LEASE_MINUTES))
                    .ifPresent(state -> synchronize(state, updateKind, projectedSlideHiddenUntilBySlideNumber));
        }
        catch (Exception e) {
            log.warn("Could not claim Iris lecture unit sync state {}", lectureUnitId, e);
        }
    }

    private void synchronize(IrisLectureUnitSyncState state, LectureContentUpdateKind updateKind) {
        synchronize(state, updateKind, null);
    }

    private void synchronize(IrisLectureUnitSyncState state, LectureContentUpdateKind updateKind, Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber) {
        try {
            AttachmentVideoUnit unit = attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(state.getLectureUnitId()).orElse(null);
            if (unit == null) {
                log.debug("Skipping Iris lecture unit sync for missing attachment video unit {}", state.getLectureUnitId());
                syncStateRepository.delete(state);
                return;
            }

            var dispatchResult = Optional.ofNullable(projectedSlideHiddenUntilBySlideNumber)
                    .map(projectedVisibility -> syncDispatchService.triggerSyncForUpdateKind(unit, updateKind, projectedVisibility))
                    .orElseGet(() -> syncDispatchService.triggerSyncForUpdateKind(unit, updateKind));
            if (dispatchResult.outcome() == LectureUnitSyncOutcome.NOT_INGESTED) {
                // Pyris holds nothing for this unit, so retrying would ask the same question every hour and get the
                // same answer. The row is settled and the ingestion reopens it once it has something to synchronise.
                log.debug("Pyris has not ingested lecture unit {}, settling its {} synchronisation", state.getLectureUnitId(), updateKind);
                syncStateRepository.updateWithLectureUnitLock(state.getLectureUnitId(), IrisLectureUnitSyncEventListener::markNotIngested);
                return;
            }
            String dispatchedHash = dispatchResult.outcome() == LectureUnitSyncOutcome.DISPATCHED ? dispatchedHash(state, updateKind, dispatchResult.visibilityHash()) : null;
            syncStateRepository.updateWithLectureUnitLock(state.getLectureUnitId(),
                    currentState -> Optional.ofNullable(dispatchedHash).ifPresentOrElse(hash -> markSynced(currentState, updateKind, hash), () -> markSkipped(currentState)));
        }
        catch (Exception e) {
            try {
                syncStateRepository.updateWithLectureUnitLock(state.getLectureUnitId(),
                        currentState -> Optional.of(currentState).filter(candidate -> isDirtyForUpdateKind(candidate, updateKind)).ifPresent(candidate -> markRetry(candidate, e)));
            }
            catch (Exception persistenceException) {
                log.warn("Could not persist retry state for Iris lecture unit sync {}", state.getLectureUnitId(), persistenceException);
            }
        }
    }

    /**
     * @param state          the state being synchronized
     * @param updateKind     what was dispatched
     * @param visibilityHash the hash of the dispatched visibility payload, which only a visibility update carries
     * @return the hash to record as synchronized
     */
    private static String dispatchedHash(IrisLectureUnitSyncState state, LectureContentUpdateKind updateKind, String visibilityHash) {
        return switch (updateKind) {
            case METADATA -> state.getMetadataHash();
            case VISIBILITY -> visibilityHash;
            default -> throw new IllegalArgumentException("Unsupported Iris lecture unit sync update kind: " + updateKind);
        };
    }

    private static boolean isDirtyForUpdateKind(IrisLectureUnitSyncState state, LectureContentUpdateKind updateKind) {
        return switch (updateKind) {
            case METADATA -> !Objects.equals(state.getMetadataHash(), state.getLastSyncedMetadataHash());
            case VISIBILITY -> !Objects.equals(state.getVisibilityHash(), state.getLastSyncedVisibilityHash());
            default -> throw new IllegalArgumentException("Unsupported Iris lecture unit sync update kind: " + updateKind);
        };
    }

    private static void markSynced(IrisLectureUnitSyncState state, LectureContentUpdateKind updateKind, String dispatchedHash) {
        switch (updateKind) {
            case METADATA -> state.setLastSyncedMetadataHash(dispatchedHash);
            case VISIBILITY -> state.setLastSyncedVisibilityHash(dispatchedHash);
            default -> throw new IllegalArgumentException("Unsupported Iris lecture unit sync update kind: " + updateKind);
        }
        if (isClean(state)) {
            state.setRetryCount(0);
            state.setNextRetryAt(null);
            state.setLastErrorKey(null);
            state.setStatus(IrisLectureUnitSyncState.STATUS_CLEAN);
        }
        else {
            state.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
            if (state.getNextRetryAt() == null) {
                state.setNextRetryAt(ZonedDateTime.now());
            }
        }
    }

    private static boolean isClean(IrisLectureUnitSyncState state) {
        return Objects.equals(state.getMetadataHash(), state.getLastSyncedMetadataHash()) && Objects.equals(state.getVisibilityHash(), state.getLastSyncedVisibilityHash());
    }

    private static void markSkipped(IrisLectureUnitSyncState state) {
        if (isSettled(state)) {
            // The two legs of a claim are dispatched one after the other, so the first can settle the row while the
            // second is still deciding. Rescheduling here would undo that settle and restart the loop it ended.
            return;
        }
        state.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
        state.setNextRetryAt(ZonedDateTime.now().plusMinutes(RETRY_LEASE_MINUTES));
        state.setLastErrorKey("DispatchSkipped");
    }

    /**
     * @param state a synchronization state
     * @return whether it has been settled, which the retry pass only revisits once its cold retry time is due
     */
    private static boolean isSettled(IrisLectureUnitSyncState state) {
        return IrisLectureUnitSyncState.STATUS_NOT_INGESTED.equals(state.getStatus()) || IrisLectureUnitSyncState.STATUS_FAILED.equals(state.getStatus());
    }

    /**
     * Settles a state whose synchronization Pyris answered with "not ingested".
     *
     * <p>
     * Only a row that is still the claimed one is settled. The claim commits before the request leaves, so an ingestion
     * can complete while the request is in flight and reopen the row to {@link IrisLectureUnitSyncState#STATUS_DIRTY}.
     * The answer then describes a lecture unit Pyris did not hold yet but does now, and settling on it would strand a
     * unit that has just become synchronizable: the backfill does not recreate a row that exists, and the ingestion
     * that would have reopened it has already run.
     *
     * @param state the current synchronization state of the lecture unit
     */
    private static void markNotIngested(IrisLectureUnitSyncState state) {
        if (!IrisLectureUnitSyncState.STATUS_IN_PROGRESS.equals(state.getStatus())) {
            return;
        }
        state.setStatus(IrisLectureUnitSyncState.STATUS_NOT_INGESTED);
        state.setRetryCount(0);
        state.setNextRetryAt(null);
        state.setLastErrorKey("NotIngestedInPyris");
    }

    private static void markRetry(IrisLectureUnitSyncState state, Exception exception) {
        if (isSettled(state)) {
            // As in markSkipped: the metadata leg can settle the row and the visibility leg of the same claim can then
            // fail, and restarting the retries here would undo a settle that the failure says nothing about.
            return;
        }
        int retryCount = state.getRetryCount() + 1;
        state.setRetryCount(retryCount);
        state.setLastErrorKey(exception.getClass().getSimpleName());
        if (retryCount >= MAX_SYNC_RETRIES) {
            // Left out of the hot retry path, but not abandoned: an outage longer than the backoff budget would
            // otherwise settle every dirty unit of the installation with nothing to bring it back, since these units
            // are already ingested and no further ingestion will reopen them.
            state.setStatus(IrisLectureUnitSyncState.STATUS_FAILED);
            state.setNextRetryAt(ZonedDateTime.now().plus(COLD_RETRY_DELAY));
            return;
        }
        state.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
        state.setNextRetryAt(ZonedDateTime.now().plusMinutes(Math.min(MAX_RETRY_DELAY_MINUTES, 1L << Math.min(retryCount, 6))));
    }
}
