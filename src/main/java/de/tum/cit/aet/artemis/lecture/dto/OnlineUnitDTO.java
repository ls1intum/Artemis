package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OnlineUnitDTO(Long id, String name, ZonedDateTime releaseDate, String description, String source, Set<CompetencyLinkDTO> competencyLinks,
        @JsonProperty("type") String type) implements LectureUnitDTO {

    public OnlineUnitDTO {
        type = "online";
    }

    public static OnlineUnitDTO of(OnlineUnit onlineUnit) {
        return new OnlineUnitDTO(onlineUnit.getId(), onlineUnit.getName(), onlineUnit.getReleaseDate(), onlineUnit.getDescription(), onlineUnit.getSource(),
                onlineUnit.getCompetencyLinks().stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet()), onlineUnit.getType());
    }
}
