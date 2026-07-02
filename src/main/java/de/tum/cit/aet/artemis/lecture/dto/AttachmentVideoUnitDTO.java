package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentUpdateIntent;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttachmentVideoUnitDTO(Long id, String name, ZonedDateTime releaseDate, String description, String videoSource, Set<CompetencyLinkDTO> competencyLinks,
        AttachmentUpdateIntent attachmentUpdateIntent) implements LectureUnitDTO {

    public static AttachmentVideoUnitDTO from(AttachmentVideoUnit unit, AttachmentUpdateIntent intent) {
        var links = unit.getCompetencyLinks();
        Set<CompetencyLinkDTO> competencyLinks = links == null || !Hibernate.isInitialized(links) ? null : links.stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet());
        return new AttachmentVideoUnitDTO(unit.getId(), unit.getName(), unit.getReleaseDate(), unit.getDescription(), unit.getVideoSource(), competencyLinks, intent);
    }
}
