package de.tum.in.www1.artemis.domain.competency;

import java.time.ZonedDateTime;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PREREQUISITE")
public class Prerequisite extends CourseCompetency {

    public Prerequisite(String title, String description, ZonedDateTime softDueDate, Integer masteryThreshold, CompetencyTaxonomy taxonomy, boolean optional) {
        super(title, description, softDueDate, masteryThreshold, taxonomy, optional);
    }

    public Prerequisite() {
    }
}
