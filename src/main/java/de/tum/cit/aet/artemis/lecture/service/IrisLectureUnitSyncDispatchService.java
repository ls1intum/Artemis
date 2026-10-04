package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.api.IrisLectureUnitSyncApi;
import de.tum.cit.aet.artemis.iris.api.dtos.LectureUnitSyncOutcome;
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
     * @return what became of the dispatch, with the visibility hash for a dispatched visibility update
     */
    public DispatchResult triggerSyncForUpdateKind(AttachmentVideoUnit attachmentVideoUnit, @NonNull LectureContentUpdateKind updateKind) {
        return triggerSyncForUpdateKind(attachmentVideoUnit, updateKind, null);
    }

    DispatchResult triggerSyncForUpdateKind(AttachmentVideoUnit attachmentVideoUnit, LectureContentUpdateKind updateKind,
            Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber) {
        return switch (updateKind) {
            case NONE -> new DispatchResult(LectureUnitSyncOutcome.SKIPPED, null);
            case METADATA ->
                new DispatchResult(irisLectureUnitSyncApi.map(api -> api.updateLectureUnitMetadataInPyris(attachmentVideoUnit)).orElse(LectureUnitSyncOutcome.SKIPPED), null);
            case VISIBILITY -> dispatchVisibility(attachmentVideoUnit, projectedSlideHiddenUntilBySlideNumber);
            case CONTENT, DELETE -> throw new IllegalArgumentException("Only metadata and visibility updates are supported by the retryable sync dispatcher");
        };
    }

    private DispatchResult dispatchVisibility(AttachmentVideoUnit attachmentVideoUnit, Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber) {
        return irisLectureUnitSyncApi.map(api -> {
            List<Slide> slides = Optional.ofNullable(projectedSlideHiddenUntilBySlideNumber).map(IrisLectureUnitSyncDispatchService::toSlides)
                    .orElseGet(() -> slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId()));
            LectureUnitSyncOutcome outcome = api.updateLectureUnitVisibilityInPyris(attachmentVideoUnit, slides);
            if (outcome != LectureUnitSyncOutcome.DISPATCHED) {
                return new DispatchResult(outcome, null);
            }
            var snapshot = new LectureContentUpdateSnapshot(attachmentVideoUnit.getId(), null, null, null, null, null, null, null, attachmentVideoUnit.resolveReleaseDate(),
                    SlideVisibilitySnapshotHelper.toSortedHiddenUntilBySlideNumber(slides));
            return new DispatchResult(outcome, IrisLectureUnitSyncService.visibilityHash(snapshot));
        }).orElse(new DispatchResult(LectureUnitSyncOutcome.SKIPPED, null));
    }

    /**
     * What a dispatch achieved.
     *
     * @param outcome        what Pyris made of the update
     * @param visibilityHash the hash of the dispatched visibility payload, or null when nothing was dispatched or the
     *                           update was a metadata one, whose hash the caller already holds
     */
    public record DispatchResult(LectureUnitSyncOutcome outcome, @Nullable String visibilityHash) {
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
