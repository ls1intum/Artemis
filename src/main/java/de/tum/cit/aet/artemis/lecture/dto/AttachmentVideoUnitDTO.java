package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Size;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentUpdateIntent;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

/**
 * DTO for the {@link AttachmentVideoUnit} REST boundary.
 * <p>
 * It is used both as the request part on create/update (the client only populates {@code name}, {@code releaseDate},
 * {@code description}, {@code videoSource}, {@code loadedVideoSource}, {@code competencyLinks}, and {@code attachmentUpdateIntent}) and as the response body
 * (additionally carrying the mapped {@link AttachmentDTO} and {@link SlideDTO}s). Lazy associations are mapped defensively so the DTO can also
 * be created right after persisting, before all associations are initialized.
 * <p>
 * On update, {@code loadedVideoSource} is the video URL the client's form was loaded with. The unit's video URL changes only if {@code videoSource} differs
 * from it, and only if the unit still has it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttachmentVideoUnitDTO(Long id, String name, ZonedDateTime releaseDate, @Size(max = 1000) String description, String videoSource,
        Set<CompetencyLinkDTO> competencyLinks, AttachmentDTO attachment, List<SlideDTO> slides, boolean slideMetadataPending, boolean completed, boolean visibleToStudents,
        LectureReferenceDTO lecture, AttachmentUpdateIntent attachmentUpdateIntent, String loadedVideoSource, @JsonProperty("type") String type) implements LectureUnitDTO {

    public AttachmentVideoUnitDTO {
        type = "attachment";
    }

    /**
     * The lecture an attachment video unit belongs to, reduced to its id.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LectureReferenceDTO(Long id) {

        public static LectureReferenceDTO of(Lecture lecture) {
            return lecture == null ? null : new LectureReferenceDTO(lecture.getId());
        }
    }

    /**
     * Maps an {@link AttachmentVideoUnit} entity to its response DTO, mapping lazy associations defensively.
     *
     * @param unit the attachment video unit to map
     * @return the populated DTO including the mapped attachment and (initialized) slides
     */
    public static AttachmentVideoUnitDTO of(AttachmentVideoUnit unit) {
        return from(unit, null);
    }

    /**
     * Maps an {@link AttachmentVideoUnit} entity and the explicit attachment update intent to its DTO.
     *
     * @param unit   the attachment video unit to map
     * @param intent the requested attachment update operation
     * @return the populated DTO
     */
    public static AttachmentVideoUnitDTO from(AttachmentVideoUnit unit, AttachmentUpdateIntent intent) {
        Set<CompetencyLinkDTO> competencyLinks = unit.getCompetencyLinks() != null && Hibernate.isInitialized(unit.getCompetencyLinks())
                ? unit.getCompetencyLinks().stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet())
                : Set.of();
        var attachmentEntity = unit.getAttachment();
        // Editor uploads keep the previous deck until asynchronous splitting reconciles its IDs and visibility.
        // Tell the preview to wait, rather than editing the new PDF with old metadata or temporary IDs.
        // Basic file replacements intentionally retire that deck and reset visibility; with no active slides, the preview can use its fully visible PDF fallback.
        boolean slidesInitialized = unit.getSlides() != null && Hibernate.isInitialized(unit.getSlides());
        boolean slideMetadataPending = attachmentEntity != null && slidesInitialized && unit.getSlides().stream().filter(slide -> !slide.isSuperseded())
                .anyMatch(slide -> !slide.belongsToRevision(attachmentEntity.getVersion(), attachmentEntity.getSha256Hash()));
        List<SlideDTO> slides = slidesInitialized ? unit.getSlides().stream().filter(slide -> !slide.isSuperseded())
                .filter(slide -> attachmentEntity == null || slide.belongsToRevision(attachmentEntity.getVersion(), attachmentEntity.getSha256Hash())).map(SlideDTO::from).toList()
                : List.of();
        AttachmentDTO attachment = attachmentEntity != null ? AttachmentDTO.of(attachmentEntity) : null;
        // The PDF preview reads attachmentVideoUnit.lecture.id when saving/updating, so keep the lightweight lecture reference.
        LectureReferenceDTO lecture = LectureReferenceDTO.of(unit.getLecture());
        return new AttachmentVideoUnitDTO(unit.getId(), unit.getName(), unit.getReleaseDate(), unit.getDescription(), unit.getVideoSource(), competencyLinks, attachment, slides,
                slideMetadataPending, unit.isCompleted(), unit.isVisibleToStudents(), lecture, intent, null, unit.getType());
    }
}
