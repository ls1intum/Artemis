package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;

@Conditional(LectureWithIrisEnabled.class)
@Service
public class IrisLectureUnitSyncEventListener {

    private static final Logger log = LoggerFactory.getLogger(IrisLectureUnitSyncEventListener.class);

    private static final int MAX_RETRY_DELAY_MINUTES = 60;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final IrisLectureUnitSyncStateRepository syncStateRepository;

    private final LectureContentProcessingService contentProcessingService;

    public IrisLectureUnitSyncEventListener(AttachmentVideoUnitRepository attachmentVideoUnitRepository, IrisLectureUnitSyncStateRepository syncStateRepository,
            LectureContentProcessingService contentProcessingService) {
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.syncStateRepository = syncStateRepository;
        this.contentProcessingService = contentProcessingService;
    }

    @EventListener
    public void handleMetadataDirty(IrisLectureUnitSyncService.IrisLectureUnitMetadataDirtyEvent event) {
        synchronize(event.lectureUnitId(), LectureContentUpdateKind.METADATA);
    }

    @EventListener
    public void handleVisibilityDirty(IrisLectureUnitSyncService.IrisLectureUnitVisibilityDirtyEvent event) {
        synchronize(event.lectureUnitId(), LectureContentUpdateKind.VISIBILITY);
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
        syncStateRepository.findByLectureUnitId(lectureUnitId).ifPresent(state -> synchronize(state, updateKind));
    }

    private void synchronize(IrisLectureUnitSyncState state, LectureContentUpdateKind updateKind) {
        try {
            AttachmentVideoUnit unit = attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(state.getLectureUnitId()).orElse(null);
            if (unit == null) {
                log.debug("Skipping Iris lecture unit sync for missing attachment video unit {}", state.getLectureUnitId());
                return;
            }

            contentProcessingService.triggerProcessingForUpdateKind(unit, updateKind);
            markSynced(state, updateKind);
        }
        catch (Exception e) {
            markRetry(state, e);
        }
        syncStateRepository.save(state);
    }

    private static void markSynced(IrisLectureUnitSyncState state, LectureContentUpdateKind updateKind) {
        if (updateKind == LectureContentUpdateKind.METADATA) {
            state.setLastSyncedMetadataHash(state.getMetadataHash());
        }
        if (updateKind == LectureContentUpdateKind.VISIBILITY) {
            state.setLastSyncedVisibilityHash(state.getVisibilityHash());
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
        state.setNextRetryAt(ZonedDateTime.now().plusMinutes(Math.min(MAX_RETRY_DELAY_MINUTES, 1L << Math.min(retryCount, 5))));
        state.setLastErrorKey(exception.getClass().getSimpleName());
    }
}
