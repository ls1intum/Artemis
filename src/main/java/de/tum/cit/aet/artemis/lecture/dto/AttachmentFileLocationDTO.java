package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Everything needed to locate the file of one attachment, without loading the attachment.
 * <p>
 * The three ids and the stored filename are exactly the arguments {@code FileSystemLocation.AttachmentVideoUnitFile} and {@code FileSystemLocation.LectureAttachment} take, so a
 * caller can build both candidate locations of an attachment file from one row of this projection. It exists for the migration entry that moves the files of the attachments the
 * lecture migration left behind, which has to look at every attachment in the installation and must not load one entity per file to do it.
 *
 * @param attachmentId          the id of the attachment, for logging
 * @param attachmentVideoUnitId the id of the attachment video unit that owns the attachment
 * @param lectureId             the id of that unit's lecture
 * @param storedFilename        the filename as the column holds it
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttachmentFileLocationDTO(long attachmentId, long attachmentVideoUnitId, long lectureId, String storedFilename) {
}
