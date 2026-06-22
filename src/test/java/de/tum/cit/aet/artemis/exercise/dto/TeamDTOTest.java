package de.tum.cit.aet.artemis.exercise.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.Team;

class TeamDTOTest {

    /**
     * Regression guard: the client's {@code AccountService.isOwnerOfParticipation} matches the logged-in login
     * against {@code participation.team.students[*].login}. The DTO must therefore carry the team members with their
     * login; dropping them broke the team text editor for the owning student.
     */
    @Test
    void mapsTeamMembersWithLoginSoTheClientCanVerifyOwnership() {
        User student = new User();
        student.setLogin("ab12cde");
        Team team = new Team();
        team.addStudents(student);

        TeamDTO dto = TeamDTO.of(team);

        assertThat(dto).isNotNull();
        assertThat(dto.students()).as("team members are exposed so the client can verify ownership").extracting(UserNameDTO::login).containsExactly("ab12cde");
    }

    @Test
    void returnsNullForNullTeam() {
        assertThat(TeamDTO.of(null)).isNull();
    }
}
