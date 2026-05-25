package de.tum.cit.aet.artemis.course.dto;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Static factory for {@link CourseRefDTO} instances.
 * <p>
 * Pure and side-effect free; intentionally not a Spring bean so it can be invoked from REST
 * response mapping paths without going through the application context.
 */
public final class CourseMapper {

    private CourseMapper() {
    }

    /**
     * Build a {@link CourseRefDTO} from a {@link Course} entity. Returns {@code null} if the
     * input is {@code null} so callers can map nullable references without a separate guard.
     *
     * @param course the course entity to project, may be {@code null}
     * @return the projected reference, or {@code null} when {@code course} is {@code null}
     */
    public static @Nullable CourseRefDTO toRef(@Nullable Course course) {
        if (course == null) {
            return null;
        }
        return new CourseRefDTO(course.getId(), course.getTitle(), course.getShortName(), course.getColor());
    }
}
