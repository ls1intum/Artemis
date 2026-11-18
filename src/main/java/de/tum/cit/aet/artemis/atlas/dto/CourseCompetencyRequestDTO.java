package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;
import java.util.function.Supplier;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseCompetencyRequestDTO(@Schema(description = "The id of the competency. Required to update one, omitted to create one.") Long id,
        @Schema(description = "The title of the competency, at most 255 characters.") @NotBlank @Size(max = 255) String title,
        @Schema(description = "A description of what the student should be able to do, at most 10000 characters.") @Size(max = 10000) String description,
        @Schema(description = "A soft due date for reaching the competency. Optional.") ZonedDateTime softDueDate,
        @Schema(description = "The progress, from 1 to 100 percent, at which the competency counts as mastered.") @NotNull @Min(1) @Max(100) Integer masteryThreshold,
        @Schema(description = "The taxonomy level of the competency. Optional.") CompetencyTaxonomy taxonomy,
        @Schema(description = "Whether the competency is optional. Defaults to false.") Boolean optional) {

    /**
     * Maps a request DTO to a course competency entity.
     *
     * @param dto         the request DTO to map
     * @param constructor the constructor for the competency subtype
     * @return the mapped competency entity
     * @param <T> the competency subtype
     */
    public static <T extends CourseCompetency> T toEntity(CourseCompetencyRequestDTO dto, Supplier<T> constructor) {
        T competency = constructor.get();
        competency.setId(dto.id());
        competency.setTitle(dto.title());
        competency.setDescription(dto.description());
        competency.setSoftDueDate(dto.softDueDate());
        competency.setMasteryThreshold(dto.masteryThreshold() != null ? dto.masteryThreshold() : CourseCompetency.DEFAULT_MASTERY_THRESHOLD);
        competency.setTaxonomy(dto.taxonomy());
        competency.setOptional(Boolean.TRUE.equals(dto.optional()));
        return competency;
    }
}
