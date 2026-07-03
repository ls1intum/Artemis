package de.tum.cit.aet.artemis.lecture.service;

import java.util.Comparator;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitMetadataWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitVisibilityWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisSlideVisibilityDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;
import de.tum.cit.aet.artemis.videosource.service.ResolvedVideo;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;

@Conditional(LectureWithIrisEnabled.class)
@Lazy
@Service
public class IrisLectureUnitSyncDispatchService {

    private final PyrisConnectorService pyrisConnectorService;

    private final IrisSettingsService irisSettingsService;

    private final VideoSourceResolverService videoSourceResolver;

    private final SlideRepository slideRepository;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public IrisLectureUnitSyncDispatchService(PyrisConnectorService pyrisConnectorService, IrisSettingsService irisSettingsService, VideoSourceResolverService videoSourceResolver,
            SlideRepository slideRepository) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.irisSettingsService = irisSettingsService;
        this.videoSourceResolver = videoSourceResolver;
        this.slideRepository = slideRepository;
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
            case METADATA -> updateLectureUnitMetadataInPyris(attachmentVideoUnit);
            case VISIBILITY -> updateLectureUnitVisibilityInPyris(attachmentVideoUnit);
            case CONTENT, DELETE -> throw new IllegalArgumentException("Only metadata and visibility updates are supported by the retryable sync dispatcher");
        }
    }

    String updateLectureUnitMetadataInPyris(AttachmentVideoUnit attachmentVideoUnit) {
        if (!isLectureUnitProcessableForPyris(attachmentVideoUnit)) {
            return null;
        }
        pyrisConnectorService.executeLectureMetadataWebhook(buildMetadataDto(attachmentVideoUnit));
        return "metadata-" + attachmentVideoUnit.getId();
    }

    String updateLectureUnitVisibilityInPyris(AttachmentVideoUnit attachmentVideoUnit) {
        if (!isLectureUnitProcessableForPyris(attachmentVideoUnit)) {
            return null;
        }
        pyrisConnectorService.executeLectureVisibilityWebhook(buildVisibilityDto(attachmentVideoUnit));
        return "visibility-" + attachmentVideoUnit.getId();
    }

    private PyrisLectureUnitMetadataWebhookDTO buildMetadataDto(AttachmentVideoUnit attachmentVideoUnit) {
        Lecture lecture = attachmentVideoUnit.getLecture();
        Course course = lecture.getCourse();

        String lectureUnitLink = "";
        if (attachmentVideoUnit.getAttachment() != null) {
            lectureUnitLink = artemisBaseUrl + "/" + attachmentVideoUnit.getAttachment().getLink();
        }

        ResolvedVideo resolved = videoSourceResolver.resolve(attachmentVideoUnit.getVideoSource());
        String videoUrl = resolved.type() != null ? resolved.url() : null;

        return new PyrisLectureUnitMetadataWebhookDTO(attachmentVideoUnit.getId(), attachmentVideoUnit.getName(), lectureUnitLink, lecture.getId(), lecture.getTitle(),
                course.getId(), course.getTitle(), course.getDescription() == null ? "" : course.getDescription(), videoUrl, artemisBaseUrl);
    }

    private PyrisLectureUnitVisibilityWebhookDTO buildVisibilityDto(AttachmentVideoUnit attachmentVideoUnit) {
        Lecture lecture = attachmentVideoUnit.getLecture();
        Course course = lecture.getCourse();
        var slides = slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId()).stream().sorted(Comparator.comparingInt(Slide::getSlideNumber))
                .map(slide -> new PyrisSlideVisibilityDTO(slide.getSlideNumber(), slide.getHidden())).toList();

        return new PyrisLectureUnitVisibilityWebhookDTO(attachmentVideoUnit.getId(), lecture.getId(), course.getId(), artemisBaseUrl, attachmentVideoUnit.getReleaseDate(), slides);
    }

    private boolean isLectureUnitProcessableForPyris(AttachmentVideoUnit attachmentVideoUnit) {
        return irisSettingsService.isEnabledForCourse(attachmentVideoUnit.getLecture().getCourse()) && !attachmentVideoUnit.getLecture().isTutorialLecture()
                && hasProcessableContent(attachmentVideoUnit);
    }

    private boolean hasProcessableContent(AttachmentVideoUnit attachmentVideoUnit) {
        String videoSource = attachmentVideoUnit.getVideoSource();
        return videoSource != null && !videoSource.isBlank() || hasProcessablePdf(attachmentVideoUnit);
    }

    private boolean hasProcessablePdf(AttachmentVideoUnit attachmentVideoUnit) {
        return attachmentVideoUnit.getAttachment() != null && attachmentVideoUnit.getAttachment().getAttachmentType() == AttachmentType.FILE
                && attachmentVideoUnit.getAttachment().getLink() != null && attachmentVideoUnit.getAttachment().getLink().endsWith(".pdf");
    }
}
