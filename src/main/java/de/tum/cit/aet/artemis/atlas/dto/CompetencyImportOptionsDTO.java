package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyImportOptionsDTO(Set<Long> competencyIds, Optional<Long> sourceCourseId, boolean importRelations, boolean importExercises, boolean importLectures,
        Optional<ZonedDateTime> referenceDate, boolean isReleaseDate) {
}
