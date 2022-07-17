package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.CIUserManagementService;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.JenkinsUserDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobPermission;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobPermissionsService;
import de.tum.in.www1.artemis.service.user.PasswordService;

@Service
@Profile("jenkins")
public class JenkinsUserManagementService implements CIUserManagementService {

    private final Logger log = LoggerFactory.getLogger(JenkinsUserManagementService.class);

    @Value("${artemis.continuous-integration.url}")
    private URL jenkinsServerUrl;

    @Value("${artemis.continuous-integration.user}")
    private String jenkinsAdminUsername;

    private final RestTemplate restTemplate;

    private final JenkinsJobPermissionsService jenkinsJobPermissionsService;

    protected final PasswordService passwordService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    @Value("${jenkins.use-pseudonyms:#{false}}")
    private boolean usePseudonyms;

    public JenkinsUserManagementService(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate, JenkinsJobPermissionsService jenkinsJobPermissionsService,
            PasswordService passwordService, ProgrammingExerciseRepository programmingExerciseRepository, UserRepository userRepository) {
        this.restTemplate = restTemplate;
        this.jenkinsJobPermissionsService = jenkinsJobPermissionsService;
        this.passwordService = passwordService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a user in Jenkins. Note that the user login acts as
     * a unique identifier in Jenkins.
     *
     * @param user     The user to create
     * @param password The user's password
     */
    @Override
    public void createUser(User user, String password) throws ContinuousIntegrationException {
        if (user.getLogin().equals(jenkinsAdminUsername)) {
            log.debug("Jenkins createUser: Skipping jenkinsAdminUser: {}.", jenkinsAdminUsername);
            return;
        }
        // Only create a user if it doesn't already exist.
        if (getUser(user.getLogin()) != null) {
            throw new JenkinsException("Cannot create user: " + user.getLogin() + " because the login already exists");
        }

        // Make sure the user login contains legal characters.
        if (!isUserLoginLegal(user)) {
            throw new JenkinsException("Cannot create user: " + user.getLogin() + " because the login contains illegal characters");
        }

        try {
            // Create the Jenkins user
            var uri = UriComponentsBuilder.fromHttpUrl(jenkinsServerUrl.toString()).pathSegment("securityRealm", "createAccountByAdmin").build().toUri();
            restTemplate.exchange(uri, HttpMethod.POST, getCreateUserFormHttpEntity(user, password), Void.class);

            // Adds the user to groups of existing programming exercises
            addUserToGroups(user.getLogin(), getUserWithGroups(user).getGroups());
        }
        catch (RestClientException e) {
            throw new JenkinsException("Cannot create user: " + user.getLogin(), e);
        }
    }

    /**
     * Creates an HttpEntity containing the form data required by the POST request for creating a
     * new Jenkins user.
     *
     * @param user     the user to create
     * @param password the user's password
     * @return http entity with the user encoded as the form data.
     */
    private HttpEntity<MultiValueMap<String, String>> getCreateUserFormHttpEntity(User user, String password) {
        var formData = new LinkedMultiValueMap<String, String>();
        formData.add("username", user.getLogin());
        formData.add("password1", password);
        formData.add("password2", password);
        if (usePseudonyms) {
            formData.add("fullname", String.format("User %s", user.getLogin()));
        }
        else {
            formData.add("fullname", user.getName());
        }
        formData.add("email", user.getEmail());

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return new HttpEntity<>(formData, headers);
    }

    @Override
    public void deleteUser(User user) throws ContinuousIntegrationException {
        var userLogin = user.getLogin();
        if (userLogin.equals(jenkinsAdminUsername)) {
            log.debug("Jenkins deleteUser: Skipping jenkinsAdminUser: {}.", jenkinsAdminUsername);
            return;
        }

        // Only delete a user if it exists.
        var jenkinsUser = getUser(userLogin);
        if (jenkinsUser == null) {
            return;
        }

        try {
            var uri = UriComponentsBuilder.fromHttpUrl(jenkinsServerUrl.toString()).pathSegment("user", userLogin, "doDelete").build().toUri();
            restTemplate.exchange(uri, HttpMethod.POST, null, Void.class);
            removeUserFromGroups(userLogin, getUserWithGroups(user).getGroups());
        }
        catch (RestClientException e) {
            throw new JenkinsException("Cannot delete user: " + userLogin, e);
        }
    }

    /**
     * Updates the user in Jenkins with the user data from Artemis. Creates
     * the user if it doesn't exist.
     * <p>
     * Note that it's not possible to change the username of the Jenkins user.
     * We can't support updating the user without setting a new password at the moment. See {@link #updateUserLogin(String, User, String)} for more information
     *
     * @param user     The user to update.
     * @param password The user's password
     */
    @Override
    public void updateUser(User user, String password) throws ContinuousIntegrationException {
        if (user.getLogin().equals(jenkinsAdminUsername)) {
            log.debug("Jenkins updateUser: Skipping jenkinsAdminUser: {}.", jenkinsAdminUsername);
            return;
        }
        if (password == null) {
            return;
        }

        // Only update a user if it exists.
        var jenkinsUser = getUser(user.getLogin());
        if (jenkinsUser != null) {
            deleteUser(user);
            createUser(user, password);
        }
    }

    /**
     * We don't support this for Jenkins because Jenkins doesn't support such a user update
     * We would have to delete and recreate the user, but we can't do this without a new password
     * In the future this could be supported by forcing to set a new password on update.
     *
     * @param oldLogin the old login
     * @param user     The Artemis user with the new login
     * @param password The user's password
     * @throws ContinuousIntegrationException if something went wrong deleting/creating the user
     */
    @Override
    public void updateUserLogin(String oldLogin, User user, String password) throws ContinuousIntegrationException {
        // Intentionally empty. See JavaDoc for details.
    }

    /**
     * We only support updating the user details if a password is provided. See {@link #updateUserLogin(String, User, String)} for more information.
     * If no password is provided we expect the frontend to show an automatic warning.
     *
     * @param oldLogin       the old login if it was updated
     * @param user           the Artemis user
     * @param password       the user's password
     * @param groupsToAdd    groups to add the user to
     * @param groupsToRemove groups to remove the user from
     * @throws ContinuousIntegrationException
     */
    @Override
    public void updateUserAndGroups(String oldLogin, User user, String password, Set<String> groupsToAdd, Set<String> groupsToRemove) throws ContinuousIntegrationException {
        if (password != null) {
            updateUser(user, password);
            removeUserFromGroups(user.getLogin(), groupsToRemove);
            addUserToGroups(user.getLogin(), groupsToAdd);
        }
        else {
            removeUserFromGroups(oldLogin, groupsToRemove);
            addUserToGroups(oldLogin, groupsToAdd);
        }
    }

    /**
     * Adds the Artemis user to a group in Jenkins. Jenkins does not support
     * groups so this function fetches all programming exercises belonging to
     * the groups and assigns the user permissions to them.
     *
     * @param userLogin The user login to add to the group
     * @param groups    The groups to add the user to
     */
    @Override
    public void addUserToGroups(String userLogin, Set<String> groups) throws ContinuousIntegrationException {
        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groups);
        log.info("Update Jenkins permissions for programming exercises: {}", exercises.stream().map(ProgrammingExercise::getProjectKey).toList());
        // TODO: in case we update a tutor group / role here, the tutor should NOT get access to exam exercises before the exam has finished
        exercises.forEach(exercise -> {

            // The exercise's project key is also the name of the Jenkins job that groups all build plans
            // for students, solution, and template.
            var jobName = exercise.getProjectKey();
            var course = exercise.getCourseViaExerciseGroupOrCourseMember();

            if (groups.contains(course.getInstructorGroupName())) {
                try {
                    // We are assigning instructor permissions since the exercise's course instructor group is the same as the one that is specified.
                    jenkinsJobPermissionsService.addPermissionsForUserToFolder(userLogin, jobName, JenkinsJobPermission.getInstructorPermissions());
                }
                catch (IOException e) {
                    throw new JenkinsException("Cannot assign instructor permissions to user: " + userLogin, e);
                }
            }
            else if (groups.contains(course.getEditorGroupName())) {
                try {
                    // We are assigning editor permissions since the exercise's course editor group is the same as the one that is specified.
                    jenkinsJobPermissionsService.addPermissionsForUserToFolder(userLogin, jobName, JenkinsJobPermission.getEditorPermissions());
                }
                catch (IOException e) {
                    throw new JenkinsException("Cannot assign editor permissions to user: " + userLogin, e);
                }
            }
            else if (groups.contains(course.getTeachingAssistantGroupName())) {
                try {
                    // We are assigning teaching assistant permissions since the exercise's course teaching assistant group is the same as the one that is specified.
                    jenkinsJobPermissionsService.addTeachingAssistantPermissionsToUserForFolder(userLogin, jobName);
                }
                catch (IOException e) {
                    throw new JenkinsException("Cannot assign teaching assistant permissions to user: " + userLogin, e);
                }
            }

        });
    }

