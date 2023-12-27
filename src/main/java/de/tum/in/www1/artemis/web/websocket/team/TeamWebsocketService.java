package de.tum.in.www1.artemis.web.websocket.team;

import java.util.*;

import javax.annotation.Nullable;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.web.websocket.dto.TeamAssignmentPayload;

@Controller
public class TeamWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(TeamWebsocketService.class);

    private final WebsocketMessagingService websocketMessagingService;

    private final String assignmentTopic = "/topic/team-assignments";

    private final TeamRepository teamRepository;

    public TeamWebsocketService(WebsocketMessagingService websocketMessagingService, TeamRepository teamRepository) {
        this.websocketMessagingService = websocketMessagingService;
        this.teamRepository = teamRepository;
    }

    /**
     * Sends out team assignment information for an exercise to students of a created/updated/deleted team
     *
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
        if (existingTeam != null) {
            if (!Hibernate.isInitialized(existingTeam.getStudents())) {
                existingTeam = teamRepository.findWithStudentsByIdElseThrow(existingTeam.getId());
            }
            TeamAssignmentPayload payload = new TeamAssignmentPayload(exercise, null, List.of());
            Set<User> unassignedUsers = new HashSet<>(existingTeam.getStudents());
            unassignedUsers.removeAll(Optional.ofNullable(updatedTeam).map(Team::getStudents).orElse(Set.of()));
            unassignedUsers.forEach(user -> websocketMessagingService.sendMessageToUser(user.getLogin(), assignmentTopic, payload));
        }

        // Users in the updated team that were not yet part of the existing team were newly assigned => inform them
        if (updatedTeam != null) {
            if (!Hibernate.isInitialized(updatedTeam.getStudents())) {
                updatedTeam = teamRepository.findWithStudentsByIdElseThrow(updatedTeam.getId());
            }
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
