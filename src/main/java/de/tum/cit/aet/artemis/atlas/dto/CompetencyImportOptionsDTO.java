package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Selects the course competencies to import and controls which related data is copied.
 *
 * @param competencyIds   the ids selected for a single or bulk import
 * @param sourceCourseId  the source course for an import of all course competencies
 * @param importRelations whether to copy relations
 * @param importExercises whether to copy linked exercises
 * @param importLectures  whether to copy linked lectures
 * @param referenceDate   the optional date used to shift imported dates
 * @param isReleaseDate   whether the reference date represents the release date
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyImportOptionsDTO(@Nullable Set<Long> competencyIds, Optional<Long> sourceCourseId, boolean importRelations, boolean importExercises, boolean importLectures,
        Optional<ZonedDateTime> referenceDate, boolean isReleaseDate) {
}
