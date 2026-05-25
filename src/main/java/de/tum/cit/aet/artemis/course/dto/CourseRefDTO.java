package de.tum.cit.aet.artemis.course.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lightweight, cycle-free projection of {@link de.tum.cit.aet.artemis.course.domain.Course}
 * intended for embedding inside REST response DTOs.
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
}
