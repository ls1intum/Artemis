package de.tum.in.www1.artemis.domain.participation;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.view.QuizView;

@Entity
@DiscriminatorValue(value = "TP")
public class TeamParticipation extends ParticipantParticipation {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private Team team;

    public Team getTeam() {
        return team;
    }

    public Participation team(Team team) {
        this.team = team;
        return this;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    /**
     * Removes the team from the participation, can be invoked to make sure that sensitive information is not sent to the client.
     * E.g. tutors should not see information about the students in the team.
     */
    @Override
    public void filterSensitiveInformation() {
        setTeam(null);
    }

    @Override
    public String toString() {
        return "TeamParticipation{" + "id=" + getId() + ", presentationScore=" + presentationScore + ", team=" + team + '}';
    }

    @Override
    public Participation copyParticipationId() {
        var participation = new TeamParticipation();
        participation.setId(getId());
        return participation;
    }
}
