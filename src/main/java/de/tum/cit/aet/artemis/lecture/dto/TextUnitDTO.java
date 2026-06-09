package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextUnitDTO(Long id, String name, ZonedDateTime releaseDate, String content, Set<CompetencyLinkDTO> competencyLinks, @JsonProperty("type") String type)
        implements LectureUnitDTO {

    public TextUnitDTO {
        type = "text";
    }

    public static TextUnitDTO of(TextUnit textUnit) {
        return new TextUnitDTO(textUnit.getId(), textUnit.getName(), textUnit.getReleaseDate(), textUnit.getContent(),
                textUnit.getCompetencyLinks().stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet()), textUnit.getType());
    }
}
