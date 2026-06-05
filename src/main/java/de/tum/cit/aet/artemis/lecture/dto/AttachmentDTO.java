package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;

/**
 * DTO for the {@link Attachment} REST boundary. It carries exactly the fields the client reads from / sends to the
 * attachment endpoints and never exposes the related entity graphs (only a lightweight {@link LectureReferenceDTO}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttachmentDTO(Long id, String name, String link, Integer version, ZonedDateTime uploadDate, ZonedDateTime releaseDate, AttachmentType attachmentType,
        String studentVersion, LectureReferenceDTO lecture) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LectureReferenceDTO(Long id) {

        public static LectureReferenceDTO of(de.tum.cit.aet.artemis.lecture.domain.Lecture lecture) {
            return lecture == null ? null : new LectureReferenceDTO(lecture.getId());
        }
    }

    public static AttachmentDTO of(Attachment attachment) {
        return new AttachmentDTO(attachment.getId(), attachment.getName(), attachment.getLink(), attachment.getVersion(), attachment.getUploadDate(), attachment.getReleaseDate(),
                attachment.getAttachmentType(), attachment.getStudentVersion(), LectureReferenceDTO.of(attachment.getLecture()));
    }
}
