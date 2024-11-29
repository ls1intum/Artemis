package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyNameDTO(long id, String title, boolean optional, CompetencyTaxonomy taxonomy, @Nullable ZonedDateTime softDueDate, CompetencyProgress userProgress,
        double masteryProgress, int numberOfLearningObjects, int numberOfCompletedLearningObjects) {

    public static CompetencyNameDTO of(CourseCompetency competency, int numberOfCompletedLearningObjects) {
        Optional<CompetencyProgress> optionalProgress = competency.getUserProgress().stream().findFirst();
        int numberOfLearningObjects = competency.getExerciseLinks().size() + competency.getLectureUnitLinks().size();
        return new CompetencyNameDTO(competency.getId(), competency.getTitle(), competency.isOptional(), competency.getTaxonomy(), competency.getSoftDueDate(),
                optionalProgress.orElse(null), optionalProgress.map(CompetencyProgressService::getMasteryProgress).orElse(0.0), numberOfLearningObjects,
                numberOfCompletedLearningObjects);
    }
}
