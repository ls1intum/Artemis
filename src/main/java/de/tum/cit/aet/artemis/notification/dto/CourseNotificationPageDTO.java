package de.tum.cit.aet.artemis.notification.dto;

import java.io.Serializable;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * REST page with the same JSON fields as the internal cached page. Failed notification reconstruction leaves a null item.
 *
 * @param content       notifications, including null reconstruction results; omitted when empty
 * @param pageNumber    zero-based page number
 * @param pageSize      requested page size
 * @param totalElements total number of rows, including reconstruction failures
 * @param totalPages    number of pages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationPageDTO(List<@Nullable CourseNotificationDTO> content, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pageNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pageSize, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) implements Serializable {

    /**
     * Copies page metadata without filtering entries or changing totals.
     *
     * @param page the existing service/cache page
     * @return its REST representation
     */
    public static CourseNotificationPageDTO from(CourseNotificationPageableDTO<CourseNotificationDTO> page) {
        return new CourseNotificationPageDTO(page.content(), page.pageNumber(), page.pageSize(), page.totalElements(), page.totalPages());
    }
}
