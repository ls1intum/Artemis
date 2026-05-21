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

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseCompetencyRequestDTO(Long id, @NotBlank @Size(max = 255) String title, @Size(max = 10000) String description, ZonedDateTime softDueDate,
        @NotNull @Min(1) @Max(100) Integer masteryThreshold, CompetencyTaxonomy taxonomy, Boolean optional) {

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
