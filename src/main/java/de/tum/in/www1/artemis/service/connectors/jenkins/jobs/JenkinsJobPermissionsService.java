package de.tum.in.www1.artemis.service.connectors.jenkins.jobs;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.offbytwo.jenkins.JenkinsServer;

@Service
@Profile("jenkins")
public class JenkinsJobPermissionsService {

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final JenkinsServer jenkinsServer;

    private final JenkinsJobService jenkinsJobService;

    public JenkinsJobPermissionsService(JenkinsServer jenkinsServer, JenkinsJobService jenkinsJobService) {
        this.jenkinsServer = jenkinsServer;
        this.jenkinsJobService = jenkinsJobService;
    }

    /**
     * Assigns teaching assistant and instructor permissions to users for the specified Jenkins job.
     *
     * @param taLogins logins of the teaching assistants
     * @param instructorLogins logins of the instructors
     * @param folderName the name of the Jenkins folder
     * @param jobName the name of the Jenkins job
     * @throws IOException exception thrown when retrieving/updating the Jenkins folder failed
     */
    public void addInstructorAndTAPermissionsToUsersForJob(Set<String> taLogins, Set<String> instructorLogins, String folderName, String jobName) throws IOException {
        var jobConfig = jenkinsJobService.getJobConfig(folderName, jobName);

        // Revoke previously-assigned permissions
        var allPermissions = Set.of(JenkinsJobPermission.values());
        JenkinsJobPermissionsUtils.removePermissionsFromJob(jobConfig, allPermissions, taLogins);
        JenkinsJobPermissionsUtils.removePermissionsFromJob(jobConfig, allPermissions, instructorLogins);

        JenkinsJobPermissionsUtils.addPermissionsToJob(jobConfig, JenkinsJobPermission.getTeachingAssistantPermissions(), taLogins);
        JenkinsJobPermissionsUtils.addPermissionsToJob(jobConfig, JenkinsJobPermission.getInstructorPermissions(), instructorLogins);

        jenkinsJobService.updateJob(folderName, jobName, jobConfig);

        addInstructorAndTAPermissionsToUsersForFolder(taLogins, instructorLogins, folderName);
    }

    /**
     * Assigns teaching assistant and instructor permissions to users for the specified Jenkins folder.
     *
     * @param taLogins logins of the teaching assistants
     * @param instructorLogins logins of the instructors
     * @param folderName the name of the Jenkins folder
     * @throws IOException exception thrown when retrieving/updating the Jenkins folder failed
     */
    public void addInstructorAndTAPermissionsToUsersForFolder(Set<String> taLogins, Set<String> instructorLogins, String folderName) throws IOException {
        var folderConfig = jenkinsJobService.getFolderConfig(folderName);

        // Revoke previously-assigned permissions
        JenkinsJobPermissionsUtils.removePermissionsFromFolder(folderConfig, Set.of(JenkinsJobPermission.values()), taLogins);
        JenkinsJobPermissionsUtils.removePermissionsFromFolder(folderConfig, Set.of(JenkinsJobPermission.values()), instructorLogins);

        // Assign teaching assistant permissions
        JenkinsJobPermissionsUtils.addPermissionsToFolder(folderConfig, JenkinsJobPermission.getTeachingAssistantPermissions(), taLogins);
        JenkinsJobPermissionsUtils.addPermissionsToFolder(folderConfig, JenkinsJobPermission.getInstructorPermissions(), instructorLogins);

        jenkinsServer.updateJob(folderName, folderConfig.toString(), useCrumb);
    }

    /**
     * Assigns teaching assistant permissions to the user. Teaching assistants only have write access to
     * build plans.
     *
     * @param userLogin the login of the user that will have the permissions
     * @param folderName the name of the Jenkins folder
     * @throws IOException exception thrown when retrieving/updating the Jenkins folder failed
     */
    public void addTeachingAssistantPermissionsToUserForFolder(String userLogin, String folderName) throws IOException {
        var folderConfig = jenkinsJobService.getFolderConfig(folderName);

        // Revoke previously-assigned permissions
        JenkinsJobPermissionsUtils.removePermissionsFromFolder(folderConfig, Set.of(JenkinsJobPermission.values()), Set.of(userLogin));

        // Assign teaching assistant permissions
        JenkinsJobPermissionsUtils.addPermissionsToFolder(folderConfig, JenkinsJobPermission.getTeachingAssistantPermissions(), Set.of(userLogin));

        jenkinsServer.updateJob(folderName, folderConfig.toString(), useCrumb);
    }

    /**
     * Adds all Jenkins folder permissions for the specific Jenkins user to the folder.
     * This function does not overwrite permissions that have already been given.
     *
     * @param userLogin the login of the user that will have the permissions
     * @param folderName the name of the Jenkins folder
     * @param permissions a list of permissions to give to the user
     * @throws IOException thrown when retrieving/updating the Jenkins folder failed
     */
    public void addPermissionsForUserToFolder(String userLogin, String folderName, Set<JenkinsJobPermission> permissions) throws IOException {
        addPermissionsForUsersToFolder(Set.of(userLogin), folderName, permissions);
    }

    /**
     * Adds all Jenkins folder permissions for the specific Jenkins users to the folder.
     * This function does not overwrite permissions that have already been given.
     *
     * @param userLogins the logins of the users that will have the permissions
     * @param folderName the name of the Jenkins folder
     * @param permissions a list of permissions to give to the users
     * @throws IOException thrown when retrieving/updating the Jenkins folder failed
     */
    public void addPermissionsForUsersToFolder(Set<String> userLogins, String folderName, Set<JenkinsJobPermission> permissions) throws IOException {
        var folderConfig = jenkinsJobService.getFolderConfig(folderName);
        JenkinsJobPermissionsUtils.addPermissionsToFolder(folderConfig, permissions, userLogins);
        jenkinsServer.updateJob(folderName, folderConfig.toString(), useCrumb);
    }

    /**
     * Removes the permissions from the user for the specific Jenkins folder.
     *
     * @param userLogin the login of the user to remove the permissions
     * @param folderName the name of the Jenkins folder
     * @param permissionsToRemove a list of permissions to remove from the user
     * @throws IOException thrown when retrieving/updating the Jenkins folder failed
     */
    public void removePermissionsFromUserOfFolder(String userLogin, String folderName, Set<JenkinsJobPermission> permissionsToRemove) throws IOException {
        removePermissionsFromUsersForFolder(Set.of(userLogin), folderName, permissionsToRemove);
    }

    /**
     * Removes the permissions from the users for the specific Jenkins folder.
     *
     * @param userLogins the logins of the users to remove the permissions
     * @param folderName the name of the Jenkins folder
     * @param permissionsToRemove a list of permissions to remove from the users
     * @throws IOException thrown when retrieving/updating the Jenkins folder failed
     */
    public void removePermissionsFromUsersForFolder(Set<String> userLogins, String folderName, Set<JenkinsJobPermission> permissionsToRemove) throws IOException {
        var folderConfig = jenkinsJobService.getFolderConfig(folderName);
        JenkinsJobPermissionsUtils.removePermissionsFromFolder(folderConfig, permissionsToRemove, userLogins);
        jenkinsServer.updateJob(folderName, folderConfig.toString(), useCrumb);
    }
}
