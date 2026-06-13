package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Team;

/**
 * Minimal DTO identifying the team participant of a team participation (id, name, short name).
 * Dumb DTO: only scalar values, no entity references.
 *
 * @param id        the team id
 * @param name      the team name
 * @param shortName the team short name
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamDTO(Long id, String name, String shortName) implements Serializable {

    /**
     * Converts a {@link Team} into a {@link TeamDTO}.
     *
     * @param team the team to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static TeamDTO of(Team team) {
        return Optional.ofNullable(team).map(t -> new TeamDTO(t.getId(), t.getName(), t.getShortName())).orElse(null);
    }
}
