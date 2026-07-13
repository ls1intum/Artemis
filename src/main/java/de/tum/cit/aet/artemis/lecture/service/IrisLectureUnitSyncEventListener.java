package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;

@Conditional(LectureWithIrisEnabled.class)
@Lazy
@Component
public class IrisLectureUnitSyncEventListener {

    private static final Logger log = LoggerFactory.getLogger(IrisLectureUnitSyncEventListener.class);

    private static final int MAX_RETRY_DELAY_MINUTES = 60;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final IrisLectureUnitSyncStateRepository syncStateRepository;

    private final IrisLectureUnitSyncDispatchService syncDispatchService;

    public IrisLectureUnitSyncEventListener(AttachmentVideoUnitRepository attachmentVideoUnitRepository, IrisLectureUnitSyncStateRepository syncStateRepository,
            IrisLectureUnitSyncDispatchService syncDispatchService) {
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.syncStateRepository = syncStateRepository;
        this.syncDispatchService = syncDispatchService;
    }

    @EventListener
    public void handleMetadataDirty(IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent event) {
        synchronize(event.lectureUnitId(), LectureContentUpdateKind.METADATA);
    }

    @EventListener
    public void handleVisibilityDirty(IrisLectureUnitSyncService.IrisLectureUnitVisibilityDirtyEvent event) {
        synchronize(event.lectureUnitId(), LectureContentUpdateKind.VISIBILITY, event.slideHiddenUntilBySlideNumber());
    }

    /**
     * Retries Iris/Pyris metadata and visibility updates that failed during event handling.
     */
    @Scheduled(fixedRate = 300000)
    public void retryDirtyStates() {
        syncStateRepository.findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(List.of(IrisLectureUnitSyncState.STATUS_DIRTY), ZonedDateTime.now())
                .forEach(this::synchronizeDirtyState);
    }

    private void synchronizeDirtyState(IrisLectureUnitSyncState state) {
        if (!Objects.equals(state.getMetadataHash(), state.getLastSyncedMetadataHash())) {
            synchronize(state.getLectureUnitId(), LectureContentUpdateKind.METADATA);
        }
        if (!Objects.equals(state.getVisibilityHash(), state.getLastSyncedVisibilityHash())) {
            synchronize(state.getLectureUnitId(), LectureContentUpdateKind.VISIBILITY);
        }
    }

    private void synchronize(Long lectureUnitId, LectureContentUpdateKind updateKind) {
        synchronize(lectureUnitId, updateKind, null);
    }

    private void synchronize(Long lectureUnitId, LectureContentUpdateKind updateKind, Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber) {
        try {
            syncStateRepository.findByLectureUnitId(lectureUnitId).ifPresent(state -> synchronize(state, updateKind, projectedSlideHiddenUntilBySlideNumber));
        }
        catch (Exception e) {
            log.warn("Could not load Iris lecture unit sync state {}", lectureUnitId, e);
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

            String dispatchedVisibilityHash = Optional.ofNullable(projectedSlideHiddenUntilBySlideNumber)
                    .map(projectedVisibility -> syncDispatchService.triggerSyncForUpdateKind(unit, updateKind, projectedVisibility))
                    .orElseGet(() -> syncDispatchService.triggerSyncForUpdateKind(unit, updateKind));
            String dispatchedHash = getDispatchedHash(state, updateKind, dispatchedVisibilityHash);
            syncStateRepository.updateWithLectureUnitLock(state.getLectureUnitId(), currentState -> markSynced(currentState, updateKind, dispatchedHash));
        }
        catch (Exception e) {
            try {
                syncStateRepository.updateWithLectureUnitLock(state.getLectureUnitId(), currentState -> Optional.of(currentState)
                        .filter(candidate -> isDirtyForUpdateKind(candidate, updateKind)).ifPresent(candidate -> markRetry(candidate, e)));
            }
            catch (Exception persistenceException) {
                log.warn("Could not persist retry state for Iris lecture unit sync {}", state.getLectureUnitId(), persistenceException);
            }
        }
    }

    private static String getDispatchedHash(IrisLectureUnitSyncState state, LectureContentUpdateKind updateKind, String dispatchedVisibilityHash) {
        Map<LectureContentUpdateKind, String> dispatchedHashes = new EnumMap<>(LectureContentUpdateKind.class);
        dispatchedHashes.put(LectureContentUpdateKind.METADATA, state.getMetadataHash());
        dispatchedHashes.put(LectureContentUpdateKind.VISIBILITY, dispatchedVisibilityHash);
        return dispatchedHashes.get(updateKind);
    }

    private static boolean isDirtyForUpdateKind(IrisLectureUnitSyncState state, LectureContentUpdateKind updateKind) {
        Map<LectureContentUpdateKind, String> currentHashes = new EnumMap<>(LectureContentUpdateKind.class);
        currentHashes.put(LectureContentUpdateKind.METADATA, state.getMetadataHash());
        currentHashes.put(LectureContentUpdateKind.VISIBILITY, state.getVisibilityHash());
        Map<LectureContentUpdateKind, String> syncedHashes = new EnumMap<>(LectureContentUpdateKind.class);
        syncedHashes.put(LectureContentUpdateKind.METADATA, state.getLastSyncedMetadataHash());
        syncedHashes.put(LectureContentUpdateKind.VISIBILITY, state.getLastSyncedVisibilityHash());
        return !Objects.equals(currentHashes.get(updateKind), syncedHashes.get(updateKind));
    }

    private static void markSynced(IrisLectureUnitSyncState state, LectureContentUpdateKind updateKind, String dispatchedHash) {
        if (updateKind == LectureContentUpdateKind.METADATA) {
            state.setLastSyncedMetadataHash(dispatchedHash);
        }
        if (updateKind == LectureContentUpdateKind.VISIBILITY) {
            state.setLastSyncedVisibilityHash(dispatchedHash);
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

    private static void markRetry(IrisLectureUnitSyncState state, Exception exception) {
        int retryCount = state.getRetryCount() + 1;
        state.setRetryCount(retryCount);
        state.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
        state.setNextRetryAt(ZonedDateTime.now().plusMinutes(Math.min(MAX_RETRY_DELAY_MINUTES, 1L << Math.min(retryCount, 6))));
        state.setLastErrorKey(exception.getClass().getSimpleName());
    }
}
