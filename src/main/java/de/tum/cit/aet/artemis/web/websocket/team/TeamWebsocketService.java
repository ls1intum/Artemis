package de.tum.cit.aet.artemis.web.websocket.team;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Nullable;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Team;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.web.websocket.dto.TeamAssignmentPayload;

@Profile(PROFILE_CORE)
@Controller
public class TeamWebsocketService {

    private final WebsocketMessagingService websocketMessagingService;

    private final String assignmentTopic = "/topic/team-assignments";

    public TeamWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Sends out team assignment information for an exercise to students of a created/updated/deleted team
     * <p>
     * Cases:
     * 1. Team was created: sendTeamAssignmentUpdate(exercise, null, createdTeam);
     * 2. Team was updated: sendTeamAssignmentUpdate(exercise, existingTeam, updatedTeam);
     * 3. Team was deleted: sendTeamAssignmentUpdate(exercise, deletedTeam, null);
     *
     * @param exercise                    Exercise for which the team assignment has been made
     * @param existingTeam                Team before the update (null when a team was created)
     * @param updatedTeam                 Team after the update (null when a team was deleted)
     * @param participationsOfUpdatedTeam Student participations of the updated team
     */
    public void sendTeamAssignmentUpdate(Exercise exercise, @Nullable Team existingTeam, @Nullable Team updatedTeam, List<StudentParticipation> participationsOfUpdatedTeam) {
        // Users in the existing team that are no longer in the updated team were unassigned => inform them
        // TODO: do we really need participations with submissions and results for the team assignment payload?
        if (existingTeam != null) {
            TeamAssignmentPayload payload = new TeamAssignmentPayload(exercise, null, List.of());
            Set<User> unassignedUsers = new HashSet<>(existingTeam.getStudents());
            unassignedUsers.removeAll(Optional.ofNullable(updatedTeam).map(Team::getStudents).orElse(Set.of()));
            unassignedUsers.forEach(user -> websocketMessagingService.sendMessageToUser(user.getLogin(), assignmentTopic, payload));
        }

        // Users in the updated team that were not yet part of the existing team were newly assigned => inform them
        if (updatedTeam != null) {
            TeamAssignmentPayload payload = new TeamAssignmentPayload(exercise, updatedTeam, participationsOfUpdatedTeam);
            Set<User> assignedUsers = new HashSet<>(updatedTeam.getStudents());
            assignedUsers.removeAll(Optional.ofNullable(existingTeam).map(Team::getStudents).orElse(Set.of()));
            assignedUsers.forEach(user -> websocketMessagingService.sendMessageToUser(user.getLogin(), assignmentTopic, payload));
        }
    }

    public void sendTeamAssignmentUpdate(Exercise exercise, @Nullable Team existingTeam, @Nullable Team updatedTeam) {
        sendTeamAssignmentUpdate(exercise, existingTeam, updatedTeam, List.of());
    }
}
