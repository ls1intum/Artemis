package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

/**
 * DTO for the {@link AttachmentVideoUnit} REST boundary.
 * <p>
 * It is used both as the request part on create/update (the client only populates {@code name}, {@code releaseDate},
 * {@code description}, {@code videoSource} and {@code competencyLinks}) and as the response body (additionally carrying
 * the mapped {@link AttachmentDTO} and {@link SlideDTO}s). Lazy associations are mapped defensively so the DTO can also
 * be created right after persisting, before all associations are initialized.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttachmentVideoUnitDTO(Long id, String name, ZonedDateTime releaseDate, String description, String videoSource, Set<CompetencyLinkDTO> competencyLinks,
        AttachmentDTO attachment, List<SlideDTO> slides, boolean completed, boolean visibleToStudents, AttachmentDTO.LectureReferenceDTO lecture, @JsonProperty("type") String type)
        implements LectureUnitDTO {

    public AttachmentVideoUnitDTO {
        type = "attachment";
    }

    /**
     * Maps an {@link AttachmentVideoUnit} entity to its response DTO, mapping lazy associations defensively.
     *
     * @param unit the attachment video unit to map
     * @return the populated DTO including the mapped attachment and (initialized) slides
     */
    public static AttachmentVideoUnitDTO of(AttachmentVideoUnit unit) {
        Set<CompetencyLinkDTO> competencyLinks = unit.getCompetencyLinks() != null && Hibernate.isInitialized(unit.getCompetencyLinks())
                ? unit.getCompetencyLinks().stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet())
                : Set.of();
        List<SlideDTO> slides = unit.getSlides() != null && Hibernate.isInitialized(unit.getSlides()) ? unit.getSlides().stream().map(SlideDTO::from).toList() : List.of();
        AttachmentDTO attachment = unit.getAttachment() != null ? AttachmentDTO.of(unit.getAttachment()) : null;
        // The PDF preview reads attachmentVideoUnit.lecture.id when saving/updating, so keep the lightweight lecture reference.
        AttachmentDTO.LectureReferenceDTO lecture = AttachmentDTO.LectureReferenceDTO.of(unit.getLecture());
        return new AttachmentVideoUnitDTO(unit.getId(), unit.getName(), unit.getReleaseDate(), unit.getDescription(), unit.getVideoSource(), competencyLinks, attachment, slides,
                unit.isCompleted(), unit.isVisibleToStudents(), lecture, unit.getType());
    }
}
