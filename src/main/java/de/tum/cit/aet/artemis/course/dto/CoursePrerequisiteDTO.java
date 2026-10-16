package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;

/**
 * A prerequisite summary exposed with a course that is available for self-enrollment.
 *
 * @param id               the prerequisite identifier
 * @param title            the prerequisite title
 * @param description      the optional prerequisite description
 * @param taxonomy         the optional competency taxonomy
 * @param softDueDate      the optional recommended completion date
 * @param masteryThreshold the percentage required for mastery
 * @param optional         whether completing the prerequisite is optional
 * @param type             the prerequisite discriminator
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CoursePrerequisiteDTO(long id, String title, @Nullable String description, @Nullable CompetencyTaxonomy taxonomy, @Nullable ZonedDateTime softDueDate,
        int masteryThreshold, boolean optional, String type) {

    /**
     * Maps a prerequisite without exposing its course, progress, or learning-object associations.
     *
     * @param prerequisite the prerequisite to map
     * @return the prerequisite summary
     */
    public static CoursePrerequisiteDTO of(Prerequisite prerequisite) {
        return new CoursePrerequisiteDTO(prerequisite.getId(), prerequisite.getTitle(), prerequisite.getDescription(), prerequisite.getTaxonomy(), prerequisite.getSoftDueDate(),
                prerequisite.getMasteryThreshold(), prerequisite.isOptional(), prerequisite.getType());
    }
}
