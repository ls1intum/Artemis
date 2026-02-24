package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseCompetencyResponseDTO(long id, String title, @Nullable String description, @Nullable CompetencyTaxonomy taxonomy, @Nullable ZonedDateTime softDueDate,
        int masteryThreshold, boolean optional, String type, @Nullable LinkedCourseCompetencyDTO linkedCourseCompetency, @Nullable Long linkedStandardizedCompetencyId,
        List<CompetencyProgressDTO> userProgress, @Nullable CourseInfoDTO course, List<CompetencyExerciseLinkResponseDTO> exerciseLinks,
        List<CompetencyLectureUnitLinkResponseDTO> lectureUnitLinks) {

    public static CourseCompetencyResponseDTO of(CourseCompetency competency) {
        var progress = Optional.ofNullable(competency.getUserProgress()).orElse(Collections.emptySet()).stream().map(CompetencyProgressDTO::of).toList();
        StandardizedCompetency linkedStandardizedCompetency = competency.getLinkedStandardizedCompetency();
        Long linkedStandardizedCompetencyId = linkedStandardizedCompetency != null ? linkedStandardizedCompetency.getId() : null;
        return new CourseCompetencyResponseDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getSoftDueDate(),
                competency.getMasteryThreshold(), competency.isOptional(), competency.getType(), LinkedCourseCompetencyDTO.of(competency.getLinkedCourseCompetency()),
                linkedStandardizedCompetencyId, progress, null, null, null);
    }

    public static CourseCompetencyResponseDTO ofWithCourse(CourseCompetency competency) {
        var base = of(competency);
        return new CourseCompetencyResponseDTO(base.id(), base.title(), base.description(), base.taxonomy(), base.softDueDate(), base.masteryThreshold(), base.optional(),
                base.type(), base.linkedCourseCompetency(), base.linkedStandardizedCompetencyId(), base.userProgress(), CourseInfoDTO.of(competency.getCourse()), null, null);
    }

    public static CourseCompetencyResponseDTO ofWithLearningObjects(CourseCompetency competency) {
        var base = of(competency);
        var exerciseLinks = Optional.ofNullable(competency.getExerciseLinks()).orElse(Collections.emptySet()).stream().map(CompetencyExerciseLinkResponseDTO::of).toList();
        var lectureUnitLinks = Optional.ofNullable(competency.getLectureUnitLinks()).orElse(Collections.emptySet()).stream().map(CompetencyLectureUnitLinkResponseDTO::of).toList();

        return new CourseCompetencyResponseDTO(base.id(), base.title(), base.description(), base.taxonomy(), base.softDueDate(), base.masteryThreshold(), base.optional(),
                base.type(), base.linkedCourseCompetency(), base.linkedStandardizedCompetencyId(), base.userProgress(), CourseInfoDTO.of(competency.getCourse()), exerciseLinks,
                lectureUnitLinks);
    }
}