    /**
     * Removes the Artemis user from the specified groups. Jenkins doesn't support groups so this function fetches
     * all programming exercises belonging to the groups, and revokes the user's permissions from them.
     *
     * @param userLogin The login of the Artemis user to remove from the group
     * @param groups    The groups to remove the user from
     */
    @Override
    public void removeUserFromGroups(String userLogin, Set<String> groups) throws ContinuousIntegrationException {
        // Remove all permissions assigned to the user for each exercise that belongs to the specified groups.
        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groups);
        log.info("Update Jenkins permissions for programming exercises: {}", exercises.stream().map(ProgrammingExercise::getProjectKey).toList());
        exercises.forEach(exercise -> {
            try {
                // The exercise's projectkey is also the name of the Jenkins folder job which groups the student's, solution,
                // and template build plans together
                var jobName = exercise.getProjectKey();
                jenkinsJobPermissionsService.removePermissionsFromUserOfFolder(userLogin, jobName, Set.of(JenkinsJobPermission.values()));
            }
            catch (IOException e) {
                throw new JenkinsException("Cannot revoke permissions from user: " + userLogin, e);
            }
        });
    }

    @Override
    public void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup)
            throws ContinuousIntegrationException {
        var newInstructorGroup = updatedCourse.getInstructorGroupName();
        var newEditorGroup = updatedCourse.getEditorGroupName();
        var newTeachingAssistantGroup = updatedCourse.getTeachingAssistantGroupName();

        // Don't do anything if the groups didn't change
        if (newInstructorGroup.equals(oldInstructorGroup) && newEditorGroup.equals(oldEditorGroup) && newTeachingAssistantGroup.equals(oldTeachingAssistantGroup)) {
            return;
        }

        // Remove all permissions assigned to the instructors and teaching assistants that do not belong to the course anymore.
        var programmingExercises = programmingExerciseRepository.findAllProgrammingExercisesInCourseOrInExamsOfCourse(updatedCourse);
        log.info("Update Jenkins permissions for programming exercises: {}", programmingExercises.stream().map(ProgrammingExercise::getProjectKey).toList());
        removePermissionsFromInstructorsAndEditorsAndTAsForCourse(oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup, updatedCourse, programmingExercises);

        // Assign teaching assistant and instructor permissions
        assignPermissionsToInstructorAndEditorAndTAsForCourse(updatedCourse, programmingExercises);
    }

    /**
     * Assigns teaching assistant and/or editor and/or instructor permissions to each user belonging to the teaching assistant/editor/instructor groups of the course.
     *
     * @param course               the course
     * @param programmingExercises list of programmingExercises for which the permissions should be changed
     */
    private void assignPermissionsToInstructorAndEditorAndTAsForCourse(Course course, List<ProgrammingExercise> programmingExercises) {
        var instructors = userRepository.findAllInGroupWithAuthorities(course.getInstructorGroupName()).stream().map(User::getLogin).collect(Collectors.toSet());
        var editors = userRepository.findAllInGroupWithAuthorities(course.getEditorGroupName()).stream().map(User::getLogin).collect(Collectors.toSet());
        var teachingAssistants = userRepository.findAllInGroupWithAuthorities(course.getTeachingAssistantGroupName()).stream().map(User::getLogin).collect(Collectors.toSet());

        // Courses can have the same groups. We do not want to add/remove users from exercises of other courses belonging to the same group

        programmingExercises.forEach(exercise -> {
            var job = exercise.getProjectKey();
            try {
                jenkinsJobPermissionsService.addInstructorAndEditorAndTAPermissionsToUsersForFolder(teachingAssistants, editors, instructors, job);
            }
            catch (IOException e) {
                throw new JenkinsException("Cannot assign teaching assistant and editor and instructor permissions for job: " + job, e);
            }
        });

    }

    /**
     * Removes all permissions assigned to instructors and editors and teaching assistants for the specified course. The function
     * fetches all exercises that belong to the course and removes all permissions assigned to all instructors and editors and
     * teaching assistants belonging to the groups.
     *
     * @param instructorGroup        the group of instructors
     * @param editorGroup            the group of editors
     * @param teachingAssistantGroup the group of teaching assistants
     * @param course                 the course
     * @param programmingExercises   list of programmingExercises for which the permissions should be changed
     */
    private void removePermissionsFromInstructorsAndEditorsAndTAsForCourse(String instructorGroup, String editorGroup, String teachingAssistantGroup, Course course,
            List<ProgrammingExercise> programmingExercises) {
        // Fetch all instructors and editors and teaching assistants belonging to the group that was removed from the course.
        var oldInstructors = userRepository.findAllInGroupWithAuthorities(instructorGroup);
        var oldEditors = userRepository.findAllInGroupWithAuthorities(editorGroup);
        var oldTeachingAssistants = userRepository.findAllInGroupWithAuthorities(teachingAssistantGroup);
        var usersFromOldGroup = Stream.concat(oldInstructors.stream(), Stream.concat(oldEditors.stream(), oldTeachingAssistants.stream())).map(User::getLogin)
                .collect(Collectors.toSet());

        // Revoke all permissions.
        programmingExercises.forEach(exercise -> {
            try {
                jenkinsJobPermissionsService.removePermissionsFromUsersForFolder(usersFromOldGroup, exercise.getProjectKey(), Set.of(JenkinsJobPermission.values()));
            }
            catch (IOException e) {
                throw new JenkinsException("Cannot remove permissions from all users for job: " + exercise.getProjectKey(), e);
            }
        });
    }

    /**
     * Gets a Jenkins user if it exists. Otherwise returns
     * null.
     *
     * @param userLogin the login of the user look up
     * @return the Jenkins user or null if the user doesn't exist
     */
    private JenkinsUserDTO getUser(String userLogin) throws ContinuousIntegrationException {
        try {
            var uri = UriComponentsBuilder.fromHttpUrl(jenkinsServerUrl.toString()).pathSegment("user", userLogin, "api", "json").build().toUri();
            return restTemplate.exchange(uri, HttpMethod.GET, null, JenkinsUserDTO.class).getBody();

        }
        catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return null;
            }

            var errorMessage = "Could not get user " + userLogin;
            log.error(errorMessage, e);
            throw new JenkinsException(errorMessage, e);
        }
    }

    /**
     * The Jenkins username acts as a unique identifier and
     * can only contain alphanumeric characters, underscore and dash
     *
     * @param user The user
     * @return whether the user login is legal or not
     */
    private boolean isUserLoginLegal(User user) {
        String regex = "^[a-zA-Z0-9_-]*$";
        return user.getLogin().matches(regex);
    }

    private User getUserWithGroups(User user) throws JenkinsException {
        if (Hibernate.isInitialized(user.getGroups())) {
            return user;
        }
        log.debug("Groups of user were not initialized. Fetching groups from repository.");
        Optional<User> userWithGroups = userRepository.findOneWithGroupsByLogin(user.getLogin());
        if (userWithGroups.isEmpty()) {
            throw new JenkinsException("Cannot find user in repository: " + user.getLogin());
        }

        return userWithGroups.get();
    }
}
