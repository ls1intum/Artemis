package de.tum.cit.aet.artemis.iris.service.pyris;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

final class PyrisLectureUnitEligibility {

    private PyrisLectureUnitEligibility() {
    }

    static boolean isProcessable(AttachmentVideoUnit attachmentVideoUnit) {
        boolean hasVideo = java.util.Optional.ofNullable(attachmentVideoUnit.getVideoSource()).filter(videoSource -> !videoSource.isBlank()).isPresent();
        boolean hasPdf = java.util.Optional.ofNullable(attachmentVideoUnit.getAttachment()).filter(attachment -> attachment.getAttachmentType() == AttachmentType.FILE)
                .map(attachment -> attachment.getLink()).filter(link -> link.endsWith(".pdf")).isPresent();
        return !attachmentVideoUnit.getLecture().isTutorialLecture() && java.util.List.of(hasVideo, hasPdf).contains(true);
    }
}
