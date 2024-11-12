package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;

public record CompetencyStudentProgressDTO(String title, String description, CompetencyTaxonomy taxonomy, ZonedDateTime softDueDate, boolean optional, String type,
        long numberOfStudents, long numberOfMasteredStudents) {

    public static CompetencyStudentProgressDTO of(CourseCompetency courseCompetency) {
        return new CompetencyStudentProgressDTO(courseCompetency.getTitle(), courseCompetency.getDescription(), courseCompetency.getTaxonomy(), courseCompetency.getSoftDueDate(),
                courseCompetency.isOptional(), courseCompetency.getClass().getSimpleName(), courseCompetency.getUserProgress().size(),
                courseCompetency.getUserProgress().stream().filter(CompetencyProgressService::isMastered).count());
    }
}
