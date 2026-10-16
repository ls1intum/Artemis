package de.tum.cit.aet.artemis.lecture.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The stored student version reference and file version of an attachment, read right before a student version is regenerated, so publishing it can check that neither changed.
 *
 * @param studentVersion the stored student version value, or null if there is none
 * @param version        the version of the attachment file
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttachmentStudentVersionStateDTO(@Nullable String studentVersion, @Nullable Integer version) {
}
