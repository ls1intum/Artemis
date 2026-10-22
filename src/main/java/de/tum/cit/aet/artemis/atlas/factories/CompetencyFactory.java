package de.tum.cit.aet.artemis.atlas.factories;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Factory for constructing {@link Competency} objects that are not backed by user input, i.e. integration test fixtures and the demo course seeded by the {@code demo} profile.
 * <p>
 * This factory only <b>constructs</b> the entity, it never persists it.
 */
public final class CompetencyFactory {

    private CompetencyFactory() {
        // static factory, do not instantiate
    }

    /**
     * Generates a competency for the given course.
     *
     * @param title            The title of the competency.
     * @param description      The description of the competency.
     * @param taxonomy         The taxonomy of the competency.
     * @param masteryThreshold The mastery threshold of the competency, in percent.
     * @param course           The course the competency belongs to.
     * @return The generated competency.
     */
    public static Competency generateCompetency(String title, String description, CompetencyTaxonomy taxonomy, int masteryThreshold, Course course) {
        Competency competency = new Competency();
        competency.setTitle(title);
        competency.setDescription(description);
        competency.setTaxonomy(taxonomy);
        competency.setMasteryThreshold(masteryThreshold);
        competency.setCourse(course);
        return competency;
    }
}
