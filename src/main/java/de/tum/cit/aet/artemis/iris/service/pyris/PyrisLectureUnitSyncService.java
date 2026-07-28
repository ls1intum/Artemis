package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitMetadataWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitVisibilityWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisSlideVisibilityDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.videosource.service.ResolvedVideo;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;

@Conditional(IrisEnabled.class)
@Lazy
@Service
public class PyrisLectureUnitSyncService {

    private final PyrisConnectorService pyrisConnectorService;

    private final IrisSettingsService irisSettingsService;

    private final VideoSourceResolverService videoSourceResolver;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisLectureUnitSyncService(PyrisConnectorService pyrisConnectorService, IrisSettingsService irisSettingsService, VideoSourceResolverService videoSourceResolver) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.irisSettingsService = irisSettingsService;
        this.videoSourceResolver = videoSourceResolver;
    }

    /**
     * Updates lightweight lecture unit metadata in Pyris without sending PDF or transcription payloads.
     *
     * @param attachmentVideoUnit the attachment video unit whose metadata changed
     * @return a dispatch token if the update was sent, otherwise null
     */
    public String updateLectureUnitMetadataInPyris(AttachmentVideoUnit attachmentVideoUnit) {
        if (!isLectureUnitProcessableForPyris(attachmentVideoUnit)) {
            return null;
        }
        pyrisConnectorService.executeLectureMetadataWebhook(buildMetadataDto(attachmentVideoUnit));
        return "metadata-" + attachmentVideoUnit.getId();
    }

    /**
     * Updates lightweight lecture unit visibility in Pyris without sending PDF or transcription payloads.
     *
     * @param attachmentVideoUnit the attachment video unit whose visibility changed
     * @param slides              all slides belonging to the lecture unit
     * @return a dispatch token if the update was sent, otherwise null
     */
    public String updateLectureUnitVisibilityInPyris(AttachmentVideoUnit attachmentVideoUnit, List<Slide> slides) {
        if (!isLectureUnitProcessableForPyris(attachmentVideoUnit)) {
            return null;
        }
        pyrisConnectorService.executeLectureVisibilityWebhook(buildVisibilityDto(attachmentVideoUnit, slides));
        return "visibility-" + attachmentVideoUnit.getId();
    }

    private PyrisLectureUnitMetadataWebhookDTO buildMetadataDto(AttachmentVideoUnit attachmentVideoUnit) {
        Lecture lecture = attachmentVideoUnit.getLecture();
        Course course = lecture.getCourse();

        String lectureUnitLink = "";
        if (attachmentVideoUnit.getAttachment() != null && attachmentVideoUnit.getAttachment().getLink() != null) {
            lectureUnitLink = artemisBaseUrl + "/" + attachmentVideoUnit.getAttachment().getLink();
        }

        ResolvedVideo resolved = resolveVideoUrl(attachmentVideoUnit.getVideoSource());
        String videoUrl = resolved.type() != null ? resolved.url() : null;

        return new PyrisLectureUnitMetadataWebhookDTO(attachmentVideoUnit.getId(), attachmentVideoUnit.getName(), lectureUnitLink, lecture.getId(), lecture.getTitle(),
                course.getId(), course.getTitle(), course.getDescription() == null ? "" : course.getDescription(), videoUrl, artemisBaseUrl);
    }

    private PyrisLectureUnitVisibilityWebhookDTO buildVisibilityDto(AttachmentVideoUnit attachmentVideoUnit, List<Slide> slides) {
        Lecture lecture = attachmentVideoUnit.getLecture();
        Course course = lecture.getCourse();
        List<PyrisSlideVisibilityDTO> slideVisibility = slides.stream().sorted(Comparator.comparingInt(Slide::getSlideNumber))
                .map(slide -> new PyrisSlideVisibilityDTO(slide.getSlideNumber(), slide.getHidden())).toList();

        return new PyrisLectureUnitVisibilityWebhookDTO(attachmentVideoUnit.getId(), lecture.getId(), course.getId(), artemisBaseUrl, attachmentVideoUnit.resolveReleaseDate(),
                slideVisibility);
    }

    private ResolvedVideo resolveVideoUrl(String videoSource) {
        if (videoSource == null || videoSource.isBlank()) {
            return new ResolvedVideo(null, null, null);
        }
        return videoSourceResolver.resolve(videoSource);
    }

    private boolean isLectureUnitProcessableForPyris(AttachmentVideoUnit attachmentVideoUnit) {
        return irisSettingsService.isEnabledForCourse(attachmentVideoUnit.getLecture().getCourse()) && PyrisLectureUnitEligibility.isProcessable(attachmentVideoUnit);
    }
}
