package de.tum.cit.aet.artemis.iris.service.pyris;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

final class PyrisLectureUnitEligibility {

    private PyrisLectureUnitEligibility() {
    }

    static boolean isProcessable(AttachmentVideoUnit attachmentVideoUnit) {
        String videoSource = attachmentVideoUnit.getVideoSource();
        return !attachmentVideoUnit.getLecture().isTutorialLecture() && (videoSource != null && !videoSource.isBlank() || hasProcessablePdf(attachmentVideoUnit));
    }

    private static boolean hasProcessablePdf(AttachmentVideoUnit attachmentVideoUnit) {
        return attachmentVideoUnit.getAttachment() != null && attachmentVideoUnit.getAttachment().getAttachmentType() == AttachmentType.FILE
                && attachmentVideoUnit.getAttachment().getLink() != null && attachmentVideoUnit.getAttachment().getLink().endsWith(".pdf");
    }
}
