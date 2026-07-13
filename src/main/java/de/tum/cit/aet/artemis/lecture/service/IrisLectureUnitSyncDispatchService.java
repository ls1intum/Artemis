package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.api.IrisLectureUnitSyncApi;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Conditional(LectureWithIrisEnabled.class)
@Lazy
@Service
public class IrisLectureUnitSyncDispatchService {

    private final SlideRepository slideRepository;

    private final Optional<IrisLectureUnitSyncApi> irisLectureUnitSyncApi;

    public IrisLectureUnitSyncDispatchService(SlideRepository slideRepository, Optional<IrisLectureUnitSyncApi> irisLectureUnitSyncApi) {
        this.slideRepository = slideRepository;
        this.irisLectureUnitSyncApi = irisLectureUnitSyncApi;
    }

    /**
     * Routes retryable lecture-unit updates to lightweight Pyris synchronization endpoints.
     *
     * @param attachmentVideoUnit the attachment video unit to synchronize
     * @param updateKind          the classified update kind
     * @return the visibility hash of the dispatched payload, or null for non-visibility updates
     */
    public String triggerSyncForUpdateKind(AttachmentVideoUnit attachmentVideoUnit, LectureContentUpdateKind updateKind) {
        return triggerSyncForUpdateKind(attachmentVideoUnit, updateKind, null);
    }

    String triggerSyncForUpdateKind(AttachmentVideoUnit attachmentVideoUnit, LectureContentUpdateKind updateKind,
            Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber) {
        Objects.requireNonNull(updateKind, "updateKind");
        return switch (updateKind) {
            case NONE -> {
                yield null;
            }
            case METADATA -> {
                irisLectureUnitSyncApi.ifPresent(api -> api.updateLectureUnitMetadataInPyris(attachmentVideoUnit));
                yield null;
            }
            case VISIBILITY -> dispatchVisibility(attachmentVideoUnit, projectedSlideHiddenUntilBySlideNumber);
            case CONTENT, DELETE -> throw new IllegalArgumentException("Only metadata and visibility updates are supported by the retryable sync dispatcher");
        };
    }

    private String dispatchVisibility(AttachmentVideoUnit attachmentVideoUnit, Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber) {
        return irisLectureUnitSyncApi.map(api -> {
            List<Slide> slides = Optional.ofNullable(projectedSlideHiddenUntilBySlideNumber).map(IrisLectureUnitSyncDispatchService::toSlides)
                    .orElseGet(() -> slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId()));
            api.updateLectureUnitVisibilityInPyris(attachmentVideoUnit, slides);
            var snapshot = new LectureContentUpdateSnapshot(attachmentVideoUnit.getId(), null, null, null, null, null, null, null,
                    LectureContentUpdateSnapshot.resolveReleaseDate(attachmentVideoUnit), toSlideHiddenUntilBySlideNumber(slides));
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

    private static Map<Integer, ZonedDateTime> toSlideHiddenUntilBySlideNumber(List<Slide> slides) {
        var slideHiddenUntilBySlideNumber = new LinkedHashMap<Integer, ZonedDateTime>();
        slides.stream().sorted(Comparator.comparingInt(Slide::getSlideNumber))
                .forEach(slide -> slideHiddenUntilBySlideNumber.put(slide.getSlideNumber(), slide.getHidden()));
        return slideHiddenUntilBySlideNumber;
    }
}
