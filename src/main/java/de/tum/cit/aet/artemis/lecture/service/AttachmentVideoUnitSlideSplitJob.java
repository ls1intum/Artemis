package de.tum.cit.aet.artemis.lecture.service;

import java.util.List;
import java.util.Objects;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideOrderDTO;

/**
 * Immutable input for asynchronous slide splitting.
 *
 * @param attachmentVideoUnitId the id of the unit to process
 * @param attachmentId          the id of the queued attachment
 * @param attachmentVersion     the version of the queued attachment
 * @param attachmentSha256Hash  the SHA-256 hash of the queued attachment
 * @param hiddenPages           the queued slide visibility configuration
 * @param pageOrder             the queued slide order, or {@code null} when only new slides should be created
 */
public record AttachmentVideoUnitSlideSplitJob(long attachmentVideoUnitId, long attachmentId, Integer attachmentVersion, String attachmentSha256Hash,
        List<HiddenPageInfoDTO> hiddenPages, List<SlideOrderDTO> pageOrder) {

    public AttachmentVideoUnitSlideSplitJob {
        hiddenPages = hiddenPages == null ? null : List.copyOf(hiddenPages);
        pageOrder = pageOrder == null ? null : List.copyOf(pageOrder);
    }

    /**
     * Captures the current attachment revision and slide configuration before asynchronous execution starts.
     *
     * @param attachmentVideoUnit the unit whose attachment should be split
     * @param hiddenPages         the slide visibility configuration
     * @param pageOrder           the slide order, or {@code null} when only new slides should be created
     * @return an immutable split job
     */
    public static AttachmentVideoUnitSlideSplitJob of(AttachmentVideoUnit attachmentVideoUnit, List<HiddenPageInfoDTO> hiddenPages, List<SlideOrderDTO> pageOrder) {
        Objects.requireNonNull(attachmentVideoUnit, "attachmentVideoUnit");
        Attachment attachment = Objects.requireNonNull(attachmentVideoUnit.getAttachment(), "attachmentVideoUnit.attachment");
        return new AttachmentVideoUnitSlideSplitJob(Objects.requireNonNull(attachmentVideoUnit.getId(), "attachmentVideoUnit.id"),
                Objects.requireNonNull(attachment.getId(), "attachment.id"), attachment.getVersion(), attachment.getSha256Hash(), hiddenPages, pageOrder);
    }

    /**
     * Checks whether the locked unit still references the exact attachment revision captured by this job.
     *
     * @param attachment the current authoritative attachment
     * @return whether the queued revision is still current
     */
    public boolean matches(Attachment attachment) {
        return attachment != null && Objects.equals(attachmentId, attachment.getId()) && Objects.equals(attachmentVersion, attachment.getVersion())
                && Objects.equals(attachmentSha256Hash, attachment.getSha256Hash());
    }
}
