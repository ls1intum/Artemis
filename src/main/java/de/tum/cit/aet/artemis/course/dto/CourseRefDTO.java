package de.tum.cit.aet.artemis.course.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Lightweight, cycle-free projection of {@link Course} intended for embedding inside REST response DTOs.
 * <p>
 * The {@code Course} entity graph contains many cyclic relations ({@code Course.tutorialGroups},
 * {@code Course.users}, exercise back-refs, ...) that should not cross the network. Most embedding
 * call sites only need id, title, short name and color to render a chip or link.
 *
 * @param id        the course id
 * @param title     the displayed course title (nullable for unbuilt fixtures)
 * @param shortName the course short name used in API paths (nullable)
 * @param color     the course color used by the UI (nullable)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRefDTO(Long id, @Nullable String title, @Nullable String shortName, @Nullable String color) {

    /**
     * Build a {@link CourseRefDTO} from a {@link Course} entity. Returns {@code null} if the
     * input is {@code null} so callers can map nullable references without a separate guard.
     *
     * @param course the course entity to project, may be {@code null}
     * @return the projected reference, or {@code null} when {@code course} is {@code null}
     */
    public static @Nullable CourseRefDTO from(@Nullable Course course) {
        if (course == null) {
            return null;
        }
        return new CourseRefDTO(course.getId(), course.getTitle(), course.getShortName(), course.getColor());
    }
}
