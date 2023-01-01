package de.tum.in.www1.artemis.web.rest.dto;

import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;

/**
 * Represents a lecture unit to be created with attachmentUnit, attachment and file
 */
public record LectureUnitDTO(AttachmentUnit attachmentUnit, Attachment attachment, MultipartFile file) {
}
