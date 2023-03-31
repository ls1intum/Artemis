package de.tum.in.www1.artemis.web.rest.dto;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;

/**
 * Represents a lecture unit to be created with attachmentUnit, attachment, file and fileDocument.
 */
public record LectureUnitDTO(AttachmentUnit attachmentUnit, Attachment attachment, MultipartFile file, PDDocument fileDocument) {
}
