package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttachmentForCompetencyDTO(Long id, String name, String link, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime uploadDate, @Nullable Integer version,
        @Nullable AttachmentType attachmentType, @Nullable String studentVersion) {

    @Nullable
    public static AttachmentForCompetencyDTO of(@Nullable Attachment attachment) {
        if (attachment == null) {
            return null;
        }
        return new AttachmentForCompetencyDTO(attachment.getId(), attachment.getName(), attachment.getLink(), attachment.getReleaseDate(), attachment.getUploadDate(),
                attachment.getVersion(), attachment.getAttachmentType(), attachment.getStudentVersion());
    }
}
