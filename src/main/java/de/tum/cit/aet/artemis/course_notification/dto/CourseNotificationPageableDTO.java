package de.tum.cit.aet.artemis.course_notification.dto;

import java.io.Serializable;
import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Record for serializing paginated notification responses
 */
public record CourseNotificationPageableDTO<T>(List<T> content, int pageNumber, int pageSize, long totalElements, int totalPages) implements Serializable {

    public static <T> CourseNotificationPageableDTO<T> from(Page<T> page) {
        return new CourseNotificationPageableDTO<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
