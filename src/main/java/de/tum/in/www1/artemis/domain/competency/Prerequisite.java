package de.tum.in.www1.artemis.domain.competency;

import java.time.ZonedDateTime;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * A competency that students are expected to have mastered before participating in a course.
 */
@Entity
@DiscriminatorValue("P")
public class Prerequisite extends CourseCompetency {

    public Prerequisite(String title, String description, ZonedDateTime softDueDate, Integer masteryThreshold, CompetencyTaxonomy taxonomy, boolean optional) {
        super(title, description, softDueDate, masteryThreshold, taxonomy, optional);
    }

    public Prerequisite() {
    }

    @Override
    public String getType() {
        return "prerequisite";
    }
}
