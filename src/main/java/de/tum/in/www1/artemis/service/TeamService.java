package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.dto.TeamSearchUserDTO;
import de.tum.in.www1.artemis.web.rest.errors.StudentsAlreadyAssignedException;

@Service
public class TeamService {

    private TeamRepository teamRepository;

    private UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    public TeamService(TeamRepository teamRepository, UserRepository userRepository, AuthorizationCheckService authCheckService) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * Finds the team of a given user for an exercise
     * @param exercise Exercise for which to find the team
     * @param user Student for which to find the team
     * @return found team (or empty if student has not been assigned to a team yet for the exercise)
     */
    public Optional<Team> findOneByExerciseAndUser(Exercise exercise, User user) {
        return teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), user.getId());
    }

    /**
     * Returns whether the student is already assigned to a team for a given exercise
     * @param exercise Exercise for which to check
     * @param user Student for which to check
     * @return boolean flag whether the student has been assigned already or not yet
     */
    public boolean isAssignedToTeam(Exercise exercise, User user) {
        return teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), user.getId()).isPresent();
    }

    /**
     * Search for users by login or name in course
     * @param course Course in which to search students
     * @param exercise Exercise in which the student might be added to a team
     * @param loginOrName Login or name by which to search students
     * @return users whose login matched
     */
    public List<TeamSearchUserDTO> searchByLoginOrNameInCourseForExerciseTeam(Course course, Exercise exercise, String loginOrName) {
        List<User> users = userRepository.searchByLoginOrNameInGroup(course.getStudentGroupName(), loginOrName);
        List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        List<TeamSearchUserDTO> teamSearchUsers = users.stream().map(TeamSearchUserDTO::new).collect(Collectors.toList());
        // Annotate whether the user is already assigned to a team for the given exercise
        HashSet<Long> loginsOfAssignedStudents = (HashSet<Long>) teamRepository.findAssignedUserIdsByExerciseIdAndUserIds(exercise.getId(), userIds);
        teamSearchUsers.forEach(user -> user.setIsAssignedToTeam(loginsOfAssignedStudents.contains(user.getId())));
        return teamSearchUsers;
    }

    /**
     * Saves a team to the database (and verifies before that none of the students is already assigned to another team)
     * @param exercise Exercise which the team belongs to
     * @param team Team to be saved
     * @return saved Team
     */
    public Team save(Exercise exercise, Team team) {
        // verify that students are not assigned yet to another team for this exercise
        List<Pair<User, Team>> conflicts = findStudentTeamConflicts(exercise, team);
        if (!conflicts.isEmpty()) {
            throw new StudentsAlreadyAssignedException(conflicts);
        }
        team.setExercise(exercise);
        return teamRepository.save(team);
    }

    /**
     * Checks for each student in the given team whether they already belong to a different team
     * @param exercise Exercise which the team belongs to
     * @param team Team whose students should be checked for conflicts with other teams
     * @return list of conflict pairs <student, team> where team is a different team than in the argument
     */
    private List<Pair<User, Team>> findStudentTeamConflicts(Exercise exercise, Team team) {
        List<Pair<User, Team>> conflicts = new ArrayList<Pair<User, Team>>();
        team.getStudents().forEach(student -> {
            Optional<Team> assignedTeam = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), student.getId());
            if (assignedTeam.isPresent() && !assignedTeam.get().equals(team)) {
                conflicts.add(Pair.of(student, assignedTeam.get()));
            }
        });
        return conflicts;
    }
}
