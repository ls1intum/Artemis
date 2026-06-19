package de.tum.cit.aet.artemis.videosource.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;

/**
 * DTO representing a {@link GocastCourseBinding} as returned by the Artemis REST API.
 * <p>
 * Serialized to JSON; null / absent optional fields are omitted (see {@link JsonInclude.Include#NON_EMPTY}).
 *
 * @param id               the binding entity id
 * @param courseId         the Artemis course id
 * @param gocastCourseId   the numeric gocast course id
 * @param gocastCourseSlug the gocast course slug (used for watch-page links)
 * @param status           the lifecycle status of this binding ({@code PENDING}, {@code ACTIVE}, or {@code REVOKED})
 * @param createdAt        the timestamp when this binding was first created
 * @param updatedAt        the timestamp when this binding was last updated
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastBindingDTO(long id, long courseId, long gocastCourseId, String gocastCourseSlug, GocastBindingStatus status, Instant createdAt, Instant updatedAt) {

    /**
     * Constructs a {@link GocastBindingDTO} from a {@link GocastCourseBinding} entity.
     *
     * @param binding the binding entity to convert
     * @return the DTO
     */
    public static GocastBindingDTO fromBinding(GocastCourseBinding binding) {
        return new GocastBindingDTO(binding.getId(), binding.getCourseId(), binding.getGocastCourseId(), binding.getGocastCourseSlug(), binding.getStatus(), binding.getCreatedAt(),
                binding.getUpdatedAt());
    }
}
