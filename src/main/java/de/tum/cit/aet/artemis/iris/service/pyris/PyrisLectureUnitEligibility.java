package de.tum.cit.aet.artemis.iris.service.pyris;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;

final class PyrisLectureUnitEligibility {

    private PyrisLectureUnitEligibility() {
    }

    /**
     * Whether Iris can ingest anything of the unit: a stored PDF, or a video from a source Iris can transcribe. A link to any other video page counts as no
     * content, so a unit that has only such a link is not processable.
     *
     * @param attachmentVideoUnit the unit to check
     * @param videoSourceResolver decides from the URL alone whether a video source is supported
     * @return true if the unit is not part of a tutorial lecture and has processable content
     */
    static boolean isProcessable(AttachmentVideoUnit attachmentVideoUnit, VideoSourceResolverService videoSourceResolver) {
        boolean hasSupportedVideo = videoSourceResolver.isSupportedSource(attachmentVideoUnit.getVideoSource());
        return !attachmentVideoUnit.getLecture().isTutorialLecture() && (hasSupportedVideo || hasPdf(attachmentVideoUnit));
    }

    static boolean hasPdf(AttachmentVideoUnit attachmentVideoUnit) {
        return attachmentVideoUnit.getAttachment() != null && attachmentVideoUnit.getAttachment().isStoredPdf();
    }
}
