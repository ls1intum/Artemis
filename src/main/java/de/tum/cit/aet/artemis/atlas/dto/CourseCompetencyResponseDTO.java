package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A course competency or prerequisite with the details available for the current request.
 *
 * @param id                             the course competency id
 * @param title                          the title
 * @param description                    the optional description
 * @param taxonomy                       the optional taxonomy level
 * @param softDueDate                    the optional target date for reaching mastery
 * @param masteryThreshold               the percentage of progress required for mastery
 * @param optional                       whether the course competency is optional
 * @param generatedByAi                  whether it was created from an AI recommendation
 * @param type                           the course competency kind
 * @param linkedCourseCompetency         the optional competency linked from another course
 * @param linkedStandardizedCompetencyId the optional id of the linked standardized competency
 * @param userProgress                   the available progress entries
 * @param course                         the course details, when included
 * @param exerciseLinks                  the exercise links, when included
 * @param lectureUnitLinks               the lecture unit links, when included
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseCompetencyResponseDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id, String title, @Nullable String description,
        @Nullable CompetencyTaxonomy taxonomy, @Nullable ZonedDateTime softDueDate, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int masteryThreshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean optional, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean generatedByAi,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "competency", "prerequisite" }) String type,
        @Nullable LinkedCourseCompetencyDTO linkedCourseCompetency, @Nullable Long linkedStandardizedCompetencyId, List<CompetencyProgressDTO> userProgress,
        @Nullable CourseInfoDTO course, List<CompetencyExerciseLinkResponseDTO> exerciseLinks, List<CompetencyLectureUnitLinkResponseDTO> lectureUnitLinks){

    /**
     * Maps a course competency to a response DTO without learning objects or course info.
     *
     * @param competency the competency to map
     * @return the DTO
     */
    public static CourseCompetencyResponseDTO of(CourseCompetency competency) {
        List<CompetencyProgressDTO> progress = List.of();
        if (Hibernate.isInitialized(competency.getUserProgress())) {
            progress = Optional.ofNullable(competency.getUserProgress()).orElse(Set.of()).stream().map(CompetencyProgressDTO::of).toList();
        }
        StandardizedCompetency linkedStandardizedCompetency = competency.getLinkedStandardizedCompetency();
        Long linkedStandardizedCompetencyId = linkedStandardizedCompetency != null ? linkedStandardizedCompetency.getId() : null;
        return new CourseCompetencyResponseDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getSoftDueDate(),
                competency.getMasteryThreshold(), competency.isOptional(), competency.isGeneratedByAi(), competency.getType(),
                LinkedCourseCompetencyDTO.of(competency.getLinkedCourseCompetency()), linkedStandardizedCompetencyId, progress, null, List.of(), List.of());
    }

    /**
     * Maps a course competency to a response DTO including course info.
     *
     * @param competency the competency to map
     * @return the DTO
     */
    public static CourseCompetencyResponseDTO ofWithCourse(CourseCompetency competency) {
        var base = of(competency);
        return new CourseCompetencyResponseDTO(base.id(), base.title(), base.description(), base.taxonomy(), base.softDueDate(), base.masteryThreshold(), base.optional(),
                base.generatedByAi(), base.type(), base.linkedCourseCompetency(), base.linkedStandardizedCompetencyId(), base.userProgress(),
                CourseInfoDTO.of(competency.getCourse()), List.of(), List.of());
    }

    /**
     * Maps a course competency to a response DTO including learning object links.
     *
     * @param competency the competency to map
     * @return the DTO
     */
    public static CourseCompetencyResponseDTO ofWithLearningObjects(CourseCompetency competency) {
        var base = of(competency);
        List<CompetencyExerciseLinkResponseDTO> exerciseLinks = List.of();
        if (Hibernate.isInitialized(competency.getExerciseLinks())) {
            exerciseLinks = Optional.ofNullable(competency.getExerciseLinks()).orElse(Set.of()).stream().map(CompetencyExerciseLinkResponseDTO::of).toList();
        }
        List<CompetencyLectureUnitLinkResponseDTO> lectureUnitLinks = List.of();
        if (Hibernate.isInitialized(competency.getLectureUnitLinks())) {
            lectureUnitLinks = Optional.ofNullable(competency.getLectureUnitLinks()).orElse(Set.of()).stream().map(CompetencyLectureUnitLinkResponseDTO::of).toList();
        }

        return new CourseCompetencyResponseDTO(base.id(), base.title(), base.description(), base.taxonomy(), base.softDueDate(), base.masteryThreshold(), base.optional(),
                base.generatedByAi(), base.type(), base.linkedCourseCompetency(), base.linkedStandardizedCompetencyId(), base.userProgress(),
                CourseInfoDTO.of(competency.getCourse()), exerciseLinks, lectureUnitLinks);
    }
}
