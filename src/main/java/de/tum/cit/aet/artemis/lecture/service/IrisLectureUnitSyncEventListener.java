package de.tum.cit.aet.artemis.lecture.service;

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
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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

    private static final int RETRY_LEASE_MINUTES = 10;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final IrisLectureUnitSyncStateRepository syncStateRepository;

    private final IrisLectureUnitSyncDispatchService syncDispatchService;

    private final SlideRepository slideRepository;

    private final IrisLectureUnitSyncService syncService;

    private final TransactionTemplate requiresNewTransactionTemplate;

    public IrisLectureUnitSyncEventListener(AttachmentVideoUnitRepository attachmentVideoUnitRepository, IrisLectureUnitSyncStateRepository syncStateRepository,
            IrisLectureUnitSyncDispatchService syncDispatchService, SlideRepository slideRepository, IrisLectureUnitSyncService syncService,
            PlatformTransactionManager transactionManager) {
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.syncStateRepository = syncStateRepository;
        this.syncDispatchService = syncDispatchService;
        this.slideRepository = slideRepository;
        this.syncService = syncService;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
    public void retryDirtyStates() {
        syncStateRepository.findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                List.of(IrisLectureUnitSyncState.STATUS_DIRTY, IrisLectureUnitSyncState.STATUS_IN_PROGRESS), ZonedDateTime.now()).forEach(candidate -> {
                    ZonedDateTime claimTime = ZonedDateTime.now();
                    claimRetryInNewTransaction(candidate.getLectureUnitId(), claimTime).ifPresent(this::synchronizeDirtyState);
                });
    }

    /**
     * Creates visibility synchronization state for active legacy units in bounded batches.
     * The resulting dirty event is handled by the same durable retry path as ordinary updates.
     */
    public void backfillMissingSyncStates() {
        attachmentVideoUnitRepository.findUnitsMissingIrisSyncStateFromActiveCourses(ZonedDateTime.now(), PageRequest.of(0, 50)).forEach(unit -> {
            try {
                var snapshot = new LectureContentUpdateSnapshot(unit.getId(), null, null, null, null, null, null, null, unit.resolveReleaseDate(),
                        SlideVisibilitySnapshotHelper.toSortedHiddenUntilBySlideNumber(slideRepository.findAllByAttachmentVideoUnitId(unit.getId())));
                syncService.markVisibilityDirtyAfterCommit(snapshot);
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
            claimRetryInNewTransaction(lectureUnitId, claimTime).ifPresent(state -> synchronize(state, updateKind, projectedSlideHiddenUntilBySlideNumber));
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
            AttachmentVideoUnit unit = findAttachmentVideoUnitInNewTransaction(state.getLectureUnitId()).orElse(null);
            if (unit == null) {
                log.debug("Skipping Iris lecture unit sync for missing attachment video unit {}", state.getLectureUnitId());
                requiresNewTransactionTemplate.executeWithoutResult(status -> syncStateRepository.delete(state));
                return;
            }

            String dispatchResult = Optional.ofNullable(projectedSlideHiddenUntilBySlideNumber)
                    .map(projectedVisibility -> syncDispatchService.triggerSyncForUpdateKind(unit, updateKind, projectedVisibility))
                    .orElseGet(() -> syncDispatchService.triggerSyncForUpdateKind(unit, updateKind));
            String dispatchedHash = getDispatchedHash(state, updateKind, dispatchResult);
            requiresNewTransactionTemplate.executeWithoutResult(status -> syncStateRepository.updateWithLectureUnitLock(state.getLectureUnitId(),
                    currentState -> Optional.ofNullable(dispatchedHash).ifPresentOrElse(hash -> markSynced(currentState, updateKind, hash), () -> markSkipped(currentState))));
        }
        catch (Exception e) {
            try {
                requiresNewTransactionTemplate.executeWithoutResult(status -> syncStateRepository.updateWithLectureUnitLock(state.getLectureUnitId(), currentState -> Optional
                        .of(currentState).filter(candidate -> isDirtyForUpdateKind(candidate, updateKind)).ifPresent(candidate -> markRetry(candidate, e))));
            }
            catch (Exception persistenceException) {
                log.warn("Could not persist retry state for Iris lecture unit sync {}", state.getLectureUnitId(), persistenceException);
            }
        }
    }

    private Optional<IrisLectureUnitSyncState> claimRetryInNewTransaction(Long lectureUnitId, ZonedDateTime claimTime) {
        Optional<IrisLectureUnitSyncState> claimedState = requiresNewTransactionTemplate
                .execute(status -> syncStateRepository.claimRetry(lectureUnitId, claimTime, claimTime.plusMinutes(RETRY_LEASE_MINUTES)));
        return claimedState != null ? claimedState : Optional.empty();
    }

    private Optional<AttachmentVideoUnit> findAttachmentVideoUnitInNewTransaction(Long lectureUnitId) {
        Optional<AttachmentVideoUnit> attachmentVideoUnit = requiresNewTransactionTemplate
                .execute(status -> attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(lectureUnitId));
        return attachmentVideoUnit != null ? attachmentVideoUnit : Optional.empty();
    }

    private static String getDispatchedHash(IrisLectureUnitSyncState state, LectureContentUpdateKind updateKind, String dispatchResult) {
        if (dispatchResult == null) {
            return null;
        }
        return switch (updateKind) {
            case METADATA -> state.getMetadataHash();
            case VISIBILITY -> dispatchResult;
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
            state.setNextRetryAt(ZonedDateTime.now());
        }
    }

    private static boolean isClean(IrisLectureUnitSyncState state) {
        return Objects.equals(state.getMetadataHash(), state.getLastSyncedMetadataHash()) && Objects.equals(state.getVisibilityHash(), state.getLastSyncedVisibilityHash());
    }

    private static void markSkipped(IrisLectureUnitSyncState state) {
        state.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
        state.setNextRetryAt(ZonedDateTime.now().plusMinutes(RETRY_LEASE_MINUTES));
        state.setLastErrorKey("DispatchSkipped");
    }

    private static void markRetry(IrisLectureUnitSyncState state, Exception exception) {
        int retryCount = state.getRetryCount() + 1;
        state.setRetryCount(retryCount);
        state.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
        state.setNextRetryAt(ZonedDateTime.now().plusMinutes(Math.min(MAX_RETRY_DELAY_MINUTES, 1L << Math.min(retryCount, 6))));
        state.setLastErrorKey(exception.getClass().getSimpleName());
    }
}
