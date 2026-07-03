package de.tum.cit.aet.artemis.lecture.service;

import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.api.IrisLectureUnitSyncApi;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
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
     */
    public void triggerSyncForUpdateKind(AttachmentVideoUnit attachmentVideoUnit, LectureContentUpdateKind updateKind) {
        Objects.requireNonNull(updateKind, "updateKind");
        switch (updateKind) {
            case NONE -> {
                return;
            }
            case METADATA -> irisLectureUnitSyncApi.ifPresent(api -> api.updateLectureUnitMetadataInPyris(attachmentVideoUnit));
            case VISIBILITY -> irisLectureUnitSyncApi
                    .ifPresent(api -> api.updateLectureUnitVisibilityInPyris(attachmentVideoUnit, slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId())));
            case CONTENT, DELETE -> throw new IllegalArgumentException("Only metadata and visibility updates are supported by the retryable sync dispatcher");
        }
    }
}
