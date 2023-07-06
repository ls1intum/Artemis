package de.tum.in.www1.artemis.team;

import java.util.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.user.UserFactory;

/**
 * Factory for creating Teams and related objects.
 */
public class TeamFactory {

    /**
     * Generate a team
     *
     * @param exercise           exercise of the team
     * @param name               name of the team
     * @param shortName          short name of the team
     * @param loginPrefix        prefix that will be added in front of every user's login
     * @param numberOfStudents   amount of users to generate for team as students
     * @param owner              owner of the team generally a tutor
     * @param creatorLogin       login of user that creates the teams
     * @param registrationPrefix prefix that will be added in front of every student's registration number
     * @return team that was generated
     */
    public static Team generateTeamForExercise(Exercise exercise, String name, String shortName, String loginPrefix, int numberOfStudents, User owner, String creatorLogin,
            String registrationPrefix) {
        List<User> students = UserFactory.generateActivatedUsersWithRegistrationNumber(shortName + loginPrefix, new String[] { "tumuser", "testgroup" },
                Set.of(new Authority(Role.STUDENT.getAuthority())), numberOfStudents, shortName + registrationPrefix);

        Team team = new Team();
        team.setName(name);
        team.setShortName(shortName);
        team.setExercise(exercise);
        team.setStudents(new HashSet<>(students));
        if (owner != null) {
            team.setOwner(owner);
        }
        if (creatorLogin != null) {
            team.setCreatedBy(creatorLogin);
            team.setLastModifiedBy(creatorLogin);
        }
        return team;
    }

    /**
     * Generate a team
     *
     * @param exercise         exercise of the team
     * @param name             name of the team
     * @param shortName        short name of the team
     * @param numberOfStudents amount of users to generate for team as students
     * @param owner            owner of the team generally a tutor
     * @return team that was generated
     */
    public static Team generateTeamForExercise(Exercise exercise, String name, String shortName, int numberOfStudents, User owner) {
        return generateTeamForExercise(exercise, name, shortName, "student", numberOfStudents, owner, null, "R");
    }
}
