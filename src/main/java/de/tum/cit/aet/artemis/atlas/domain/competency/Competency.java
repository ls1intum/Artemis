package de.tum.cit.aet.artemis.atlas.domain.competency;

import java.time.ZonedDateTime;
import java.util.Objects;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

@Entity
@DiscriminatorValue("C")
public class Competency extends CourseCompetency {

    public Competency(String title, String description, ZonedDateTime softDueDate, Integer masteryThreshold, CompetencyTaxonomy taxonomy, boolean optional) {
        super(title, description, softDueDate, masteryThreshold, taxonomy, optional);
    }

    public Competency(CourseCompetency courseCompetency) {
        super(courseCompetency.getTitle(), courseCompetency.getDescription(), courseCompetency.getSoftDueDate(), courseCompetency.getMasteryThreshold(),
                courseCompetency.getTaxonomy(), courseCompetency.isOptional());
    }

    public Competency() {
    }

    @Override
    public String getType() {
        return "competency";
    }

    /**
     * Validates that the given competency belongs to the same course as the exercise.
     * If the exercise has no course (e.g. inconsistent state), this check is skipped.
     *
     * @param exerciseCourseId the course id of the exercise (maybe {@code null})
     * @param competency       a managed competency entity or reference
     * @throws BadRequestAlertException if the competency is associated with a different course
     */
    public void validateCompetencyBelongsToExerciseCourse(Long exerciseCourseId, Competency competency) {
        if (exerciseCourseId == null) {
            return;
        }
        var competencyCourse = competency.getCourse();
        Long competencyCourseId = competencyCourse != null ? competencyCourse.getId() : null;

        if (competencyCourseId != null && !Objects.equals(exerciseCourseId, competencyCourseId)) {
            throw new BadRequestAlertException("The competency does not belong to the exercise's course.", "CourseCompetency", "wrongCourse");
        }
    }
}
