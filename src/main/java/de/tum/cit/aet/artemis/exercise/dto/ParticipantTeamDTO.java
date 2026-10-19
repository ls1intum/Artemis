package de.tum.cit.aet.artemis.exercise.dto;

import java.time.Instant;
import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Team;

/**
 * The team of a team participation, as the exercise details and the participation submissions put it on the wire.
 * <p>
 * The client checks ownership of a team participation against {@code team.students[*].login}, so the members stay
 * nested users. They are present only where the route fetched them, exactly as the lazy entity collection was.
 *
 * @param id                    the id of the team
 * @param createdDate           when the team was created
 * @param createdBy             who created the team
 * @param lastModifiedDate      when the team was last changed
 * @param lastModifiedBy        who last changed the team
 * @param name                  the name of the team
 * @param shortName             the short name of the team
 * @param participantIdentifier the short name again, as the participant identifier
 * @param image                 the image of the team
 * @param students              the members of the team, where the route fetched them
 * @param owner                 the tutor who owns the team
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipantTeamDTO(Long id, @Nullable Instant createdDate, @Nullable String createdBy, @Nullable Instant lastModifiedDate, @Nullable String lastModifiedBy,
        @Nullable String name, @Nullable String shortName, @Nullable String participantIdentifier, @Nullable String image, @Nullable List<ParticipantUserDTO> students,
        @Nullable ParticipantUserDTO owner) {

    /**
     * Maps a team, or nothing when it was not loaded.
     *
     * @param team the team, may be {@code null} or an uninitialized proxy
     * @return the team as the payloads carry it, or {@code null}
     */
    public static @Nullable ParticipantTeamDTO of(@Nullable Team team) {
        if (team == null || !Hibernate.isInitialized(team)) {
            return null;
        }
        List<ParticipantUserDTO> students = team.getStudents() != null && Hibernate.isInitialized(team.getStudents())
                ? team.getStudents().stream().map(ParticipantUserDTO::of).toList()
                : null;
        return new ParticipantTeamDTO(team.getId(), team.getCreatedDate(), team.getCreatedBy(), team.getLastModifiedDate(), team.getLastModifiedBy(), team.getName(),
                team.getShortName(), team.getParticipantIdentifier(), team.getImage(), students, ParticipantUserDTO.of(team.getOwner()));
    }
}
