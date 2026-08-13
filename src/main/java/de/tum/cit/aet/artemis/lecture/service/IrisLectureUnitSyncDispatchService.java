package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.iris.api.IrisLectureUnitSyncApi;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Conditional(LectureWithIrisEnabled.class)
@Lazy
@Service
public class IrisLectureUnitSyncDispatchService {

    private final SlideRepository slideRepository;

    private final Optional<IrisLectureUnitSyncApi> irisLectureUnitSyncApi;

    private final LectureUnitProcessingStateRepository processingStateRepository;

    public IrisLectureUnitSyncDispatchService(SlideRepository slideRepository, Optional<IrisLectureUnitSyncApi> irisLectureUnitSyncApi,
            LectureUnitProcessingStateRepository processingStateRepository) {
        this.slideRepository = slideRepository;
        this.irisLectureUnitSyncApi = irisLectureUnitSyncApi;
        this.processingStateRepository = processingStateRepository;
    }

    /**
     * Routes retryable lecture-unit updates to lightweight Pyris synchronization endpoints.
     *
     * @param attachmentVideoUnit the attachment video unit to synchronize
     * @param updateKind          the classified update kind
     * @return the visibility hash of the dispatched payload, or null for non-visibility updates
     */
    @Transactional
    public String triggerSyncForUpdateKind(AttachmentVideoUnit attachmentVideoUnit, LectureContentUpdateKind updateKind) {
        return triggerSyncForUpdateKind(attachmentVideoUnit, updateKind, null);
    }

    @Transactional
    String triggerSyncForUpdateKind(AttachmentVideoUnit attachmentVideoUnit, LectureContentUpdateKind updateKind,
            Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber) {
        Objects.requireNonNull(updateKind, "updateKind");
        if (processingStateRepository.findAttachmentVideoUnitForUpdateById(attachmentVideoUnit.getId()).isEmpty()) {
            return null;
        }
        if ((updateKind == LectureContentUpdateKind.METADATA || updateKind == LectureContentUpdateKind.VISIBILITY)
                && !processingStateRepository.existsByLectureUnit_IdAndPhase(attachmentVideoUnit.getId(), ProcessingPhase.DONE)) {
            return null;
        }
        return switch (updateKind) {
            case NONE -> {
                yield null;
            }
            case METADATA -> irisLectureUnitSyncApi.map(api -> api.updateLectureUnitMetadataInPyris(attachmentVideoUnit)).orElse(null);
            case VISIBILITY -> dispatchVisibility(attachmentVideoUnit, projectedSlideHiddenUntilBySlideNumber);
            case CONTENT, DELETE -> throw new IllegalArgumentException("Only metadata and visibility updates are supported by the retryable sync dispatcher");
        };
    }

    private String dispatchVisibility(AttachmentVideoUnit attachmentVideoUnit, Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber) {
        return irisLectureUnitSyncApi.map(api -> {
            List<Slide> slides = Optional.ofNullable(projectedSlideHiddenUntilBySlideNumber).map(IrisLectureUnitSyncDispatchService::toSlides)
                    .orElseGet(() -> slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId()));
            String dispatchToken = api.updateLectureUnitVisibilityInPyris(attachmentVideoUnit, slides);
            if (dispatchToken == null) {
                return null;
            }
            var snapshot = new LectureContentUpdateSnapshot(attachmentVideoUnit.getId(), null, null, null, null, null, null, null, attachmentVideoUnit.resolveReleaseDate(),
                    SlideVisibilitySnapshotHelper.toSortedHiddenUntilBySlideNumber(slides));
            return IrisLectureUnitSyncService.visibilityHash(snapshot);
        }).orElse(null);
    }

    private static List<Slide> toSlides(Map<Integer, ZonedDateTime> slideHiddenUntilBySlideNumber) {
        return slideHiddenUntilBySlideNumber.entrySet().stream().map(entry -> {
            var slide = new Slide();
            slide.setSlideNumber(entry.getKey());
            slide.setHidden(entry.getValue());
            return slide;
        }).toList();
    }

}
