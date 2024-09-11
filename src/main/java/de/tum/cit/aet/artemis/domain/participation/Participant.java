package de.tum.cit.aet.artemis.domain.participation;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.domain.User;

public interface Participant {

    Long getId();

    String getName();

    String getParticipantIdentifier();

    /**
     * convenience method
     *
     * @return either the user itself in a singleton set or all participants of a team
     */
    @JsonIgnore
    Set<User> getParticipants();
}
