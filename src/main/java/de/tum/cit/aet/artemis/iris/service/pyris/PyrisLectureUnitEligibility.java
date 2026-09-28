package de.tum.cit.aet.artemis.iris.service.pyris;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

final class PyrisLectureUnitEligibility {

    private PyrisLectureUnitEligibility() {
    }

    static boolean isProcessable(AttachmentVideoUnit attachmentVideoUnit) {
        boolean hasVideo = java.util.Optional.ofNullable(attachmentVideoUnit.getVideoSource()).filter(videoSource -> !videoSource.isBlank()).isPresent();
        return !attachmentVideoUnit.getLecture().isTutorialLecture() && (hasVideo || hasPdf(attachmentVideoUnit));
    }

    static boolean hasPdf(AttachmentVideoUnit attachmentVideoUnit) {
        return attachmentVideoUnit.getAttachment() != null && attachmentVideoUnit.getAttachment().isStoredPdf();
    }
}
