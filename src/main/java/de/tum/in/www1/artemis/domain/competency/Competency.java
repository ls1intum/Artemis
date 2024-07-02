package de.tum.in.www1.artemis.domain.competency;

import java.time.ZonedDateTime;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("C")
public class Competency extends CourseCompetency {

    public Competency(String title, String description, ZonedDateTime softDueDate, Integer masteryThreshold, CompetencyTaxonomy taxonomy, boolean optional) {
        super(title, description, softDueDate, masteryThreshold, taxonomy, optional);
    }

    public Competency() {
    }
}
