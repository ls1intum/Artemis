package de.tum.in.www1.artemis.team;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.user.UserFactory;

/**
 * Factory for creating Teams and related objects.
 */
public class TeamFactory {

    /**
     * Generates a Team with the given arguments. Also generates the required Users.
     * Note: The Team cannot be saved to the database without saving the Users first.
     *
     * @param exercise           The Exercise the Team belongs to
     * @param name               The name of the Team
     * @param shortName          The short name of the Team
     * @param loginPrefix        The prefix that will be added in front of every user's login together with the shortName
     * @param numberOfStudents   The number of students to generate for the Team
     * @param owner              The owner of the Team
     * @param creatorLogin       The login of the user that creates the Teams
     * @param registrationPrefix The prefix that will be added in front of every student's registration number together with the shortName
     * @return The generated Team
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
     * Generates a Team with the given arguments. Also generates the required Users.
     * Note: The Team cannot be saved to the database without saving the Users first.
     *
     * @param exercise         The Exercise the Team belongs to
     * @param name             The name of the Team
     * @param shortName        The short name of the Team
     * @param numberOfStudents The number of students to generate for the Team
     * @param owner            The owner of the Team
     * @return The generated Team
     */
    public static Team generateTeamForExercise(Exercise exercise, String name, String shortName, int numberOfStudents, User owner) {
        return generateTeamForExercise(exercise, name, shortName, "student", numberOfStudents, owner, null, "R");
    }
}
